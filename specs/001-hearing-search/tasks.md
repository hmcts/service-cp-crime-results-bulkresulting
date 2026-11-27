# Tasks: Hearing search for bulk resulting

All tasks are complete on `001-hearing-search`.

## Phase 1: Setup
- [x] T001 Scaffold from the crime Spring Boot template with the validator build layout and probation's workflows and tooling (on `main`)
- [x] T002 Consume `api-cp-crime-results-bulkresulting` 0.1.0 through the `apiSpec` configuration

## Phase 2: Foundational
- [x] T003 [P] `CpHttpClient`: GET JSON as the user, timeouts, retries, error kinds (`CpHttpClientTest`)
- [x] T004 [P] Flyway V1 batch store (`HearingSearchIT` runs it against PostgreSQL 15)
- [x] T005 [P] `ActionHeaderFilter` and the interim authorisation rule (`ActionHeaderFilterTest`, `ServiceStartupIT`)

## Phase 3: User Story 1 - find eligible hearings (P1)
- [x] T006 [P] [US1] Clients for usersgroups, reference data, listing, hearing and progression (`ClientsTest`)
- [x] T007 [P] [US1] `SearchFilterFactory` with the 28-day window (`SearchFilterFactoryTest`)
- [x] T008 [P] [US1] `DisplayedDefendant` and `HearingRowOrder` (`DisplayedDefendantTest`, `HearingRowOrderTest`)
- [x] T009 [US1] `HearingSearchService` orchestration and paging (`HearingSearchServiceTest`)
- [x] T010 [US1] `HearingsController` and `SearchResponseAssembler` (`HearingSearchIT`)

## Phase 4: User Story 2 - batch store overlay (P1)
- [x] T011 [US2] `BatchStateRepository` in flight, failed to share, in-flight count (`HearingSearchIT`)

## Phase 5: User Story 3 - boundaries (P1)
- [x] T012 [US3] Court check, window, 401, 422 and the problem details mapping (`HearingSearchIT`, `ProblemDetailsHandlerTest`)

## Phase 6: User Story 4 - config (P2)
- [x] T013 [US4] `ConfigController` (`ServiceStartupIT`, `HearingSearchIT`)

## Phase N: Polish
- [x] T014 docker compose stack with shared WireMock stubs; container smoke script
- [x] T015 Manual STE deployment values and runbook (`doc/runbooks/manual-ste-deploy.md`)
