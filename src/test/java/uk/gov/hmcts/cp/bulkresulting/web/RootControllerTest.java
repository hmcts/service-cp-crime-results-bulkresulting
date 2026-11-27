package uk.gov.hmcts.cp.bulkresulting.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RootControllerTest {

    @Test
    void root_should_greet() {
        assertThat(new RootController().getRoot().getBody()).contains("bulkresulting");
    }
}
