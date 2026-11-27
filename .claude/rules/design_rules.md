# Architecture & Domain Rules

This service is a **contract-first REST application**. Its surface is the API defined in
`hmcts/api-cp-crime-results-bulkresulting` (context path `/bulkresulting`) plus Spring Boot
Actuator. Feature 001-hearing-search provides `GET /hearings` and `GET /config`; submit
(`POST /batches`), the per-hearing pipeline, reconcile and AEC slot booking are later features,
each with its own spec. The authoritative design is the bulk resulting solution design (Option A),
held outside this repo; specs restate the rules they need.

## Request Architecture (ports and adapters)

```
HTTP request (CJSCPPUID set by the IDAM gateway)
   │
   ▼
cp-auth-rules-filter + ActionHeaderFilter   CPP-ACTION from the path; Drools rules in acl/*.drl
   ▼
Controller                                  implements the generated interface; maps generated
   │                                        models <-> application records ONLY
   ▼
HearingSearchService                        application core — the use case, no I/O of its own
   ├─▶ user court centres     «port»       usersgroups, as the user
   ├─▶ listed hearings        «port»       listing range search, as the user
   ├─▶ share state            «port»       hearing get.hearings per (date, courtroom), as the user
   ├─▶ batch state            «port»       own tables: in-flight and failed-to-share hearings
   └─▶ account numbers        «port»       progression, for the page shown only, as the user
```

- **Controller:** implements the generated interface, validates nothing the contract already
  validates, maps to and from application records, and returns. NO business logic. NO downstream
  calls.
- **Application core:** orchestrates against **ports only**. It MUST NOT import HTTP client, JDBC,
  Spring Web or generated contract types. It is unit-testable with plain mocks and no Spring
  context.
- **Ports:** Java interfaces owned by the application package. Adapters implement them and live in
  their own packages. If swapping an adapter forces a service edit, the port is wrong — fix the
  port.
- **Adapters:** one per CP context API, each a `RestClient` with timeouts, passing the caller's
  `CJSCPPUID` through; one persistence adapter over the batch tables.

NEVER put business logic in a controller or a filter.
NEVER call a repository or an HTTP client from a controller.
NEVER let a generated contract type cross into the application core.

### Package structure

```
uk.gov.hmcts.cp.bulkresulting
├── api/           controllers implementing the generated interfaces; problem-detail advice
├── application/   use cases and port interfaces
├── domain/        records and enums
├── adapter/
│   ├── usersgroups/  court centres of the user
│   ├── listing/      hearing range search
│   ├── hearing/      get.hearings share state
│   └── progression/  account number
├── persistence/   repositories over bulk_resulting, bulk_resulting_hearing, bulk_inflight;
│                  Flyway in db/migration
├── security/      ActionHeaderFilter and identity helpers
└── config/        typed @ConfigurationProperties, RestClient and cache configuration
```

The plan for each feature fixes the real class names. Each package's `package-info.java` states its
responsibility and what it may depend on; keep them true as code lands.

## Hearing Search Rules (feature 001)

- **Identity first.** A request without `CJSCPPUID` is refused before any downstream call. Every CP
  call carries that value unchanged. There is no system-user fallback.
- **Court boundary, server-side.** The requested court must be one of the user's court centres from
  usersgroups; otherwise the request is refused (403) before listing is called. The client is never
  trusted to have filtered.
- **What is searched.** Allocated magistrates' enforcement hearings — business types `ENF` and
  `ENF_AUTO` — at the requested court, over the requested listing range, bounded by
  `searchWindowDays` (28).
- **Share state.** Checked through hearing `get.hearings` once per (hearing date, courtroom), not
  once per hearing.
- **Own batch state.** Hearings in flight in a bulk batch (`bulk_inflight`) are hidden; hearings
  whose last bulk share failed are flagged "failed to share" (from `bulk_resulting` /
  `bulk_resulting_hearing`).
- **Account number.** Added from progression only for the rows on the page being returned.
- **Shape.** Rows grouped by hearing date and courtroom, 100 per page. The defendant shown on a row
  is the **displayed defendant**: the first defendant of the first case, in the order listing
  returns them.
- **Config.** `GET /config` returns `maxBatchSize` (100) and `searchWindowDays` (28) from static
  configuration.

## Failure Handling

- **NO swallowed exceptions, ever.** No empty catch, no `catch (Exception e) { log.debug(...); }`,
  no returning an empty list or a "success" object from a catch block.
- A CP context API that answers with an error status or an unreadable body → **502** problem
  detail; one that cannot be reached or times out → **503**. A partial page is never returned as if
  complete.
- Problem details carry bounded codes; never exception text, stack traces or a downstream body.
- One `@ControllerAdvice` does the mapping.

## Persistence

- Flyway — never Liquibase. Migrations are additive and forward-only.
- The batch tables are this service's own. In feature 001 they are read only; the submit feature
  and later write them.
- Hearing and defendant data from the CP APIs is **never persisted** beyond what a spec names.

## Privacy and Logging

- Every log line carries `requestId` / `correlationId` (MDC).
- **Never** log defendant names, dates of birth, case URNs or account numbers, at any level, in any
  profile; never put them in a metric label or span attribute; never log a request or response
  body.
- A dependency failure is logged once with the dependency name, HTTP status and elapsed time.

## Out of Scope — do not build here

- Anything of a later feature before its spec exists: submit (`POST /batches`), the per-hearing
  pipeline (save draft, attendance, share through the existing hearing commands as the user),
  reconcile, AEC slot booking.
- Any change to the CP context services themselves, or a redefinition of their APIs.
- Any route that acts as a system user on a user's request.
