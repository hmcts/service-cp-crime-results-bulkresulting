package uk.gov.hmcts.cp.bulkresulting.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The service starts against a real Postgres, reports ready without a user, and serves
 * {@code GET /config} and {@code GET /} through the real authorisation filter.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServiceStartupIT {

    private static final String PERMISSIONS =
            "/usersgroups-query-api/query/api/rest/usersgroups/users/logged-in-user/permissions";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort()
            .usingFilesUnderDirectory("docker/wiremock"));

    @LocalServerPort
    private int port;

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
        registry.add("authz.http.identity-url-template", () -> WIREMOCK.baseUrl() + PERMISSIONS);
    }

    private HttpResponse<String> call(final String path, final String user) throws Exception {
        final HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/bulkresulting" + path)).GET();
        if (user != null) {
            request.header("CJSCPPUID", user);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void readiness_should_be_up_without_a_user() throws Exception {
        final HttpResponse<String> response = call("/actuator/health/readiness", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }

    @Test
    void config_should_admit_any_identified_caller_and_look_up_their_permissions() throws Exception {
        final String user = UUID.randomUUID().toString();

        final HttpResponse<String> response = call("/config", user);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"maxBatchSize\":100").contains("\"searchWindowDays\":28");
        WIREMOCK.verify(getRequestedFor(urlPathEqualTo(PERMISSIONS)).withHeader("CJSCPPUID", equalTo(user)));
    }

    @Test
    void config_should_refuse_a_request_without_a_user() throws Exception {
        assertThat(call("/config", null).statusCode()).isEqualTo(401);
    }

    @Test
    void root_should_answer_an_identified_caller() throws Exception {
        final HttpResponse<String> response = call("/", UUID.randomUUID().toString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("bulkresulting");
    }
}
