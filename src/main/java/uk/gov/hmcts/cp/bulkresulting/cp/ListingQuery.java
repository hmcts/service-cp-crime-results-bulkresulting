package uk.gov.hmcts.cp.bulkresulting.cp;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * One listing range search: allocated magistrates' hearings at a court for one business type.
 *
 * @param ouCode       the court's OU code
 * @param startDate    the first hearing day
 * @param endDate      the last hearing day
 * @param courtRoomId  the courtroom, or empty for all
 * @param courtSession AM, PM or AD, or empty for any session
 * @param businessType the business type code, for example {@code ENF}
 */
public record ListingQuery(String ouCode, LocalDate startDate, LocalDate endDate, Optional<UUID> courtRoomId,
                           Optional<String> courtSession, String businessType) {
}
