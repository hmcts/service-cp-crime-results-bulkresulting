<!--
SYNC IMPACT REPORT
==================
Version change: (none) → 1.0.0
Bump rationale: initial ratification (2026-11-27).

Principles defined (1.0.0):
  I    Contract First
  II   Test-Driven Development
  III  SOLID with Ports and Adapters
  IV   Explicit Failure — Nothing Is Ever Swallowed
  V    Acting as the User
  VI   Court Boundary Enforced Server-Side
  VII  Privacy in Telemetry
  VIII Estate Conventions
  IX   Public Repository
  X    Scope — Search Now, Later Slices as Separate Features

Templates requiring updates (1.0.0):
  - .specify/templates/*.md                   ✅ compatible - no change.
  - CLAUDE.md, .claude/rules/*.md, .claude/agents/*.md
                                              ✅ aligned at ratification.
-->

# service-cp-crime-results-bulkresulting Constitution

This service provides **bulk resulting for enforcement**. It lets a court user
find the allocated magistrates' enforcement hearings (business types `ENF` and
`ENF_AUTO`) at their court and, in later features, result a batch of them in
one action. It owns no hearing, case or defendant data: everything it shows
comes from the Common Platform (CP) context APIs, which it calls **as the
user**. It keeps only its own batch state.

Feature 001-hearing-search is the first increment: `GET /hearings` and
`GET /config`. Submit (`POST /batches`), the per-hearing pipeline (save draft,
attendance, share through the existing hearing commands as the user),
reconcile and AEC slot booking follow as separate features. The authoritative
design is the bulk resulting solution design (Option A), held outside this
repository.

## Core Principles

### I. Contract First (NON-NEGOTIABLE)

The REST contract is owned by `hmcts/api-cp-crime-results-bulkresulting`. Its
OpenAPI document generates the Spring interfaces and models
(`uk.gov.hmcts.cp.bulkresulting.openapi.api` / `.model`) that this service
consumes through the `apiSpec` Gradle configuration.

- Controllers **implement the generated interfaces**. A hand-written
  controller signature, request or response model that duplicates the
  contract is forbidden; generated code is never edited.
- A contract change is made in the api repo first, reviewed and released
  there, and then picked up here by a version bump.
- Release builds reject draft `X.Y.Z-<sha>` api versions
  (`./gradlew validateApiSpecVersions`).
- The CP context APIs this service calls (usersgroups, listing, hearing,
  progression) are **consumed, never redefined**. Their responses are read
  into records that model only the fields this service reads; a change to
  them is a cross-team event, not a spec.
- Generated models stay at the boundary: the controller maps them to and from
  application records.

**Rationale**: the user interface and this service are built against the same
published contract. A model duplicated here drifts silently; a generated
interface fails the build when the contract moves.

### II. Test-Driven Development (NON-NEGOTIABLE)

Red → Green → Refactor for every behaviour change, without exception.

1. Write the failing test first. It MUST run and fail for the *correct* reason
   — the assertion, not a missing class or a compile error.
2. Write the minimum production code to make it pass.
3. Refactor with the test still green.

Because "it really did fail first" cannot be proved from a commit graph, the
evidence is a convention that a reviewer can audit:

- Test tasks precede implementation tasks in every task list, and each test
  task is closed before the implementation task it guards is opened. A test
  task lands its compile-safe seams so that the recorded red run is a failing
  assertion, never a compile error.
- Each task's commit narrative records the observed red run before the green
  run — the failing assertion, quoted.
- Reviewers reject implementation commits whose tests could not have failed
  first: assertions that are tautologically true, tests that assert only that
  no exception was thrown, coverage added in the same breath as the code with
  no red run recorded.
- Test naming: `{ClassName}Test` for unit tests, `{ClassName}IT` for
  integration tests; methods `{action}_{scenario}_should_{expectation}`.
- **Coverage ratchet**: the JaCoCo gate is LINE ≥ 0.88 / BRANCH ≥ 0.85, with
  `Application` and `config/**` excluded. Thresholds only go up; they are
  never lowered to make a build pass.

The `qa` reviewer agent gates on that convention. Production code arriving
without an accompanying failing-then-passing test is a FAIL, not a style
comment.

Exempt: pure mechanical refactors (rename, move, extract with no behaviour
change), formatting, and comment-only edits.

**Rationale**: a test written after the code encodes what the code does; a
test written from the spec encodes what it should do. Only the second
protects the behaviour.

### III. SOLID with Ports and Adapters (NON-NEGOTIABLE)

- The application core orchestrates against **port interfaces** it owns. It
  MUST NOT import HTTP-client, JDBC, Spring Web or generated contract types,
  and it is unit-testable with plain mocks and no Spring context.
- Each CP context API is an adapter behind a port; the batch tables are a
  persistence adapter behind a port. If swapping an adapter forces a change to
  the core, the port is wrong — fix the port.
- Controllers and filters hold no business logic and call no repository or
  HTTP client directly.
- Constructor injection only, `private final` fields; Java records for values;
  enums for bounded value sets; switch expressions for exhaustive routing.
- Single responsibility per class; methods do one thing (target ≤ 20 lines,
  hard limit 40).

**Rationale**: later features add a pipeline that writes through the same CP
APIs. Clean ports let that pipeline reuse the adapters and let every rule be
tested without the network.

### IV. Explicit Failure — Nothing Is Ever Swallowed (NON-NEGOTIABLE)

- No empty catch, no catch-log-and-continue, no `return null` or empty
  collection from a catch block, no success value returned on failure. Catch
  only to classify and rethrow.
- A **CP dependency failure becomes a problem detail**: an error status or an
  unreadable body from a CP context API → **502**; a connect failure or a
  timeout → **503**. Never a 500, never an empty result, never a partial page
  presented as complete.
- An empty, successful answer from a dependency (no hearings listed) is
  distinct from a failure and is returned as such.
- One `@ControllerAdvice` maps exceptions to RFC 7807 problem details with
  bounded codes. Exception text, stack traces and downstream bodies never
  reach a response.
- Every `RestClient` has connect and read timeouts. Retries only where a spec
  names them, bounded, and never on a non-idempotent call.

**Rationale**: a court user who sees an empty list because a dependency
failed will conclude there is nothing to result. Failure must be visible to
the caller and to support.

### V. Acting as the User (NON-NEGOTIABLE)

- The `CJSCPPUID` header is set by the IDAM gateway and is **required** on
  every business request. A request without it is refused before any
  downstream call.
- Every call to a CP context API carries the inbound `CJSCPPUID` **unchanged**.
  There is **no system-user fallback** on a user request, and no
  service-held credential substitutes for the user.
- Authorisation is by `cp-auth-rules-filter` (Drools rules in `acl/*.drl`),
  with `ActionHeaderFilter` setting `CPP-ACTION` from the request path. Each
  endpoint's action mapping is pinned by a test.
- Later features that write (save draft, attendance, share) do so through the
  existing hearing commands, as the user, under the same rule.

**Rationale**: CP context services apply their own permissions to the user.
Acting as the user keeps those checks in force and the audit trail truthful.

### VI. Court Boundary Enforced Server-Side (NON-NEGOTIABLE)

- The requested court MUST be one of the user's court centres, read from
  usersgroups as the user, before listing is called. Otherwise the request is
  refused (403).
- The check is made by this service on every request; the client is never
  trusted to have filtered, and a cached answer never outlives its bounded
  expiry.

**Rationale**: the user interface can be bypassed. The boundary holds only if
the service enforces it.

### VII. Privacy in Telemetry (NON-NEGOTIABLE)

- **No defendant names, dates of birth, case URNs or account numbers** in log
  lines at any level, in any profile, in metric labels or in span attributes.
- No request or response body — this service's or a CP API's — is logged.
- Log lines carry `requestId` / `correlationId` and non-identifying fields
  (court centre id, date range, page number, counts, dependency name, HTTP
  status, elapsed time). `CJSCPPUID` appears only in the estate audit trail,
  never in an application log line.
- JSON logging to stdout, unconditionally (`logback.xml`); SLF4J only — no
  `System.out`, `System.err` or `printStackTrace()`.
- Personal data from the CP APIs is never persisted beyond what a spec names.

**Rationale**: logs and traces reach a wider audience than the data's
owners. Identifiers are enough to support the service.

### VIII. Estate Conventions (NON-NEGOTIABLE)

- Derived from `hmcts/service-hmcts-crime-springboot-template`: never
  scaffolded from scratch and never from Spring Initializr. The template's
  `build.gradle` includes, `Dockerfile`, `logback.xml` and workflows are kept
  in its shape.
- **Gradle, never Maven.** Flyway, never Liquibase; migrations additive and
  forward-only.
- No wildcard imports; explicit access modifiers; Checkstyle (`google_checks`,
  `maxWarnings = 0`) and PMD clean on main and test sources.
- Conventional Commits. No tool or assistant attribution in commits, pull
  requests, comments or documentation.
- British English in documentation, comments and log messages.
- A defendant shown on a search row is the **displayed defendant**: the first
  defendant of the first case, in the order listing returns them.

**Rationale**: the estate is supported by teams who move between services.
Shared conventions are what make that possible.

### IX. Public Repository (NON-NEGOTIABLE)

This repository is public. Never commit internal hostnames, keys, tokens,
connection strings, people's names or email addresses, internal Confluence or
Jira links, personal data or production data. Environment-specific values are
environment-variable placeholders (for example `CP_BASE_URL`). Test fixtures
are synthetic. Jira items are cited by key only. The design is referred to by
name and is not copied, summarised or linked here.

**Rationale**: everything committed is published, permanently.

### X. Scope — Search Now, Later Slices as Separate Features

- Feature 001-hearing-search: `GET /hearings` and `GET /config`. The batch
  tables are read only, to hide in-flight hearings and flag failed shares.
- Submit (`POST /batches`), the per-hearing pipeline, reconcile and AEC slot
  booking are **separate features**, each specified under `specs/<NNN>-*`
  before any of its code lands. Nothing is built ahead of its spec.
- Out of scope throughout: changes to the CP context services themselves, and
  any route that acts as a system user on a user's request.

**Rationale**: small, specified increments keep each change reviewable and
keep unfinished behaviour out of production.

## Technology Stack & Deployment

- **Java**: 25. **Framework**: Spring Boot 4.1. **Build**: Gradle.
- **Package**: `uk.gov.hmcts.cp.bulkresulting`. **Context path**:
  `/bulkresulting`. **Port**: 4550.
- **Contract**: `hmcts/api-cp-crime-results-bulkresulting`, consumed via the
  `apiSpec` Gradle configuration; draft api versions rejected on release.
- **Persistence**: PostgreSQL with Flyway. This service's own tables:
  `bulk_resulting`, `bulk_resulting_hearing`, `bulk_inflight`.
- **Outbound HTTP**: Spring `RestClient` to the CP context APIs at
  `CP_BASE_URL`, as the user, with connect and read timeouts on every client.
- **Caching**: Caffeine, only where a spec names it, bounded in size and
  expiry.
- **Auth**: `cp-auth-rules-filter` 1.0.7 (Drools, `acl/*.drl`) plus
  `ActionHeaderFilter`; `CJSCPPUID` required.
- **Configuration**: `maxBatchSize` 100 and `searchWindowDays` 28, typed
  `@ConfigurationProperties`, returned by `GET /config`.
- **Tests**: JUnit 5, Mockito, AssertJ; Testcontainers (PostgreSQL); WireMock
  for the CP context APIs. `docker compose up -d` starts PostgreSQL and
  WireMock locally.
- **Observability**: JSON logs to stdout; Application Insights by the Java
  agent; OpenTelemetry and Micrometer in process. Readiness does not depend on
  a CP context API.
- **CI/CD**: GitHub Actions → ADO pipeline 460 → ACR → the shared
  `springboot-app` Helm chart. Deployment wiring lives in sibling
  infrastructure repositories.
- **Secrets**: Azure Key Vault via the Secrets Store CSI driver with workload
  identity. No static key, no committed connection string, no secret in a Helm
  value or an environment default.
- **Ownership**: results-validation-service-team (CODEOWNERS).

### Increments

- `001-hearing-search` — specified and implemented on branch
  `001-hearing-search` (`specs/001-hearing-search/`). Stories CRA-394 …
  CRA-397 under epic CHD-2035 / CRA-390.

## Development Workflow & Quality Gates

- The REST contract MUST be changed in the api repo **before** any code
  change here that depends on it (Principle I).
- Every feature built via Spec Kit lives under `specs/NNN-slug/` containing at
  least `spec.md`, `plan.md` and `tasks.md`. Flow:
  `/speckit-specify → /speckit-plan → /speckit-tasks → /speckit-implement
  → /speckit-analyze`.
- Non-trivial changes flow through `Spec → Write → Code Review → QA →
  Spec-Validate → Fix → Ship`. The reviewer agents (`code-reviewer`, `qa`,
  `spec-validator`) report findings only; they MUST NOT modify code. The
  primary agent or a human applies fixes and re-runs until all three return
  PASS / COMPLIANT. Exempt: markdown-only edits, whitespace/import-only edits,
  and `.claude/rules/*` or `CLAUDE.md` updates.
- Required to run cleanly before merge:
  - `./gradlew build` — compilation, the full test suite, **PMD** (main and
    test), **Checkstyle** (main and test) and the **JaCoCo coverage
    verification**. Every analysis runs in `check`.
  - `./gradlew test` — the whole suite; the Testcontainers suites need Docker
    only when those tests are in the selection.
  - `./gradlew jacocoTestReport check` — the order CI uses, so the coverage
    report exists to be read whichever way the gate goes.
- Pull requests: the description MUST state which principle(s) the change
  touches. Any deviation requires explicit written justification in the PR
  description and MUST be flagged in the plan's "Complexity Tracking" section.
- Reviewers MUST specifically look for: a hand-written model or controller
  duplicating the contract (Principle I); production code with no preceding
  failing test (Principle II); an infrastructure type in the core (Principle
  III); a swallowed exception or a dependency failure not mapped to 502/503
  (Principle IV); a CP call without the inbound `CJSCPPUID` or a system-user
  fallback (Principle V); listing called before the court check (Principle
  VI); personal data or a body in telemetry (Principle VII); anything internal
  or personal committed (Principle IX); and later-feature code without a spec
  (Principle X).

## Governance

This constitution supersedes the informal conventions in `.claude/rules/` and
the template-derived guidance in `CLAUDE.md`. Where this document and those
files disagree, this document wins; they are retained as quick-reference
material and MUST be kept in sync.

The bulk resulting solution design (Option A), held outside this repository,
is the source of truth for names and behaviour. Specs restate the rules they
need; where a spec and the design differ, the design wins and the difference
is raised with the design owner. This constitution states the principles the
design implies; it does not take decisions the design leaves open. A later
design revision that changes a principle here is adopted by amendment.

**Amendment procedure**:

1. Propose the change in a feature spec under `specs/`.
2. Bump `Version` per semantic versioning:
   - **MAJOR** — a breaking principle change, removal, or redefinition that
     invalidates existing practice.
   - **MINOR** — a new principle, new section, or materially expanded
     guidance.
   - **PATCH** — clarifications, wording, typo fixes, or non-semantic
     refinements.
3. Re-run `/speckit-analyze` on every in-flight feature spec to verify it still
   aligns with the amended principles; update or waive as required.

**Compliance expectations**:

- All PRs MUST honour these principles.
- Deviations MUST be explicitly justified in the PR description and, where
  relevant, in the plan's "Complexity Tracking" table.
- Reviewers MUST block merges that silently violate a NON-NEGOTIABLE principle
  without a written waiver.

**Version**: 1.0.0 | **Ratified**: 2026-11-27 | **Last Amended**: 2026-11-27
