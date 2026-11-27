package uk.gov.hmcts.cp.bulkresulting.cp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import uk.gov.hmcts.cp.bulkresulting.search.CandidateHearing;
import uk.gov.hmcts.cp.bulkresulting.search.ListedCase;
import uk.gov.hmcts.cp.bulkresulting.search.ListedDefendant;
import uk.gov.hmcts.cp.bulkresulting.search.ListedOffence;

/**
 * Listing's range search ({@code listing.range.search.hearings}) for allocated magistrates'
 * hearings by OU code and business type. With a business type, listing answers from the court
 * scheduler: one entry per hearing day, its {@code hearingDays} cut to that day, with
 * {@code hearingDayCount} set. All matches come back in one page ({@code useMaxPageSize=true}),
 * up to listing's configured maximum; {@code results} is the full count.
 */
public class ListingClient {

    /** Short name used in logs and errors. */
    public static final String DEPENDENCY = "listing";
    private static final Logger LOGGER = LoggerFactory.getLogger(ListingClient.class);
    private static final String PATH = "/listing-query-api/query/api/rest/listing/hearings/range-search";
    private static final String ACCEPT = "application/vnd.listing.search.hearings+json";

    private final CpHttpClient http;

    /**
     * Creates the client.
     *
     * @param http the Common Platform HTTP client
     */
    public ListingClient(final CpHttpClient http) {
        this.http = http;
    }

    /**
     * Runs one range search.
     *
     * @param query  the search
     * @param caller the calling user
     * @return the matches
     */
    public ListingResult rangeSearch(final ListingQuery query, final CallerContext caller) {
        final Map<String, Object> params = new LinkedHashMap<>();
        params.put("allocated", true);
        params.put("jurisdictionType", "MAGISTRATES");
        params.put("ouCode", query.ouCode());
        params.put("startDate", query.startDate().toString());
        params.put("endDate", query.endDate().toString());
        params.put("courtRoomId", query.courtRoomId().orElse(null));
        params.put("courtSession", query.courtSession().orElse(null));
        params.put("businessType", query.businessType());
        params.put("useMaxPageSize", true);
        return http.getJson(DEPENDENCY, http.uri(PATH, params), ACCEPT, caller)
                .map(ListingClient::toResult)
                .orElseGet(() -> new ListingResult(0, List.of()));
    }

    private static ListingResult toResult(final JsonNode body) {
        final List<CandidateHearing> hearings = new ArrayList<>();
        int skipped = 0;
        for (final JsonNode hearing : JsonFields.array(body, "hearings")) {
            final Optional<CandidateHearing> candidate = toCandidate(hearing);
            if (candidate.isPresent()) {
                hearings.add(candidate.get());
            } else {
                skipped++;
            }
        }
        if (skipped > 0) {
            LOGGER.warn("Skipped {} listing hearing(s) with no id or no hearing day", skipped);
        }
        final JsonNode results = body.get("results");
        final long count = results != null && results.isNumber() ? results.asLong() : hearings.size();
        return new ListingResult(count, hearings);
    }

    private static Optional<CandidateHearing> toCandidate(final JsonNode hearing) {
        final Optional<UUID> hearingId = JsonFields.uuid(hearing, "id");
        final Optional<JsonNode> day = JsonFields.array(hearing, "hearingDays").stream().findFirst();
        return hearingId.flatMap(id -> day.flatMap(hearingDay -> JsonFields.date(hearingDay, "hearingDate")
                .map(date -> new CandidateHearing(id, date,
                        JsonFields.uuid(hearingDay, "courtRoomId").or(() -> JsonFields.uuid(hearing, "courtRoomId")),
                        JsonFields.text(hearing.get("type"), "description").orElse(null),
                        dayCount(hearing),
                        cases(hearing)))));
    }

    private static Optional<Long> dayCount(final JsonNode hearing) {
        final JsonNode count = hearing.get("hearingDayCount");
        return count != null && count.isNumber() ? Optional.of(count.asLong()) : Optional.empty();
    }

    private static List<ListedCase> cases(final JsonNode hearing) {
        final List<ListedCase> cases = new ArrayList<>();
        for (final JsonNode listedCase : JsonFields.array(hearing, "listedCases")) {
            JsonFields.uuid(listedCase, "id").ifPresent(caseId -> cases.add(new ListedCase(caseId,
                    JsonFields.text(listedCase.get("caseIdentifier"), "caseReference").orElse(null),
                    defendants(listedCase))));
        }
        return cases;
    }

    private static List<ListedDefendant> defendants(final JsonNode listedCase) {
        final List<ListedDefendant> defendants = new ArrayList<>();
        for (final JsonNode defendant : JsonFields.array(listedCase, "defendants")) {
            JsonFields.uuid(defendant, "id").ifPresent(id -> defendants.add(new ListedDefendant(id,
                    JsonFields.text(defendant, "firstName").orElse(null),
                    JsonFields.text(defendant, "lastName").orElse(null),
                    JsonFields.text(defendant, "organisationName").orElse(null),
                    JsonFields.date(defendant, "dateOfBirth").orElse(null),
                    offences(defendant))));
        }
        return defendants;
    }

    private static List<ListedOffence> offences(final JsonNode defendant) {
        final List<ListedOffence> offences = new ArrayList<>();
        for (final JsonNode offence : JsonFields.array(defendant, "offences")) {
            JsonFields.uuid(offence, "id").ifPresent(id -> offences.add(new ListedOffence(id,
                    JsonFields.text(offence, "offenceCode").orElse(null),
                    JsonFields.text(offence.get("statementOfOffence"), "title").orElse(null))));
        }
        return offences;
    }
}
