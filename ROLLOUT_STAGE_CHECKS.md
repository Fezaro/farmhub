# Production feedback implementation — rollout stage checks

> Latest candidate includes the full-catalogue search correction. Its signed
> artifacts and 39 unit-test results are verified; hashes below identify this
> replacement. Earlier device results predate this final search-only correction;
> the final signed-APK smoke run and Play preparation are recorded at the end.

## Protected baseline

- Android baseline commit: `d7ecf58`, published 1.0.10 (11).
- Dashboard baseline commit: `67e5062`.
- Backend baseline commit: `308abed`.
- Android candidate: 1.0.11 (12). Uploaded and submitted in Play Console after explicit user confirmation; approval/publication pending.
- Keep production API contracts, active-category validation and release shrinking disabled.
- No assistant production content uploads, database migrations or backend/dashboard deployments. The user confirmed those deployments on 2026-09-03 and later explicitly approved Play submission, which is recorded below.

## Register

| ID | Issue | Implementation / verification |
|---|---|---|
| 1 | Chat attachments do not open fully | Shared viewer; incoming/outgoing/history, zoom, back and scroll position |
| 2 | Legacy Android category chips | Active authoritative taxonomy, stable IDs, no injected legacy list |
| 3 | Old dashboard fallback categories | Remove fabricated taxonomy; preserve valid choices on failure |
| 4 | Invalid/inactive upload assignments | Fresh preflight; retain API validation and upload drafts |
| 5 | Dashboard session renewal mismatch | Existing token refresh contract, bounded renewal, logout protection |
| 6 | Whole-dashboard error replacement | Local recovery; preserve mounted form and queue |
| 7 | Failures after multiple uploads | Actual incident cause unconfirmed; trace 429s and honor Retry-After |
| 8 | Prolonged taxonomy failure | Bounded recovery and explicit retry without losing draft |
| 9 | Transient Android feed failures | Bounded GET retry, preserve last successful feed, no 20-item cap |
| 10 | Error also reports no videos | Separate failed, loading, cached and empty states |
| 11 | Lost completion response can report an already-uploaded video as failed | Replay an owned completed upload by its stored file identity; return existing media instead of uploading again |
| 12 | Video error is overwritten by a later player callback, leaving Loading without Retry | Preserve error through IDLE/stopped callbacks; explicit Retry clears it. Six regression tests passed; actual connection failure → visible Retry → successful recovery passed on the rebuilt staging app |
| 13 | Search only considers the current display batches, so later catalogue items may be missed | Search the full catalogue before display pagination, retain query in saved state, and combine with category filters; six scale/search tests passed and replacement signed artifacts verified; final signed APK immediately finds live record 88 without prior scrolling |

## Gates

1. Backend isolated tests and TypeScript build.
2. Dashboard fault-injection/contract tests and production build.
3. Android unit tests and signed release build.
4. Local UI/end-to-end checks with fixtures; no production upload/message writes.
5. Signed release emulator checks where available; report any unverified gates explicitly.
6. User approval required before deployment/upload/submission.

## Results

Release preparation and submission are complete (2026-09-03): implementation, builds, local cross-repository tests, earlier signed-release upgrade checks, real database-backed dashboard batch uploads, dashboard/app image round trip, rendered traversal beyond 20 videos, live compatibility checks, final signed-APK focused emulator smoke checks, and Play upload/release submission are done. The user explicitly confirmed publication; Google's automated checks/review and rollout observation are not complete. Physical-phone and actual Play-distributed upgrade checks remain unverified. Do not represent submission completion as 100% rollout or a reliability guarantee.

Release artifacts: `app/build/outputs/apk/release/app-release.apk` and `app/build/outputs/bundle/release/app-release.aab`, version 1.0.11 (12). APK signature verification passed (v2).

