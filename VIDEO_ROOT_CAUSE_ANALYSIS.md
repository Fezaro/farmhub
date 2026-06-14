# VIDEO_ROOT_CAUSE_ANALYSIS

Date: 2026-06-06

## Executive Finding

The playback failure root cause is a backend/app contract mismatch in media URL mapping:

- Backend `GET /media` returns video URL in `streamUrl`.
- Android DTO `MediaItemResponse` did not map `streamUrl`.
- `MediaItemResponse.resolvedMediaUrl()` only checked `mediaUrl` and `video`.
- Result: `VideoItem.mediaUrl` became `null`, and `VideoPlayer` could not prepare a playable source.

This exactly matches the user symptom: list + thumbnails visible, but video never starts.

## Reproduction Evidence

### API evidence (authenticated)

`GET https://api.farmers-hub.co.ke/media` sample payload:

```json
{
  "id": "34c5b082-5f53-4031-bc21-5e414c4b3b5a",
  "title": "Test",
  "company": "Test",
  "mediaType": "VIDEO",
  "thumbnailUrl": "http://api.farmers-hub.co.ke/files/...-thumbnail.webp",
  "streamUrl": "http://api.farmers-hub.co.ke/files/..._.mp4"
}
```

### Flow trace

- UI: `VideoDetailScreen` -> `VideoPlayer(url = video.mediaUrl, ...)`
- ViewModel: `MediaViewModel` creates `VideoItem(mediaUrl = item.mediaUrl?.takeIf { ... })`
- Repository: `MediaRepository` normalizes `item.resolvedMediaUrl()`
- DTO before fix: `resolvedMediaUrl()` = `mediaUrl ?: video`
- Missing branch: `streamUrl`

## Network inspection results

Authenticated checks performed:

- `GET /media` -> `200`
- `GET /media/{mediaId}` -> `200`
- `GET /media/search?q=test` -> `200`

Media file URL checks:

- `http://api.farmers-hub.co.ke/files/...mp4` -> `200`, `Content-Type: video/mp4`
- `https://api.farmers-hub.co.ke/files/...mp4` -> `200`, `Content-Type: video/mp4`

Conclusion: media file itself is reachable; failure was mapping, not file availability.

## ExoPlayer findings

`VideoPlayer` uses Media3 and constructs source from the passed `url`:

- `MediaItem.fromUri(initialUrl)`
- `setMediaSource(...)`
- `prepare()`
- `playWhenReady`

With null/empty URL, player cannot transition into ready playback with a valid source.

## Fix applied

### 1) DTO mapping fix (primary)

File: `app/src/main/java/com/farm_tech/farmhub/models/media/MediaItemResponse.kt`

- Added:
  - `@SerializedName("streamUrl") val streamUrl: String? = null`
- Updated resolver:
  - `resolvedMediaUrl()` now returns `mediaUrl ?: streamUrl ?: video`

### 2) Related videos fallback improvement

File: `app/src/main/java/com/farm_tech/farmhub/ui/tabs/VideoDetailScreen.kt`

- If remote related list is empty (e.g., only one backend video), fallback to static related list.
- Prevents empty Related Videos section.

### 3) Playback resilience across config changes

File: `app/src/main/java/com/farm_tech/farmhub/ui/components/VideoPlayer.kt`

- Persisted `playbackPosition` and `resumeWhenStarted` with `rememberSaveable(initialUrl)`.
- Improves rotate/background behavior.

## Validation performed

- Build: `assembleDebug` successful.
- Unit tests: `testDebugUnitTest` successful.
- Added unit tests for URL resolution precedence:
  - `app/src/test/java/com/farm_tech/farmhub/models/media/MediaItemResponseTest.kt`

## Logs captured

- API payload confirms `streamUrl` field present.
- Emulator/system logs showed intermittent ANRs during UI automation, but no contradictory evidence against the root-cause mapping failure.

## Remaining risk

- Full interactive emulator walkthrough was impacted by emulator-level instability (multiple system and app ANRs while driving UI via adb automation).
- Manual smoke validation in Android Studio emulator is still recommended on a stable AVD session to confirm UX path end-to-end after this fix.

