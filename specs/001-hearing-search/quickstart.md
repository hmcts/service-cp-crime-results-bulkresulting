# Quickstart: Hearing search for bulk resulting

```bash
./gradlew build
docker compose up -d
curl -H 'CJSCPPUID: 77777777-7777-4777-8777-777777777777' \
  "http://localhost:4550/bulkresulting/hearings?ouCode=B01AA00&startDate=$(date -d '+1 day' +%F)"
curl -H 'CJSCPPUID: 77777777-7777-4777-8777-777777777777' http://localhost:4550/bulkresulting/config
docker compose down -v
```

Expected with the committed stubs: two groups on 1 December 2026 (Courtroom 01 and Courtroom 02),
six rows; the multi-day hearing, the hearing with no courtroom, the shared hearing and the hearing
with an application are absent.

Refusals to try: `startDate` in the past (400), `ouCode=C99XX00` (403), no `CJSCPPUID` header (401).
