package uk.gov.hmcts.cp.bulkresulting.search;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A hearing day returned by listing's range search, before the share state and the batch store
 * are applied.
 *
 * @param hearingId       the hearing id
 * @param hearingDate     the hearing day
 * @param courtRoomId     the courtroom for that day, if allocated to one
 * @param hearingType     the hearing type description
 * @param hearingDayCount how many days the hearing has, if listing reported it
 * @param cases           the cases, in listing's order
 */
public record CandidateHearing(UUID hearingId, LocalDate hearingDate, Optional<UUID> courtRoomId,
                               String hearingType, Optional<Long> hearingDayCount, List<ListedCase> cases) {

    /**
     * Copies the cases.
     *
     * @param hearingId       the hearing id
     * @param hearingDate     the hearing day
     * @param courtRoomId     the courtroom
     * @param hearingType     the hearing type
     * @param hearingDayCount the number of hearing days
     * @param cases           the cases
     */
    public CandidateHearing {
        cases = List.copyOf(cases);
    }

    /**
     * Whether the hearing runs over more than one day.
     *
     * @return true for a multi-day hearing
     */
    public boolean isMultiDay() {
        return hearingDayCount.map(count -> count > 1).orElse(false);
    }
}
