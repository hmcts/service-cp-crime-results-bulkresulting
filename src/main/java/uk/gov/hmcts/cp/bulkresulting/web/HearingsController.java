package uk.gov.hmcts.cp.bulkresulting.web;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.bulkresulting.cp.CallerContext;
import uk.gov.hmcts.cp.bulkresulting.openapi.api.HearingsApi;
import uk.gov.hmcts.cp.bulkresulting.openapi.model.BusinessType;
import uk.gov.hmcts.cp.bulkresulting.openapi.model.CourtSession;
import uk.gov.hmcts.cp.bulkresulting.openapi.model.HearingSearchResponse;
import uk.gov.hmcts.cp.bulkresulting.search.HearingSearchService;
import uk.gov.hmcts.cp.bulkresulting.search.SearchFilterFactory;

/** {@code GET /hearings}. */
@RestController
public class HearingsController implements HearingsApi {

    private final HearingSearchService searchService;
    private final SearchFilterFactory filterFactory;

    /**
     * Creates the controller.
     *
     * @param searchService the search
     * @param filterFactory builds and checks the filter
     */
    public HearingsController(final HearingSearchService searchService, final SearchFilterFactory filterFactory) {
        this.searchService = searchService;
        this.filterFactory = filterFactory;
    }

    @Override
    @SuppressWarnings({"PMD.FormalParameterNamingConventions", "checkstyle:ParameterName"})
    public ResponseEntity<HearingSearchResponse> searchHearings(final String CJSCPPUID, final String ouCode,
                                                                final LocalDate startDate,
                                                                final String CPPCLIENTCORRELATIONID,
                                                                final UUID courtRoomId, final LocalDate endDate,
                                                                final CourtSession session,
                                                                final BusinessType businessType,
                                                                final Integer page) {
        final CallerContext caller = new CallerContext(CJSCPPUID, Optional.ofNullable(CPPCLIENTCORRELATIONID));
        return ResponseEntity.ok(SearchResponseAssembler.toResponse(searchService.search(caller,
                filterFactory.create(ouCode, startDate, endDate, courtRoomId, session, businessType, page))));
    }
}
