package uk.gov.hmcts.cp.bulkresulting.search;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import uk.gov.hmcts.cp.bulkresulting.openapi.model.BusinessType;
import uk.gov.hmcts.cp.bulkresulting.openapi.model.CourtSession;

/**
 * Builds a {@link SearchFilter} from the request and checks the date window: from today to today
 * plus the configured number of days. Past dates cannot be searched.
 */
public class SearchFilterFactory {

    private static final int FIRST_PAGE = 1;

    private final Clock clock;
    private final int searchWindowDays;

    /**
     * Creates the factory.
     *
     * @param clock            the clock, in the zone that decides what today is
     * @param searchWindowDays how many days after today a search may reach
     */
    public SearchFilterFactory(final Clock clock, final int searchWindowDays) {
        this.clock = clock;
        this.searchWindowDays = searchWindowDays;
    }

    /**
     * Builds and checks the filter.
     *
     * @param ouCode       the court's OU code
     * @param startDate    the first hearing day
     * @param endDate      the last hearing day, or null for the start date
     * @param courtRoomId  the courtroom, or null for all
     * @param session      the session, or null for any
     * @param businessType the business type, or null for both
     * @param page         the page, or null for 1
     * @return the filter
     * @throws InvalidFilterException when a value is outside what may be searched
     */
    public SearchFilter create(final String ouCode, final LocalDate startDate, final LocalDate endDate,
                               final UUID courtRoomId, final CourtSession session,
                               final BusinessType businessType, final Integer page) {
        if (ouCode == null || ouCode.isBlank()) {
            throw new InvalidFilterException("ouCode is required");
        }
        if (startDate == null) {
            throw new InvalidFilterException("startDate is required");
        }
        final LocalDate today = LocalDate.now(clock);
        if (startDate.isBefore(today)) {
            throw new InvalidFilterException("startDate must not be in the past");
        }
        final LocalDate end = endDate == null ? startDate : endDate;
        if (end.isBefore(startDate)) {
            throw new InvalidFilterException("endDate must not be before startDate");
        }
        if (end.isAfter(today.plusDays(searchWindowDays))) {
            throw new InvalidFilterException("endDate must not be more than " + searchWindowDays
                    + " days after today");
        }
        final int pageNumber = page == null ? FIRST_PAGE : page;
        if (pageNumber < FIRST_PAGE) {
            throw new InvalidFilterException("page must be 1 or more");
        }
        return new SearchFilter(ouCode.trim().toUpperCase(Locale.ROOT), startDate, end,
                Optional.ofNullable(courtRoomId), session == null ? CourtSession.ANY : session,
                Optional.ofNullable(businessType), pageNumber);
    }
}
