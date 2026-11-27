package uk.gov.hmcts.cp.bulkresulting.cp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CpHttpClientTest {

    private static final String PATH = "/thing";
    private static final CallerContext CALLER = new CallerContext("user-1", Optional.of("corr-1"));

    private final WireMockServer wireMock = new WireMockServer(options().dynamicPort());
    private final List<Duration> waits = new ArrayList<>();
    private CpHttpClient client;

    @BeforeEach
    void start() {
        wireMock.start();
        client = new CpHttpClient(wireMock.baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(2), 3,
                Duration.ofMillis(100), Duration.ofMillis(300), waits::add);
    }

    @AfterEach
    void stop() {
        wireMock.stop();
    }

    private URI uri() {
        return client.uri(PATH, Map.of());
    }

    @Test
    void fetching_json_with_a_body_should_return_it_and_send_the_caller_headers() {
        wireMock.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("{\"a\":1}")));

        final Optional<tools.jackson.databind.JsonNode> body = client.getJson("dep", uri(), "application/x", CALLER);

        assertThat(body).hasValueSatisfying(node -> assertThat(node.get("a").asInt()).isEqualTo(1));
        wireMock.verify(getRequestedFor(urlPathEqualTo(PATH)).withHeader("CJSCPPUID", equalTo("user-1"))
                .withHeader("CPPCLIENTCORRELATIONID", equalTo("corr-1")).withHeader("Accept", equalTo("application/x")));
    }

    @Test
    void fetching_json_with_an_empty_body_should_return_empty() {
        wireMock.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(200)));

        assertThat(client.getJson("dep", uri(), "application/x", new CallerContext("u", Optional.empty()))).isEmpty();
    }

    @Test
    void fetching_json_with_a_json_null_should_return_empty() {
        wireMock.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("null")));

        assertThat(client.getJson("dep", uri(), "application/x", CALLER)).isEmpty();
    }

    @Test
    void fetching_json_after_a_503_then_200_should_retry_and_return_the_body() {
        wireMock.stubFor(get(urlPathEqualTo(PATH)).inScenario("s").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "7")).willSetStateTo("ok"));
        wireMock.stubFor(get(urlPathEqualTo(PATH)).inScenario("s").whenScenarioStateIs("ok")
                .willReturn(okJson("{}")));

        assertThat(client.getJson("dep", uri(), "application/x", CALLER)).isPresent();
        assertThat(waits).containsExactly(Duration.ofMillis(300));
    }

    @Test
    void fetching_json_with_repeated_503_should_fail_unavailable_after_all_attempts() {
        wireMock.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.getJson("dep", uri(), "application/x", CALLER))
                .isInstanceOfSatisfying(CpDependencyException.class, failure -> {
                    assertThat(failure.getKind()).isEqualTo(CpDependencyException.Kind.UNAVAILABLE);
                    assertThat(failure.getUpstreamStatus()).isEqualTo(503);
                    assertThat(failure.getDependency()).isEqualTo("dep");
                });
        wireMock.verify(3, getRequestedFor(urlPathEqualTo(PATH)));
        assertThat(waits).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200));
    }

    @Test
    void fetching_json_with_repeated_500_should_fail_with_bad_response() {
        wireMock.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.getJson("dep", uri(), "application/x", CALLER))
                .isInstanceOfSatisfying(CpDependencyException.class,
                        failure -> assertThat(failure.getKind()).isEqualTo(CpDependencyException.Kind.BAD_RESPONSE));
    }

    @Test
    void fetching_json_with_403_should_fail_access_denied_without_retrying() {
        wireMock.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(403)));

        assertThatThrownBy(() -> client.getJson("dep", uri(), "application/x", CALLER))
                .isInstanceOfSatisfying(CpDependencyException.class,
                        failure -> assertThat(failure.getKind()).isEqualTo(CpDependencyException.Kind.ACCESS_DENIED));
        wireMock.verify(1, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    void fetching_json_with_401_should_fail_access_denied() {
        wireMock.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> client.getJson("dep", uri(), "application/x", CALLER))
                .isInstanceOfSatisfying(CpDependencyException.class,
                        failure -> assertThat(failure.getKind()).isEqualTo(CpDependencyException.Kind.ACCESS_DENIED));
    }

    @Test
    void fetching_json_with_404_should_fail_with_bad_response_without_retrying() {
        wireMock.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client.getJson("dep", uri(), "application/x", CALLER))
                .isInstanceOfSatisfying(CpDependencyException.class,
                        failure -> assertThat(failure.getKind()).isEqualTo(CpDependencyException.Kind.BAD_RESPONSE));
        wireMock.verify(1, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    void fetching_json_with_a_body_that_is_not_json_should_fail_with_bad_response() {
        wireMock.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody("<html>")));

        assertThatThrownBy(() -> client.getJson("dep", uri(), "application/x", CALLER))
                .isInstanceOfSatisfying(CpDependencyException.class,
                        failure -> assertThat(failure.getKind()).isEqualTo(CpDependencyException.Kind.BAD_RESPONSE));
    }

    @Test
    void fetching_json_with_no_server_should_fail_unavailable() {
        final CpHttpClient unreachable = new CpHttpClient("http://localhost:1", Duration.ofMillis(200),
                Duration.ofMillis(200), 2, Duration.ofMillis(1), Duration.ofMillis(1), waits::add);

        assertThatThrownBy(() -> unreachable.getJson("dep", unreachable.uri(PATH, Map.of()), "application/x", CALLER))
                .isInstanceOfSatisfying(CpDependencyException.class, failure -> {
                    assertThat(failure.getKind()).isEqualTo(CpDependencyException.Kind.UNAVAILABLE);
                    assertThat(failure.getUpstreamStatus()).isZero();
                });
    }

    @Test
    void fetching_json_interrupted_while_waiting_should_fail_unavailable_and_keep_the_interrupt() {
        wireMock.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(429)));
        final CpHttpClient interrupted = new CpHttpClient(wireMock.baseUrl(), Duration.ofSeconds(1),
                Duration.ofSeconds(1), 3, Duration.ofMillis(1), Duration.ofMillis(1), wait -> {
                    throw new InterruptedException("stop");
                });

        assertThatThrownBy(() -> interrupted.getJson("dep", uri(), "application/x", CALLER))
                .isInstanceOfSatisfying(CpDependencyException.class,
                        failure -> assertThat(failure.getKind()).isEqualTo(CpDependencyException.Kind.UNAVAILABLE));
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void uri_should_leave_out_null_query_values_and_expand_path_variables() {
        final Map<String, Object> query = new java.util.LinkedHashMap<>();
        query.put("a", "x y");
        query.put("b", null);

        assertThat(client.uri("/c/{id}", query, "42").toString()).isEqualTo(wireMock.baseUrl() + "/c/42?a=x%20y");
    }
}
