package uk.gov.hmcts.cp.bulkresulting.search;

import com.github.benmanes.caffeine.cache.Cache;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.cp.bulkresulting.batch.BatchStateRepository;
import uk.gov.hmcts.cp.bulkresulting.cp.CallerContext;
import uk.gov.hmcts.cp.bulkresulting.cp.Court;
import uk.gov.hmcts.cp.bulkresulting.cp.CpDependencyException;
import uk.gov.hmcts.cp.bulkresulting.cp.HearingQueryClient;
import uk.gov.hmcts.cp.bulkresulting.cp.HearingShareState;
import uk.gov.hmcts.cp.bulkresulting.cp.ListingClient;
import uk.gov.hmcts.cp.bulkresulting.cp.ListingQuery;
import uk.gov.hmcts.cp.bulkresulting.cp.ListingResult;
import uk.gov.hmcts.cp.bulkresulting.cp.ProgressionClient;
import uk.gov.hmcts.cp.bulkresulting.cp.ReferenceDataClient;
import uk.gov.hmcts.cp.bulkresulting.cp.UsersGroupsClient;
import uk.gov.hmcts.cp.bulkresulting.openapi.model.BusinessType;
import uk.gov.hmcts.cp.bulkresulting.openapi.model.CourtSession;

/**
 * The hearing search (solution design §6), run as the calling user.
 *
 * <ol start="0">
 *     <li>The court must be one of the user's court centres in usersgroups.</li>
 *     <li>Listing's range search, once per business type, gives the candidates. A search that
 *     listing reports as too big is refused. Multi-day hearings are dropped and the rest
 *     de-duplicated. Candidates are cached per user and filter.</li>
 *     <li>Hearing's share state is read once per date and courtroom, fresh on every request.
 *     Hearings shared for the day, or with an application, are dropped.</li>
 *     <li>The batch store hides hearings in flight and labels those whose latest bulk attempt did
 *     not share.</li>
 *     <li>Progression gives the account number of each case on the page shown.</li>
 * </ol>
 */
