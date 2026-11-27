package uk.gov.hmcts.cp.bulkresulting.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import uk.gov.hmcts.cp.bulkresulting.openapi.model.EligibleHearing;
import uk.gov.hmcts.cp.bulkresulting.openapi.model.HearingCase;
import uk.gov.hmcts.cp.bulkresulting.openapi.model.HearingDefendant;
import uk.gov.hmcts.cp.bulkresulting.openapi.model.HearingGroup;
import uk.gov.hmcts.cp.bulkresulting.openapi.model.HearingOffence;
import uk.gov.hmcts.cp.bulkresulting.openapi.model.HearingSearchResponse;
import uk.gov.hmcts.cp.bulkresulting.search.DisplayedDefendant;
import uk.gov.hmcts.cp.bulkresulting.search.HearingRow;
import uk.gov.hmcts.cp.bulkresulting.search.ListedCase;
import uk.gov.hmcts.cp.bulkresulting.search.ListedDefendant;
import uk.gov.hmcts.cp.bulkresulting.search.ListedOffence;
import uk.gov.hmcts.cp.bulkresulting.search.SearchPage;

/**
 * Shapes a search page for the UI: rows grouped by hearing date and courtroom, in display order.
 * A group that spans pages appears on each page it has rows on.
 */
public final class SearchResponseAssembler {

    private SearchResponseAssembler() {
    }

    /**
     * Builds the response.
     *
     * @param page the search page
     * @return the response body
     */
    // A new group object starts each time the hearing date or courtroom changes.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public static HearingSearchResponse toResponse(final SearchPage page) {
        final List<HearingGroup> groups = new ArrayList<>();
        HearingGroup current = null;
        for (final HearingRow row : page.rows()) {
            if (current == null || !sameGroup(current, row)) {
                current = HearingGroup.builder()
                        .hearingDate(row.hearing().hearingDate())
                        .courtRoomId(row.hearing().courtRoomId().orElse(null))
                        .courtRoomName(row.courtRoomName().orElse(null))
                        .hearings(new ArrayList<>())
                        .build();
                groups.add(current);
            }
            current.getHearings().add(toHearing(row, page));
        }
        return HearingSearchResponse.builder()
                .page(page.page())
                .pageSize(page.pageSize())
                .totalPages(page.totalPages())
                .totalMatched(page.totalMatched())
                .displayedCount(page.rows().size())
                .inFlightCount(page.inFlightCount())
                .groups(groups)
                .build();
    }

    private static boolean sameGroup(final HearingGroup group, final HearingRow row) {
        return group.getHearingDate().equals(row.hearing().hearingDate())
                && Objects.equals(group.getCourtRoomId(), row.hearing().courtRoomId().orElse(null));
    }

    private static EligibleHearing toHearing(final HearingRow row, final SearchPage page) {
        final Optional<DisplayedDefendant> displayed = row.displayed();
        final Optional<ListedDefendant> defendant = displayed.map(DisplayedDefendant::defendant);
        final Optional<ListedCase> displayedCase = displayed.map(DisplayedDefendant::listedCase);
        return EligibleHearing.builder()
                .hearingId(row.hearing().hearingId())
                .hearingType(row.hearing().hearingType())
                .failedToShare(row.failedToShare())
                .defendantName(displayed.flatMap(DisplayedDefendant::displayName).orElse(null))
                .firstName(defendant.map(ListedDefendant::firstName).orElse(null))
                .lastName(defendant.map(ListedDefendant::lastName).orElse(null))
                .organisationName(defendant.map(ListedDefendant::organisationName).orElse(null))
                .dateOfBirth(defendant.map(ListedDefendant::dateOfBirth).orElse(null))
                .urn(displayedCase.map(ListedCase::urn).orElse(null))
                .accountNumber(displayedCase.map(ListedCase::caseId).flatMap(page::accountNumber).orElse(null))
                .offenceTitles(defendant.map(SearchResponseAssembler::offenceTitles).orElse(new ArrayList<>()))
                .cases(row.hearing().cases().stream().map(listedCase -> toCase(listedCase, page)).toList())
                .build();
    }

    private static List<String> offenceTitles(final ListedDefendant defendant) {
        return defendant.offences().stream().map(ListedOffence::offenceTitle).filter(Objects::nonNull).toList();
    }

    private static HearingCase toCase(final ListedCase listedCase, final SearchPage page) {
        final UUID caseId = listedCase.caseId();
        return HearingCase.builder()
                .caseId(caseId)
                .urn(listedCase.urn())
                .accountNumber(page.accountNumber(caseId).orElse(null))
                .defendants(listedCase.defendants().stream().map(SearchResponseAssembler::toDefendant).toList())
                .build();
    }

    private static HearingDefendant toDefendant(final ListedDefendant defendant) {
        return HearingDefendant.builder()
                .defendantId(defendant.defendantId())
                .firstName(defendant.firstName())
                .lastName(defendant.lastName())
                .organisationName(defendant.organisationName())
                .dateOfBirth(defendant.dateOfBirth())
                .offences(defendant.offences().stream().map(SearchResponseAssembler::toOffence).toList())
                .build();
    }

    private static HearingOffence toOffence(final ListedOffence offence) {
        return HearingOffence.builder()
                .offenceId(offence.offenceId())
                .offenceCode(offence.offenceCode())
                .offenceTitle(offence.offenceTitle())
                .build();
    }
}
