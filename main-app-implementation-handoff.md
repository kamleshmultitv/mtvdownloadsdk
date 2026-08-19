# Main App Implementation Handoff

This document lists the app-side work needed to integrate the MTV downloader SDK and VideoPlayer SDK together. It is intended for the main app/sample app codebase.

## Goal

The app must map API content into:

- `PlayerModel` for online and offline playback.
- `DownloadModel` for MTV downloader downloads.

The app is also responsible for building the DRM license URL used by both online DRM playback and offline Widevine license acquisition.

## Online PlayerModel Mapping

For normal API-list playback, pass both HLS and MPD URLs when available:

```kotlin
PlayerModel(
    id = content.id,
    hlsUrl = content.hlsUrl,
    mpdUrl = content.url,
    videoUrl = mp4Url,
    liveUrl = null,
    isLive = false,
    drmToken = getDrmTokenOrNull(context, content, offline = true)
)
```

Important behavior from the sample app:

- Do not set `drm = "1"` for normal API-list playback if you want the player to keep using HLS.
- If `drm = "1"` is set, the VideoPlayer SDK will choose MPD DRM and the license URL must work.
- This is why online playback was restored by passing `drmToken` but not forcing `drm = "1"` for the API list.

For a manual/override MPD DRM playback test, set both:

```kotlin
drm = "1"
drmToken = validWidevineLicenseUrl
```

## DownloadModel Mapping

For download requests, pass all available URLs and DRM metadata:

```kotlin
DownloadModel(
    id = content.id.orEmpty(),
    seasonId = content.seasonId.orEmpty(),
    hlsUrl = content.hlsUrl,
    mpdUrl = content.url,
    mp4Url = mp4Url,
    drm = content.drm.takeIf { content.drm == "1" },
    drmToken = getDrmTokenOrNull(context, content, offline = true),
    drmLicenseExpiresAt = calculatedExpiryMillis,
    drmKeyId = content.kId,
    drmScheme = "widevine",
    imageUrl = thumbnail,
    title = content.title.orEmpty(),
    description = content.shortDesc.orEmpty(),
    srt = subtitleUrl
)
```

The downloader resolves content like this:

1. DRM item with HLS URL: download HLS first, without offline DRM license acquisition.
2. DRM item with only MPD URL: acquire Widevine offline license first, then download DASH.
3. Non-DRM item: prefer HLS, then MPD, then MP4.

If an MPD-only DRM item fails with HTTP 401 during `DRM_OFFLINE_LICENSE_START`, the failure is in the license entitlement/package/signing/token path, not in segment downloading.

## DRM License URL Builder

The app must build the AOL/EZDRM license URL in this shape:

```text
<DRM_LICENSE_URL>?user_id=<deviceGuid>&type=widevine&authorization=<authorization>&payload=<base64Payload>
```

If `DRM_LICENSE_URL` already ends with `?` or `&`, append directly instead of adding another separator.

Payload must be JSON encoded with Base64 `NO_WRAP`:

```json
{
  "content_id": "<content id>",
  "k_id": "<kid>",
  "user_id": "<licensed user id>",
  "package_id": "<package id>",
  "licence_duration": 2592000,
  "security_level": "0",
  "rental_duration": "0",
  "content_type": "1",
  "download": "1",
  "can_renew": true
}
```

Use `download = "1"` and `can_renew = true` for offline downloads. Use the actual paid/free value for `content_type`.

The sample app exposes these configuration constants:

```kotlin
const val TOKEN = "<stored auth token>"
const val DRM_USER_ID = ""
const val DRM_PACKAGE_ID = ""
const val DRM_AUTHORIZATION_SOURCE = "stored"
```

`DRM_AUTHORIZATION_SOURCE` supports:

- `stored`: use `TOKEN` as-is.
- `jwt`: parse and use the JWT candidate from `TOKEN`.
- `claim_token`: parse and use the nested `token` claim from `TOKEN`.

