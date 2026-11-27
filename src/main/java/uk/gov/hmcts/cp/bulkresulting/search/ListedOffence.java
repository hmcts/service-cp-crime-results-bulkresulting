package uk.gov.hmcts.cp.bulkresulting.search;

import java.util.UUID;

/**
 * An offence on a listed defendant, as listing returns it.
 *
 * @param offenceId    the offence id
 * @param offenceCode  the offence code
 * @param offenceTitle the statement of offence title
 */
public record ListedOffence(UUID offenceId, String offenceCode, String offenceTitle) {
}
