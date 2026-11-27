# Consumed Common Platform APIs

All calls are `GET`, made as the user (`CJSCPPUID`; `CPPCLIENTCORRELATIONID` when the UI sent one),
under `CP_BASE_URL`.

| API | Path | Accept | Used for |
|---|---|---|---|
| usersgroups | `/usersgroups-query-api/query/api/rest/usersgroups/users/logged-in-user/roles` | `application/vnd.usersgroups.get-roles-for-logged-in-user+json` | Court check: `allRoles[].userPlacements[].placementId` |
| usersgroups (authorisation filter) | `/usersgroups-query-api/query/api/rest/usersgroups/users/logged-in-user/permissions` | `application/vnd.usersgroups.get-logged-in-user-permissions+json` | The caller's groups and permissions for the Drools rules |
| reference data | `/referencedata-query-api/query/api/rest/referencedata/courtrooms?oucode=` | `application/vnd.referencedata.ou-courtrooms+json` | `organisationunits[].id` (court centre id), `courtrooms[].id` / `courtroomName` |
| listing | `/listing-query-api/query/api/rest/listing/hearings/range-search` | `application/vnd.listing.search.hearings+json` | Candidates: `results`, `hearings[]` (`id`, `type.description`, `hearingDays[0].hearingDate` / `courtRoomId`, `hearingDayCount`, `listedCases[]`) |
| hearing | `/hearing-query-api/query/api/rest/hearing/hearings?date&courtCentreId&roomId` | `application/vnd.hearing.get.hearings+json` | Share state: `hearingSummaries[].hasSharedResults`, `hearingDays[].hasSharedResults` for the date, `courtApplicationSummaries` |
| progression | `/progression-query-api/query/api/rest/progression/prosecutioncases/{caseId}` | `application/vnd.progression.query.prosecutioncase+json` | Account number: `prosecutionCase.relatedUrn`; a 200 with no body means none |

**Listing parameters**: `allocated=true`, `jurisdictionType=MAGISTRATES`, `ouCode`, `startDate`,
`endDate`, `courtRoomId` (optional), `courtSession` (AM, PM or AD; left out for any), `businessType`
(ENF or ENF_AUTO), `useMaxPageSize=true`.

**Retries**: connect and read failures, 408, 429, 500, 502, 503 and 504; 3 attempts, doubling from
200 ms to a 2 s cap, honouring a delta-seconds `Retry-After`.
