# Data Model: Hearing search for bulk resulting

The batch store is created by `V1__batch_store.sql` (solution design §13.1). This feature only reads it.

| Table | Key columns read by the search |
|---|---|
| `bulk_resulting` | `bulk_id`, `created_by` (the in-flight count is for the caller's own batches) |
| `bulk_resulting_hearing` | `hearing_id`, `status`, `updated_at` |
| `bulk_inflight` | not read; 10 token rows seeded for the submit pipeline |

**Statuses** (`bulk_resulting_hearing.status`):

| Group | Statuses | Effect on the search |
|---|---|---|
| In flight | PENDING, IN_PROGRESS, UNCONFIRMED | Hidden; counted in the submitter's `inFlightCount` |
| Did not share | DRAFT_IN_PROGRESS, CONFLICT, SLOTS_UNAVAILABLE, TECHNICAL, NOT_SHARED | Listed with `failedToShare` when it is the latest row that is not NOT_TAKEN |
| Other | SUCCESS, SHARED_ELSEWHERE, NOT_ELIGIBLE, NOT_TAKEN | No effect (shared hearings are already dropped by hearing's share state) |

**Indexes** used: the partial unique index on `hearing_id` for in-flight statuses, and
`(hearing_id, updated_at)` where status is not NOT_TAKEN, for the latest outcome.

**In-memory records**: `CandidateHearing` → `ListedCase` → `ListedDefendant` → `ListedOffence`
(from listing), `HearingShareState` (from hearing), `Court` (from reference data), `HearingRow`
and `SearchPage` (assembled).