Keep logs redacted. Log token source, payload source, package source, content id, KID presence, and license URL host/path/query keys only.

## Offline Playback Mapping

When opening a downloaded item, build `PlayerModel` from the downloaded database entity:

```kotlin
PlayerModel(
    id = entity.contentId,
    hlsUrl = hlsUrlIfDownloadedContentIsHls,
    mpdUrl = mpdUrlIfDownloadedContentIsDash,
    videoUrl = mp4UrlIfDownloadedContentIsMp4,
    drm = if (entity.licenseUri.isNotBlank()) "1" else null,
    drmToken = entity.licenseUri.takeIf { it.isNotBlank() },
    downloadManager = DownloadUtil.getDownloadManager(context),
    downloadCache = DownloadUtil.getDownloadCache(context),
    drmOfflineKeySetId = entity.drmOfflineKeySetId,
    drmOfflineKeySetIdBase64 = entity.drmOfflineKeySetIdBase64,
    isLive = false
)
```

Set `drm = "1"` for downloaded DRM playback only when the downloaded entity has a license URI/offline DRM data. Do not set it for downloaded HLS or MP4 content.

## Package, Signing, And Backend Validation

The sample app currently uses:

```kotlin
applicationId = "com.sspt.aol"
```

The license backend may validate package name, signing certificate, user entitlement, content id, package id, KID, and token expiry. For the main app, confirm these with the backend/license provider:

- Real application id used for the installed build.
- SHA-1/SHA-256 signing certificate for debug and release builds.
- `google-services.json` package entry matches the app id.
- User id and package id in DRM payload are entitled for the content.
- Authorization token is not expired and is accepted for offline download.
- `content_id` and `k_id` match the MPD DRM metadata.

Use this command to capture app signing details:

```bash
./gradlew :app:signingReport
```

## Logcat For Manual Debugging

Use this filtered logcat command while tapping download/play:

```bash
adb logcat -v time SampleDrmLicense:I MtvDrmOffline:I OfflineDrmLicenseUtil:I DownloadWorker:D ReelDownloadHelper:D DownloadUtil:D ExoPlayerImplInternal:E '*:S'
```

Important log events:

- `DRM_LICENSE_URL_BUILT`: app built the license URL.
- `DRM_PACKAGE_SIGNING`: downloader logged installed package/signing summary.
- `DRM_MANIFEST_PREPARE_START`: downloader started reading DASH DRM init data.
- `DRM_OFFLINE_LICENSE_START`: downloader is requesting Widevine offline license.
- `DRM_OFFLINE_LICENSE_SUCCESS`: downloader received and saved keySetId.
- `DRM_OFFLINE_LICENSE_FAILED httpCode=401`: backend rejected license request.
- `DRM_OFFLINE_PLAYBACK_PREPARE`: player restored offline keySetId.

## Acceptance Checklist

- Online content still plays from the API list.
- HLS-backed DRM-flagged content downloads using HLS and does not fail on offline license acquisition.
- MP4 content downloads and plays from cache.
- MPD-only DRM content logs `DRM_OFFLINE_LICENSE_SUCCESS` before segment download starts.
- Downloaded DRM MPD persists `drmOfflineKeySetIdBase64` in the database.
- Downloaded DRM MPD plays after app restart.
- Pause/resume keeps progress and does not restart from 0.
- When one item resumes and another pauses, each item keeps its own progress state.
- Notification shows download progress text and app UI shows different colors for in-progress and downloaded states.

## Files Mirrored In This Repo

The equivalent implementation in this repo is currently in:

- `app/src/main/java/com/app/sample/utils/FileUtils.kt`
- `app/src/main/java/com/app/sample/extra/ApiConstant.kt`
- `app/build.gradle.kts`
- `mtvdownloader/src/main/java/com/app/mtvdownloader/utils/DownloadSourceResolver.kt`
- `mtvdownloader/src/main/java/com/app/mtvdownloader/utils/OfflineDrmLicenseUtil.kt`
