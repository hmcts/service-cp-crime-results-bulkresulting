package uk.gov.hmcts.cp.bulkresulting.search;

import java.io.Serial;

/** The caller's identity header is missing or not a user id (401). */
public class InvalidCallerException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message a message safe to show the caller
     */
    public InvalidCallerException(final String message) {
        super(message);
    }
}
