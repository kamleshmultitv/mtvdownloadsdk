# Main Application Full Change Guide

This document explains every app-side change needed to use the MTV Downloader SDK with the VideoPlayer SDK in a real main application.

Use this when your main app already has VideoPlayer SDK code implemented in multiple places. The main app does not need a different player. It must pass the correct models, cache objects, DRM license URL, and offline keySetId into the existing player flow.

## What Changed In The Sample App

The sample app was updated in these areas:

| Area | Sample File | What Changed |
| --- | --- | --- |
| Downloader SDK dependency | `app/build.gradle.kts` | App uses the local `:mtvdownloader` module for development. Main app should use the JitPack dependency. |
| App package for DRM testing | `app/build.gradle.kts` | `applicationId = "com.sspt.aol"` was used for AOL license-server compatibility testing. |
| App startup | `AppClass.kt` | Calls `DownloadSdk.init()` once with cache size, parallel download count, retry count, and analytics listener. |
| Runtime permission | `MainActivity.kt` | Calls `NotificationPermission.requestIfRequired(this)` for Android 13+. |
| Manifest | `AndroidManifest.xml` | Registers `AppClass`, enables PiP on `MainActivity`, keeps internet permission. |
| Online player mapping | `FileUtils.buildPlayerContentList()` | Builds VideoPlayer SDK `PlayerModel` without forcing `drm = "1"` for normal API-list playback. |
| Download mapping | `FileUtils.buildDownloadContentList()` | Builds downloader SDK `DownloadModel` with HLS, MPD, MP4, DRM token, KID, scheme, and license expiry. |
| DRM license URL | `FileUtils.getDrmToken()` | Builds AOL/EZDRM Widevine license URL with `authorization` and Base64 payload. |
| Download button | `ContentCard.kt` | Adds `DownloadButton`, custom quality selector, custom icons, monetization gate, and downloaded-list callback. |
| Monetization example | `SampleDownloadMonetization.kt` | Adds app-side gate before download starts. |
| Downloaded list | `DownloadedContentList.kt` | Displays persisted `DownloadedContentEntity` rows. |
| Offline playback | `DownloadPlayer.kt` | Plays downloaded content through the same `MtvVideoPlayerSdk`. |
| Offline PlayerModel mapping | `FileUtils.buildContentListFromDownloaded()` | Passes downloaded URL, cache, `DownloadManager`, and DRM offline keySetId into the player. |
| Secrets | `.gitignore`, `app/.gitignore` | `google-services.json` is ignored and must not be pushed. |

## Main App Dependency Setup

In the real main app, add JitPack in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Add the downloader SDK dependency in the app module:

```kotlin
dependencies {
    implementation("com.github.kamleshmultitv:mtvdownloader:download-1.1.0")
}
```

Keep your existing VideoPlayer SDK dependency. If your main app has VideoPlayer SDK source code copied directly, apply the player-side changes from:

```text
videoplayer-sdk-handoff.md
```

## Files Not To Push

Do not commit these:

```text
app/google-services.json
google-services.json
local.properties
app/release/
*/build/
.idea/
```

Keep `google-services.json` local or provide it from your secure CI/secret process.

## Android Manifest Changes

The app must have internet permission:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

The downloader SDK manifest contributes:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

If manifest merge does not include them in your main app, add them manually.

Register your `Application` class:

```xml
<application
    android:name=".AppClass"
    ...>
</application>
```

If your app uses player PiP, keep these on the player activity:

```xml
android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"
android:resizeableActivity="true"
android:supportsPictureInPicture="true"
```

## Application Class Changes

Initialize downloader once from `Application.onCreate()` before any download UI is shown:

