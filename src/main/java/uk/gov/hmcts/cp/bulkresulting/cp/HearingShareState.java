package uk.gov.hmcts.cp.bulkresulting.cp;

/**
 * What hearing says about a hearing on one day.
 *
 * @param sharedForDay   results are shared for the day
 * @param hasApplication the hearing has a court application
 */
public record HearingShareState(boolean sharedForDay, boolean hasApplication) {

    /**
     * Whether the hearing can still be bulk resulted on the day.
     *
     * @return true when not shared and with no application
     */
    public boolean isEligible() {
        return !sharedForDay && !hasApplication;
    }
}
