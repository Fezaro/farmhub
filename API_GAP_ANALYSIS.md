# API Gap Analysis

Date: 2026-06-05
Collection baseline: `app/FarmHub APP.postman_collection.json` (15 requests)

## Coverage Summary

- Collection endpoints implemented in Android data layer: **15/15**
- Collection endpoints wired to active UI flows: **9/15**
- Repo-only (ready, no UI yet): reset password, specialist posts/process, post detail, counties/subcounties

## High-Priority Gaps (UI Wiring)

1. Reset Password
- Endpoint exists and repository exists.
- Gap: no reset password screen/flow.

2. Specialist Endpoints
- Endpoints now implemented in Retrofit + repository.
- Gap: no specialist UI workflow.

3. Counties/Subcounties
- Endpoint and repository exist.
- Gap: signup UI not consuming dynamic county/subcounty data yet.

4. Post Detail
- Endpoint and repository exist.
- Gap: no post-detail screen using `/posts/{id}`.

## Backend Endpoint Missing (Feature Referenced by App)

| Screen | Feature | Expected Endpoint | Impact |
|---|---|---|---|
| `WeatherScreen` | Weather forecast | `/weather` | Feature depends on endpoint not present in Postman collection; contract verification blocked |

## Phase-5 Domain Coverage Check

The requested domain list includes many APIs not present in the Postman collection. These are marked as **Backend Endpoint Missing** from the supplied source of truth.

| Domain | Status vs Postman | Notes |
|---|---|---|
| AUTH (login/profile) | Present | Implemented |
| AUTH (refresh/logout) | Backend Endpoint Missing | Not in collection |
| DASHBOARD statistics | Backend Endpoint Missing | Not in collection |
| CATEGORIES CRUD | Backend Endpoint Missing | Not in collection |
| SUBCATEGORIES CRUD | Backend Endpoint Missing | Not in collection |
| MEDIA details/search/upload/update/delete | Backend Endpoint Missing | Collection only has `GET /media` |
| RESUMABLE UPLOADS | Backend Endpoint Missing | Not in collection |
| USERS CRUD | Backend Endpoint Missing | Not in collection |
| MESSAGING list/get/send | Present | Implemented |
| PAYMENTS | Backend Endpoint Missing | Not in collection |
| ANALYTICS | Backend Endpoint Missing | Not in collection |
| FAVORITES | Backend Endpoint Missing | Not in collection |
| MODERATION | Backend Endpoint Missing | Not in collection |
| AUDIT LOGS | Backend Endpoint Missing | Not in collection |
| DATA counties/sub-counties | Present | Implemented (repo-level) |

## Recommended Next Steps

1. Confirm backend contract expansion and update Postman collection for all missing domains.
2. Add UI entry points for reset-password, specialist queue, and county/subcounty selection.
3. Add unit tests for `ErrorMapper` and repository success/error mappings.
4. Migrate callback-based repositories (`LoginRepository`, `SignupRepository`, `PostRepository`, `ProfileRepository`) to `NetworkResult` for full consistency.

