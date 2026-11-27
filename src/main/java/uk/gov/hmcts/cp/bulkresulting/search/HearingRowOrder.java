package uk.gov.hmcts.cp.bulkresulting.search;

import java.util.Comparator;
import java.util.function.Function;

/**
 * The display order of hearing rows: hearing date, then courtroom, then the displayed defendant's
 * first name (solution design §6, CRA-395). Changing the order means changing only this class.
 */
public final class HearingRowOrder {

    /** The display order. */
    public static final Comparator<HearingRow> DISPLAY_ORDER = Comparator
            .comparing((HearingRow row) -> row.hearing().hearingDate())
            .thenComparing(HearingRowOrder::courtRoomName, text())
            .thenComparing(row -> row.hearing().courtRoomId().map(Object::toString).orElse(null), text())
            .thenComparing(defendantField(defendant -> defendant.firstName()), text())
            .thenComparing(defendantField(defendant -> defendant.lastName()), text())
            .thenComparing(defendantField(defendant -> defendant.organisationName()), text())
            .thenComparing(row -> row.hearing().hearingId());

    private HearingRowOrder() {
    }

    private static String courtRoomName(final HearingRow row) {
        return row.courtRoomName().orElse(null);
    }

    private static Function<HearingRow, String> defendantField(final Function<ListedDefendant, String> field) {
        return row -> row.displayed().map(DisplayedDefendant::defendant).map(field).orElse(null);
    }

    private static Comparator<String> text() {
        return Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
    }
}
