package uk.gov.hmcts.cp.bulkresulting.filters;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ActionHeaderFilterTest {

    private final ActionHeaderFilter filter = new ActionHeaderFilter();

    private HttpServletRequest pass(final MockHttpServletRequest request) throws Exception {
        final AtomicReference<HttpServletRequest> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> seen.set((HttpServletRequest) req));
        return seen.get();
    }

    @Test
    void known_paths_should_get_their_action_even_when_the_caller_sent_another() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/bulkresulting/hearings");
        request.setServletPath("/hearings");
        request.addHeader("CPP-ACTION", "something.else");

        final HttpServletRequest seen = pass(request);

        assertThat(seen.getHeader("CPP-ACTION")).isEqualTo(ActionHeaderFilter.SEARCH_HEARINGS);
        assertThat(Collections.list(seen.getHeaders("cpp-action"))).containsExactly(ActionHeaderFilter.SEARCH_HEARINGS);
        assertThat(Collections.list(seen.getHeaderNames())).containsOnlyOnce("CPP-ACTION");
        assertThat(seen.getHeader("Other")).isNull();
        assertThat(Collections.list(seen.getHeaders("Other"))).isEmpty();
    }

    @Test
    void config_should_get_its_action_from_the_request_uri_when_there_is_no_servlet_path() throws Exception {
        final HttpServletRequest seen = pass(new MockHttpServletRequest("GET", "/config"));

        assertThat(seen.getHeader("CPP-ACTION")).isEqualTo(ActionHeaderFilter.GET_CONFIG);
        assertThat(Collections.list(seen.getHeaderNames())).contains("CPP-ACTION");
    }

    @Test
    void root_should_get_its_action() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/bulkresulting/");
        request.setServletPath("/");

        assertThat(pass(request).getHeader("CPP-ACTION")).isEqualTo(ActionHeaderFilter.ROOT);
    }

    @Test
    void unknown_paths_should_pass_unchanged() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setServletPath("/actuator/health");

        assertThat(pass(request)).isSameAs(request);
    }
}
