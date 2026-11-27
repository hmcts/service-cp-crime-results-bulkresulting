package uk.gov.hmcts.cp.bulkresulting.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.bulkresulting.config.BulkResultingProperties;
import uk.gov.hmcts.cp.bulkresulting.openapi.api.ConfigApi;
import uk.gov.hmcts.cp.bulkresulting.openapi.model.BulkResultingConfig;

/** {@code GET /config}: the settings the UI must follow. */
@RestController
public class ConfigController implements ConfigApi {

    private final BulkResultingProperties properties;

    /**
     * Creates the controller.
     *
     * @param properties the service settings
     */
    public ConfigController(final BulkResultingProperties properties) {
        this.properties = properties;
    }

    @Override
    @SuppressWarnings({"PMD.FormalParameterNamingConventions", "checkstyle:ParameterName"})
    public ResponseEntity<BulkResultingConfig> getConfig(final String CJSCPPUID, final String CPPCLIENTCORRELATIONID) {
        return ResponseEntity.ok(BulkResultingConfig.builder()
                .maxBatchSize(properties.search().maxBatchSize())
                .searchWindowDays(properties.search().searchWindowDays())
                .build());
    }
}
