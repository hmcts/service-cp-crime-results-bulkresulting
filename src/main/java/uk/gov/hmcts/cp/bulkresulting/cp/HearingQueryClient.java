package uk.gov.hmcts.cp.bulkresulting.cp;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * Reads the share state of the hearings in one courtroom on one day from hearing's
 * {@code hearing.get.hearings}. The query takes one date per call and, with a room id, also
 * returns hearings that have ended, which are the ones bulk resulting is for. When nothing
 * matches, hearing returns no {@code hearingSummaries} at all.
 */
public class HearingQueryClient {

    /** Short name used in logs and errors. */
    public static final String DEPENDENCY = "hearing";
    private static final String PATH = "/hearing-query-api/query/api/rest/hearing/hearings";
    private static final String ACCEPT = "application/vnd.hearing.get.hearings+json";
    private static final int DATE_LENGTH = 10;

    private final CpHttpClient http;

    /**
     * Creates the client.
     *
     * @param http the Common Platform HTTP client
     */
    public HearingQueryClient(final CpHttpClient http) {
        this.http = http;
    }

    /**
     * The share state of each hearing in a courtroom on a day.
     *
     * @param date          the hearing day
     * @param courtCentreId the court centre
     * @param roomId        the courtroom
     * @param caller        the calling user
     * @return the state by hearing id; hearings hearing did not return are absent
     */
    public Map<UUID, HearingShareState> shareStates(final LocalDate date, final UUID courtCentreId,
                                                    final UUID roomId, final CallerContext caller) {
        final Map<String, Object> query = new LinkedHashMap<>();
        query.put("date", date.toString());
        query.put("courtCentreId", courtCentreId);
        query.put("roomId", roomId);
        final Map<UUID, HearingShareState> states = new HashMap<>();
        http.getJson(DEPENDENCY, http.uri(PATH, query), ACCEPT, caller).ifPresent(body -> {
            for (final JsonNode summary : JsonFields.array(body, "hearingSummaries")) {
                JsonFields.uuid(summary, "id").ifPresent(id -> states.put(id, stateOf(summary, date)));
            }
        });
        return states;
    }

    private static HearingShareState stateOf(final JsonNode summary, final LocalDate date) {
        final boolean dayShared = JsonFields.array(summary, "hearingDays").stream()
                .filter(day -> JsonFields.text(day, "sittingDay")
                        .map(sitting -> sitting.length() >= DATE_LENGTH
                                && date.toString().equals(sitting.substring(0, DATE_LENGTH)))
                        .orElse(false))
                .anyMatch(day -> JsonFields.isTrue(day, "hasSharedResults"));
        // Bulk resulting handles single-day hearings only, so the hearing-level flag also means the
        // day is shared.
        final boolean shared = dayShared || JsonFields.isTrue(summary, "hasSharedResults");
        final boolean hasApplication = !JsonFields.array(summary, "courtApplicationSummaries").isEmpty();
        return new HearingShareState(shared, hasApplication);
    }
}
