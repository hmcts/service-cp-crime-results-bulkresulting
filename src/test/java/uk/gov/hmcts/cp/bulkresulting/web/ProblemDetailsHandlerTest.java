package uk.gov.hmcts.cp.bulkresulting.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;
import uk.gov.hmcts.cp.bulkresulting.cp.CpDependencyException;
import uk.gov.hmcts.cp.bulkresulting.search.InvalidCallerException;

class ProblemDetailsHandlerTest {

    private final ProblemDetailsHandler handler = new ProblemDetailsHandler();

    @Test
    void dependency_access_denied_should_be_403_with_the_dependency_named() {
        final ProblemDetail problem = handler.dependency(new CpDependencyException("listing",
                CpDependencyException.Kind.ACCESS_DENIED, 403, "refused"));

        assertThat(problem.getStatus()).isEqualTo(403);
        assertThat(problem.getProperties()).containsEntry("code", "DEPENDENCY_ACCESS_DENIED")
                .containsEntry("dependency", "listing");
    }

    @Test
    void missing_headers_should_be_401_for_the_user_and_400_otherwise() {
        final MethodParameter parameter = mock(MethodParameter.class);

        assertThat(handler.missingHeader(new MissingRequestHeaderException("CJSCPPUID", parameter)).getStatus())
                .isEqualTo(401);
        assertThat(handler.missingHeader(new MissingRequestHeaderException("X-OTHER", parameter)).getStatus())
                .isEqualTo(400);
    }

    @Test
    void invalid_caller_should_be_401() {
        assertThat(handler.invalidCaller(new InvalidCallerException("bad")).getStatus()).isEqualTo(401);
    }
}
