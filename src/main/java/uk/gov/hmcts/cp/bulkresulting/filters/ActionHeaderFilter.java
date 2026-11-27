package uk.gov.hmcts.cp.bulkresulting.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sets the CPP-ACTION header from the request path, for the Drools authorisation filter.
 *
 * <p>The action is always derived from the path and never taken from the caller, even when the
 * request already carries a CPP-ACTION header, so a caller cannot choose a more permissive action.
 * Paths this filter does not recognise are passed through unchanged.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ActionHeaderFilter extends OncePerRequestFilter {

    /** The action for {@code GET /hearings}. */
    public static final String SEARCH_HEARINGS = "bulkresulting.search-hearings";
    /** The action for {@code GET /config}. */
    public static final String GET_CONFIG = "bulkresulting.get-config";
    /** The action for {@code GET /}. */
    public static final String ROOT = "bulkresulting.root";

    private static final String ACTION_HEADER = "CPP-ACTION";
    private static final Map<String, String> PATH_TO_ACTION = Map.of(
            "/hearings", SEARCH_HEARINGS,
            "/config", GET_CONFIG,
            "/", ROOT);

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        final String servletPath = request.getServletPath();
        final String path = servletPath == null || servletPath.isEmpty() ? request.getRequestURI() : servletPath;
        final String action = PATH_TO_ACTION.get(path);
        if (action == null) {
            filterChain.doFilter(request, response);
        } else {
            filterChain.doFilter(new ActionHeaderRequestWrapper(request, action), response);
        }
    }

    private static final class ActionHeaderRequestWrapper extends HttpServletRequestWrapper {

        private final String action;

        /* default */ ActionHeaderRequestWrapper(final HttpServletRequest request, final String action) {
            super(request);
            this.action = action;
        }

        @Override
        public String getHeader(final String name) {
            return ACTION_HEADER.equalsIgnoreCase(name) ? action : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(final String name) {
            return ACTION_HEADER.equalsIgnoreCase(name)
                    ? Collections.enumeration(List.of(action))
                    : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            final List<String> names = Collections.list(super.getHeaderNames());
            if (names.stream().noneMatch(ACTION_HEADER::equalsIgnoreCase)) {
                names.add(ACTION_HEADER);
            }
            return Collections.enumeration(names);
        }
    }
}
