package uk.gov.hmcts.cp.bulkresulting.search;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One page of the search, before it is shaped for the response.
 *
 * @param page           this page, from 1
 * @param pageSize       rows per page
 * @param totalMatched   eligible hearings on all pages
 * @param rows           this page's rows, in display order
 * @param accountNumbers account numbers by case id, for the cases on this page
 * @param inFlightCount  the caller's own hearings in flight
 */
public record SearchPage(int page, int pageSize, int totalMatched, List<HearingRow> rows,
                         Map<UUID, Optional<String>> accountNumbers, int inFlightCount) {

    /**
     * Copies the collections.
     *
     * @param page           the page
     * @param pageSize       rows per page
     * @param totalMatched   eligible hearings
     * @param rows           the rows
     * @param accountNumbers account numbers by case
     * @param inFlightCount  the in-flight count
     */
    public SearchPage {
        rows = List.copyOf(rows);
        accountNumbers = Map.copyOf(accountNumbers);
    }

    /**
     * The number of pages.
     *
     * @return the page count, 0 when nothing matched
     */
    public int totalPages() {
        return (totalMatched + pageSize - 1) / pageSize;
    }

    /**
     * A case's account number.
     *
     * @param caseId the case
     * @return the account number, if the case has one
     */
    public Optional<String> accountNumber(final UUID caseId) {
        return accountNumbers.getOrDefault(caseId, Optional.empty());
    }
}
