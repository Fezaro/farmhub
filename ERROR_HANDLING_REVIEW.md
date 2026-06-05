# Error Handling Review

Date: 2026-06-05

## Findings

### Before
- Error handling was inconsistent (raw `Response`, callback strings, ad-hoc `try/catch`).
- HTTP status handling was not centralized.
- Transport errors (DNS/SSL/timeout) were not mapped consistently.
- Some ViewModels only exposed Loading/Success/Error without Empty.

### After
Implemented centralized network primitives:
- `network/NetworkResult.kt`
- `network/ApiException.kt`
- `network/ErrorMapper.kt`

Added:
- `safeApiCall` wrapper for Retrofit `execute()` calls
- HTTP mappings for: 400, 401, 403, 404, 409, 422, 429, 500, 502, 503
- Transport mappings for: no internet/DNS, timeout, socket timeout, SSL, unexpected network errors

## Repositories Migrated to Central Handling

- `MediaRepository`
- `MessageRepository`
- `CountiesRepository`
- `PostDetailRepository`
- `ResetPasswordRepository` (new)
- `SpecialistPostRepository` (new)

## ViewModel State Coverage

- `MessageViewModel`: Loading / Success / Error / Empty for threads and conversations
- `MediaViewModel`: Loading / Success / Error / Empty

## Outstanding Gaps

- Callback-based repositories still use legacy string errors:
  - `LoginRepository`
  - `SignupRepository`
  - `ProfileRepository`
  - `PostRepository`
- Full app-wide consistency requires migrating these to `NetworkResult` or `ApiResult`.

## Risk Notes

1. Mixed error paradigms still exist until callback repositories are migrated.
2. ExoPlayer package is deprecated in current dependency set; migrate to Media3 to reduce future risk.
3. `ApiClient` still auto-logs out on 401 globally; ensure this matches product behavior for all flows.

