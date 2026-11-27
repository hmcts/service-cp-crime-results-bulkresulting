package uk.gov.hmcts.cp.bulkresulting.search;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A defendant on a listed case, as listing returns it.
 *
 * @param defendantId      the defendant id
 * @param firstName        the first name, for a person
 * @param lastName         the last name, for a person
 * @param organisationName the organisation name, for a corporate defendant
 * @param dateOfBirth      the date of birth, if known
 * @param offences         the offences, in listing's order
 */
public record ListedDefendant(UUID defendantId, String firstName, String lastName, String organisationName,
                              LocalDate dateOfBirth, List<ListedOffence> offences) {

    /**
     * Copies the offences.
     *
     * @param defendantId      the defendant id
     * @param firstName        the first name
     * @param lastName         the last name
     * @param organisationName the organisation name
     * @param dateOfBirth      the date of birth
     * @param offences         the offences
     */
    public ListedDefendant {
        offences = List.copyOf(offences);
    }
}
