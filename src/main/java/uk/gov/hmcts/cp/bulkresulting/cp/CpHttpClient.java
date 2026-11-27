package uk.gov.hmcts.cp.bulkresulting.cp;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * GETs JSON from the Common Platform context query APIs as the calling user. Every call carries
 * the user's {@code CJSCPPUID}, and the session correlation id when the caller sent one.
 *
 * <p>Connect and read failures, 408, 429, 500, 502, 503 and 504 are retried with a doubling
 * back-off, honouring a delta-seconds {@code Retry-After} up to the cap. Any other non-2xx answer
 * is not retried. A 2xx with an empty body is an empty result, which progression returns for a
 * case it does not hold.</p>
 */
public class CpHttpClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(CpHttpClient.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Pattern DELTA_SECONDS = Pattern.compile("\\d{1,10}");
    private static final String USER_HEADER = "CJSCPPUID";
    private static final String CORRELATION_HEADER = "CPPCLIENTCORRELATIONID";
    private static final String RETRY_AFTER = "Retry-After";
    private static final int HTTP_OK = 200;
    private static final int HTTP_MULTIPLE_CHOICES = 300;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_FORBIDDEN = 403;
    private static final int HTTP_REQUEST_TIMEOUT = 408;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_INTERNAL_SERVER_ERROR = 500;
    private static final int HTTP_BAD_GATEWAY = 502;
    private static final int HTTP_SERVICE_UNAVAILABLE = 503;
    private static final int HTTP_GATEWAY_TIMEOUT = 504;

    private final RestClient restClient;
    private final String baseUrl;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final Sleeper sleeper;

    /** Waits between attempts; replaced in tests. */
    @FunctionalInterface
    public interface Sleeper {
        /**
         * Waits.
         *
         * @param wait how long
         * @throws InterruptedException when interrupted
         */
        void sleep(Duration wait) throws InterruptedException;
    }

    /**
     * Creates the client.
     *
     * @param baseUrl        the internal gateway host
     * @param connectTimeout connect timeout per attempt
     * @param readTimeout    read timeout per attempt
     * @param maxAttempts    attempts per call, including the first
     * @param initialBackoff the first back-off
     * @param maxBackoff     the back-off cap
     * @param sleeper        waits between attempts
     */
    // The HttpClient is held by the RestClient for the client's whole lifetime.
    @SuppressWarnings("PMD.CloseResource")
    public CpHttpClient(final String baseUrl, final Duration connectTimeout, final Duration readTimeout,
                        final int maxAttempts, final Duration initialBackoff, final Duration maxBackoff,
                        final Sleeper sleeper) {
        this.baseUrl = baseUrl;
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.sleeper = sleeper;
        final HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(connectTimeout)
                .build();
        final JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * Builds a URI under the base URL.
     *
     * @param path        the path, with {@code {name}} variables
     * @param queryParams query parameters, added in order; null values are left out
     * @param uriVariables values for the path variables
     * @return the encoded URI
     */
    public URI uri(final String path, final Map<String, ?> queryParams, final Object... uriVariables) {
        final UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl).path(path);
        queryParams.forEach((name, value) -> {
            if (value != null) {
                builder.queryParam(name, value);
            }
        });
        return builder.encode().buildAndExpand(uriVariables).toUri();
    }

    /**
     * GETs a JSON document.
     *
     * @param dependency the dependency's short name, used in logs and errors
     * @param uri        the full URI
     * @param accept     the vendor media type to accept
     * @param caller     the calling user
     * @return the body, or empty for a 2xx with no body
     * @throws CpDependencyException when no usable answer arrives
     */
    public Optional<JsonNode> getJson(final String dependency, final URI uri, final String accept,
                                      final CallerContext caller) {
        Duration backoff = initialBackoff;
        Attempt outcome = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            outcome = tryOnce(dependency, uri, accept, caller);
            if (!(outcome instanceof Attempt.Retryable retryable) || attempt == maxAttempts) {
                break;
            }
            final Duration wait = waitFor(retryable.retryAfter(), backoff);
            LOGGER.warn("Retrying {} after status {} (attempt {} of {}, waiting {} ms)",
                    dependency, retryable.status(), attempt, maxAttempts, wait.toMillis());
            pause(dependency, wait);
            backoff = capped(backoff.multipliedBy(2));
        }
        return resolve(dependency, outcome);
    }

    private Optional<JsonNode> resolve(final String dependency, final Attempt outcome) {
        final Optional<JsonNode> result;
        if (outcome instanceof Attempt.Success success) {
            result = success.body();
        } else if (outcome instanceof Attempt.Retryable retryable) {
            throw exhausted(dependency, retryable.status());
        } else {
            final Attempt.Failed failed = (Attempt.Failed) outcome;
            throw new CpDependencyException(dependency, failed.kind(), failed.status(), failed.reason());
        }
        return result;
    }

    private static CpDependencyException exhausted(final String dependency, final int status) {
        final boolean unavailable = status == 0 || status == HTTP_SERVICE_UNAVAILABLE
                || status == HTTP_GATEWAY_TIMEOUT || status == HTTP_REQUEST_TIMEOUT
                || status == HTTP_TOO_MANY_REQUESTS;
        final CpDependencyException.Kind kind = unavailable
                ? CpDependencyException.Kind.UNAVAILABLE
                : CpDependencyException.Kind.BAD_RESPONSE;
        return new CpDependencyException(dependency, kind, status,
                dependency + " did not answer successfully after retries (last status " + status + ")");
    }

    private Attempt tryOnce(final String dependency, final URI uri, final String accept,
                            final CallerContext caller) {
        Attempt attempt;
        try {
            attempt = restClient.get().uri(uri)
                    .header("Accept", accept)
                    .headers(withCaller(caller))
                    .exchange((request, response) -> classify(dependency,
                            response.getStatusCode().value(),
                            response.getHeaders().getFirst(RETRY_AFTER),
                            response.getBody()), true);
        } catch (final ResourceAccessException transportFailure) {
            LOGGER.warn("No answer from {}: {}", dependency, transportFailure.getClass().getSimpleName());
            attempt = new Attempt.Retryable(0, OptionalLong.empty());
        }
        return attempt;
    }

    private static Consumer<HttpHeaders> withCaller(final CallerContext caller) {
        return headers -> {
            headers.set(USER_HEADER, caller.userId());
            caller.correlationId().ifPresent(id -> headers.set(CORRELATION_HEADER, id));
        };
    }

    private static Attempt classify(final String dependency, final int status, final String retryAfter,
                                    final InputStream body) throws IOException {
        final Attempt attempt;
        if (status >= HTTP_OK && status < HTTP_MULTIPLE_CHOICES) {
            attempt = parse(dependency, status, body);
        } else if (isRetryable(status)) {
            attempt = new Attempt.Retryable(status, retryAfterSeconds(retryAfter));
        } else if (status == HTTP_UNAUTHORIZED || status == HTTP_FORBIDDEN) {
            attempt = new Attempt.Failed(CpDependencyException.Kind.ACCESS_DENIED, status,
                    dependency + " refused the user (status " + status + ")");
        } else {
            attempt = new Attempt.Failed(CpDependencyException.Kind.BAD_RESPONSE, status,
                    dependency + " answered with status " + status);
        }
        return attempt;
    }

    private static Attempt parse(final String dependency, final int status, final InputStream body)
            throws IOException {
        Attempt attempt;
        final byte[] bytes = body.readAllBytes();
        if (bytes.length == 0) {
            attempt = new Attempt.Success(Optional.empty());
        } else {
            try {
                final JsonNode tree = MAPPER.readTree(bytes);
                attempt = new Attempt.Success(tree == null || tree.isMissingNode() || tree.isNull()
                        ? Optional.empty() : Optional.of(tree));
            } catch (final JacksonException unreadable) {
                attempt = new Attempt.Failed(CpDependencyException.Kind.BAD_RESPONSE, status,
                        dependency + " answered with a body that is not JSON");
            }
        }
        return attempt;
    }

    private static boolean isRetryable(final int status) {
        return status == HTTP_REQUEST_TIMEOUT || status == HTTP_TOO_MANY_REQUESTS
                || status == HTTP_INTERNAL_SERVER_ERROR || status == HTTP_BAD_GATEWAY
                || status == HTTP_SERVICE_UNAVAILABLE || status == HTTP_GATEWAY_TIMEOUT;
    }

    private static OptionalLong retryAfterSeconds(final String header) {
        final String value = header == null ? "" : header.trim();
        return DELTA_SECONDS.matcher(value).matches()
                ? OptionalLong.of(Long.parseLong(value))
                : OptionalLong.empty();
    }

    private Duration waitFor(final OptionalLong retryAfter, final Duration backoff) {
        return retryAfter.isPresent()
                ? capped(Duration.ofSeconds(retryAfter.getAsLong()))
                : capped(backoff);
    }

    private Duration capped(final Duration wait) {
        return wait.compareTo(maxBackoff) > 0 ? maxBackoff : wait;
    }

    private void pause(final String dependency, final Duration wait) {
        try {
            sleeper.sleep(wait);
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CpDependencyException(dependency, CpDependencyException.Kind.UNAVAILABLE, 0,
                    "interrupted while waiting to retry " + dependency);
        }
    }

    /** The classified result of one attempt. */
    private sealed interface Attempt permits Attempt.Success, Attempt.Retryable, Attempt.Failed {

        /** A 2xx answer. */
        record Success(Optional<JsonNode> body) implements Attempt {
        }

        /** An answer, or no answer, that may be retried; status 0 means no answer. */
        record Retryable(int status, OptionalLong retryAfter) implements Attempt {
        }

        /** An answer that is not retried. */
        record Failed(CpDependencyException.Kind kind, int status, String reason) implements Attempt {
        }
    }
}
