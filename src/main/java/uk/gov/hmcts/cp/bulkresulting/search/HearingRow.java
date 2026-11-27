package uk.gov.hmcts.cp.bulkresulting.search;

import java.util.Optional;

/**
 * An eligible hearing with what the page needs to order and show it.
 *
 * @param hearing       the hearing day from listing
 * @param courtRoomName the courtroom's name from reference data, if known
 * @param displayed     the defendant the row shows, if any
 * @param failedToShare the latest bulk attempt did not share
 */
public record HearingRow(CandidateHearing hearing, Optional<String> courtRoomName,
                         Optional<DisplayedDefendant> displayed, boolean failedToShare) {
}
