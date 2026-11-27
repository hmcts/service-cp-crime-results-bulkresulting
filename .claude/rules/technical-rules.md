# Coding Conventions — MOJ / CPP Standard

## Dependency Injection

- Constructor injection ONLY — NEVER use `@Autowired` on fields
- All injected fields MUST be `private final`
- Use Lombok `@RequiredArgsConstructor` OR an explicit constructor
- The application layer injects **port interfaces**, never adapter classes

```java
// CORRECT
private final UserCourtCentreSource userCourtCentreSource;
private final ListedHearingSource listedHearingSource;

public HearingSearchService(UserCourtCentreSource userCourtCentreSource,
                            ListedHearingSource listedHearingSource) {
    this.userCourtCentreSource = userCourtCentreSource;
    this.listedHearingSource = listedHearingSource;
}

// WRONG — never do this
@Autowired
private ListingClient listingClient;
```

## Contract and DTOs

- The REST contract is generated from `hmcts/api-cp-crime-results-bulkresulting`
  (`uk.gov.hmcts.cp.bulkresulting.openapi.api` / `.model`), consumed through the `apiSpec` Gradle
  configuration. Controllers **implement the generated interfaces**; never hand-write a request or
  response model the contract defines, and never edit generated code
- Release builds reject a draft `X.Y.Z-<sha>` api version (`./gradlew validateApiSpecVersions`)
- The application layer works on its own records, not on generated models; the controller maps at
  the boundary
- The CP context API responses (usersgroups, listing, hearing, progression) are read into records
  that model **only the fields this service reads**; they are consumed, never redefined
- Java records for ALL value types — immutable by design
- Use sealed interfaces for polymorphic results where a switch must be exhaustive

## Error Handling

- Custom exceptions extending `RuntimeException`, typed by cause (for example
  `CourtNotPermittedException`, `DependencyUnavailableException`)
- **NEVER swallow exceptions.** No empty catch blocks, no catch-and-log-and-continue, no returning an
  empty list or a success value from a catch block. Catch only to classify and rethrow
- One `@ControllerAdvice` maps exceptions to RFC 7807 problem details; controllers never build error
  responses themselves
- A CP context API failure is explicit: an error response or an unreadable body becomes **502**; a
  connect failure or timeout becomes **503**. Never a 500, never a partial or empty result presented
  as success
- A court the user has no access to is refused server-side (403) before any listing call
- A missing or blank `CJSCPPUID` is refused before any downstream call
- Problem details carry bounded codes, never exception text, stack traces or downstream bodies

## Outbound HTTP

- Spring `RestClient` with connect and read timeouts on every client; no client without them
- Every call to a CP context API is made **as the user**: the inbound `CJSCPPUID` is passed through
  unchanged. There is no system-user fallback on a user request
- Base URL from `CP_BASE_URL`; exact CPP vendor media types on every request, asserted in tests
- Retries only where the spec names them, bounded, and never on a non-idempotent call
- Caffeine caches only what the spec names, with a bounded size and expiry; a cache never hides a
  dependency failure

## Persistence

- Flyway migrations at `src/main/resources/db/migration/V<n>__<snake_case_description>.sql`
- Migrations are additive and forward-only; never edit an applied migration
- The batch tables (`bulk_resulting`, `bulk_resulting_hearing`, `bulk_inflight`) are this service's
  own; search reads them to hide in-flight hearings and flag failed shares. Writes to them belong to
  the submit feature and later
- No database triggers: rules live in the Java tier
- Every CHECK the spec names is in the migration, and an integration test asserts the SQL value sets
  against the Java enums

## Enums and Routing

- Java enums for fixed value sets (for example business types `ENF` / `ENF_AUTO`, share state)
- Switch expressions for routing — the compiler enforces exhaustive coverage
- Include a `fromValue(String)` factory when parsing wire strings; unknown values are an explicit
  failure, never a silent default

## Logging

- SLF4J with Logback (via the Spring Boot starter)
- Lombok `@Slf4j` or `private static final Logger log = LoggerFactory.getLogger(...)` — the only
  allowed forms
- MDC on every request: `requestId`, `correlationId` (cleared in a `finally`)
- `LoggingEventCompositeJsonEncoder` emits structured JSON unconditionally (`logback.xml`)
- A dependency failure is logged once, with the dependency name, HTTP status and elapsed time
- NEVER use `System.out.println`, `System.err.println`, or `Throwable#printStackTrace()`
- NEVER log secrets, tokens or connection strings
- **NEVER log defendant names, dates of birth, case URNs or account numbers at any level**, and never
  a request or response body. Identifiers only

## Imports

- NEVER use wildcard imports (`import java.util.*`) — always explicit imports

## Naming Conventions

| Component        | Pattern            | Example                          |
|------------------|--------------------|----------------------------------|
| Controller       | `*Controller`      | `HearingSearchController`        |
| Application svc  | `*Service`         | `HearingSearchService`           |
| Port (interface) | capability noun    | `UserCourtCentreSource`, `ListedHearingSource` |
| Adapter          | `*Adapter` / `*Client` | `ListingClient`, `ProgressionClient` |
| Repository       | `*Repository`      | `BulkInflightRepository`         |
| Exception        | `*Exception`       | `DependencyUnavailableException` |
| Config           | `*Configuration` / `*Properties` | `CpClientConfiguration` |
| Test             | `*Test` / `*IT`    | `HearingSearchServiceTest`, `HearingSearchIT` |

Names in these examples are illustrative; the spec and plan fix the real ones.

## Testing Conventions

- JUnit 5 + Mockito + AssertJ for unit tests; `@ExtendWith(MockitoExtension.class)`
- `@Nested` classes with `@DisplayName` for grouped scenarios
- Method naming: `{action}_{scenario}_should_{expectation}`
- The application layer is tested with plain mocks — **no Spring context**
- Testcontainers (Postgres) for integration tests (suffix `*IT`)
- WireMock for the CP context APIs (use `dynamicPort()`), asserting exact CPP vendor media types and
  the passed-through `CJSCPPUID` header
- **No production data in this repository** — it is public. Fixtures are synthetic
- TDD: write the failing test first, see it fail for the right reason, then implement
- Logging in tests: SLF4J only
- Test commands: `./gradlew test` runs the whole suite — unit and `*IT` classes alike; the
  Testcontainers suites need Docker only when those tests are in the selection. `./gradlew build` =
  compile + `test` + **every analysis**, because all of them are in `check`: Checkstyle
  (`google_checks`, `maxWarnings = 0`) over `checkstyleMain` and `checkstyleTest`; PMD over
  `pmdMain` against `.github/pmd-ruleset.xml` and `pmdTest` against `.github/pmd-test-ruleset.xml`;
  and the JaCoCo coverage gate (`jacocoTestCoverageVerification`, LINE ≥ 0.88 / BRANCH ≥ 0.85, with
  `Application` and `config/**` excluded). Naming a task is a way to run one of them **sooner**,
  never a way to run one at all
- Coverage ratchet: the thresholds only go up; never lower them to make a build pass
