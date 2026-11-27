package uk.gov.hmcts.cp.bulkresulting.search;

import java.io.Serial;

/** Too many hearings match to show a complete list; the caller should narrow the filter (422). */
public class TooManyResultsException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message a message safe to show the caller
     */
    public TooManyResultsException(final String message) {
        super(message);
    }
}
