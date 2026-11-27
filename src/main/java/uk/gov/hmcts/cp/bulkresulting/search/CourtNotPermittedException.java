package uk.gov.hmcts.cp.bulkresulting.search;

import java.io.Serial;

/** The court is not one of the caller's court centres (403). */
public class CourtNotPermittedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message a message safe to show the caller
     */
    public CourtNotPermittedException(final String message) {
        super(message);
    }
}
