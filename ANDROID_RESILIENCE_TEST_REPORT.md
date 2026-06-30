# ANDROID_RESILIENCE_TEST_REPORT

Date: 2026-06-06

## Test matrix and outcomes

| Scenario | Method | Expected | Result |
|---|---|---|---|
| Baseline build/test | `assembleDebug`, `testDebugUnitTest` | Build + tests pass | PASS |
| Network/API contract check | Direct authenticated API calls | Valid media URL fields | PASS (streamUrl verified) |
| Video URL reachability | HEAD to media file URLs | 200 + video content type | PASS |
| Network loss in emulator | Toggle network while playback | graceful buffering/error/retry | BLOCKED (emulator instability) |
| Slow network in emulator | Throttled network in playback path | spinner + recover/retry | BLOCKED (emulator instability) |
| API failure simulation 401/404/500 | Inject fail URLs / auth changes | user-facing error + retry | PARTIAL (code path present; not fully exercised in stable UI run) |
| Orientation changes | rotate during playback | position/state restore | PARTIAL (implemented in code; no stable end-to-end replay session) |
| Background/foreground | home/resume during playback | state restore | PARTIAL (implemented in code; no stable end-to-end replay session) |

## What was executed

1. Verified backend login, `/media`, `/media/{id}`, and `/media/search` responses with bearer auth.
2. Verified backend media URLs are reachable and return `video/mp4`.
3. Implemented URL mapping fix for `streamUrl`.
4. Added unit tests for media URL resolution ordering.
5. Added playback state persistence across recomposition/config changes using `rememberSaveable`.

## Blocking factor

Automated emulator driving via adb encountered repeated ANRs (system services + app input focus timeout), which prevented reliable full UI resilience replay in this session.

Representative ANR signatures seen in logs:

- `ANR in com.farm_tech.farmhub/.MainActivity` (input dispatch timeout)
- Repeated keyboard/system ANRs (`com.google.android.inputmethod.latin`, `com.android.systemui`)

## Code-level resilience status

- Buffering/loading UI: present in `VideoPlayer`.
- Retry path: present in `VideoPlayer`.
- Error mapping: present for network/401/404/timeout.
- Playback resume intent + position across configuration changes: improved via `rememberSaveable(initialUrl)`.

## Recommended final QA pass (stable emulator/device)

1. Login with valid user.
2. Open `FarmVideos` -> select a video -> tap play.
3. Confirm spinner -> buffering -> playback start.
4. Rotate device during playback and verify resume from prior position.
5. Background app and return; verify state continuity.
6. Disable network during playback; verify graceful error and retry.