```kotlin
class AppClass : Application() {
    override fun onCreate() {
        super.onCreate()

        DownloadSdk.init(
            application = this,
            config = DownloadSdkConfig(
                maxCacheBytes = 1024L * 1024L * 1024L,
                maxParallelDownloads = 1,
                minRetryCount = 3
            ),
            analyticsListener = object : DownloadAnalyticsListener {
                override fun onDownloadRequested(contentItem: DownloadModel) {
                    // Send analytics if needed.
                }

                override fun onDownloadCompleted(contentId: String) {
                    // Send analytics if needed.
                }

                override fun onDownloadFailed(
                    contentId: String,
                    errorCode: String?,
                    errorMessage: String?
                ) {
                    // Send analytics/support logs if needed.
                }
            }
        )
    }
}
```

Use `maxParallelDownloads = 1` if you want one active download at a time. This matches the pause/resume behavior already fixed in the downloader.

## Activity Changes

Request notification permission on Android 13+ before starting downloads:

```kotlin
NotificationPermission.requestIfRequired(this)
```

If your activity implements PiP:

```kotlin
override fun onPipRequested(isPipActive: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        enterPictureInPictureMode(params)
    }
}
```

## DRM Constants In Main App

The main app needs equivalent values:

```kotlin
const val DRM_TYPE = "widevine"
const val TOKEN = "<runtime auth token>"
const val DRM_USER_ID = ""
const val DRM_PACKAGE_ID = ""
const val DRM_AUTHORIZATION_SOURCE = "stored"
```

Do not hardcode production tokens in source code. In production, provide them from login/session storage or backend APIs.

`DRM_AUTHORIZATION_SOURCE` supports:

- `stored`: send the stored token exactly as provided.
- `jwt`: parse and send the JWT candidate.
- `claim_token`: parse and send the nested `token` claim.

The current AOL reference behavior is `stored`.

## DRM License URL Builder

The app must build the Widevine license URL for DRM content.

Shape:

```text
<DRM_LICENSE_URL>?user_id=<deviceGuid>&type=widevine&authorization=<authorization>&payload=<base64Payload>
```

Payload:

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

Rules:

- `content_id` must match the backend content id.
- `k_id` must match the Widevine KID for MPD DRM.
- `user_id` must be the AOL/backend licensed user id.
- `package_id` must be the entitled package/rental/subscription package id.
- `licence_duration` should use API `downloadExpiry`; fallback can be 30 days.
- `download = "1"` for offline download license.
- `can_renew = true` for offline license renewal support.
- Payload must be Base64 encoded without line wrapping.
- Logs must not print token or full license URL.

Safe log fields:

```text
contentId
mode
kIdPresent
payloadUserIdSource
packageIdSource
authorizationSource
contentType
durationSec
download
canRenew
licenseUri scheme/host/path/queryKeys
```

## Online PlayerModel Mapping

In the main app API-list playback path, pass both HLS and MPD when available:

```kotlin
PlayerModel(
    id = content.id,
    hlsUrl = content.hlsUrl,
    mpdUrl = content.mpdUrl,
    videoUrl = content.mp4Url,
    liveUrl = null,
    isLive = false,
    drmToken = getDrmTokenOrNull(context, content, offline = true),
    imageUrl = content.thumbnail,
    title = content.title,
    episodeTitle = content.title,
    description = content.description,
    srt = content.subtitleUrl
)
```

Important: do not set `drm = "1"` for normal API-list playback if online playback should continue through HLS. In our fix, playback started working again because the app stopped forcing DRM mode for the list player.

Only set this for an explicit MPD DRM playback path:

```kotlin
drm = "1"
drmToken = validWidevineLicenseUrl
```

If your main app already has player code in many places, check every place that creates `PlayerModel`. Do not set `drm = "1"` globally just because the API says the item is DRM. Set it only where the player should choose MPD DRM.

## DownloadModel Mapping

Every row/card that supports download must build `DownloadModel` with stable content id and all available source URLs:

```kotlin
DownloadModel(
    id = content.id.orEmpty(),
    seasonId = content.seasonId.orEmpty(),
    hlsUrl = content.hlsUrl,
    mpdUrl = content.mpdUrl,
    mp4Url = content.mp4Url,
    drm = content.drm.takeIf { content.drm == "1" },
    drmToken = getDrmTokenOrNull(context, content, offline = true),
    drmLicenseExpiresAt = calculatedExpiryMillis,
    drmKeyId = content.kId,
    drmScheme = "widevine",
    imageUrl = content.thumbnail.orEmpty(),
    title = content.title.orEmpty(),
    description = content.description.orEmpty(),
    srt = content.subtitleUrl.orEmpty()
)
```

