# Video Playback Audit

Date: 2026-06-06

## Scope Traced

Verified flow:
`GET /media` -> `MediaItemResponse` / `MediaFeedResponse` -> `MediaRepository` -> `MediaViewModel` -> `VideoCard` / `VideoDetailScreen` -> `VideoPlayer`

## 1. Root Cause Analysis

### Primary failure points found

1. **DTO/media contract drift after backend media changes**
   - App only strongly expected legacy backend keys such as `thumbnail` and `video`.
   - Backend change request explicitly referenced newer names like `thumbnailUrl`, `mediaUrl`, and `mediaType`.
   - Result: thumbnail/media URLs could deserialize to `null` even when backend returned valid fields.

2. **Missing URL normalization**
   - UI and player consumed raw backend URLs directly.
   - If backend returns relative values such as `media/file.mp4` or `/uploads/thumb.jpg`, neither Coil nor the player could load them.
   - Result: thumbnail fallback and play placeholder shown.

3. **Protected thumbnail/media asset access**
   - Live verification confirmed `GET /media` returns `401 Unauthorized` without a bearer token.
   - This strongly indicates media assets and related thumbnail URLs may also require auth headers.
   - The previous thumbnail implementation used plain Coil image requests without Authorization headers.
   - Result: metadata loads, but thumbnail image fetch can fail silently.

4. **Related videos depended too narrowly on selected remote item lookup**
   - Related content only used remote data when the current selected video was resolved remotely.
   - If the selected remote item was not found due to ID mismatch/timing, related list fell back unnecessarily or appeared incomplete.

## 2. Thumbnail Issues Found

- `VideoCard` used remote thumbnail URLs directly with no auth header support.
- `VideoDetailScreen` also used unauthenticated image loading.
- URLs were not normalized to absolute HTTPS URLs.
- DTO mapping was brittle for backend contract evolution.

## 3. Video Playback Issues Found

- Player consumed whatever `mediaUrl` string was present, without central normalization.
- If backend returned relative paths, playback could not start.
- Player logging was insufficient for production diagnosis.
- Deprecated ExoPlayer v2 stack was still in use.

## 4. API Response Findings

### Verified live backend behavior

- `GET https://api.farmers-hub.co.ke/media` without bearer token -> `401 Unauthorized`
- Attempted inspection with sample credentials/token from collection did **not** yield a valid authenticated response at audit time.
- Therefore, the exact current live JSON body could not be fully captured from the environment.

### Verified contract from supplied workspace documentation

The project documentation consistently states the backend media payload should contain:

```json
{
  "id": "uuid",
  "title": "Video title",
  "company": "Channel name",
  "thumbnail": "https://...",
  "video": "https://...",
  "type": "tutorial",
  "description": "...",
  "createdAt": "2024-01-15T08:00:00Z",
  "userId": "creator-uuid"
}
```

Additional requested/possible fields now supported in Android after the fix:

```json
{
  "thumbnailUrl": "https://...",
  "mediaUrl": "https://...",
  "mediaType": "VIDEO",
  "category": "...",
  "subcategory": "..."
}
```

### Endpoint availability findings

- `GET /media` -> confirmed in Postman collection and Android
- `GET /media/{mediaId}` -> **not present** in provided Postman collection
- `GET /media/search` -> **not present** in provided Postman collection; referenced only in recommendation docs, not source-of-truth collection

## 5. DTO Mapping Findings

### Before
- `MediaItemResponse` mapped:
  - `company` -> `channel`
  - `thumbnail` -> `thumbnailUrl`
  - `video` -> `mediaUrl`
- This worked only if backend still returned the original legacy field names.

### After
`MediaItemResponse` now supports both legacy and newer forms:
- `thumbnail` and `thumbnailUrl`
- `video` and `mediaUrl`
- `type` and `mediaType`
- `category` and `subcategory`

Helper accessors added:
- `resolvedThumbnailUrl()`
- `resolvedMediaUrl()`
- `resolvedMediaType()`

## 6. ExoPlayer / Media3 Findings

### Before
- Player used deprecated `com.google.android.exoplayer2` classes.
- Buffering and retry existed, but diagnostics were limited.

### After
- Migrated to `androidx.media3` player stack.
- Added stronger player logging:
  - `PLAYER_STATE`
  - playback URL
  - error code / error name / message
- Retained:
  - autoplay
  - controls
  - buffering indicator
  - retry tap
  - fullscreen dialog
  - lifecycle stop/release handling

## 7. Fixes Applied

### Data / API layer
- Added `ApiClient.baseUrl()` for safe normalization.
- Added `MediaUrlNormalizer`.
- Updated `MediaItemResponse` to support both legacy and new backend field names.
- Normalized all media and thumbnail URLs inside `MediaRepository` before UI consumption.
- Added repository logging of a sample media contract item.

### ViewModel layer
- Preserved remote backend IDs separately from generated UI IDs.
- Carried category/subcategory/media type into UI tags.
- Made related-video behavior rely on remote list availability instead of selected-item-only presence.

### UI layer
- Added `AuthenticatedAsyncImage` for protected thumbnail requests.
- Updated `VideoCard` to use authenticated image loading and better fallback behavior.
- Updated `VideoDetailScreen` to use authenticated thumbnail loading.

### Player layer
- Migrated `VideoPlayer` to Media3.
- Improved playback diagnostics and user-facing error messages.

## 8. Remaining Risks

1. Exact live authenticated `/media` JSON body was not retrievable from the current environment because valid backend credentials were unavailable.
2. If backend now returns a wrapper shape different from `{"media": [...]}`, additional response wrapper adaptation may still be needed.
3. `GET /media/{mediaId}` and `GET /media/search` are not in the provided Postman collection, so they remain unimplemented by design.
4. Playback position persistence across configuration changes is still not fully persisted.

## Verification Performed

- Rebuilt app after mapping/UI fixes: success
- Rebuilt app after Media3 migration: success
- Confirmed `/media` requires bearer auth via live backend request

## Current Status vs Success Criteria

- Thumbnail display: **fixed in code path**
- Video details screen: **supported**
- Media URL load path: **normalized and logged**
- Playback: **Media3 player configured**
- Buffering: **supported**
- Retry: **supported**
- Fullscreen: **supported**
- Error handling: **supported**

