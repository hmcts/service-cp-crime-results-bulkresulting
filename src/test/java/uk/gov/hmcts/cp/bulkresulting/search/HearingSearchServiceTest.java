package uk.gov.hmcts.cp.bulkresulting.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.bulkresulting.batch.BatchStateRepository;
import uk.gov.hmcts.cp.bulkresulting.cp.CallerContext;
import uk.gov.hmcts.cp.bulkresulting.cp.Court;
import uk.gov.hmcts.cp.bulkresulting.cp.CpDependencyException;
import uk.gov.hmcts.cp.bulkresulting.cp.HearingQueryClient;
import uk.gov.hmcts.cp.bulkresulting.cp.HearingShareState;
import uk.gov.hmcts.cp.bulkresulting.cp.ListingClient;
import uk.gov.hmcts.cp.bulkresulting.cp.ListingResult;
import uk.gov.hmcts.cp.bulkresulting.cp.ProgressionClient;
import uk.gov.hmcts.cp.bulkresulting.cp.ReferenceDataClient;
import uk.gov.hmcts.cp.bulkresulting.cp.UsersGroupsClient;
import uk.gov.hmcts.cp.bulkresulting.openapi.model.BusinessType;
import uk.gov.hmcts.cp.bulkresulting.openapi.model.CourtSession;

class HearingSearchServiceTest {

    private static final String USER = UUID.randomUUID().toString();
    private static final CallerContext CALLER = new CallerContext(USER, Optional.empty());
    private static final LocalDate DAY = LocalDate.of(2026, 12, 1);
    private static final UUID ROOM = UUID.randomUUID();
    private static final Court COURT = new Court(UUID.randomUUID(), "B01AA00", Map.of(ROOM, "Courtroom 01"));

    private final UsersGroupsClient usersGroups = mock(UsersGroupsClient.class);
    private final ReferenceDataClient referenceData = mock(ReferenceDataClient.class);
    private final ListingClient listing = mock(ListingClient.class);
    private final HearingQueryClient hearings = mock(HearingQueryClient.class);
    private final ProgressionClient progression = mock(ProgressionClient.class);
    private final BatchStateRepository batchState = mock(BatchStateRepository.class);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final HearingSearchService service = new HearingSearchService(
            new HearingSearchService.Clients(usersGroups, referenceData, listing, hearings, progression),
            batchState, new BoundedParallel(executor, 2), Caffeine.newBuilder().build(), Caffeine.newBuilder().build(),
            new HearingSearchService.Limits(4000, 2));

    @AfterEach
    void close() {
        executor.close();
    }

    private static SearchFilter filter(final int page) {
        return new SearchFilter("B01AA00", DAY, DAY, Optional.empty(), CourtSession.ANY, Optional.of(BusinessType.ENF),
                page);
    }

    private static CandidateHearing candidate(final String first, final Optional<UUID> room) {
        return new CandidateHearing(UUID.randomUUID(), DAY, room, "Enforcement", Optional.of(1L),
                List.of(new ListedCase(UUID.randomUUID(), "URN", List.of(
                        new ListedDefendant(UUID.randomUUID(), first, "X", null, null, List.of())))));
    }

    private void courtAllowed() {
        when(usersGroups.courtOuCodes(CALLER)).thenReturn(Set.of("B01AA00"));
        when(referenceData.court("B01AA00", CALLER)).thenReturn(Optional.of(COURT));
    }

    @Test
    void search_should_refuse_a_caller_whose_id_is_not_a_uuid() {
        assertThatThrownBy(() -> service.search(new CallerContext("not-a-uuid", Optional.empty()), filter(1)))
                .isInstanceOf(InvalidCallerException.class);
    }

    @Test
    void search_should_fail_with_502_kind_when_reference_data_has_no_court() {
        when(usersGroups.courtOuCodes(CALLER)).thenReturn(Set.of("B01AA00"));
        when(referenceData.court("B01AA00", CALLER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.search(CALLER, filter(1)))
                .isInstanceOfSatisfying(CpDependencyException.class,
                        failure -> assertThat(failure.getKind()).isEqualTo(CpDependencyException.Kind.BAD_RESPONSE));
    }

    @Test
    void search_should_page_rows_cache_the_court_and_candidates_and_skip_unroomed_and_unreturned_hearings() {
        courtAllowed();
        final CandidateHearing amy = candidate("Amy", Optional.of(ROOM));
        final CandidateHearing ben = candidate("Ben", Optional.of(ROOM));
        final CandidateHearing cal = candidate("Cal", Optional.of(ROOM));
        final CandidateHearing unroomed = candidate("Dee", Optional.empty());
        final CandidateHearing unreturned = candidate("Eve", Optional.of(ROOM));
        final CandidateHearing multiDay = new CandidateHearing(UUID.randomUUID(), DAY, Optional.of(ROOM), "E",
                Optional.of(3L), List.of());
        when(listing.rangeSearch(any(), any())).thenReturn(
                new ListingResult(6, List.of(cal, amy, ben, unroomed, unreturned, multiDay)));
        when(hearings.shareStates(DAY, COURT.courtCentreId(), ROOM, CALLER)).thenReturn(Map.of(
                amy.hearingId(), new HearingShareState(false, false),
                ben.hearingId(), new HearingShareState(false, false),
                cal.hearingId(), new HearingShareState(false, false)));
        when(progression.accountNumber(any(), any())).thenReturn(Optional.of("ACC"));

        final SearchPage first = service.search(CALLER, filter(1));
        final SearchPage second = service.search(CALLER, filter(2));

        assertThat(first.totalMatched()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.rows()).extracting(row -> row.displayed().orElseThrow().defendant().firstName())
                .containsExactly("Amy", "Ben");
        assertThat(second.rows()).extracting(row -> row.displayed().orElseThrow().defendant().firstName())
                .containsExactly("Cal");
        assertThat(second.accountNumber(cal.cases().get(0).caseId())).contains("ACC");
        assertThat(second.accountNumber(UUID.randomUUID())).isEmpty();
        verify(listing, times(1)).rangeSearch(any(), any());
        verify(referenceData, times(1)).court(anyString(), any());
    }

    @Test
    void search_should_refuse_too_many_results() {
        courtAllowed();
        when(listing.rangeSearch(any(), any())).thenReturn(new ListingResult(4000, List.of()));

        assertThatThrownBy(() -> service.search(CALLER, filter(1))).isInstanceOf(TooManyResultsException.class);
    }
}
