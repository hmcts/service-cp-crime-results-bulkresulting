package uk.gov.hmcts.cp.bulkresulting.cp;

import java.util.List;
import uk.gov.hmcts.cp.bulkresulting.search.CandidateHearing;

/**
 * Listing's answer to one range search.
 *
 * @param results  the number of matches listing reports, which may exceed what it returned
 * @param hearings the hearing days it returned
 */
public record ListingResult(long results, List<CandidateHearing> hearings) {

    /**
     * Copies the hearings.
     *
     * @param results  the number of matches
     * @param hearings the hearing days
     */
    public ListingResult {
        hearings = List.copyOf(hearings);
    }
}