- Final APK SHA-256: `7FE4267D5F699DD8E21BF6B6772B2BF81A4D337CE57BC28E12AC2C23898372BE`
- Final AAB SHA-256: `B11BAE19CB3EEBED9E2D6140FE162F1A7AF099D4CEE181CCBB97AC578C818FCB`
- These supersede the pre-search-fix APK `1F6FB17E...4A05EE` and AAB `2AF146FD...B37AFD`, and the still earlier playback-fix candidates. Download copies are now under `artifacts/rollout/release-1.0.11-12-final/`; the folder without `-final` retains the older, superseded binaries.
- Latest verification: 39 unit tests, zero failures/errors; release APK identity/signature and unchanged baseline permissions verified; AAB signature verified with the existing self-signed/no-timestamp warnings. Production endpoint, signing identity and disabled release shrinking remain unchanged; staging resources absent. Authentication/session/login source files have no diff from the working baseline.

- Backend TypeScript build: passed. Generated tracked `build/` files updated with their sources.
- Backend isolated regression tests: 19 passed (taxonomy contracts, trace/rate-limit headers, video pagination, messaging/lifecycle regressions).
- Cross-repository local HTTP test: passed. Uses the real category/media Express routes, real JWT validation, dashboard request/upload code, temporary disk chunks and an in-memory database adapter. It does NOT test the production database or proxy.
  - 12 sequential synthetic uploads completed; injected 429 and expired session after upload 10 recovered.
  - Category 500 response does not invent old categories; recovery succeeds.
  - Active IDs survive upload into returned media/feed; inactive selection rejected by client and API.
  - Replaying completed upload returns same media ID, no extra row; another user receives 404.
  - Signed-out request receives 401. Synthetic files cleaned up; no production uploads/messages created.
