# MTV Downloader SDK Changelog

## download-1.1.0 - 2026-08-19

- Fixed pause/resume so paused downloads keep cached bytes, progress, source URL, DRM license URL, and selected stream keys.
- Resume now reuses the persisted download row instead of overwriting Room progress with `0`.
- Pause now uses Media3 stop reasons instead of `removeDownload()`, so cached partial downloads are preserved.
- Added HLS, DASH/MPD, and MP4 source resolution through one SDK path.
- Added `DownloadModel.mp4Url` for progressive MP4 downloads.
- Added `DownloadModel.drmLicenseExpiresAt` and persisted DRM license state fields.
- Added Widevine offline license acquisition for DRM MPD downloads and persisted `drmOfflineKeySetId`.
- Added Base64 `drmOfflineKeySetIdBase64`, DRM scheme/key id, and content MIME metadata with Room v5 migration.
- Added stage-based DRM logs for manifest prepare, init-data detection, offline license acquisition, package/signing validation, segment download, and offline playback.
- Added extensionless DRM DASH routing when API DRM metadata and a Widevine license URL are present.
- Added offline DRM license renewal and release paths.
- Updated sample app DRM payload generation to use AOL-compatible `download = "1"`, `can_renew = true`, `k_id`, `licence_duration`, and Base64 `NO_WRAP`.
- Updated offline playback to restore `keySetId` from the Media3 request, Room bytes, or Base64 metadata without falling back to the online license URL.
- Added Room v4 migration for offline DRM `keySetId` storage.
- Added `DownloadSdkConfig` for cache size, parallel download count, and retry count.
- Added `DownloadAnalyticsListener` for request, monetization, enqueue, progress, completion, cancellation, pause/resume, and failure events.
- Added Room v3 migration for queue timestamp, failure details, retry visibility, and DRM license state.
- Added queue ordering by `queuedAt`.
- Added persisted failure code/reason when workers fail.
- Added sample app monetization gate and analytics setup.
- Added downloader infrastructure instrumentation test and config unit tests.

## download-1.0.9

- Previous downloader release.
