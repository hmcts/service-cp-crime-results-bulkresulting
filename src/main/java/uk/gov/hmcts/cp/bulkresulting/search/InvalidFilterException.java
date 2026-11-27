package uk.gov.hmcts.cp.bulkresulting.search;

import java.io.Serial;

/** The search filter is invalid (400). */
public class InvalidFilterException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message a message safe to show the caller
     */
    public InvalidFilterException(final String message) {
        super(message);
    }
}
