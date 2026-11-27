package uk.gov.hmcts.cp.bulkresulting.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.bulkresulting.openapi.api.RootApi;

/** {@code GET /}. */
@RestController
public class RootController implements RootApi {

    @Override
    public ResponseEntity<String> getRoot() {
        return ResponseEntity.ok("Welcome to service-cp-crime-results-bulkresulting");
    }
}
