# MTV Downloader Monetization And Quality Plan

Date: 2026-08-19

Scope: `mtvdownloader` module, sample third-party app usage, and release documentation. Formats covered: HLS (`.m3u8`), DASH/MPD (`.mpd`), and progressive MP4 (`.mp4`, `.m4v`).

## Current Risk Level

Overall code risk after this pass: Low-Medium.

External release risk: Medium until real-device validation is completed for HLS, MPD, MP4, pause/resume/cancel, app kill, network switch, and low-storage behavior.

DRM offline release risk: Medium-High until the Widevine license server accepts the MTV/sample app package name, signing certificate, auth token, payload user/package id, and offline-license policy. The sample app runtime `applicationId` is now `com.sspt.aol` for AOL compatibility testing. A fresh sample authorization token is now present, so any remaining HTTP `401` during online playback or offline license acquisition should be treated as a backend/license validation failure stage, not a Media3 segment-cache failure.

Previous risk level: Medium-High, because the module had HLS-only URL routing in several places, no MP4 model support, duplicate enqueue logic, a broken Room migration, fixed cache policy, no analytics API, no persisted failure reason, no persisted DRM license state, and no monetization sample.

## Completed

- Added MP4/progressive support to `DownloadModel.mp4Url`.
- Added HLS, DASH/MPD, and MP4 source resolution through one SDK path.
- Updated source resolution so DRM-flagged content with a playable HLS URL downloads HLS first, matching the restored online playback route and avoiding a failing Widevine offline-license request for HLS-backed items.
- DRM MPD content now requires MPD plus a non-empty license URL before enqueueing; DRM-flagged HLS content can enqueue through the HLS path.
- Clear content can now download HLS, DASH/MPD, or MP4.
- Worker now creates Media3 `DownloadRequest` with explicit MIME type for HLS, DASH, and MP4.
- Added `DownloadMonetizationGate` so the host app can run rewarded ads, paywall checks, subscription checks, or server authorization before a new download starts.
- `DownloadButton` now runs the monetization gate before starting quality selection or MP4 download.
- MP4 downloads skip quality selection and start as original/progressive.
- HLS/DASH quality selection still uses Media3 track discovery.
- Removed duplicated new-download enqueue logic from `ReelDownloadHelper`.
- Queue checks now treat both `queued` and `downloading` items as active queue work.
- Added `queuedAt` and queue ordering by `queuedAt ASC, contentId ASC`.
- Preserved stored stream keys when starting the next queued item.
- Hardened `StreamKeyUtil.fromString()` against malformed persisted values.
- Fixed `DownloadSdk.init()` so API 24/25 callers do not need an Android O guard.
- Added `DownloadSdkConfig` for max cache size, max parallel downloads, and retry count.
- Replaced fixed 500 MB cache policy with configurable `maxCacheBytes`.
- Added retry policy visibility with persisted `retryCount` and `maxRetryCount`.
- Added persisted `failureCode` and `failureReason`.
- Worker now updates Room and analytics on final failure.
- Fixed pause/resume so paused downloads keep cached bytes and resume from the previous Media3 cache position.
- Pause now uses Media3 stop reasons instead of removing the download.
- Resume now reuses the persisted Room row, preserving progress, source URL, DRM license URL, stream keys, selected height, and bitrate.
- Added persisted DRM state: `drmLicenseExpiresAt`, `drmLicenseLastRefreshAt`, and `drmLicenseRefreshStatus`.
- Added Widevine offline DRM license acquisition for MPD downloads.
- Persisted offline DRM `keySetId` in Room as `drmOfflineKeySetId`.
- Persisted offline DRM `keySetId` as Base64 `NO_WRAP` in `drmOfflineKeySetIdBase64`.
- Added Room version 5 migration for `drmOfflineKeySetIdBase64`, `drmScheme`, `drmKeyId`, and `contentMimeType`.
- Attached offline DRM `keySetId` to Media3 `DownloadRequest` for cached MPD playback.
- Updated the video player offline DASH path to restore `drmOfflineKeySetId` without switching players.
- Updated the video player offline DASH path to restore `drmOfflineKeySetIdBase64` and to avoid using the online license URL as an offline fallback.
- Updated the sample app to pass `drmOfflineKeySetId`, `DownloadManager`, and download cache into `videosdk`.
- Updated the sample app to pass `drmOfflineKeySetIdBase64` into `videosdk`.
- Updated downloaded/offline DRM player content to set `drm = "1"` instead of passing only the license URL.
- Updated the sample app DRM payload to match AOL behavior: `k_id`, `licence_duration`, `download = "1"`, `can_renew = true`, active `package_id`, and Base64 `NO_WRAP`.
- Restored the video SDK playback payload to AOL-compatible DRM fields: playback and downloader requests both send `download = "1"` and `can_renew = true`.
- Restored API-list online playback routing so the sample app does not force `drm = "1"` into `videosdk` for online list items; this keeps online playback on the previously working route while download still uses DRM metadata.
- Updated the sample DRM payload builder to resolve payload `user_id` from `DRM_USER_ID`, JWT `user_id`, then the legacy AOL fallback instead of using JWT `owner_id` as the DRM user.
- Added optional `DRM_PACKAGE_ID` override before falling back to API `ContentItem.packageId`.
- Added optional `DRM_AUTHORIZATION_SOURCE` selection for `stored`, decoded `jwt`, and nested `claim_token` license authorization testing. The default remains `stored` to match the AOL reference flow.
- Added redacted `DRM_LICENSE_URL_BUILT` logs in the sample app.
- Added package/signing SHA-256 logging before offline license acquisition.
- Added DRM authorization token expiry detection before requesting an offline license.
- Added stage-based DRM logs: `DRM_DOWNLOAD_START`, `DRM_MANIFEST_PREPARE_START`, `DRM_MANIFEST_PREPARED`, `DRM_INIT_DATA_FOUND`, `DRM_OFFLINE_LICENSE_START`, `DRM_OFFLINE_LICENSE_SUCCESS`, `DRM_DOWNLOAD_REQUEST_CREATED`, `DRM_SEGMENT_DOWNLOAD_START`, `DRM_SEGMENT_DOWNLOAD_COMPLETE`, and offline playback prepare/failure logs.
- Added distinct failure codes for `DRM_AUTH_TOKEN_EXPIRED`, `DRM_MANIFEST_PREPARE_FAILED`, `DRM_INIT_DATA_MISSING`, `DRM_OFFLINE_LICENSE_FAILED`, `DRM_KEYSETID_EMPTY`, `DRM_DOWNLOAD_REQUEST_FAILED`, and `DRM_SEGMENT_DOWNLOAD_FAILED` logs.
- Added extensionless DRM DASH routing when API DRM metadata and a Widevine license URL are present.
- Added offline DRM license renewal that reacquires and replaces `drmOfflineKeySetId` and `drmOfflineKeySetIdBase64`.
- Added Room database version 4 migration and schema export for `drmOfflineKeySetId`.
- Added offline DRM license release during delete/cancel cleanup.
- Added repository API to update DRM license state after host refresh.
- Added `DownloadAnalyticsListener` for request, monetization, enqueue, start, progress, pause, resume, cancel, complete, and failure events.
- Wired analytics through `DownloadButton`, `ReelDownloadHelper`, and `DownloadWorker`.
- Added sample third-party monetization gate in the sample app.
- Added sample SDK init with `DownloadSdkConfig` and analytics listener in the sample app.
- Updated sample download model mapping for MP4 URLs and DRM expiry.
- Fixed Room migrations with v1-to-v2 no-op and v2-to-v3 additive migration.
- Configured Room schema export and generated version 2 and version 3 schema files.
- Added unit coverage for source resolution.
- Added unit coverage for config sanitizing.
- Added instrumentation source coverage for SDK init, WorkManager availability, and `MediaDownloadService` class wiring.
- Updated third-party README with install, setup, HLS/MPD/MP4/DRM usage, monetization, config, analytics, failure/retry fields, and troubleshooting.
- Added `mtvdownloader/CHANGELOG.md`.
- Bumped downloader artifact version to `download-1.1.1`.
- Updated `jitpack.yml` to publish `:mtvdownloader`.

