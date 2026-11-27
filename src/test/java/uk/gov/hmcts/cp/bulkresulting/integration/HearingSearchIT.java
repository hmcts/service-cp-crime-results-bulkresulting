package uk.gov.hmcts.cp.bulkresulting.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The whole search through the real HTTP stack, the real authorisation filter, a real Postgres
 * (Flyway-migrated) and WireMock standing in for usersgroups, reference data, listing, hearing and
 * progression, using the same stubs as the local docker-compose stack.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "bulkresulting.cp.initial-backoff=1ms")
class HearingSearchIT {

    private static final String USER = "77777777-7777-4777-8777-777777777777";
    private static final String COURT = "B01AA00";
    private static final String OTHER_COURT = "B01ZZ00";
    private static final String R1 = "22222222-2222-4222-8222-000000000001";
    private static final String LISTING = "/listing-query-api/query/api/rest/listing/hearings/range-search";
    private static final String COURTROOMS = "/referencedata-query-api/query/api/rest/referencedata/courtrooms";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort()
            .usingFilesUnderDirectory("docker/wiremock"));

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbcClient;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void startWireMock() {
        WIREMOCK.start();
    }

    @AfterAll
    static void stopWireMock() {
        WIREMOCK.stop();
    }

    @DynamicPropertySource
    static void cpBaseUrl(final DynamicPropertyRegistry registry) {
        registry.add("bulkresulting.cp.base-url", WIREMOCK::baseUrl);
        registry.add("authz.http.identity-url-template", () -> WIREMOCK.baseUrl()
                + "/usersgroups-query-api/query/api/rest/usersgroups/users/logged-in-user/permissions");
    }

    /** Fixes today so the stubbed hearing day (1 December 2026) is inside the search window. */
    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-27T09:00:00Z"), ZoneId.of("Europe/London"));
        }
    }

    @BeforeEach
    void resetState() {
        WIREMOCK.resetToDefaultMappings();
        WIREMOCK.resetRequests();
        jdbcClient.sql("DELETE FROM bulk_resulting_hearing").update();
        jdbcClient.sql("DELETE FROM bulk_resulting").update();
    }

    @AfterEach
    void clearStubs() {
        WIREMOCK.resetToDefaultMappings();
    }

    @Test
    void search_should_return_eligible_hearings_grouped_sorted_and_labelled() throws Exception {
        final UUID batch = insertBatch(USER);
        insertHearing(batch, hearing(8), "IN_PROGRESS");
        insertHearing(batch, hearing(9), "TECHNICAL");

        final HttpResponse<String> response = call("/hearings?ouCode=b01aa00&startDate=2026-12-01&endDate=2026-12-03", USER);

        assertThat(response.statusCode()).isEqualTo(200);
        final JsonNode body = MAPPER.readTree(response.body());
        assertThat(body.get("totalMatched").asInt()).isEqualTo(5);
        assertThat(body.get("displayedCount").asInt()).isEqualTo(5);
        assertThat(body.get("totalPages").asInt()).isEqualTo(1);
        assertThat(body.get("pageSize").asInt()).isEqualTo(100);
        assertThat(body.get("inFlightCount").asInt()).isEqualTo(1);

        final JsonNode groups = body.get("groups");
        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).get("courtRoomName").asString()).isEqualTo("Courtroom 01");
        assertThat(names(groups.get(0))).containsExactly("Bob Brown", "Dan Failed", "Zara Adams");
        assertThat(groups.get(1).get("courtRoomName").asString()).isEqualTo("Courtroom 02");
        assertThat(names(groups.get(1))).containsExactly("Carl Clark", "Acme Haulage Ltd (C)");

        final JsonNode bob = groups.get(0).get("hearings").get(0);
        assertThat(bob.get("hearingType").asString()).isEqualTo("Enforcement");
        assertThat(bob.get("urn").asString()).isEqualTo("URN000002");
        assertThat(bob.get("accountNumber").asString()).isEqualTo("ACC0000002");
        assertThat(bob.get("dateOfBirth").asString()).isEqualTo("1975-06-07");
        assertThat(bob.get("offenceTitles")).hasSize(2);
        assertThat(bob.get("failedToShare").asBoolean()).isFalse();
        assertThat(groups.get(0).get("hearings").get(1).get("failedToShare").asBoolean()).isTrue();

        final JsonNode zara = groups.get(0).get("hearings").get(2);
        assertThat(zara.get("cases").get(0).get("defendants")).hasSize(2);
        assertThat(zara.get("accountNumber").asString()).isEqualTo("ACC0000001");
        assertThat(groups.get(1).get("hearings").get(1).has("accountNumber")).isFalse();

        WIREMOCK.verify(getRequestedFor(urlPathEqualTo(LISTING)).withHeader("CJSCPPUID", equalTo(USER))
                .withQueryParam("businessType", equalTo("ENF")));
        WIREMOCK.verify(getRequestedFor(urlPathEqualTo(LISTING)).withQueryParam("businessType", equalTo("ENF_AUTO")));
    }

    @Test
    void search_should_page_rows_and_repeat_group_headers() throws Exception {
        final HttpResponse<String> response = call("/hearings?ouCode=B01AA00&startDate=2026-12-01&page=2", USER);

        assertThat(response.statusCode()).isEqualTo(200);
        final JsonNode body = MAPPER.readTree(response.body());
        assertThat(body.get("page").asInt()).isEqualTo(2);
        assertThat(body.get("displayedCount").asInt()).isZero();
        assertThat(body.get("groups")).isEmpty();
    }

    @Test
    void search_should_call_listing_once_for_one_business_type_and_pass_the_session() throws Exception {
        final HttpResponse<String> response =
                call("/hearings?ouCode=B01AA00&startDate=2026-12-01&businessType=ENF_AUTO&session=AM&courtRoomId=" + R1,
                        USER);

        assertThat(response.statusCode()).isEqualTo(200);
        WIREMOCK.verify(1, getRequestedFor(urlPathEqualTo(LISTING)));
        WIREMOCK.verify(getRequestedFor(urlPathEqualTo(LISTING)).withQueryParam("courtSession", equalTo("AM"))
                .withQueryParam("courtRoomId", equalTo(R1)));
    }

    @Test
    void search_should_refuse_a_court_outside_the_users_court_centres() throws Exception {
        final HttpResponse<String> response = call("/hearings?ouCode=C99XX00&startDate=2026-12-01", USER);

        assertProblem(response, 403, "COURT_NOT_PERMITTED");
    }

    @Test
    void search_should_refuse_dates_outside_the_window() throws Exception {
        assertProblem(call("/hearings?ouCode=B01AA00&startDate=2026-11-26", USER), 400, "INVALID_FILTER");
        assertProblem(call("/hearings?ouCode=B01AA00&startDate=2026-12-01&endDate=2026-12-26", USER), 400,
                "INVALID_FILTER");
        assertProblem(call("/hearings?ouCode=B01AA00&startDate=not-a-date", USER), 400, "INVALID_FILTER");
        assertProblem(call("/hearings?ouCode=B01AA00&startDate=2026-12-01&courtRoomId="
                + UUID.randomUUID(), USER), 400, "INVALID_FILTER");
    }

    @Test
    void search_should_refuse_a_request_without_a_user() throws Exception {
        final HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                URI.create(base() + "/hearings?ouCode=B01AA00&startDate=2026-12-01")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void any_identified_caller_should_pass_authorisation_and_the_permissions_lookup_should_be_made_as_them()
            throws Exception {
        final String stranger = UUID.randomUUID().toString();
        WIREMOCK.stubFor(get(urlPathEqualTo("/usersgroups-query-api/query/api/rest/usersgroups/users/logged-in-user/roles"))
                .withHeader("CJSCPPUID", equalTo(stranger))
                .willReturn(okJson("{\"allRoles\":[]}")));

        assertThat(call("/config", stranger).statusCode()).isEqualTo(200);
        assertProblem(call("/hearings?ouCode=B01AA00&startDate=2026-12-01", stranger), 403, "COURT_NOT_PERMITTED");
        WIREMOCK.verify(getRequestedFor(urlPathEqualTo(
                "/usersgroups-query-api/query/api/rest/usersgroups/users/logged-in-user/permissions"))
                .withHeader("CJSCPPUID", equalTo(stranger)));
    }

    @Test
    void config_should_refuse_a_request_without_a_user() throws Exception {
        final HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(base() + "/config"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void search_should_refuse_too_many_results() throws Exception {
        stubOtherCourt();
        WIREMOCK.stubFor(get(urlPathEqualTo(LISTING)).withQueryParam("ouCode", equalTo(OTHER_COURT))
                .willReturn(okJson("{\"results\":4000,\"pageCount\":1,\"hearings\":[]}")));

        assertProblem(call("/hearings?ouCode=B01ZZ00&startDate=2026-12-01", USER), 422, "TOO_MANY_RESULTS");
    }

    @Test
    void search_should_answer_503_when_listing_is_unavailable_and_502_when_it_fails() throws Exception {
        stubOtherCourt();
        WIREMOCK.stubFor(get(urlPathEqualTo(LISTING)).withQueryParam("ouCode", equalTo(OTHER_COURT))
                .willReturn(aResponse().withStatus(503)));
        assertProblem(call("/hearings?ouCode=B01ZZ00&startDate=2026-12-01", USER), 503, "DEPENDENCY_UNAVAILABLE");
        WIREMOCK.verify(3, getRequestedFor(urlPathEqualTo(LISTING)));

        WIREMOCK.stubFor(get(urlPathEqualTo(LISTING)).withQueryParam("ouCode", equalTo(OTHER_COURT))
                .willReturn(aResponse().withStatus(400)));
        assertProblem(call("/hearings?ouCode=B01ZZ00&startDate=2026-12-02", USER), 502, "DEPENDENCY_FAILED");
    }

    @Test
    void config_should_return_the_settings() throws Exception {
        final HttpResponse<String> response = call("/config", USER);

        assertThat(response.statusCode()).isEqualTo(200);
        final JsonNode body = MAPPER.readTree(response.body());
        assertThat(body.get("maxBatchSize").asInt()).isEqualTo(100);
        assertThat(body.get("searchWindowDays").asInt()).isEqualTo(28);
    }

    @Test
    void health_should_be_up_without_a_user() throws Exception {
        final HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                URI.create(base() + "/actuator/health/readiness")).GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }

    private void stubOtherCourt() {
        WIREMOCK.stubFor(get(urlPathEqualTo(COURTROOMS)).withQueryParam("oucode", equalTo(OTHER_COURT))
                .willReturn(okJson("{\"organisationunits\":[{\"id\":\"" + UUID.randomUUID()
                        + "\",\"oucode\":\"B01ZZ00\",\"courtrooms\":[]}]}")));
    }

    private static List<String> names(final JsonNode group) {
        return group.get("hearings").valueStream().map(row -> row.get("defendantName").asString()).toList();
    }

    private static void assertProblem(final HttpResponse<String> response, final int status, final String code)
            throws IOException {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(MAPPER.readTree(response.body()).get("code").asString()).isEqualTo(code);
    }

    private HttpResponse<String> call(final String path, final String user) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create(base() + path)).header("CJSCPPUID", user).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String base() {
        return "http://localhost:" + port + "/bulkresulting";
    }

    private static UUID hearing(final int number) {
        return UUID.fromString(String.format("33333333-3333-4333-8333-%012d", number));
    }

    private UUID insertBatch(final String createdBy) {
        final UUID bulkId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO bulk_resulting (bulk_id, created_by, request_hash, court_centre_id, hearing_day,
                                                    template, status)
                        VALUES (:bulkId, :createdBy, 'hash', '11111111-1111-4111-8111-111111111111',
                                DATE '2026-12-01', '[]'::jsonb, 'IN_PROGRESS')
                        """)
                .param("bulkId", bulkId)
                .param("createdBy", UUID.fromString(createdBy))
                .update();
        return bulkId;
    }

    private void insertHearing(final UUID bulkId, final UUID hearingId, final String status) {
        jdbcClient.sql("INSERT INTO bulk_resulting_hearing (bulk_id, hearing_id, status) VALUES (:b, :h, :s)")
                .param("b", bulkId)
                .param("h", hearingId)
                .param("s", status)
                .update();
    }
}
