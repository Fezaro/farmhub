# FarmHub 1.0.11 (12) — app rollout handoff

> Use the replacement files in `release-1.0.11-12-final`, which include the
> full-catalogue search fix. Signed artifact checks and all 39 unit tests pass.
> The earlier download folder is superseded. The user confirmed backend/dashboard
> deployment, and live compatibility checks passed on 2026-09-03. The final signed
> APK has now passed the focused emulator smoke checks. The production AAB is
> uploaded and submitted in Play Console following explicit user confirmation.
> Google review/approval and production availability are not yet confirmed.

## Ownership and release order

1. User deploys the backward-compatible backend candidate, then the dashboard.
2. Confirm those deployments are live; check the public API/proxy from the app.
3. Verify the signed candidate on a physical phone, including upgrade/session,
   chat image viewing, categories, video playback and retry.
4. Upload the verified production AAB to Play Console and review the release
   summary, version code and existing reviewer access before submission.
5. Begin rollout stage checks. A local build or staging pass is not a claim that
   Google has approved the update or that production rollout is complete.

The user has deployed the backend/dashboard. The assistant's live checks passed:
test-account login, current active taxonomy with no-store/trace/CORS headers,
88 media records and the terminating second page. The deployed dashboard bundle
contains the category retry, draft-preservation and library-recovery changes and
the production API endpoint. This is a bounded compatibility check, not a
production upload soak or proof of the original incident's root cause.

Play Console still shows active production 1.0.10 (11), in 177 countries/regions.
Candidate 1.0.11 (12) is uploaded and submitted as the single change in review,
with release notes and full rollout (100%, all existing targeted countries).
The user confirmed publication; both submission confirmation buttons were
completed. Automated quick checks have finished, and Play now explicitly says
`Your changes are now in review`. Google approval and publication remain pending.
Managed publishing is off, so approval can publish the update automatically.
Existing reviewer credentials were checked visually and match the account used
for the successful final login. Do not publish the staging APK.

Release preparation is complete, not production rollout. Final signed-APK checks
on the isolated Android 16 emulator passed login, all 88 live feed records,
search for the last catalogue record, playback recovery, cold-start session
persistence, an existing incoming chat image/full-screen/zoom/Back, Profile
scrolling and explicit logout surviving restart. An emulator decoder error on
first playback recovered with Retry; it is recorded, not counted as a first-try
playback pass. No physical phone was connected, so physical-device and actual
Play-distributed upgrade checks remain unverified.

## Suggested Play release notes

View chat photos full screen and zoom in for more detail. Browse up-to-date
video categories and search across the full video library. Improved video
loading and recovery from connection issues.

## Artifact selection

The production artifacts are generated at:

- `app/build/outputs/bundle/release/app-release.aab` — Play upload.
- `app/build/outputs/apk/release/app-release.apk` — signed device checks.

Versioned copies: `artifacts/rollout/release-1.0.11-12-final/FarmHub-1.0.11-12.apk`
and `FarmHub-1.0.11-12.aab` in that same directory. Use the APK on a spare test
device. Do not uninstall the working Play app to work around a signing mismatch;
the actual Play-distributed upgrade check must use a Play testing track.

The older `app/release/app-release.aab` is **not** this candidate. Final build
verification and SHA-256 values are recorded in `ROLLOUT_STAGE_CHECKS.md`.
Run `scripts/verify-release.ps1` after building to check the APK's identity,
signature and unchanged permissions, production configuration, and absence of
staging resources in both artifacts.

Production package: `com.farm_tech.farmhub`; version 1.0.11 (12).
Keep the release API endpoint, signing identity and disabled release shrinking
unchanged. The opt-in `.staging` variant and its local CA must not enter this build.

## Final phone checks

For today's release, keep this to a focused smoke pass rather than a new stress
testing project: login/cold restart, search a known later-catalogue video, play
it, open a chat image and confirm explicit logout. Backend/dashboard deployment
and live compatibility checks have passed. The extended 125/250-device performance checks
below are additional coverage, not completed results.

- Upgrade without clearing data; open FarmVideos without signing in again.
- Open incoming and historical sent chat images, zoom, close and press Back.
- Compare current category/subcategory IDs with the dashboard; obsolete entries
  must not reappear after refresh or restart.
- Play a video; check recovery from a network failure with Retry.
- Validate catalogues of at least 125 and 250 videos: cross the 100-item API
  page boundary, reach the final card, search/filter/play later-page records,
  and check recovery if a later page fails. The earlier 26-video staging dataset
  is not a catalogue capacity limit or a 100+ device-performance sign-off.
- During feed failure, preserve saved videos and show a
  recovery notice instead of also claiming there are no videos.
- Scroll Profile to Sign Out; explicit logout must survive a cold restart.

Use only the approved isolated accounts/content for upload and message tests.
Do not clear a real user's app data to make an upgrade test pass.

## Rollback

Retain the published backend/dashboard builds before deploying. Stop the app
rollout if crash, login or playback regressions appear. Devices that already
installed the update require a corrective version with a higher version code;
do not attempt a production version-code downgrade.
