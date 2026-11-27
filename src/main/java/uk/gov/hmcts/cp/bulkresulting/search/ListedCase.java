package uk.gov.hmcts.cp.bulkresulting.search;

import java.util.List;
import java.util.UUID;

/**
 * A case listed in a hearing, as listing returns it.
 *
 * @param caseId     the prosecution case id
 * @param urn        the case reference (URN)
 * @param defendants the defendants, in listing's order
 */
public record ListedCase(UUID caseId, String urn, List<ListedDefendant> defendants) {

    /**
     * Copies the defendants.
     *
     * @param caseId     the prosecution case id
     * @param urn        the case reference
     * @param defendants the defendants
     */
    public ListedCase {
        defendants = List.copyOf(defendants);
    }
}