Rules:

- `id` must be stable and unique for the same content.
- Do not change `id` between pause and resume.
- Do not reuse one `id` for different videos.
- Pass `hlsUrl` when available.
- Pass `mpdUrl` for MPD/DASH content.
- Pass `mp4Url` for progressive MP4 content.
- For DRM MPD-only content, pass `drm = "1"` and non-empty `drmToken`.
- For DRM content with HLS available, the downloader now downloads HLS first and skips offline DRM license acquisition.

Downloader source selection:

| Content | Downloader Behavior |
| --- | --- |
| DRM + HLS | Downloads HLS first; no Widevine offline license request. |
| DRM + MPD only | Requests Widevine offline license, saves keySetId, then downloads DASH. |
| Non-DRM HLS | Downloads HLS with quality selection. |
| Non-DRM MPD | Downloads MPD with quality selection. |
| MP4 | Downloads progressive MP4 directly. |

## Download Button Integration

Add `DownloadButton` next to content rows/cards:

```kotlin
DownloadButton(
    contentItem = downloadModel,
    customQualitySelector = { qualities, onSelect, onDismiss ->
        // Use your app bottom sheet/dialog here.
    },
    iconProvider = DownloadIconProvider { status ->
        when (status) {
            Constants.DOWNLOAD_STATUS_PAUSED -> R.drawable.ic_download_pause
            Constants.DOWNLOAD_STATUS_QUEUED -> R.drawable.ic_download_queue
            Constants.DOWNLOAD_STATUS_DOWNLOADING -> R.drawable.ic_downloading
            Constants.DOWNLOAD_STATUS_COMPLETED -> R.drawable.ic_download_done
            else -> R.drawable.ic_download
        }
    },
    monetizationGate = appDownloadMonetizationGate,
    onDownloadedListUpdate = { list ->
        // Store/update downloaded list in app state.
    }
)
```

The SDK button handles:

- Start download.
- Quality selection.
- Pause.
- Resume.
- Cancel.
- Active download popup with Pause and Cancel.
- Progress.
- Completed state.

## Monetization Gate

Create app-side policy before download starts:

```kotlin
val appDownloadMonetizationGate = DownloadMonetizationGate { context, content ->
    val isLoggedIn = accountManager.isLoggedIn()
    val hasEntitlement = entitlementRepository.canDownload(content.id.orEmpty())

    if (!isLoggedIn || !hasEntitlement) {
        return@DownloadMonetizationGate false
    }

    rewardedAdController.showAndWait(context)
}
```

The gate controls UI/download start only. Paid or DRM content must still be protected by backend entitlement and the DRM license server.

## Downloaded List Screen

Keep the downloaded list from:

```kotlin
onDownloadedListUpdate = { list ->
    downloadedItems.clear()
    downloadedItems.addAll(list)
}
```

Display each `DownloadedContentEntity` using `contentId` as a stable key:

```kotlin
items(downloadedItems, key = { it.contentId }) { item ->
    DownloadedContentRow(
        item = item,
        onItemClick = { openDownloadedPlayer(item) }
    )
}
```

Important fields available in `DownloadedContentEntity`:

- `contentId`
- `contentUrl`
- `contentMimeType`
- `licenseUri`
- `downloadProgress`
- `downloadStatus`
- `videoHeight`
- `videoBitrate`
- `drmOfflineKeySetId`
- `drmOfflineKeySetIdBase64`
- `drmLicenseExpiresAt`
- `drmKeyId`
- `drmScheme`

## Offline Playback Mapping

When the user opens a downloaded item, build VideoPlayer SDK `PlayerModel` from `DownloadedContentEntity`.

