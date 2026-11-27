# Skill: Generate Project Documents

> **Mostly not used in this repository.** The design narrative for
> `service-cp-crime-results-bulkresulting` is the bulk resulting solution design (Option A), held
> outside this repo, which is the single source of truth. Do **not** generate
> `doc/SOLUTION_BRIEF.md`, `doc/TECHNICAL_DESIGN.md` or `doc/MIGRATION_PLAN.md` here, and do not copy
> or summarise the design into the repo. The REST contract lives in
> `hmcts/api-cp-crime-results-bulkresulting`; this skill does not restate it.

Generate or update the repository's own documents.

## Documents

| #  | Document           | File                        | Purpose                          |
|----|--------------------|-----------------------------|----------------------------------|
| 1  | Runbook            | doc/RUNBOOK.md              | Operations, monitoring, alerts: health, CP dependency failures (502/503), batch-table housekeeping once built |
| 2  | Changelog          | doc/CHANGELOG.md            | Version history                  |

## Instructions

When invoked, generate or update the requested documents by:
1. Reading the current codebase to understand what is implemented
2. Reading the existing documents and the design for context
3. Generating accurate documentation that reflects the actual implementation
4. Flagging any drift between docs and code

Never include internal hostnames, keys, personal data or hearing payload content — the repository is
public. Docs never quote hearing, case or defendant data, even synthetic fixtures.

## Output

Return a compact status table:

```
| Document         | Status  | Notes                    |
|------------------|---------|--------------------------|
| RUNBOOK          | Updated | 502/503 handling added   |
| CHANGELOG        | Updated | 3 entries added          |
```
