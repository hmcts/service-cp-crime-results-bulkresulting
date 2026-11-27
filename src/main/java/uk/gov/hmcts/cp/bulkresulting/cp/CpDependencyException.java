package uk.gov.hmcts.cp.bulkresulting.cp;

import java.io.Serial;

/**
 * A Common Platform call that did not give a usable answer. The kind decides the status the
 * caller sees: {@link Kind#UNAVAILABLE} is 503, {@link Kind#ACCESS_DENIED} is 403 and
 * {@link Kind#BAD_RESPONSE} is 502.
 */
public class CpDependencyException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Why the call failed. */
    public enum Kind {
        /** No answer: a connect or read failure, a timeout, or a 503/504 after the last retry. */
        UNAVAILABLE,
        /** The dependency refused the user (401 or 403). */
        ACCESS_DENIED,
        /** Any other non-2xx answer, or a body that could not be read. */
        BAD_RESPONSE
    }

    private final String dependency;
    private final Kind kind;
    private final int upstreamStatus;

    /**
     * Creates the exception.
     *
     * @param dependency     the dependency's short name, for example {@code listing}
     * @param kind           why the call failed
     * @param upstreamStatus the dependency's HTTP status, or 0 when there was no answer
     * @param message        a message with no personal data in it
     */
    public CpDependencyException(final String dependency, final Kind kind, final int upstreamStatus,
                                 final String message) {
        super(message);
        this.dependency = dependency;
        this.kind = kind;
        this.upstreamStatus = upstreamStatus;
    }

    public String getDependency() {
        return dependency;
    }

    public Kind getKind() {
        return kind;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }
}
