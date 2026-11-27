# Feature Specification: Hearing search for bulk resulting

**Feature Branch**: `001-hearing-search`
**Created**: 2026-11-27
**Status**: Implemented
**Input**: The bulk resulting solution design (Option A), §5 settings, §6 search and eligibility, §12 security, §13.1 tables and §13.2 REST API; stories CRA-395 (search and results table) and CRA-394 (access).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Find the enforcement hearings I can bulk result (Priority: P1)

A legal adviser at a magistrates' court chooses their court, a date or date range, and optionally a
courtroom, a session and a business type. They see the enforcement hearings that can still be
resulted, grouped by hearing date and courtroom, one row per hearing.

**Why this priority**: Nothing else in bulk resulting works without a list of eligible hearings to
select from.

**Independent Test**: Call `GET /hearings` for a court and date with stubbed Common Platform APIs and
check the rows, their grouping and order.

**Acceptance Scenarios**:

1. **Given** allocated ENF and ENF_AUTO hearings at the court on the day, **When** the user searches with no business type, **Then** both types are returned, each hearing once.
2. **Given** a hearing already shared for the day, or with a court application, **When** the user searches, **Then** it is not returned.
3. **Given** a multi-day hearing, **When** the user searches, **Then** it is not returned.
4. **Given** eligible hearings, **Then** rows are sorted by hearing date, then courtroom, then the displayed defendant's first name, and grouped by hearing date and courtroom with the courtroom's name.
5. **Given** more than 100 eligible hearings, **Then** they are paged 100 rows at a time, with the total matched, the page count and the rows on this page; a group spanning pages appears on each.

### User Story 2 - See which hearings bulk resulting is already handling (Priority: P1)

**Acceptance Scenarios**:

1. **Given** a hearing in flight in a bulk batch (PENDING, IN_PROGRESS or UNCONFIRMED), **When** anyone searches, **Then** it is hidden.
2. **Given** a hearing whose latest bulk attempt (ignoring NOT_TAKEN) did not share, **When** anyone searches, **Then** it is listed with `failedToShare` true.
3. **Given** the caller has hearings in flight, **Then** `inFlightCount` gives their number (the caller's own batches only).

### User Story 3 - Only search where I may act, within the window (Priority: P1)

**Acceptance Scenarios**:

1. **Given** a court that is not one of the user's court centres in usersgroups, **When** they search it, **Then** the answer is 403 `COURT_NOT_PERMITTED`.
2. **Given** a start date in the past, an end date beyond today + 28 days, or an end date before the start date, **Then** the answer is 400 `INVALID_FILTER`.
3. **Given** no `CJSCPPUID`, **Then** the answer is 401.
4. **Given** a search listing reports 4,000 or more matches for, **Then** the answer is 422 `TOO_MANY_RESULTS` and the user is told to narrow it.

### User Story 4 - Settings the UI follows (Priority: P2)

`GET /config` returns `maxBatchSize` (100) and `searchWindowDays` (28), so the UI's selection cap
and date picker follow the service.

### Edge Cases

- A hearing with no courtroom cannot have its share state read (hearing's query is per courtroom) and is excluded; the count is logged.
- A hearing listing returns but hearing's query does not (for example, not visible to the user) is excluded; the count is logged.
- A case progression does not hold answers 200 with an empty body: no account number.
- A hearing with several cases or defendants is one row; the row shows the first defendant of the first case as listing returns them, and `cases[]` carries all of them.
- A corporate defendant's name is shown as the organisation name followed by " (C)".
- A Common Platform API that gives no answer (timeouts, connection failures, 503/504 after retries) gives 503; any other failure gives 502; one that refuses the user gives 403.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The service MUST check the court against the user's court centres (usersgroups `logged-in-user/roles`, `allRoles[].userPlacements[].placementId`) before any other call.
- **FR-002**: The service MUST call listing's range search with `allocated=true`, `jurisdictionType=MAGISTRATES`, the OU code, the dates, the optional courtroom and session, the business type and `useMaxPageSize=true`, once per business type (ENF and ENF_AUTO when none is given).
- **FR-003**: The service MUST refuse a search for which listing reports `results` at or above the configured maximum (4,000).
- **FR-004**: The service MUST drop hearings with `hearingDayCount > 1` and de-duplicate by hearing id.
- **FR-005**: The service MUST cache listing candidates for 5 minutes per user and filter (page excluded), and MUST read the share state and the batch store fresh on every request.
- **FR-006**: The service MUST read hearing's `get.hearings` once per hearing date and courtroom, with the court centre id from reference data, and drop hearings shared for the day or with an application.
- **FR-007**: The service MUST hide hearings in flight, label failed attempts, and count the caller's in-flight hearings from the batch store.
- **FR-008**: The service MUST read the account number (`prosecutionCase.relatedUrn`) from progression only for the cases on the page returned, with at most 10 calls in flight.
- **FR-009**: Every Common Platform call MUST be made as the user (`CJSCPPUID` passed through, and `CPPCLIENTCORRELATIONID` when sent), with connect and read timeouts and bounded retries of reads.
- **FR-010**: The response MUST follow the `api-cp-crime-results-bulkresulting` contract.
- **FR-011**: Errors MUST be RFC 9457 problem details with a machine-readable `code`, and MUST NOT contain personal data.
- **FR-012**: Logs MUST NOT contain defendant names, dates of birth, URNs or account numbers.

### Key Entities

- **Candidate hearing**: a hearing day from listing: id, date, courtroom, hearing type, day count, cases → defendants → offences.
- **Hearing row**: an eligible candidate with its courtroom name, displayed defendant and failed-to-share flag.
- **Batch store**: `bulk_resulting`, `bulk_resulting_hearing`, `bulk_inflight` (design §13.1). Read only by this feature; written by submit (a later feature).

## Success Criteria *(mandatory)*

- **SC-001**: For a court and day, the search returns exactly the eligible hearings, in the agreed order and grouping, with the account number for each case on the page.
- **SC-002**: Every refusal (400, 401, 403, 422) and dependency failure (502, 503) is covered by an integration test through the real HTTP stack.
- **SC-003**: `./gradlew build` passes with the coverage gate (0.88 line, 0.85 branch), Checkstyle and PMD.

## Assumptions

- Authorisation: until the "Bulk resulting" role and the "Bulk resulting for Enforcement" permission exist in usersgroups, an interim rule admits any identified caller; the court check is the boundary.
- Listing's and hearing's own access rules currently admit existing groups (for example Legal Advisers), not a bulk-only role. STE testing needs such a user with a placement at the court searched.
- Listing has no notion of a principal defendant; the row's displayed defendant is the first defendant of the first case (to be confirmed with the product owner).
- Hearing's `get.hearings` default time window is 00:00 to 23:59 UTC on the date.