```kotlin
PlayerModel(
    id = entity.contentId,
    hlsUrl = hlsUrlIfDownloadedContentIsHls,
    mpdUrl = mpdUrlIfDownloadedContentIsDash,
    videoUrl = mp4UrlIfDownloadedContentIsMp4,
    drm = if (entity.licenseUri.isNotBlank()) "1" else null,
    drmToken = entity.licenseUri.takeIf { it.isNotBlank() },
    imageUrl = entity.thumbnailUrl ?: entity.seasonImage,
    title = entity.title,
    episodeTitle = entity.title,
    seasonTitle = entity.seasonName,
    selectedVideoQuality = entity.videoHeight ?: 1080,
    isLive = false,
    downloadManager = DownloadUtil.getDownloadManager(context),
    downloadCache = DownloadUtil.getDownloadCache(context),
    drmOfflineKeySetId = entity.drmOfflineKeySetId,
    drmOfflineKeySetIdBase64 = entity.drmOfflineKeySetIdBase64
)
```

Source split rules:

- If `contentMimeType == "application/x-mpegURL"` or URL ends with `.m3u8`, set `hlsUrl`.
- If `contentMimeType == "application/dash+xml"` or URL ends with `.mpd`, set `mpdUrl`.
- If `contentMimeType == "video/mp4"` or URL ends with `.mp4`/`.m4v`, set `videoUrl`.

Do not build a direct file path player. Use the same `MtvVideoPlayerSdk` and pass `DownloadUtil.getDownloadManager(context)` plus `DownloadUtil.getDownloadCache(context)`.

## VideoPlayer SDK Compatibility Requirement

If your main app uses a published/new VideoPlayer SDK, confirm it supports:

```kotlin
val downloadManager: DownloadManager?
val downloadCache: SimpleCache?
val cacheFactory: CacheDataSource.Factory?
val drmOfflineKeySetId: ByteArray?
val drmOfflineKeySetIdBase64: String?
```

If your main app has old VideoPlayer SDK source copied into the app, port these player-side behaviors:

- `PlaybackSourceResolver`: choose MPD only when `drm == "1"`; otherwise prefer HLS.
- `PlayerFactory`: check `DownloadManager.downloadIndex` for completed download by content id.
- `PlayerFactory`: use app-provided Media3 download cache for offline media.
- `PlayerFactory`: for offline DASH DRM, restore keySetId from `DownloadRequest.keySetId`, raw `drmOfflineKeySetId`, or Base64 field.
- `PlayerFactory`: build offline DRM config with `setKeySetId(keySetId)`.
- `PlayerFactory`: do not use online license URL for true offline DRM playback.

See:

```text
videoplayer-sdk-handoff.md
```

## Pause/Resume Rules

The resume issue is fixed in the downloader SDK, but the main app must not break identity.

Main app rules:

- Use the same `DownloadModel.id` every time for the same content.
- Do not generate random ids in Composables.
- Do not change source URLs between pause/resume unless the content really changed.
- Keep `seasonId`, `drmToken`, `drmKeyId`, and original source fields populated when rebuilding the row.
- Render one `DownloadButton` per content id.

Expected behavior:

- Pause keeps progress.
- Resume starts from the same position.
- If another item starts, the previous active item pauses cleanly.
- Resuming the paused item does not restart from 0.

## Package, Signing, And DRM Backend

For AOL DRM testing, the sample used:

```kotlin
applicationId = "com.sspt.aol"
```

Your main app must confirm with backend/license team:

- Actual application id.
- Debug SHA-1/SHA-256 signing certificate.
- Release SHA-1/SHA-256 signing certificate.
- `google-services.json` package entry.
- User id in DRM payload.
- Package id in DRM payload.
- Content id.
- KID.
- Offline download entitlement.
- Token expiry and accepted token format.

Command:

```bash
./gradlew :app:signingReport
```

HTTP `401` during DRM MPD download means license server rejection. It is usually package/signing/token/user/package entitlement/offline-policy related.

## Logcat Commands

Use this when download starts and then fails or returns to idle:

