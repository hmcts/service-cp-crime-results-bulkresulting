# service-cp-crime-results-bulkresulting

Bulk resulting for enforcement on the Crime Common Platform. The service lets a court user find the
enforcement hearings at their court that can be resulted together, and (in later features) result
them in one batch through the existing hearing commands, acting as that user.

The REST contract lives in [api-cp-crime-results-bulkresulting](https://github.com/hmcts/api-cp-crime-results-bulkresulting).
The service implements the interfaces generated from it (`uk.gov.hmcts.cp.bulkresulting.openapi.api`).

## Endpoints

The context path is `/bulkresulting`. Through the gateway the UI calls `/api/bulkresulting/...`.

| Method | Path | Purpose |
|---|---|---|
| GET | `/hearings` | Allocated magistrates' enforcement hearings (ENF, ENF_AUTO) at a court that are eligible for bulk resulting: grouped by hearing date and courtroom, 100 rows per page, with the caller's in-flight count and the "failed to share" flag |
| GET | `/config` | `maxBatchSize` and `searchWindowDays` |
| GET | `/actuator/health` | Liveness and readiness (no user needed) |

Every other call needs the `CJSCPPUID` header, which the gateway sets from the user's session; without
it the service answers 401. Errors are RFC 9457 problem details with a `code`.

### How the search works

The service calls each Common Platform API **as the user** (`CJSCPPUID` passed through):

1. **usersgroups**: the user's court centres (role placements). The court searched must be one of them (403 otherwise).
2. **reference data**: the court centre id and courtroom names for the OU code.
3. **listing** range search, once per business type: the candidate hearing days. More than the configured maximum (4,000) is refused with 422. Multi-day hearings are dropped. Candidates are cached for 5 minutes per user and filter.
4. **hearing** `get.hearings`, once per date and courtroom: hearings shared for the day, or with a court application, are dropped.
5. **batch store** (Postgres): hearings in flight in a bulk batch are hidden; the "failed to share" flag comes from the latest bulk attempt.
6. **progression**: the account number of each case on the page shown.

The date window is today to today plus 28 days; past dates cannot be searched.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `CP_BASE_URL` | `http://localhost:8080` | Host of the internal gateway to the Common Platform context APIs |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | local compose values | The batch store |
| `SERVER_PORT` | `4550` | HTTP port |
| `SERVER_SERVLET_CONTEXT_PATH` | `/bulkresulting` | Context path |
| `BULK_MAX_BATCH_SIZE` | `100` | Published by `GET /config` |
| `BULK_SEARCH_WINDOW_DAYS` | `28` | Days after today a search may reach |
| `BULK_LISTING_MAX_RESULTS` | `4000` | Listing result count that is refused as too broad |
| `CP_CONNECT_TIMEOUT`, `CP_READ_TIMEOUT`, `CP_MAX_ATTEMPTS`, `CP_PARALLELISM` | `2s`, `30s`, `3`, `10` | Common Platform client settings |

Authorisation uses `cp-auth-rules-filter` with the rules in `src/main/resources/acl/`. The active rule
is interim and admits any identified caller; it is replaced by the "Bulk resulting for Enforcement"
permission rule once that permission exists in usersgroups.

## Build and run

Requires JDK 25 and Docker (for the Testcontainers tests).

```bash
./gradlew build                     # compile, tests, Checkstyle, PMD and the coverage gate
docker compose up -d                # Postgres, WireMock stubs of the Common Platform APIs, and the service
./scripts/container-smoke.sh        # build the image and check it starts and answers
./gradlew bootRun                   # run from source against `docker compose up -d postgres wiremock`
```

With the compose stack up:

```bash
curl -H 'CJSCPPUID: 77777777-7777-4777-8777-777777777777' \
  'http://localhost:4550/bulkresulting/hearings?ouCode=B01AA00&startDate=<a date in the next 28 days>'
```

## Deployment

GitHub Actions builds and publishes the jar and image; ADO pipeline 460 pushes the image to ACR, and
the shared `springboot-app` Helm chart deploys it. Secrets come from Azure Key Vault through the
Secrets Store CSI driver.

## License

This project is licensed under the [MIT License](LICENSE).
