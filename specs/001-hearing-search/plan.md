# Implementation Plan: Hearing search for bulk resulting

**Branch**: `001-hearing-search` | **Date**: 2026-11-27 | **Spec**: [spec.md](spec.md)

## Summary

`GET /hearings` and `GET /config` on the new service, implementing the generated interfaces of
`api-cp-crime-results-bulkresulting` 0.1.0. The search orchestrates five Common Platform reads as the
user and one read of its own batch store, and shapes a grouped, paged response.

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 4.1 (web, validation, actuator, JDBC, Flyway), RestClient on the JDK HTTP client, Caffeine, cp-auth-rules-filter 1.0.7
**Storage**: PostgreSQL 15 (Flyway V1: the batch store)
**Testing**: JUnit 5, AssertJ, Mockito, WireMock, Testcontainers PostgreSQL
**Target Platform**: AKS via the shared `springboot-app` chart; context path `/bulkresulting`, port 4550
**Performance Goals**: one listing call per business type, one hearing call per (date, courtroom), at most one progression call per case on the page; parallel calls bounded at 10
**Constraints**: acts as the user; no personal data in logs; listing's 4,000 cap

## Constitution Check

- Contract first: the controllers implement the generated `HearingsApi`, `ConfigApi` and `RootApi`. PASS
- TDD: every component has unit or WireMock tests; the integration test covers every status. PASS
- Ports and adapters: one client class per CP API over a shared `CpHttpClient`; the batch store behind `BatchStateRepository`. PASS
- Explicit failure: every CP failure becomes `CpDependencyException` and a 403/502/503 problem. PASS
- Privacy: logs carry counts, statuses and dependency names only. PASS
- Acting as the user and the court boundary: `CallerContext` on every call; the court check runs first. PASS

## Project Structure

```text
src/main/java/uk/gov/hmcts/cp/bulkresulting/
├── batch/      BatchStateRepository (in flight, failed to share, in-flight count)
├── config/     BulkResultingProperties, ServiceConfiguration
├── cp/         CpHttpClient and the usersgroups, reference data, listing, hearing and progression clients
├── filters/    ActionHeaderFilter (CPP-ACTION from the path)
├── search/     HearingSearchService, SearchFilterFactory, DisplayedDefendant, HearingRowOrder, records
└── web/        HearingsController, ConfigController, RootController, SearchResponseAssembler, ProblemDetailsHandler
src/main/resources/
├── acl/bulkresulting-rules.drl
└── db/migration/V1__batch_store.sql
docker/wiremock/   stubs shared by docker compose and the integration tests
```

## Complexity Tracking

None.
