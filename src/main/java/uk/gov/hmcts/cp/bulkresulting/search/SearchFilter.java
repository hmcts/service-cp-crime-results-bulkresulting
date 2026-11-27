package uk.gov.hmcts.cp.bulkresulting.search;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import uk.gov.hmcts.cp.bulkresulting.openapi.model.BusinessType;
import uk.gov.hmcts.cp.bulkresulting.openapi.model.CourtSession;

/**
 * A search as the caller asked for it, with defaults applied.
 *
 * @param ouCode       the court's OU code, upper case
 * @param startDate    the first hearing day
 * @param endDate      the last hearing day
 * @param courtRoomId  the courtroom, or empty for all
 * @param session      the session; ANY for all
 * @param businessType the business type, or empty for both ENF and ENF_AUTO
 * @param page         the page, from 1
 */
public record SearchFilter(String ouCode, LocalDate startDate, LocalDate endDate, Optional<UUID> courtRoomId,
                           CourtSession session, Optional<BusinessType> businessType, int page) {

    /**
     * The same filter without the page, which keys the candidate cache.
     *
     * @return the filter on page 1
     */
    public SearchFilter withoutPage() {
        return new SearchFilter(ouCode, startDate, endDate, courtRoomId, session, businessType, 1);
    }
}
