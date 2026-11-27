package uk.gov.hmcts.cp.bulkresulting.web;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import uk.gov.hmcts.cp.bulkresulting.cp.CpDependencyException;
import uk.gov.hmcts.cp.bulkresulting.search.CourtNotPermittedException;
import uk.gov.hmcts.cp.bulkresulting.search.InvalidCallerException;
import uk.gov.hmcts.cp.bulkresulting.search.InvalidFilterException;
import uk.gov.hmcts.cp.bulkresulting.search.TooManyResultsException;

/**
 * Maps failures to RFC 9457 problem details. Each carries a machine-readable {@code code}.
 * Details never contain personal data.
 */
@RestControllerAdvice
public class ProblemDetailsHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProblemDetailsHandler.class);
    private static final String CODE = "code";

    /**
     * A filter the service refuses.
     *
     * @param exception the failure
     * @return 400
     */
    @ExceptionHandler(InvalidFilterException.class)
    public ProblemDetail invalidFilter(final InvalidFilterException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_FILTER", exception.getMessage());
    }

    /**
     * A parameter that is missing or of the wrong type.
     *
     * @param exception the failure
     * @return 400
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
        HandlerMethodValidationException.class})
    public ProblemDetail badParameter(final Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "A query parameter is missing or invalid");
    }

    /**
     * A missing identity header.
     *
     * @param exception the failure
     * @return 401 for CJSCPPUID, otherwise 400
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail missingHeader(final MissingRequestHeaderException exception) {
        return "CJSCPPUID".equalsIgnoreCase(exception.getHeaderName())
                ? problem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "The CJSCPPUID header is required")
                : problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "A required header is missing");
    }

    /**
     * An identity header that is not a user id.
     *
     * @param exception the failure
     * @return 401
     */
    @ExceptionHandler(InvalidCallerException.class)
    public ProblemDetail invalidCaller(final InvalidCallerException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", exception.getMessage());
    }

    /**
     * A court outside the caller's court centres.
     *
     * @param exception the failure
     * @return 403
     */
    @ExceptionHandler(CourtNotPermittedException.class)
    public ProblemDetail courtNotPermitted(final CourtNotPermittedException exception) {
        return problem(HttpStatus.FORBIDDEN, "COURT_NOT_PERMITTED", exception.getMessage());
    }

    /**
     * A search too broad to show completely.
     *
     * @param exception the failure
     * @return 422
     */
    @ExceptionHandler(TooManyResultsException.class)
    public ProblemDetail tooManyResults(final TooManyResultsException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "TOO_MANY_RESULTS", exception.getMessage());
    }

    /**
     * A Common Platform dependency that failed.
     *
     * @param exception the failure
     * @return 403 when it refused the user, 503 when it gave no answer, otherwise 502
     */
    @ExceptionHandler(CpDependencyException.class)
    public ProblemDetail dependency(final CpDependencyException exception) {
        LOGGER.error("Dependency {} failed ({}, upstream status {}): {}", exception.getDependency(),
                exception.getKind(), exception.getUpstreamStatus(), exception.getMessage());
        final ProblemDetail problem = switch (exception.getKind()) {
            case ACCESS_DENIED -> problem(HttpStatus.FORBIDDEN, "DEPENDENCY_ACCESS_DENIED",
                    exception.getDependency() + " refused the request for this user");
            case UNAVAILABLE -> problem(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                    exception.getDependency() + " is unavailable");
            case BAD_RESPONSE -> problem(HttpStatus.BAD_GATEWAY, "DEPENDENCY_FAILED",
                    exception.getDependency() + " returned an error");
        };
        problem.setProperty("dependency", exception.getDependency());
        return problem;
    }

    private static ProblemDetail problem(final HttpStatus status, final String code, final String detail) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        problem.setProperty(CODE, code);
        return problem;
    }
}