```bash
adb logcat -v time SampleDrmLicense:I MtvDrmOffline:I OfflineDrmLicenseUtil:D DownloadWorker:D ReelDownloadHelper:D DownloadUtil:D WorkManager:E ExoPlayerImplInternal:E '*:S'
```

Use this when offline playback fails:

```bash
adb logcat -v time SdkLogger:D ExoPlayerImplInternal:E MtvDrmOffline:I SampleDrmLicense:I '*:S'
```

Important logs:

| Log | Meaning |
| --- | --- |
| `DRM_LICENSE_URL_BUILT` | Main app built license URL. |
| `DRM_PACKAGE_SIGNING` | Downloader logged package/signing summary. |
| `DRM_MANIFEST_PREPARE_START` | Downloader started reading MPD DRM init data. |
| `DRM_OFFLINE_LICENSE_START` | Downloader started Widevine offline license request. |
| `DRM_OFFLINE_LICENSE_SUCCESS` | License server returned offline keySetId. |
| `DRM_OFFLINE_LICENSE_FAILED httpCode=401` | License server rejected request. |
| `DRM_OFFLINE_PLAYBACK_PREPARE` | Player restored offline DRM keySetId. |
| `missing_keySetId` | Offline DRM download did not persist keySetId correctly. |

## Main App Migration Checklist

Apply these in order:

1. Add JitPack repository.
2. Add downloader SDK dependency.
3. Keep/update VideoPlayer SDK dependency or port player-side source changes.
4. Add/confirm manifest permissions.
5. Register app `Application` class.
6. Call `DownloadSdk.init()` in `Application.onCreate()`.
7. Request notification permission in main/player activity.
8. Add DRM license URL builder.
9. Update every online `PlayerModel` mapping.
10. Update every download row/card to build `DownloadModel`.
11. Add `DownloadButton` and monetization gate.
12. Store/display `DownloadedContentEntity` list.
13. Build offline `PlayerModel` from `DownloadedContentEntity`.
14. Pass `downloadManager`, `downloadCache`, and DRM keySetId fields into VideoPlayer SDK.
15. Test HLS, MPD, MP4, DRM HLS fallback, DRM MPD-only, pause/resume, app kill, and offline playback.

## Common Main App Mistakes

| Mistake | Result | Fix |
| --- | --- | --- |
| Force `drm = "1"` for all DRM API rows | Online HLS playback may switch to MPD and fail with license 401. | Only set `drm = "1"` when you intentionally want MPD DRM playback. |
| Do not pass `downloadManager`/`downloadCache` to player | Downloaded content may try network playback or fail. | Pass `DownloadUtil.getDownloadManager(context)` and `DownloadUtil.getDownloadCache(context)`. |
| Do not pass `drmOfflineKeySetIdBase64` | Offline DRM MPD cannot restore license after restart. | Persist and pass keySetId fields from `DownloadedContentEntity`. |
| Change content id on recomposition | Resume starts from 0 or creates a duplicate download. | Use stable backend content id. |
| Commit `google-services.json` | Secret/config leak. | Keep it ignored and provide locally/CI. |
| MPD-only DRM returns 401 | Download never starts. | Fix backend entitlement/package/signing/token/license payload. |
| Use another local player for downloads | DRM/cache path breaks. | Use the same VideoPlayer SDK with offline fields. |

## Manual Acceptance Tests

Before release, test:

- Online HLS playback.
- Online MPD playback.
- Online MPD DRM playback with valid license.
- HLS download with quality selection.
- MPD download with quality selection.
- MP4 download.
- DRM item with HLS URL downloads successfully.
- DRM MPD-only item reaches `DRM_OFFLINE_LICENSE_SUCCESS`.
- Pause and resume same item; progress does not reset.
- Start second item while first is paused; resume first item.
- Cancel active download.
- Kill app during download and reopen.
- Play downloaded HLS in airplane mode.
- Play downloaded MP4 in airplane mode.
- Play downloaded DRM MPD in airplane mode after keySetId is saved.
- Android 13+ notification permission flow.
- Notification progress text.
- UI color difference between active progress and completed download circle.
