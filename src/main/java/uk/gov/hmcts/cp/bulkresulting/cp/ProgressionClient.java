package uk.gov.hmcts.cp.bulkresulting.cp;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads a case's enforcement account number from progression: {@code prosecutionCase.relatedUrn}
 * on {@code progression.query.prosecutioncase}. Progression answers 200 with an empty body for a
 * case it does not hold, which means no account number.
 */
public class ProgressionClient {

    /** Short name used in logs and errors. */
    public static final String DEPENDENCY = "progression";
    private static final String PATH = "/progression-query-api/query/api/rest/progression/prosecutioncases/{caseId}";
    private static final String ACCEPT = "application/vnd.progression.query.prosecutioncase+json";

    private final CpHttpClient http;

    /**
     * Creates the client.
     *
     * @param http the Common Platform HTTP client
     */
    public ProgressionClient(final CpHttpClient http) {
        this.http = http;
    }

    /**
     * The case's account number.
     *
     * @param caseId the prosecution case id
     * @param caller the calling user
     * @return the account number, or empty when the case has none
     */
    public Optional<String> accountNumber(final UUID caseId, final CallerContext caller) {
        return http.getJson(DEPENDENCY, http.uri(PATH, Map.of(), caseId), ACCEPT, caller)
                .flatMap(body -> JsonFields.text(body.get("prosecutionCase"), "relatedUrn"));
    }
}
