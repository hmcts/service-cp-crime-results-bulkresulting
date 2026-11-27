package uk.gov.hmcts.cp.bulkresulting.cp;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * Maps an OU code to its court centre id and courtroom names, from reference data's
 * {@code GET /courtrooms?oucode=}.
 */
public class ReferenceDataClient {

    /** Short name used in logs and errors. */
    public static final String DEPENDENCY = "referencedata";
    private static final String PATH = "/referencedata-query-api/query/api/rest/referencedata/courtrooms";
    private static final String ACCEPT = "application/vnd.referencedata.ou-courtrooms+json";

    private final CpHttpClient http;

    /**
     * Creates the client.
     *
     * @param http the Common Platform HTTP client
     */
    public ReferenceDataClient(final CpHttpClient http) {
        this.http = http;
    }

    /**
     * The court centre with an OU code.
     *
     * @param ouCode the OU code
     * @param caller the calling user
     * @return the court, or empty when reference data has no such organisation unit
     */
    public Optional<Court> court(final String ouCode, final CallerContext caller) {
        final Map<String, Object> query = new HashMap<>();
        query.put("oucode", ouCode);
        return http.getJson(DEPENDENCY, http.uri(PATH, query), ACCEPT, caller)
                .flatMap(body -> toCourt(ouCode, JsonFields.array(body, "organisationunits")));
    }

    private static Optional<Court> toCourt(final String ouCode, final List<JsonNode> units) {
        return units.stream()
                .filter(unit -> JsonFields.text(unit, "oucode").map(ouCode::equalsIgnoreCase).orElse(false))
                .findFirst()
                .flatMap(unit -> JsonFields.uuid(unit, "id").map(id -> new Court(id, ouCode, roomNames(unit))));
    }

    private static Map<UUID, String> roomNames(final JsonNode unit) {
        final Map<UUID, String> names = new LinkedHashMap<>();
        for (final JsonNode room : JsonFields.array(unit, "courtrooms")) {
            JsonFields.uuid(room, "id").ifPresent(id ->
                    names.put(id, JsonFields.text(room, "courtroomName").orElse("")));
        }
        return names;
    }
}