public class HearingSearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HearingSearchService.class);

    private final UsersGroupsClient usersGroups;
    private final ReferenceDataClient referenceData;
    private final ListingClient listing;
    private final HearingQueryClient hearings;
    private final ProgressionClient progression;
    private final BatchStateRepository batchState;
    private final BoundedParallel parallel;
    private final Cache<CandidateKey, List<CandidateHearing>> candidateCache;
    private final Cache<String, Court> courtCache;
    private final int listingMaxResults;
    private final int pageSize;

    /**
     * Creates the service.
     *
     * @param clients        the Common Platform clients
     * @param batchState     the batch store
     * @param parallel       bounded parallel calls
     * @param candidateCache listing candidates per user and filter
     * @param courtCache     reference data per OU code
     * @param limits         the listing and paging limits
     */
    public HearingSearchService(final Clients clients, final BatchStateRepository batchState,
                                final BoundedParallel parallel,
                                final Cache<CandidateKey, List<CandidateHearing>> candidateCache,
                                final Cache<String, Court> courtCache, final Limits limits) {
        this.usersGroups = clients.usersGroups();
        this.referenceData = clients.referenceData();
        this.listing = clients.listing();
        this.hearings = clients.hearings();
        this.progression = clients.progression();
        this.batchState = batchState;
        this.parallel = parallel;
        this.candidateCache = candidateCache;
        this.courtCache = courtCache;
        this.listingMaxResults = limits.listingMaxResults();
        this.pageSize = limits.pageSize();
    }

    /**
     * The Common Platform clients the search uses.
     *
     * @param usersGroups   usersgroups
     * @param referenceData reference data
     * @param listing       listing
     * @param hearings      hearing
     * @param progression   progression
     */
    public record Clients(UsersGroupsClient usersGroups, ReferenceDataClient referenceData, ListingClient listing,
                          HearingQueryClient hearings, ProgressionClient progression) {
    }

    /**
     * Listing and paging limits.
     *
     * @param listingMaxResults a range search reporting this many results or more is refused
     * @param pageSize          hearing rows per page
     */
    public record Limits(int listingMaxResults, int pageSize) {
    }

    /**
     * The candidate cache key: the user and the filter without its page.
     *
     * @param userId the user
     * @param filter the filter on page 1
     */
    public record CandidateKey(String userId, SearchFilter filter) {
    }

    /**
     * Runs the search.
     *
     * @param caller the calling user
     * @param filter the checked filter
     * @return the page
     */
    public SearchPage search(final CallerContext caller, final SearchFilter filter) {
        final UUID userId = parseUserId(caller.userId());
        checkCourtAccess(caller, filter.ouCode());
        final Court court = court(caller, filter.ouCode());
        filter.courtRoomId().ifPresent(roomId -> {
            if (!court.hasCourtRoom(roomId)) {
                throw new InvalidFilterException("courtRoomId is not a courtroom at the court");
            }
        });

        final List<CandidateHearing> candidates = candidateCache.get(
                new CandidateKey(caller.userId(), filter.withoutPage()), key -> loadCandidates(caller, filter));
        final List<CandidateHearing> unshared = applyShareState(caller, court, candidates);
        final List<UUID> ids = unshared.stream().map(CandidateHearing::hearingId).toList();
        final Set<UUID> inFlight = batchState.inFlight(ids);
        final Set<UUID> failed = batchState.failedToShare(ids);

        final List<HearingRow> rows = new ArrayList<>();
        for (final CandidateHearing hearing : unshared) {
            if (!inFlight.contains(hearing.hearingId())) {
                rows.add(new HearingRow(hearing, hearing.courtRoomId().flatMap(court::courtRoomName),
                        DisplayedDefendant.from(hearing), failed.contains(hearing.hearingId())));
            }
        }
        rows.sort(HearingRowOrder.DISPLAY_ORDER);

        final int from = Math.min(rows.size(), (filter.page() - 1) * pageSize);
        final List<HearingRow> pageRows = rows.subList(from, Math.min(rows.size(), from + pageSize));
        return new SearchPage(filter.page(), pageSize, rows.size(), pageRows, accountNumbers(caller, pageRows),
                batchState.inFlightCountFor(userId));
    }

    private static UUID parseUserId(final String userId) {
        try {
            return UUID.fromString(userId);
        } catch (final IllegalArgumentException notUuid) {
            throw new InvalidCallerException("CJSCPPUID is not a user id");
        }
    }

    private void checkCourtAccess(final CallerContext caller, final String ouCode) {
        if (!usersGroups.courtOuCodes(caller).contains(ouCode)) {
            throw new CourtNotPermittedException("The court is not one of your court centres");
        }
    }

    private Court court(final CallerContext caller, final String ouCode) {
        final Court cached = courtCache.getIfPresent(ouCode);
        final Court court;
        if (cached == null) {
            court = referenceData.court(ouCode, caller).orElseThrow(() -> new CpDependencyException(
                    ReferenceDataClient.DEPENDENCY, CpDependencyException.Kind.BAD_RESPONSE, 200,
                    "reference data has no court centre for the OU code"));
            courtCache.put(ouCode, court);
        } else {
            court = cached;
        }
        return court;
    }

    // One listing query per business type: at most two.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private List<CandidateHearing> loadCandidates(final CallerContext caller, final SearchFilter filter) {
        final List<BusinessType> businessTypes = filter.businessType()
                .map(List::of)
                .orElse(List.of(BusinessType.ENF, BusinessType.ENF_AUTO));
        final Optional<String> session = filter.session() == CourtSession.ANY
                ? Optional.empty()
                : Optional.of(filter.session().getValue());
        final Map<UUID, CandidateHearing> unique = new LinkedHashMap<>();
        int multiDay = 0;
        for (final BusinessType businessType : businessTypes) {
            final ListingResult result = listing.rangeSearch(new ListingQuery(filter.ouCode(), filter.startDate(),
                    filter.endDate(), filter.courtRoomId(), session, businessType.getValue()), caller);
            if (result.results() >= listingMaxResults) {
                throw new TooManyResultsException("Too many hearings match. Narrow the search, for example "
                        + "by date, courtroom, session or business type");
            }
            for (final CandidateHearing hearing : result.hearings()) {
                if (hearing.isMultiDay()) {
                    multiDay++;
                } else {
                    unique.putIfAbsent(hearing.hearingId(), hearing);
                }
            }
        }
        LOGGER.info("Listing returned {} single-day candidate hearing(s); dropped {} multi-day hearing day(s)",
                unique.size(), multiDay);
        return List.copyOf(unique.values());
    }

    // One key per hearing day and courtroom, and one list per key, is the point of the loop.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private List<CandidateHearing> applyShareState(final CallerContext caller, final Court court,
                                                   final List<CandidateHearing> candidates) {
        final Map<RoomDay, List<CandidateHearing>> byRoomDay = new LinkedHashMap<>();
        int noRoom = 0;
        for (final CandidateHearing hearing : candidates) {
            if (hearing.courtRoomId().isPresent()) {
                byRoomDay.computeIfAbsent(new RoomDay(hearing.hearingDate(), hearing.courtRoomId().get()),
                        key -> new ArrayList<>()).add(hearing);
            } else {
                noRoom++;
            }
        }
        if (noRoom > 0) {
            LOGGER.warn("Excluded {} candidate hearing(s) with no courtroom: their share state cannot be read",
                    noRoom);
        }
        final List<RoomDay> roomDays = List.copyOf(byRoomDay.keySet());
        final List<Map<UUID, HearingShareState>> states = parallel.map(roomDays, roomDay ->
                hearings.shareStates(roomDay.date(), court.courtCentreId(), roomDay.courtRoomId(), caller));

        final List<CandidateHearing> eligible = new ArrayList<>();
        int notReturned = 0;
        int shared = 0;
        for (int i = 0; i < roomDays.size(); i++) {
            final Map<UUID, HearingShareState> roomStates = states.get(i);
            for (final CandidateHearing hearing : byRoomDay.get(roomDays.get(i))) {
                final HearingShareState state = roomStates.get(hearing.hearingId());
                if (state == null) {
                    notReturned++;
                } else if (state.isEligible()) {
                    eligible.add(hearing);
                } else {
                    shared++;
                }
            }
        }
        LOGGER.info("Share state: {} eligible, {} shared or with an application, {} not returned by hearing",
                eligible.size(), shared, notReturned);
        return eligible;
    }

    private Map<UUID, Optional<String>> accountNumbers(final CallerContext caller, final List<HearingRow> rows) {
        final Set<UUID> caseIds = new LinkedHashSet<>();
        rows.forEach(row -> row.hearing().cases().forEach(listedCase -> caseIds.add(listedCase.caseId())));
        final List<UUID> ordered = List.copyOf(caseIds);
        final List<Optional<String>> numbers = parallel.map(ordered,
                caseId -> progression.accountNumber(caseId, caller));
        final Map<UUID, Optional<String>> byCase = new LinkedHashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            byCase.put(ordered.get(i), numbers.get(i));
        }
        return byCase;
    }

    private record RoomDay(LocalDate date, UUID courtRoomId) {
    }
}
