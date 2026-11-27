package uk.gov.hmcts.cp.bulkresulting.cp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.bulkresulting.search.CandidateHearing;

class ClientsTest {

    private static final CallerContext CALLER = new CallerContext("user-1", Optional.empty());

    private final WireMockServer wireMock = new WireMockServer(options().dynamicPort());
    private CpHttpClient http;

    @BeforeEach
    void start() {
        wireMock.start();
        http = new CpHttpClient(wireMock.baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(2), 1,
                Duration.ofMillis(1), Duration.ofMillis(1), wait -> {
        });
    }

    @AfterEach
    void stop() {
        wireMock.stop();
    }

    @Test
    void usersgroups_should_collect_placement_ou_codes_of_all_roles() {
        wireMock.stubFor(get(urlPathMatching(".*/logged-in-user/roles")).willReturn(okJson("""
                {"allRoles":[{"userPlacements":[{"placementId":" b01aa00 "},{"home":true}]},
                             {"userPlacements":[{"placementId":"C02BB00"}]},{"label":"no placements"}]}""")));

        assertThat(new UsersGroupsClient(http).courtOuCodes(CALLER)).containsExactly("B01AA00", "C02BB00");
    }

    @Test
    void usersgroups_with_an_empty_body_should_give_no_courts() {
        wireMock.stubFor(get(urlPathMatching(".*/logged-in-user/roles")).willReturn(aResponse().withStatus(200)));

        assertThat(new UsersGroupsClient(http).courtOuCodes(CALLER)).isEmpty();
    }

    @Test
    void referencedata_should_map_the_matching_unit_and_its_rooms() {
        final UUID court = UUID.randomUUID();
        final UUID room = UUID.randomUUID();
        wireMock.stubFor(get(urlPathMatching(".*/courtrooms")).willReturn(okJson("{\"organisationunits\":["
                + "{\"id\":\"" + UUID.randomUUID() + "\",\"oucode\":\"X\"},"
                + "{\"id\":\"" + court + "\",\"oucode\":\"b01aa00\",\"courtrooms\":[{\"id\":\"" + room
                + "\",\"courtroomName\":\"Room 1\"},{\"courtroomName\":\"no id\"}]}]}")));

        final Optional<Court> result = new ReferenceDataClient(http).court("B01AA00", CALLER);

        assertThat(result).hasValueSatisfying(found -> {
            assertThat(found.courtCentreId()).isEqualTo(court);
            assertThat(found.courtRoomName(room)).contains("Room 1");
            assertThat(found.hasCourtRoom(UUID.randomUUID())).isFalse();
        });
    }

    @Test
    void referencedata_with_no_matching_unit_should_be_empty() {
        wireMock.stubFor(get(urlPathMatching(".*/courtrooms")).willReturn(okJson("{\"organisationunits\":[]}")));

        assertThat(new ReferenceDataClient(http).court("B01AA00", CALLER)).isEmpty();
    }

    @Test
    void hearing_should_read_day_and_hearing_level_share_flags_and_applications() {
        final UUID dayShared = UUID.randomUUID();
        final UUID hearingShared = UUID.randomUUID();
        final UUID otherDayShared = UUID.randomUUID();
        final UUID withApplication = UUID.randomUUID();
        wireMock.stubFor(get(urlPathMatching(".*/hearing/hearings")).willReturn(okJson("{\"hearingSummaries\":["
                + summary(dayShared, false, "2026-12-01T10:00:00Z", true, false) + ","
                + summary(hearingShared, true, "2026-12-01T10:00:00Z", false, false) + ","
                + summary(otherDayShared, false, "2026-12-02T10:00:00Z", true, false) + ","
                + summary(withApplication, false, "bad", false, true) + ",{\"id\":\"not-a-uuid\"}]}")));

        final Map<UUID, HearingShareState> states = new HearingQueryClient(http).shareStates(LocalDate.of(2026, 12, 1), UUID.randomUUID(),
                UUID.randomUUID(), CALLER);

        assertThat(states).hasSize(4);
        assertThat(states.get(dayShared).isEligible()).isFalse();
        assertThat(states.get(hearingShared).sharedForDay()).isTrue();
        assertThat(states.get(otherDayShared).isEligible()).isTrue();
        assertThat(states.get(withApplication).hasApplication()).isTrue();
    }

    @Test
    void hearing_with_no_summaries_should_be_empty() {
        wireMock.stubFor(get(urlPathMatching(".*/hearing/hearings")).willReturn(okJson("{}")));

        assertThat(new HearingQueryClient(http).shareStates(LocalDate.of(2026, 12, 1), UUID.randomUUID(),
                UUID.randomUUID(), CALLER)).isEmpty();
    }