## Completed Monetization Steps

1. SDK gate point: Completed.
   `DownloadButton` accepts `monetizationGate`, and default behavior allows downloads so old clients keep working.

2. Host business rule sample: Completed.
   The sample app includes `SampleDownloadMonetization.gate`, which demonstrates subscription-first and rewarded-ad-fallback behavior without adding a real ad SDK dependency.

3. Rewarded ad/paywall hook: Completed.
   The SDK can wait for any suspend host implementation and starts downloads only when the gate returns `true`.

4. Analytics: Completed.
   `DownloadAnalyticsListener` tracks allowed, blocked, cancelled, failed, completed, progress, pause/resume, and enqueue events.

5. Backend enforcement guidance: Completed.
   README documents that SDK gating improves UX, but paid content must still be enforced by backend authorization and DRM/license rules.

## Requires Real-Device Validation

These items cannot be honestly marked complete from code inspection alone:

- HLS quality download on a real device.
- DASH/MPD clear download on a real device.
- DASH/MPD DRM download with a real Widevine license.
- MP4 progressive download on a real device.
- Pause, resume, cancel, app kill, and relaunch behavior.
- Network switch and airplane-mode behavior during active downloads.
- Low-storage behavior and Media3 cache eviction.
- Android 13+ notification permission behavior.
- Android 14+ foreground service behavior.

## In Progress

