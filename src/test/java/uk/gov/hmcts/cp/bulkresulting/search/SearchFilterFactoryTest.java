package uk.gov.hmcts.cp.bulkresulting.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.bulkresulting.openapi.model.BusinessType;
import uk.gov.hmcts.cp.bulkresulting.openapi.model.CourtSession;

class SearchFilterFactoryTest {

    // 23:30 UTC on 30 June is 00:30 on 1 July in London: today is 1 July.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-30T23:30:00Z"), ZoneId.of("Europe/London"));
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 1);
    private final SearchFilterFactory factory = new SearchFilterFactory(CLOCK, 28);

    @Test
    void create_with_defaults_should_use_the_start_date_any_session_both_types_and_page_one() {
        final SearchFilter filter = factory.create(" b01aa00 ", TODAY, null, null, null, null, null);

        assertThat(filter.ouCode()).isEqualTo("B01AA00");
        assertThat(filter.endDate()).isEqualTo(TODAY);
        assertThat(filter.session()).isEqualTo(CourtSession.ANY);
        assertThat(filter.businessType()).isEmpty();
        assertThat(filter.courtRoomId()).isEmpty();
        assertThat(filter.page()).isEqualTo(1);
    }

    @Test
    void create_with_every_value_should_keep_them_and_drop_the_page_for_the_cache_key() {
        final UUID room = UUID.randomUUID();
        final SearchFilter filter = factory.create("B01AA00", TODAY.plusDays(1), TODAY.plusDays(28), room,
                CourtSession.PM, BusinessType.ENF, 3);

        assertThat(filter.courtRoomId()).contains(room);
        assertThat(filter.businessType()).contains(BusinessType.ENF);
        assertThat(filter.withoutPage().page()).isEqualTo(1);
        assertThat(filter.withoutPage().endDate()).isEqualTo(TODAY.plusDays(28));
    }

    @Test
    void create_should_refuse_values_outside_the_window_or_missing() {
        assertThatThrownBy(() -> factory.create("B01AA00", TODAY.minusDays(1), null, null, null, null, null))
                .isInstanceOf(InvalidFilterException.class).hasMessageContaining("past");
        assertThatThrownBy(() -> factory.create("B01AA00", TODAY, TODAY.plusDays(29), null, null, null, null))
                .isInstanceOf(InvalidFilterException.class).hasMessageContaining("28 days");
        assertThatThrownBy(() -> factory.create("B01AA00", TODAY.plusDays(2), TODAY.plusDays(1), null, null, null,
                null)).isInstanceOf(InvalidFilterException.class).hasMessageContaining("before");
        assertThatThrownBy(() -> factory.create(" ", TODAY, null, null, null, null, null))
                .isInstanceOf(InvalidFilterException.class);
        assertThatThrownBy(() -> factory.create(null, TODAY, null, null, null, null, null))
                .isInstanceOf(InvalidFilterException.class);
        assertThatThrownBy(() -> factory.create("B01AA00", null, null, null, null, null, null))
                .isInstanceOf(InvalidFilterException.class);
        assertThatThrownBy(() -> factory.create("B01AA00", TODAY, null, null, null, null, 0))
                .isInstanceOf(InvalidFilterException.class);
    }
}