    private static String summary(final UUID id, final boolean shared, final String sittingDay,
                                  final boolean dayShared, final boolean application) {
        return "{\"id\":\"" + id + "\",\"hasSharedResults\":" + shared + ",\"hearingDays\":[{\"sittingDay\":\""
                + sittingDay + "\",\"hasSharedResults\":" + dayShared + "}],\"courtApplicationSummaries\":"
                + (application ? "[{}]" : "[]") + "}";
    }

    @Test
    void progression_should_read_related_urn_and_treat_an_empty_body_as_none() {
        final UUID withNumber = UUID.randomUUID();
        final UUID without = UUID.randomUUID();
        final UUID missing = UUID.randomUUID();
        wireMock.stubFor(get(urlPathEqualTo("/progression-query-api/query/api/rest/progression/prosecutioncases/"
                + withNumber)).willReturn(okJson("{\"prosecutionCase\":{\"relatedUrn\":\"ACC1\"}}")));
        wireMock.stubFor(get(urlPathEqualTo("/progression-query-api/query/api/rest/progression/prosecutioncases/"
                + without)).willReturn(okJson("{\"prosecutionCase\":{}}")));
        wireMock.stubFor(get(urlPathEqualTo("/progression-query-api/query/api/rest/progression/prosecutioncases/"
                + missing)).willReturn(aResponse().withStatus(200)));
        final ProgressionClient client = new ProgressionClient(http);

        assertThat(client.accountNumber(withNumber, CALLER)).contains("ACC1");
        assertThat(client.accountNumber(without, CALLER)).isEmpty();
        assertThat(client.accountNumber(missing, CALLER)).isEmpty();
    }

    @Test
    void listing_should_map_hearings_and_skip_those_without_an_id_or_day() {
        final UUID hearingId = UUID.randomUUID();
        final UUID hearingRoom = UUID.randomUUID();
        wireMock.stubFor(get(urlPathMatching(".*/range-search")).willReturn(okJson("{\"results\":7,\"hearings\":["
                + "{\"id\":\"" + hearingId + "\",\"courtRoomId\":\"" + hearingRoom + "\",\"type\":{\"description\":\"Enf\"},"
                + "\"hearingDays\":[{\"hearingDate\":\"2026-12-01\"}],"
                + "\"listedCases\":[{\"id\":\"" + UUID.randomUUID() + "\",\"defendants\":[{\"id\":\""
                + UUID.randomUUID() + "\",\"offences\":[{\"id\":\"" + UUID.randomUUID() + "\"},{\"no\":\"id\"}]},"
                + "{\"no\":\"id\"}]},{\"no\":\"id\"}]},"
                + "{\"id\":\"" + UUID.randomUUID() + "\",\"hearingDays\":[]},"
                + "{\"hearingDays\":[{\"hearingDate\":\"2026-12-01\"}]}]}")));

        final ListingResult result = new ListingClient(http).rangeSearch(new ListingQuery("B01AA00",
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 1), Optional.empty(), Optional.empty(), "ENF"),
                CALLER);

        assertThat(result.results()).isEqualTo(7);
        assertThat(result.hearings()).singleElement().satisfies(hearing -> {
            final CandidateHearing candidate = hearing;
            assertThat(candidate.courtRoomId()).contains(hearingRoom);
            assertThat(candidate.hearingDayCount()).isEmpty();
            assertThat(candidate.isMultiDay()).isFalse();
            assertThat(candidate.cases()).singleElement().satisfies(listedCase -> {
                assertThat(listedCase.urn()).isNull();
                assertThat(listedCase.defendants()).singleElement()
                        .satisfies(defendant -> assertThat(defendant.offences()).hasSize(1));
            });
        });
    }

    @Test
    void listing_without_results_should_count_the_hearings_and_an_empty_body_should_be_no_matches() {
        wireMock.stubFor(get(urlPathMatching(".*/range-search")).willReturn(okJson("{\"hearings\":[]}")));
        final ListingQuery query = new ListingQuery("B01AA00", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 1),
                Optional.of(UUID.randomUUID()), Optional.of("AM"), "ENF");

        assertThat(new ListingClient(http).rangeSearch(query, CALLER).results()).isZero();

        wireMock.stubFor(get(urlPathMatching(".*/range-search")).willReturn(aResponse().withStatus(200)));
        assertThat(new ListingClient(http).rangeSearch(query, CALLER).hearings()).isEmpty();
    }
}
