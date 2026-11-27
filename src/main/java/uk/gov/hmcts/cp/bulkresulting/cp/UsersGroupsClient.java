package uk.gov.hmcts.cp.bulkresulting.cp;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Reads the user's court centres from usersgroups, the way the hearing UI does: the placement OU
 * codes of all the user's roles ({@code allRoles[].userPlacements[].placementId}).
 */
public class UsersGroupsClient {

    /** Short name used in logs and errors. */
    public static final String DEPENDENCY = "usersgroups";
    private static final String PATH =
            "/usersgroups-query-api/query/api/rest/usersgroups/users/logged-in-user/roles";
    private static final String ACCEPT = "application/vnd.usersgroups.get-roles-for-logged-in-user+json";

    private final CpHttpClient http;

    /**
     * Creates the client.
     *
     * @param http the Common Platform HTTP client
     */
    public UsersGroupsClient(final CpHttpClient http) {
        this.http = http;
    }

    /**
     * The OU codes of the user's court centres.
     *
     * @param caller the calling user
     * @return the OU codes, upper case; empty when the user has no placements
     */
    public Set<String> courtOuCodes(final CallerContext caller) {
        final Set<String> ouCodes = new LinkedHashSet<>();
        http.getJson(DEPENDENCY, http.uri(PATH, Map.of()), ACCEPT, caller).ifPresent(body -> {
            for (final JsonNode role : JsonFields.array(body, "allRoles")) {
                for (final JsonNode placement : JsonFields.array(role, "userPlacements")) {
                    JsonFields.text(placement, "placementId")
                            .ifPresent(ouCode -> ouCodes.add(ouCode.trim().toUpperCase(Locale.ROOT)));
                }
            }
        });
        return ouCodes;
    }
}
