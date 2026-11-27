package uk.gov.hmcts.cp.bulkresulting.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DisplayedDefendantTest {

    static ListedDefendant person(final String first, final String last) {
        return new ListedDefendant(UUID.randomUUID(), first, last, null, null, List.of());
    }

    static CandidateHearing hearing(final List<ListedCase> cases) {
        return new CandidateHearing(UUID.randomUUID(), LocalDate.of(2026, 12, 1), Optional.empty(), "Enforcement",
                Optional.of(1L), cases);
    }

    @Test
    void from_should_take_the_first_defendant_of_the_first_case() {
        final ListedDefendant first = person("Ann", "Able");
        final ListedCase firstCase = new ListedCase(UUID.randomUUID(), "U1", List.of(first, person("Bea", "Bee")));
        final ListedCase second = new ListedCase(UUID.randomUUID(), "U2", List.of(person("Cal", "Cee")));

        final Optional<DisplayedDefendant> displayed = DisplayedDefendant.from(hearing(List.of(firstCase, second)));

        assertThat(displayed).hasValueSatisfying(value -> {
            assertThat(value.defendant()).isEqualTo(first);
            assertThat(value.listedCase()).isEqualTo(firstCase);
            assertThat(value.displayName()).contains("Ann Able");
        });
    }

    @Test
    void from_should_be_empty_without_cases_or_defendants() {
        assertThat(DisplayedDefendant.from(hearing(List.of()))).isEmpty();
        assertThat(DisplayedDefendant.from(hearing(List.of(new ListedCase(UUID.randomUUID(), "U", List.of())))))
                .isEmpty();
    }

    @Test
    void displayName_should_mark_organisations_and_handle_missing_names() {
        final ListedCase listedCase = new ListedCase(UUID.randomUUID(), "U", List.of());
        final ListedDefendant org = new ListedDefendant(UUID.randomUUID(), null, null, " Acme Ltd ", null, List.of());

        assertThat(new DisplayedDefendant(listedCase, org).displayName()).contains("Acme Ltd (C)");
        assertThat(new DisplayedDefendant(listedCase, person(null, "Solo")).displayName()).contains("Solo");
        assertThat(new DisplayedDefendant(listedCase, person(" ", null)).displayName()).isEmpty();
        assertThat(new DisplayedDefendant(listedCase,
                new ListedDefendant(UUID.randomUUID(), "Ann", null, " ", null, List.of())).displayName())
                .contains("Ann");
    }
}
