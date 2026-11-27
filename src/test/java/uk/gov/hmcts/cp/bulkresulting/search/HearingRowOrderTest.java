package uk.gov.hmcts.cp.bulkresulting.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HearingRowOrderTest {

    private static HearingRow row(final String label, final LocalDate date, final String room, final String first) {
        final List<ListedCase> cases = first == null ? List.of() : List.of(new ListedCase(UUID.randomUUID(), label,
                List.of(new ListedDefendant(UUID.randomUUID(), first, "X", null, null, List.of()))));
        final CandidateHearing hearing = new CandidateHearing(UUID.randomUUID(), date,
                Optional.of(UUID.nameUUIDFromBytes(String.valueOf(room).getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                "Enforcement", Optional.of(1L), cases);
        return new HearingRow(hearing, Optional.ofNullable(room), DisplayedDefendant.from(hearing), false);
    }

    @Test
    void display_order_should_be_date_then_courtroom_then_first_name_case_insensitive() {
        final LocalDate day1 = LocalDate.of(2026, 12, 1);
        final LocalDate day2 = LocalDate.of(2026, 12, 2);
        final List<HearingRow> rows = new ArrayList<>(List.of(
                row("d2", day2, "Courtroom 01", "Amy"),
                row("r2-bob", day1, "Courtroom 02", "bob"),
                row("r1-zed", day1, "Courtroom 01", "Zed"),
                row("r1-none", day1, "Courtroom 01", null),
                row("r1-al", day1, "Courtroom 01", "al"),
                row("noroom", day1, null, "Aaron")));

        rows.sort(HearingRowOrder.DISPLAY_ORDER);

        assertThat(rows).extracting(r -> r.hearing().cases().isEmpty() ? "r1-none" : r.hearing().cases().get(0).urn())
                .containsExactly("r1-al", "r1-zed", "r1-none", "r2-bob", "noroom", "d2");
    }
}
