package uk.gov.hmcts.cp.bulkresulting.search;

import java.util.Optional;

/**
 * The one defendant a hearing row shows. Listing has no notion of a principal defendant, so the
 * row shows the first defendant of the first case, in the order listing returns them. This is
 * the only place that rule lives.
 *
 * @param listedCase the case the defendant is on
 * @param defendant  the defendant
 */
public record DisplayedDefendant(ListedCase listedCase, ListedDefendant defendant) {

    private static final String CORPORATE_SUFFIX = " (C)";

    /**
     * The defendant a hearing row shows.
     *
     * @param hearing the hearing
     * @return the displayed defendant, or empty when the hearing has no case with a defendant
     */
    public static Optional<DisplayedDefendant> from(final CandidateHearing hearing) {
        return hearing.cases().stream().findFirst()
                .flatMap(first -> first.defendants().stream().findFirst()
                        .map(defendant -> new DisplayedDefendant(first, defendant)));
    }

    /**
     * The name ready to display: first and last name for a person, or the organisation name with
     * " (C)" for a corporate defendant.
     *
     * @return the name, or empty when the defendant has none
     */
    public Optional<String> displayName() {
        final Optional<String> name;
        if (hasText(defendant.organisationName())) {
            name = Optional.of(defendant.organisationName().trim() + CORPORATE_SUFFIX);
        } else {
            final String person = (nullToEmpty(defendant.firstName()) + " " + nullToEmpty(defendant.lastName())).trim();
            name = person.isEmpty() ? Optional.empty() : Optional.of(person);
        }
        return name;
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToEmpty(final String value) {
        return value == null ? "" : value.trim();
    }
}