- Dashboard regression tests: 13 passed, including exact refresh contract, single-flight refresh, logout race protection, no ambiguous one-shot POST replay, cancel-during-retry, and 125-item pagination.
- Dashboard real-browser UI test: passed against isolated local fixtures. Item 25 renders; active choices only; failed preflight preserves draft/file; retry recovers without reopening; global error keeps the form mounted; no uncaught browser errors.
- Dashboard production build: passed. Existing large-bundle warning remains (about 966 kB uncompressed JS); not a new failure.
- Android unit tests: latest 33 passed, including six playback callback/error/retry regressions. Final normal (non-staging) signed release APK/AAB build and release lint passed after the playback fix. APK signing verification and AAB `jarsigner -verify` passed. Jarsigner reports the existing self-signed certificate/untrusted-chain/no-timestamp warnings; signing identity is unchanged.
- `scripts/verify-release.ps1` passed on final artifacts: production package/version, original signing SHA-256 identity, unchanged baseline permissions, production endpoint and no staging resources in either artifact. No new Android permissions were added.
- Android 16 image-viewer instrumentation: 2 passed. Shared incoming/outgoing/history bubble click opens the full viewer; double-tap does not crash; Close returns to both chat bubbles.
- Test-only AndroidX dependencies updated to Espresso 3.7.0 / JUnit extension 1.3.0 after the old runner failed on Android 16 `InputManager.getInstance`. This documented test-framework incompatibility occurred before UI assertions and is fixed in [AndroidX Test 3.7.0](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0). Release runtime dependencies and APK hash are unchanged.
- Android release shrinking stays disabled, preserving the published login fix. No auth contract change in the app.
- Signed release 1.0.11 (12), fresh install on Android 16 emulator: login with the supplied test account passed; live feed loaded 87 items. Crop Farming and Animal Farming show current API choices, and selecting Poultry returns matching videos. This confirms feed ingestion above 20 items, not a manual inspection of every rendered item or playback.
- Signed-release session checks: force-stop/cold launch then FarmVideos reopened without credentials; Profile scroll exposed Sign Out; explicit logout returned to login; another force-stop/cold launch then FarmVideos still required login. No crash-buffer entries during these checks. Intro/Welcome is the app's normal start screen even with a saved session.
- Signed-release network-stall test: emulator network delay 60,000 ms / speed 1:1 induced a confirmed media transport failure after cold start. The persisted feed and authoritative categories remained available with a saved-content notice and Retry; no contradictory empty-results message. Merely disabling Wi-Fi/mobile data was insufficient because virtual Ethernet still carried live requests, so that first attempt is not counted as an offline test. Network settings were restored after fault injection.
- Recovery after restoring network: the first immediate retry still used the fallback; a subsequent retry loaded 87 live items and cleared the saved-content notice. Recovery is subject to the existing request timeouts, not instantaneous. Evidence: `release-network-fallback.png`, `stall-check.xml`, and `final-recovery.xml` in the same ignored evidence directory.
- Device evidence is kept locally in ignored `artifacts/rollout/`: `release-feed.png`, `release-poultry.png`, `release-profile-scroll.png`, `persistence.xml`, and `logout-verified.xml`. Evidence may contain test-account profile information; do not publish it publicly.
- `git diff --check` passed across all three repositories (only line-ending normalization warnings).
- Initial emulator startup/system UI stalls and an initial bind-startup ANR were observed during device setup. Cold boot and a lower-resolution 720×1600 test display recovered; the image tests then passed. Do not omit these setup failures from the test history. The emulator uses `-read-only -no-snapshot` so test changes to its virtual disk are discarded on exit ([Android emulator documentation](https://developer.android.com/studio/releases/emulator#28-0-16)).
- No production deployments, migrations, content changes, or Play submissions performed.

## Re-running local checks

- Backend repo: `npm run test:rollout` (requires adjacent `Farm-Dashboard`, or set `ROLLOUT_DASHBOARD_ROOT`). Database adapter is isolated; does not load `.env` or connect Prisma.
- Dashboard repo: `npm run test:rollout` and `npm run build`.
- Browser fixture: start `node scripts/rollout-ui-fixture.mjs`; start Vite with `VITE_API_URL=http://127.0.0.1:18080` on port 5178; run `npm run test:rollout-ui`. Provide Playwright via installed dependency or `PLAYWRIGHT_MODULE_ROOT`, and a browser executable via `ROLLOUT_BROWSER_EXE`.
- Android repo: `gradlew.bat :app:testDebugUnitTest :app:assembleRelease :app:bundleRelease`. Instrumented image checks: `ChatImageViewerTest` in `app/src/androidTest/`.

## Rollout stage checks — release gates

1. Review changes across all three repositories; retain baseline deployment artifacts and database backup. No schema migration is needed for this patch.
2. After deployment approval, deploy backward-compatible backend changes first. Confirm `/categories` and `/subcategories` cache headers and 429 trace IDs/Retry-After through the real proxy.
3. Deploy dashboard candidate to staging/preview against the staged backend. Repeat real-file uploads across at least 12 items and a token renewal, including pause/cancel, inactive selection, category rename/archive, thumbnails and visibility. Test complete-response recovery; compare persisted media count to prevent duplicates.
4. Test signed Android candidate on a fresh install and an upgrade from published 1.0.10. Verify sign-in, force-stop/relaunch persistence, explicit logout, all category filters, >20 videos, playback and slow/offline/error recovery. Check incoming and historical sent chat images, zoom, close/back and retained chat position.
5. Cross-client check: dashboard upload with current IDs → API persisted record → Android feed/filter/playback; dashboard chat image → app full viewer; app image → dashboard full viewer. Use an explicitly approved test conversation/account and test content.
6. Start controlled rollout only after those gates pass. Watch request IDs, 401/429/5xx rates, upload completion/duplicate counts, app crashes, login and playback. Incident after ~10 uploads is not closed until real proxy/server logs identify or rule out throttling/session expiry.
7. Roll back dashboard/backend to saved baseline builds if regressions appear; stop the app rollout. Already-installed Android versions require a new corrective version rather than a version-code downgrade.

### Catalogue growth clarification (2026-09-03)

- Requirement: the existing catalogue exceeds 30 videos and must support growth
  past 100 into the hundreds. The 26-video staged run is a test dataset, not a
  supported catalogue limit; the final live read-only smoke test loaded 88.
- Code inspection: `/media` caps each response at 100 records, not the whole
  library. Android requests successive 100-record pages until the final short
  page. Its view-model exposes 20 additional records per display batch, and the
  lazy list requests the next batch near the bottom. Filtering uses the full
  loaded catalogue. These constants are not 20/30/100-video product limits.
- Dashboard's existing 125-record, two-page regression was rerun and passed.
- Added Android view-model logic tests for 31, 100, 101, 125, 250, 500 and 1,000
  videos, exact 20-item boundaries, final-record access and category/subcategory
  filtering beyond item 100. All six scale/search tests and 39 total unit tests
  passed, including immediate search for item 1,000 and restored queries. They seed an
  already-loaded synthetic catalogue: they are not Android HTTP, playback or
  physical-device performance tests.
- Extended device/performance coverage (not a new stress-test blocker for the
  user's requested focused release check): extend the end-to-end device dataset to at
  least 125 and 250 videos, cross the 100-record API boundary on the app, reach
  the final card, search/filter later records, play a later-page video and
  check second-page failure/retry plus slow-network behaviour. Record device
  responsiveness and memory use. Do not replace that gate with the 26-video run
  or with logic-test counts.
- Continuing those checks exposed issue 13, requiring an app runtime search fix
  and replacement signed artifacts. No production content was changed.
- Isolated real API checks passed with 125 (100+25) and 250 (100+100+50) records,
  distinct IDs, final-record search, byte-range video reads and an empty page
  beyond the end. HTTP metadata reads took 252 ms and 132 ms respectively on
  this local host; these are not mobile or production performance benchmarks.
- The scale dataset has 26 earlier uploaded records plus 224 DB-seeded fixtures
  with unique legal EBML metadata/hashes, shared locally generated footage and
  active IDs. This is not a claim of 250 independent successful uploads. The
  seed's first attempt used an invalid processing enum and was corrected to
  copy the existing uploaded fixture's valid state; no production DB was touched.

Local fixture success is not a claim that production end-to-end validation has finished. Production/staged deployment and physical-device gates must be signed off before release.

## Next handoff

- User approved an isolated staging environment on 2026-09-03, with Docker Desktop permitted. Docker engine requests returned timeouts/500, so its existing containers were not reset. A separate native PostgreSQL cluster is running only on 127.0.0.1:55439, database `farmhub_rollout`, under the backend's ignored `tmp/rollout-stage/` directory. No production database was used.
- The final candidate was rebuilt and rehashed after the playback error-state fix. The full hashes above identify the files to test and upload; do not use the preceding candidate or the old `app/release/` bundle.
- The disposable read-only emulator is shut down after these checks; its test session/disk changes are discarded. No user's existing installed app was removed: the base test AVD had no FarmHub install, and the only uninstall was our temporary debug test install before installing the signed candidate.

## Approved isolated staging validation — results

- `scripts/rollout-stage.cjs` copies the compiled API into an isolated runtime and uses the actual Express middleware/routes, Prisma and PostgreSQL, not the earlier in-memory fixture. Only the listen transport is replaced with local HTTPS. Test accounts and a seeded test conversation are separate from production.
- All 22 existing migrations applied successfully to `farmhub_rollout`. The initial Prisma invocation discovered the repository .env, but the explicitly supplied URL and migration output confirmed the isolated localhost database. The harness now has its own runtime project root and empty .env; rerunning reports no pending migrations against the same isolated database.
- Dashboard real-browser batch: 10 valid WebM files completed; the signed test cookie was then expired; the same retained form successfully renewed through the real `/auth/refresh` route and completed 15 more. Dashboard and API both returned all 25 records with generated thumbnails.
- The valid WebM fixtures share locally generated footage but have distinct legal EBML Void metadata, keeping the backend's binary-hash duplicate protection enabled. An initial identical-file attempt correctly received 409; an overloaded browser encoder also produced empty fixture output in an earlier setup attempt. Neither is counted as a product pass or hidden from test history.
- `scripts/rollout-stage-verify.cjs`: passed real persistence, unique hashes, active category IDs, thumbnail files, HTTP byte-range playback, completed-upload replay without a new row, archive/stale-ID rejection with test selection restored, and actual rate-limit 429 trace/no-store/Retry-After headers.
- Dashboard chat send/full viewer: passed real image upload and full 1280x960 image loading, zoom and return to the conversation. The first selection attempt used the wrong role and the default Unread filter; the rerun selected All and the actual list item. This was test setup, not a product defect.
- A source rebuild of published baseline commit `d7ecf58`, version 1.0.10 (11), passed. It is not a Play-downloaded APK. Installing it, signing in, then upgrading with `adb install -r` to signed 1.0.11 (12) preserved login and loaded the live feed without credentials. No uninstall/data clearing occurred between versions. Evidence: `upgraded-feed.xml/png`.
- New opt-in `staging` variant uses separate application ID `com.farm_tech.farmhub.staging`, label `FarmHub Staging`, and HTTPS `10.0.2.2:18443`. Its short-lived local test certificate is only in staging resources. Build with `-ProlloutStaging=true`; ordinary debug/release builds do not include or require it. Initial staging lint found missing includeSubdomains attributes; corrected without disabling lint and the staging build passed.
- Actual staging app login and active Crop Farming → Avocado filtering passed. The dashboard batch video opened with matching persisted category IDs. Absolute media URLs use localhost in this isolated API, so emulator port reverse `18443` is required; after adding it, Media3 reached READY/ENDED and Replay appeared. The missing port initially induced the connection error that exposed issue 12; production endpoints were not modified.
- Dashboard → app chat image passed: actual server-loaded image opens uncropped with all four corner labels, double-tap visibly zooms, Android Back returns to the conversation. Evidence: `staging-incoming-full.png`, `staging-incoming-zoom.png`.
- App → backend → dashboard image passed. Dashboard loaded its actual original 1280x960 image, zoomed, and closed back to the conversation with no browser page errors. The first browser attempt timed out before completing login under heavy host load; rerun after closing the disposable emulator passed. Under that load, ADB text injection produced caption `Rout`; verification selected the actual caption without creating a duplicate message.
- Force-stop/cold-launch staging app, reopen chat, open its historical sent image: passed; all corner labels visible, Close returned to the same message position. This verifies server-backed history rather than only the optimistic attachment URI. Evidence: `staging-history-reload.xml`, `staging-historical-full.png`, `staging-history-close.xml`; dashboard evidence: `artifacts/rollout-stage/dashboard-app-image-full.png` in its repository.
- Android log confirms 26 staged media items loaded. Manual traversal on the rebuilt staging app reached the distinct oldest record, `Rollout real-video 1788419799795`, confirmed by the isolated database as record 26. This validates rendering beyond 20, not just loading. Evidence: `final-feed-last-video.xml/png`. Rapid ADB gestures initially acted as taps; slower 700 ms drags completed traversal. Upgrade/live read-only checks saw 88 live items at that later time; the earlier 87 count was a prior snapshot, not a fixed expected production count.
- During this emulator session, a system Bluetooth native crash and System UI stall occurred. No FarmHub crash has been observed; keep these system setup failures separate from app test results.
- Rebuilt staging app playback-fix retest passed: no local media port forwarding induced `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED`; after the subsequent IDLE callback, the screen still displayed the connection error and Retry (no stuck Loading). Restoring only the local port and tapping Retry on the same screen reached READY then ENDED/Replay. Evidence: `final-video-error-visible.xml/png`, `final-video-recovered.xml/png`.
- Pre-search-fix production APK (`1F6FB17E...4A05EE`), fresh install: test-account login passed, live feed loaded 88 items; force-stop/COLD launch and FarmVideos reopened without credentials and loaded the feed again. No FarmHub crash-buffer entries. Evidence: `final-production-loaded.xml/png`, `final-production-coldstart.xml`, `final-production-persistence-loaded.xml`. Production chat/content writes were not used. Do not attribute this device run to the newer search-fix binary.
- The read-only emulator was reopened after the builds; its 161-second cold boot and another recoverable System UI ANR were recorded. The final app tests proceeded after recovery. At handoff it was shut down, and only our isolated API/Vite processes and native PostgreSQL cluster were stopped. Local test data/evidence are retained; Docker Desktop and the user's services were not reset.
- Deployment ownership confirmed by the user: user deploys backend/dashboard, assistant prepares/tests/uploads the Android app. The user subsequently confirmed both are live; compatibility results are recorded below.
- Latest versioned handoff copies are in `artifacts/rollout/release-1.0.11-12-final/`, with matching final hashes. The earlier folder is retained as a superseded candidate. Play Console was inspected read-only: last published September 2, managed publishing off; no candidate uploaded or submitted.
- Outstanding: actual Play-distributed/physical-phone upgrade sign-off, Play submission and rollout observation. Public API smoke checks passed; production 429/proxy behaviour under load was not deliberately induced. Staging rate-limit/session tests do not identify the original production incident's cause. Real-proxy logs and the deployment checklist remain required; unit/fixture cancellation checks are not a claim of an exhaustive production upload soak.

## Live deployment compatibility check — 2026-09-03

- User confirmed backend and dashboard are live. No assistant deployment, production upload, chat message, taxonomy mutation or database change was performed.
- Supplied review-account login passed; token was kept only in the test process, not logged or saved.
- Production `/categories` and `/subcategories` returned HTTP 200 and `Cache-Control: no-store`. The request ID `rollout-production-validation-20260903` was echoed. CORS allowed `https://farmhub-dashboard.netlify.app` and exposed `X-Request-ID`, `X-Correlation-ID` and `Retry-After`.
- Five active categories returned: Crop Farming, Environment Care, Animal Farming, Agribusiness and Sector Updates. Current taxonomy includes Avocado, Pest & Diseases and Poultry rather than the obsolete Avocados/Chicken entries.
- `/media?page=1&size=100` returned 88 records with total 88; page 2 returned zero records with total 88. Both returned HTTP 200. This confirms the current live catalogue, not a live 100+ catalogue test; isolated scale tests cover that boundary.
- Dashboard HTML and deployed JS asset `/assets/index-d79c81f2.js` loaded successfully. The bundle contains category retry, inactive-selection draft-preservation and library-recovery messages, the production API URL, and neither checked local fixture API URL. This verifies deployed change markers, not an authenticated production upload test or byte-for-byte equality with the local build.
- Final versioned AAB SHA-256 rechecked and matches `B11BAE19CB3EEBED9E2D6140FE162F1A7AF099D4CEE181CCBB97AC578C818FCB`.
- Play Console inspected read-only: active production release 11 (1.0.10), 177 countries/regions; last published September 2; managed publishing off. No draft release created, bundle uploaded or review submission made during this check.
- No blocker found in this bounded live pass. The latest search-only correction remains covered by tests/build checks but has not received the final phone UI smoke check; earlier device results must not be attributed to that replacement binary.

## Final signed-app smoke and Play preparation — 2026-09-03

- User authorized completing the remaining checks and preparing the Play update. No application code or backend/dashboard configuration was changed during this final preparation.
- Re-ran `scripts/verify-release.ps1`: final APK/AAB hashes, version 1.0.11 (12), production endpoint, original signing identity, unchanged permissions and absence of staging resources passed. Existing JUnit XML confirms 39 tests, zero failures/errors. Versioned final AAB was uploaded, not an older artifact.
- Installed that exact final signed APK on the existing isolated read-only Android 16 emulator. No physical phone was connected. The initial System UI ANR recovered; a transient UI automation null-root snapshot was retried. Crash buffer contained only earlier system entries, no FarmHub crash entries during this run.
- Fresh review-account login passed. Feed log reported 88 items from the deployed API. Cold force-stop/relaunch reopened FarmVideos and loaded the feed without asking for credentials.
- Queried the live feed read-only to identify its last record, ID `34c5b082-5f53-4031-bc21-5e414c4b3b5a`, title `Unlock Dairy Success: The Simple Guide to Zero-Grazing and Better Feeds!`. Searching `Unlock Dairy` immediately displayed this record without first scrolling through the catalogue. Evidence: `final-update-last-video-search.xml/png`.
- Opening that last video initially failed with `ERROR_CODE_DECODING_FAILED` (4003), `c2.goldfish.h264.decoder`, followed by IDLE. The app retained a visible Retry action. Tapping Retry reached `PLAYER_STATE ... READY` and displayed video content, without an APK change. This is a successful recovery, not a first-attempt playback pass or proof of behaviour on all physical devices. Evidence: `final-update-playing.xml/png`, `final-update-playback-retry.xml`, `final-update-playback-recovered.png`.
- Opened an existing incoming image in the authorized test account's chat: full image loaded, double-tap visibly zoomed, Android Back returned to the same message position. No new production messages or uploads were created. Historical outgoing-image coverage remains the earlier isolated cross-client test. Evidence: `final-update-image-viewer.xml/png`, `final-update-image-zoom.png`, `final-update-image-return.xml`.
- Profile scroll exposed Sign Out. Explicit logout returned to login; after force-stop/relaunch and choosing FarmVideos, login was still required. Evidence: `final-update-profile-scroll.xml`, `final-update-logout.xml`, `final-update-logout-confirmed.xml`.
- Play production candidate created, AAB uploaded and accepted as 12 (1.0.11), API 25+, target SDK 36. Supported devices unchanged: no previously supported devices lost across any listed form factor.
- Saved English release notes covering full-screen chat photos/zoom, current taxonomy, full-library search and loading/recovery improvements. Saved release to Publishing overview as one unsubmitted change, `1.0.11 (12) — Start full rollout`, 100% of existing targeted countries. Active published release remains 11 (1.0.10).
- Play validation reports two warnings, no blocking errors: missing deobfuscation file and native debug symbols. Release obfuscation remains disabled to preserve the working configuration, so there is no R8 mapping file to upload. The production dashboard additionally lists an existing optimization deadline of February 2027; track separately rather than changing shrinking during this rollout.
- Existing Google Test reviewer entry was visually verified to match the supplied account and password; no credential changes made. Automated DOM reads did not expose those values, so they were not treated as evidence of missing credentials. Existing access instructions and settings were preserved.
- At handoff, Play automated quick checks are running and `Submit 1 change for review` is available. No submit action taken. Managed publishing remains off; submission followed by successful review can automatically publish the configured full rollout. User confirmation is required for that final action.
- Physical-phone/Play-distributed upgrade validation and production rollout observation remain unverified. The final preparation pass does not resolve the original upload incident's root cause or guarantee approval.

## Confirmed Play submission — 2026-09-03

- User explicitly instructed `publish now` and confirmed. Clicked `Submit 1 change for review`, then `Send changes for review` in the confirmation dialog.
- Verified Play Console changed to `Changes in review` with the sole item `Production — 1.0.11 (12) — Start full rollout`.
- At that verification, quick checks were at 90%, with up to one minute indicated; Play states the change will be sent for review when checks complete successfully. Submission is accepted, not evidence that checks or review have passed.
- Subsequent verification in the same turn: the quick-check progress disappeared and Play explicitly reported `Your changes are now in review. We may find additional issues when reviewing your app.` Quick checks have completed and Google review is underway; this is not approval or publication.
- Managed publishing remains off. The configured 100% rollout across existing targeted countries can publish automatically after Google's successful checks and approval. Current public availability of the update has not been confirmed.
- No additional unrelated changes, credential updates or permission changes were submitted. Future production rollout checks must verify actual availability and observe login, playback, image viewing, taxonomy, feed reliability and crashes; submission itself does not complete those checks.
