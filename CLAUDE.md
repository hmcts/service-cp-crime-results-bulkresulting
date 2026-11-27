# service-cp-crime-results-bulkresulting

Bulk resulting for enforcement. Lets a court user find the allocated magistrates' enforcement
hearings (business types `ENF` / `ENF_AUTO`) at their court and, in later features, result a batch
of them in one action. Every call it makes to the Common Platform (CP) context APIs is made **as
the user**: the `CJSCPPUID` header set by the IDAM gateway is passed through unchanged.

Feature 001-hearing-search is built: `GET /hearings` checks the requested court against the user's
court centres from usersgroups, searches listing over the requested date range, checks each
hearing's share state through hearing `get.hearings` per (date, courtroom), hides hearings in
flight in a bulk batch and flags "failed to share" from the service's own batch tables
(`bulk_resulting`, `bulk_resulting_hearing`, `bulk_inflight`), adds the account number from
progression for the page shown, and returns rows grouped by hearing date and courtroom, 100 per
page. `GET /config` returns `maxBatchSize` (100) and `searchWindowDays` (28).

Not built yet, each a separate feature: submit (`POST /batches`), the per-hearing pipeline (save
draft, attendance, share through the existing hearing commands as the user), reconcile and AEC
slot booking.

## Programme
Crime Common Platform (CPP) — Modern by Default (MbD)
Organisation: HMCTS / Ministry of Justice — Team: results-validation-service-team (CODEOWNERS)
Jira: epic CHD-2035 / CRA-390; stories CRA-394 … CRA-397

## Stack
- Spring Boot 4.1, Java 25, **Gradle — never Maven**
- Package: `uk.gov.hmcts.cp.bulkresulting`
- Context path `/bulkresulting`; port 4550
- Postgres + Flyway; Caffeine; Spring `RestClient` for the CP context APIs
- Tests: JUnit 5, Testcontainers (Postgres), WireMock (CP context APIs)
- Auth: `cp-auth-rules-filter` 1.0.7 (Drools rules under `acl/*.drl`) plus an `ActionHeaderFilter`
  that sets `CPP-ACTION` from the request path; `CJSCPPUID` is required on every business request
- Provenance: derived from the `hmcts/service-hmcts-crime-springboot-template` crime Spring Boot
  template. **Never scaffold from scratch and never use Spring Initializr** — the estate
  conventions are inherited from the template.

## Key Documentation
| Document | Location |
|---|---|
| **Design (authoritative)** | The bulk resulting solution design (Option A), held outside this repo. This repo carries **no** design narrative: do not copy, summarise or link it here |
| Constitution | `.specify/memory/constitution.md` |
| Feature 001 | `specs/001-hearing-search/` — spec, plan, research, data model, contracts, tasks and quickstart |
| REST contract | `hmcts/api-cp-crime-results-bulkresulting` — the OpenAPI document and the generated Spring interfaces (`uk.gov.hmcts.cp.bulkresulting.openapi.api` / `.model`) |

## Contract Rule
The REST contract is owned by `hmcts/api-cp-crime-results-bulkresulting`. This service consumes
the published artefact through the `apiSpec` Gradle configuration and **implements the generated
interfaces**; it never hand-writes a controller signature or a model that the contract defines. A
contract change is made in the api repo first, released, and then picked up here. Release builds
reject draft `X.Y.Z-<sha>` api versions (`./gradlew validateApiSpecVersions`).

The CP context APIs it calls (usersgroups, listing, hearing, progression) are **consumed, never
redefined**: their request and response shapes are modelled only as far as this service reads
them, and a change to them is a cross-team event. The spec-validator agent checks contract
compliance after implementation.

## Scope Rule
Search only, until the next feature is specified. Submit (`POST /batches`), the per-hearing
pipeline, reconcile and AEC slot booking are separate features with their own `specs/<NNN>-*`
folder; none of their behaviour is built ahead of its spec. The batch tables exist now only so
search can hide in-flight hearings and flag failed shares. Out of scope throughout: any change to
the CP context services themselves, and any route that acts as a system user on a user's request.

## Public Repository
This repository is **public**. Never commit internal hostnames, keys, connection strings, tokens,
people's names or email addresses, internal Confluence or Jira links, personal data or production
hearing data. Environment-specific values are environment-variable placeholders (for example
`CP_BASE_URL`). Test fixtures are synthetic.

## Deployment
- **CI/CD**: GitHub Actions → **ADO Pipeline 460** → ACR → deployed with the shared
  **`springboot-app`** Helm chart.
- **Secrets** come from **Azure Key Vault via the CSI driver, with workload identity** — the
  datasource credentials and the App Insights connection string. No static key, no committed
  connection string, no secret in a Helm value or an environment default.
- The CP context APIs are reached at `CP_BASE_URL`, always with the caller's `CJSCPPUID`.
- The deployment wiring (Helm values, identity, network policy) lives in the sibling
  infrastructure repos, not here.

## Build & Test
```bash
./gradlew build              # Compile + the full test suite + PMD + Checkstyle (main and test
                             # sources for both) + the JaCoCo coverage gate
./gradlew test               # The whole suite: unit and *IT alike; the Testcontainers suites
                             # need Docker only when those tests are in the selection
./gradlew checkstyleMain checkstyleTest   # Checkstyle alone (google_checks, maxWarnings 0)
./gradlew pmdMain pmdTest    # PMD alone; src/test uses .github/pmd-test-ruleset.xml
./gradlew jacocoTestReport check          # The order CI uses: the report is written before the
                             # coverage gate reads it (0.88 line / 0.85 branch)
./gradlew bootRun            # Run locally
```

The toolchain is JDK 25. `docker compose up -d` starts the local stack (Postgres and WireMock
standing in for the CP context APIs).

## Repository Conventions
- Conventional Commits; no tool or assistant attribution in commits, PRs, comments or docs.
- TDD red-run convention per `specs/*/tasks.md`: a test task lands its compile-safe seams so the
  recorded red run is a failing assertion, never a compile error; the paired implementation task
  quotes the green run.
- No wildcard imports. British English in docs, comments and log messages.
- A defendant shown on a search row is the **displayed defendant**: the first defendant of the
  first case, in the order listing returns them.
- Never run two committing agents concurrently in this repo.

## Setup

<!-- SPECKIT START -->
Feature `001-hearing-search` is specified and implemented on branch `001-hearing-search`
(`specs/001-hearing-search/plan.md`, with `spec.md`, `research.md`, `data-model.md`,
`contracts/`, `quickstart.md` and `tasks.md` alongside it). The Spec Kit scripts resolve the
active feature from `SPECIFY_FEATURE_DIRECTORY` if set, then from `.specify/feature.json`
(currently `specs/001-hearing-search`, rewritten by `/speckit-specify` for each new feature), and
only then from the branch name's numeric prefix.
The next feature (submit, `POST /batches`) starts with `/speckit-specify` on its own branch. Specs
are written against the bulk resulting solution design (Option A); the design wins where they
differ.
<!-- SPECKIT END -->

<!-- BEGIN token-budget compact-backups -->

## Token Budget — backup guard

Files ending in `.full.md` inside `specs/` and `.specify/memory/`
(e.g. `spec.full.md`, `plan.full.md`) are pre-compaction backups created
by `/speckit.token-budget.compact`. **Do not read them.** They contain the
full uncompacted content; loading them cancels the token savings compaction
achieved. To revert an artifact to its original state, run
`/speckit.token-budget.restore` instead.

<!-- END token-budget compact-backups -->
