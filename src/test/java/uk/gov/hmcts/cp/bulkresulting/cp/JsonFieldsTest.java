package uk.gov.hmcts.cp.bulkresulting.cp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class JsonFieldsTest {

    private static final JsonNode NODE = JsonMapper.builder().build().readTree("""
            {"blank":" ","nul":null,"date":"2026-12-01T10:00:00Z","bad":"x","flag":true,"str":"true","arr":"no"}""");

    @Test
    void text_should_be_empty_for_missing_null_blank_and_null_nodes() {
        assertThat(JsonFields.text(NODE, "missing")).isEmpty();
        assertThat(JsonFields.text(NODE, "nul")).isEmpty();
        assertThat(JsonFields.text(NODE, "blank")).isEmpty();
        assertThat(JsonFields.text(null, "x")).isEmpty();
    }

    @Test
    void date_should_cut_date_times_and_reject_non_dates() {
        assertThat(JsonFields.date(NODE, "date")).contains(LocalDate.of(2026, 12, 1));
        assertThat(JsonFields.date(NODE, "bad")).isEmpty();
        assertThat(JsonFields.uuid(NODE, "bad")).isEmpty();
    }

    @Test
    void booleans_and_arrays_should_be_read_strictly() {
        assertThat(JsonFields.isTrue(NODE, "flag")).isTrue();
        assertThat(JsonFields.isTrue(NODE, "str")).isFalse();
        assertThat(JsonFields.isTrue(null, "flag")).isFalse();
        assertThat(JsonFields.array(NODE, "arr")).isEmpty();
        assertThat(JsonFields.array(null, "arr")).isEmpty();
    }
}