- DRM MPD license-server validation for the MTV/sample app package and signing certificate.
- Backend comparison for the current HTTP `401` after testing `authorizationSource=stored`, `jwt`, and `claim_token`.
- Confirming the updated online DRM payload allows the same AOL content to play in `videosdk` after backend/package/signing validation is corrected.
- Confirming a real AOL DRM content item reaches `DRM_OFFLINE_LICENSE_SUCCESS` and returns a non-empty `keySetId` after backend/package/signing validation is corrected.
- Confirming offline playback with internet disabled after app restart.
- Confirming license renewal updates the saved Base64 `keySetId`.

## Pending

- Backend/DRM team confirmation that `com.sspt.aol` with the current signing certificate, or the production consuming app package/signing pair, is whitelisted for Widevine offline licenses.
- Backend/DRM team confirmation that the debug/release signing SHA-256 fingerprints are whitelisted when required.
- Backend/DRM team confirmation of the exact payload `user_id` and `package_id` expected for the working AOL entitlement if HTTP `401` remains with a fresh token.
- A build signed with one of the real `com.sspt.aol` fingerprints from the AOL/Firebase configuration, or backend whitelist for this local debug SHA-256: `E1:01:1D:63:A5:CB:2A:BC:C4:98:C7:51:08:B3:5B:FC:81:AA:46:AE:A9:59:5E:D5:47:F9:75:23:2C:75:B9:7E`.
- Real-device test evidence for `DRM_SEGMENT_DOWNLOAD_COMPLETE` followed by offline playback without network.

## Remaining Risk

| Area | Risk | Why It Matters | Reduction Plan |
| --- | --- | --- | --- |
| DRM offline downloads | Medium-High | Code now acquires and persists offline Widevine `keySetId`, but real behavior still depends on package/signing whitelist, token audience, payload user/package id, license policy, expiry, and device support. | Run DRM MPD on device, confirm `DRM_LICENSE_URL_BUILT mode=offline`, confirm `DRM_PACKAGE_SIGNING_CHECK`, confirm backend whitelist for package/signing/user/package id, confirm `DRM_OFFLINE_LICENSE_SUCCESS`, verify expiry, and delete/re-download old DRM items created before `drmOfflineKeySetIdBase64` existed. |
| Device lifecycle | Medium | WorkManager and foreground service behavior must be validated under OS lifecycle conditions. | Run instrumentation/device tests for app kill, relaunch, background, and network changes. |
| Storage pressure | Medium | Cache eviction behavior depends on actual device storage and content size. | Test low-storage and long-form downloads with configured cache sizes. |
| Public API rollout | Low-Medium | New model/config/listener fields require publishing and consumer rebuild. | Publish `download-1.1.1`, document migration, and keep added model fields at the end. |
| Package/signing/license server | Medium-High | Widevine itself does not require a package name, but the EZDRM/backend license endpoint can reject requests by package name, signing certificate, app id, auth token, payload user id, or payload package id. | Use `adb logcat` to capture `DRM_LICENSE_URL_BUILT` and `DRM_PACKAGE_SIGNING_CHECK`, compare with AOL `com.sspt.aol`, and ask the DRM/backend team to whitelist the actual consuming app package, signing SHA-256, and entitlement payload values. |

## Format Behavior

| Format | Source Field | Quality Selector | Download Path |
| --- | --- | --- | --- |
| HLS | `hlsUrl` | Yes, when variants are available | Media3 HLS download request |
| DASH/MPD | `mpdUrl` | Yes, when tracks are available | Media3 DASH download request |
| MP4 | `mp4Url` | No | Media3 progressive download request |
| DRM DASH/MPD | `mpdUrl`, `drm = "1"`, `drmToken` | Yes, when tracks are available; falls back to original if qualities cannot be inferred | Media3 DASH request with acquired offline license and persisted `drmOfflineKeySetId` plus `drmOfflineKeySetIdBase64` |

## Verification Completed

- `sh gradlew :mtvdownloader:testDebugUnitTest`
- `sh gradlew :videosdk:testDebugUnitTest`
- `sh gradlew :mtvdownloader:compileDebugAndroidTestKotlin`
- `sh gradlew :app:compileDebugKotlin`
- `sh gradlew :app:assembleDebug`
- `sh gradlew :app:signingReport`
- `git diff --check`

## Release Checklist

- Run real-device validation for HLS, MPD, DRM MPD, and MP4.
- Confirm the Widevine license server supports persistent/offline licenses for downloadable DRM MPD content.
- Confirm the Widevine license server accepts the actual package name and signing certificate.
- Delete and re-download any old DRM MPD content created before `drmOfflineKeySetId` support.
- Publish `download-1.1.1`.
- Verify JitPack publishes `:mtvdownloader`.
- Confirm third-party apps call `DownloadSdk.init()` before using `DownloadButton`.
- Confirm paid-download apps use `DownloadMonetizationGate` plus backend/DRM enforcement.
