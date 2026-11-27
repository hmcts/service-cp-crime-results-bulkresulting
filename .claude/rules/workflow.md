# Workflow: Mandatory Build Loop

Every non-trivial code change MUST follow this cycle:

```
Contract → Failing test → Write → Code Review (agent) → QA (agent) → Contract Validate (agent) → Fix → Ship
```

- **Contract:** this service owns one contract and consumes four (constitution Principle I):
  1. the **REST contract** — the OpenAPI document in `hmcts/api-cp-crime-results-bulkresulting`,
     consumed here as generated Spring interfaces and models
     (`uk.gov.hmcts.cp.bulkresulting.openapi.api` / `.model`) through the `apiSpec` Gradle
     configuration;
  2. consumed, never redefined: the CP context APIs — usersgroups (the user's court centres),
     listing (the hearing search), hearing (`get.hearings`, share state) and progression (account
     number).

  A change to the REST contract is made in the api repo FIRST, released, and then picked up here;
  never hand-write a controller signature or model the contract defines. A change to a consumed CP
  API is a cross-team event, not a spec.
- **Contract Validate:** run the `spec-validator` agent to check the code against the contract, the
  spec and the gates below.

Loop repeats until ALL agents return PASS / COMPLIANT.

## What Requires the Loop

| Must Go Through Loop                            | Exempt                         |
|-------------------------------------------------|--------------------------------|
| New / modified Java class                       | Markdown / docs only           |
| New / modified test class or fixture            | Whitespace / import only       |
| `apiSpec` version change                        | CLAUDE.md and rule updates     |
| Flyway migration                                | README changes                 |
| Auth rules (`acl/*.drl`) or filter changes      |                                |
| Dockerfile changes                              |                                |
| CI/CD pipeline config                           |                                |

## TDD is Non-Negotiable

- Write the failing test first; confirm it fails for the *correct* reason (assertion failure, not a
  compilation error)
- Then write the minimum production code to make it pass
- Then refactor with the test still green
- Commit history MUST show the failing test was authored at or before the production code —
  **every commit**, no exceptions, no "tests to follow"

## Gates

A change ships only when all applicable gates are green:

1. **API-first gate.** Every endpoint implements a generated interface from the api repo; no
   hand-written request or response model duplicates the contract. Release builds pass
   `./gradlew validateApiSpecVersions` (no draft `X.Y.Z-<sha>` api version).
2. **Acting-as-the-user gate.** Every CP context API call carries the inbound `CJSCPPUID`
   unchanged; a request without it is refused before any downstream call; there is no system-user
   fallback. Asserted in WireMock tests.
3. **Court-boundary gate.** A court outside the user's court centres (from usersgroups) is refused
   server-side before listing is called. Proven by test.
4. **Explicit-failure gate.** A CP dependency failure becomes a 502 or 503 problem detail, never a
   500, an empty result or a partial page presented as success. No empty catch, no
   catch-and-continue, no success returned from a catch block. Reviewers reject on sight.
5. **Search-correctness gate.** Only `ENF` / `ENF_AUTO` allocated hearings at the requested court
   and in the requested range are returned; in-flight hearings are hidden; failed shares are
   flagged; rows are grouped by hearing date and courtroom, at most 100 per page; the account
   number is added for the page shown only. Each rule is pinned by a test.
6. **No-PII gate.** No defendant names, dates of birth, case URNs or account numbers in logs,
   metrics or traces at any level; no request or response body logged; no production data
   committed to this public repository.
7. **Coverage gate.** JaCoCo LINE ≥ 0.88 / BRANCH ≥ 0.85; Checkstyle and PMD clean. Thresholds
   only ratchet up.

## Agent Definitions

### code-reviewer (Read only)
- Spawned as sub-agent with Read-only tools
- Analyses code for: logic errors, null safety, ports-and-adapters violations (infrastructure types
  leaking into the application layer), swallowed exceptions, dependency failures not mapped to
  502/503, missing `CJSCPPUID` pass-through, secrets, PII in logs, `System.out` usage
- Returns: **PASS** or **NEEDS CHANGES** with severity-rated findings
- NEVER modifies code — reports only

### qa (Read only)
- Spawned as sub-agent with read-only tools; `Bash` for inspection and for running the existing
  suite only
- Judges the tests that exist against the test matrix in `.claude/agents/qa.md` (JUnit Jupiter +
  Mockito + AssertJ; Testcontainers Postgres; WireMock for the CP context APIs) and names every gap
- Verifies TDD discipline (test tasks ordered before implementation tasks; a red run recorded before
  the green run)
- Runs `./gradlew test`
- Returns: **PASS** or **FAIL** with test results and missing-coverage findings
- NEVER writes tests and NEVER fixes production code — reports only

### software-engineer (Full access)
- For full feature implementation tasks
- **Writes every test**, test-first, under the TDD rule above — the `qa` agent reviews them, it does
  not author them
- Follows all rules in `technical-rules.md` and `design_rules.md`
- Runs `./gradlew build` after changes

### spec-validator (Read only)
- Spawned as sub-agent with Read-only tools
- Checks:
  - every endpoint implements its generated interface; no contract drift against the api repo
  - consumed CP API calls match what the spec says is read, with `CJSCPPUID` passed through
  - every functional requirement and acceptance scenario in the spec has code and a test
  - the court boundary, the explicit-failure mapping and the privacy rules above
  - nothing from a later feature (submit, pipeline, reconcile, AEC booking) built ahead of its spec
- Returns: **COMPLIANT** or **DRIFT DETECTED** with severity-rated findings
- NEVER modifies code — reports only

### research (Read, Glob, Grep, WebSearch)
- For deep codebase investigation, including the CP context services this service calls
- Cross-references the bulk resulting solution design (Option A)
- Returns structured findings with citations

## Critical Principle

**Agents are reporters, not fixers.** The parent agent (or developer) reads agent reports and applies
all fixes. This prevents conflicting changes and keeps the team in control.
