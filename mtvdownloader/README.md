# MTV Downloader SDK

Android downloader SDK for third-party apps that need offline downloads for HLS, DASH/MPD, and MP4 content. The SDK owns the Media3 download queue, foreground service, notification, Room download state, pause/resume/cancel actions, and a Compose download button.

## What It Supports

| Format | Field | Quality Selection | Notes |
| --- | --- | --- | --- |
| HLS | `hlsUrl` | Yes | `.m3u8` streams use Media3 HLS downloads. |
| DASH/MPD | `mpdUrl` | Yes | `.mpd` streams use Media3 DASH downloads. |
| MP4 | `mp4Url` | No | Progressive MP4 downloads start directly. |
| Widevine DRM MPD | `mpdUrl` + `drm = "1"` + `drmToken` | Yes | Used when no HLS URL is available; requires a non-empty license URL before enqueueing. |

## Install

Add JitPack to the third-party app repositories.

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

Add the SDK dependency.

```kotlin
dependencies {
    implementation("com.github.kamleshmultitv:mtvdownloader:download-1.1.0")
}
```

Replace `download-1.1.0` with the JitPack tag used by your release if you publish a newer version.

## Android Setup

Add permissions in the third-party app `AndroidManifest.xml`.

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

For Android 13 and above, request notification permission before starting downloads.

```kotlin
NotificationPermission.requestIfRequired(activity)
```

Initialize the SDK once in the app `Application` class.

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        DownloadSdk.init(
            application = this,
            config = DownloadSdkConfig(
                maxCacheBytes = 1024L * 1024L * 1024L,
                maxParallelDownloads = 1,
                minRetryCount = 3
            )
        )
    }
}
```

Register the `Application` class in the app manifest.

```xml
<application
    android:name=".App"
    ...>
</application>
```

The SDK manifest contributes its own `MediaDownloadService`, so the host app does not need to declare the service manually.

## Compose Setup

The ready-made UI is Compose based, so the third-party app must enable Compose if it uses `DownloadButton`.

```kotlin
android {
    buildFeatures {
        compose = true
    }
}
```

## Basic Usage

Create a `DownloadModel` for each content item and pass it to `DownloadButton`.

```kotlin
val downloadModel = DownloadModel(
    id = "movie_1001",
    seasonId = "season_01",
    title = "Sample Movie",
    seasonTitle = "Season 1",
    hlsUrl = "https://example.com/movie/master.m3u8",
    imageUrl = "https://example.com/movie/poster.jpg"
)

DownloadButton(
    contentItem = downloadModel
)
```

The SDK will:

- Detect the source format.
- Show quality selection for HLS and DASH when variants are available.
- Start MP4 downloads directly.
- Queue downloads with WorkManager.
- Run downloads with Media3 `DownloadManager`.
- Persist state in Room.
- Show foreground download notifications.
- Expose progress to the button UI.

## DownloadModel Fields

```kotlin
data class DownloadModel(
    val id: String? = null,
    val seasonId: String? = null,
    val hlsUrl: String? = null,
    val mpdUrl: String? = null,
    val drmToken: String? = null,
    val imageUrl: String? = null,
    val title: String? = null,
    val description: String? = null,
    val seasonTitle: String? = null,
    val seasonDescription: String? = null,
    val srt: String? = null,
    val drm: String? = null,
    val mp4Url: String? = null,
    val drmLicenseExpiresAt: Long? = null
)
```

Required:

- `id`: stable unique content ID. Do not change it for the same content.
- At least one playable URL: `hlsUrl`, `mpdUrl`, or `mp4Url`.

Recommended:

- `title`: shown in user messages and notification lookup.
- `imageUrl`: used by host apps that display downloaded content.
- `seasonId` and `seasonTitle`: useful for episodic content.

DRM:

- Set `drm = "1"` for DRM content.
- Provide `hlsUrl` when the same content is playable through HLS; the SDK downloads that HLS source first.
- Provide `mpdUrl` and a non-empty `drmToken` Widevine license URL when the content must be downloaded through DRM MPD.
- Provide `drmKeyId` when the host app has the Widevine `k_id`.
- Optionally provide `drmLicenseExpiresAt` when the host app knows the license expiry timestamp.
- Signed or extensionless DRM DASH URLs are accepted when `drm = "1"` and `drmToken` are present; manifest preparation then validates that the URL is a DASH MPD with Widevine init data.

For DRM-flagged content with HLS, the SDK downloads HLS first to match online playback and avoid unnecessary Widevine offline-license requests. For DRM MPD downloads, the SDK acquires a Widevine offline license before adding the Media3 download request. The resulting `keySetId` is attached to the `DownloadRequest` and persisted in Room as both `drmOfflineKeySetId` bytes and `drmOfflineKeySetIdBase64`.

## Format Examples

HLS:

```kotlin
DownloadModel(
    id = "hls_001",
    title = "HLS Video",
    hlsUrl = "https://example.com/video/master.m3u8"
)
```

DASH/MPD:

```kotlin
DownloadModel(
    id = "dash_001",
    title = "DASH Video",
    mpdUrl = "https://example.com/video/manifest.mpd"
)
```

MP4:

```kotlin
DownloadModel(
    id = "mp4_001",
    title = "MP4 Video",
    mp4Url = "https://example.com/video/file.mp4"
)
```

Widevine DRM DASH:

```kotlin
DownloadModel(
    id = "drm_001",
    title = "DRM Video",
    mpdUrl = "https://example.com/video/manifest.mpd",
    drm = "1",
    drmToken = "https://license.example.com/widevine"
)
```

## Use In A List

```kotlin
@Composable
fun ContentRow(
    item: ContentItem,
    onOpen: () -> Unit
) {
    val downloadModel = remember(item) {
        DownloadModel(
            id = item.id,
            seasonId = item.seasonId,
            title = item.title,
            seasonTitle = item.seasonTitle,
            hlsUrl = item.hlsUrl,
            mpdUrl = item.mpdUrl,
            mp4Url = item.mp4Url,
            drm = if (item.isDrm) "1" else null,
            drmToken = item.licenseUrl,
            imageUrl = item.thumbnailUrl
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.title.orEmpty(),
            modifier = Modifier
                .weight(1f)
                .clickable { onOpen() }
        )

        DownloadButton(contentItem = downloadModel)
    }
}
```

## Monetization Gate

Use `DownloadMonetizationGate` when the third-party app must show a rewarded ad, validate subscription, check entitlement, or call a backend before allowing downloads.

```kotlin
DownloadButton(
    contentItem = downloadModel,
    monetizationGate = DownloadMonetizationGate { context, content ->
        val isLoggedIn = accountManager.isLoggedIn()
        val hasDownloadAccess = entitlementRepository.canDownload(content.id.orEmpty())

        if (!isLoggedIn || !hasDownloadAccess) {
            return@DownloadMonetizationGate false
        }

        rewardedAdController.showAndWait(context)
    }
)
```

Return `true` only when the user is allowed to start the download. Return `false` to block the download. The SDK gate improves UX, but paid content should still be protected by backend authorization and DRM license rules.

## SDK Configuration

Configure download storage and retry policy during SDK initialization.

```kotlin
DownloadSdk.init(
    application = this,
    config = DownloadSdkConfig(
        maxCacheBytes = 1024L * 1024L * 1024L,
        maxParallelDownloads = 1,
        minRetryCount = 3
    )
)
```

Configuration fields:

| Field | Default | Meaning |
| --- | --- | --- |
| `maxCacheBytes` | `500 MB` | Maximum Media3 cache size before LRU eviction. |
| `maxParallelDownloads` | `1` | Number of parallel Media3 downloads. |
| `minRetryCount` | `3` | Media3 retry count before final failure. |

Call `DownloadSdk.init()` before starting downloads so cache configuration is applied before Media3 cache creation.

## Analytics Listener

Apps can receive download and monetization events.

```kotlin
DownloadSdk.setAnalyticsListener(
    object : DownloadAnalyticsListener {
        override fun onDownloadRequested(contentItem: DownloadModel) {
            analytics.track("download_requested", contentItem.id)
        }

        override fun onMonetizationAllowed(contentItem: DownloadModel) {
            analytics.track("download_allowed", contentItem.id)
        }

        override fun onDownloadEnqueued(
            contentId: String,
            sourceUrl: String,
            sourceType: String,
            qualityHeight: Int?,
            qualityBitrate: Int?
        ) {
            analytics.track("download_enqueued", contentId)
        }

        override fun onDownloadFailed(
            contentId: String,
            errorCode: String?,
            errorMessage: String?
        ) {
            analytics.track("download_failed", "$contentId:$errorCode")
        }
    }
)
```

Events available:

- `onDownloadRequested`
- `onMonetizationAllowed`
- `onMonetizationBlocked`
- `onDownloadEnqueued`
- `onDownloadStarted`
- `onDownloadProgress`
- `onDownloadPaused`
- `onDownloadResumed`
- `onDownloadCancelled`
- `onDownloadCompleted`
- `onDownloadFailed`

## Custom Quality Selector

HLS and DASH downloads can show quality options. If no custom selector is provided, the SDK uses its default dialog.

```kotlin
DownloadButton(
    contentItem = downloadModel,
    customQualitySelector = { qualities, onSelect, onDismiss ->
        ModalBottomSheet(onDismissRequest = onDismiss) {
            qualities.forEach { quality ->
                ListItem(
                    headlineContent = { Text(quality.label) },
                    supportingContent = { Text("${quality.bitrate / 1000} kbps") },
                    modifier = Modifier.clickable {
                        onSelect(quality)
                        onDismiss()
                    }
                )
            }
        }
    }
)
```

MP4 has no quality selector because it is a single progressive file.

## Custom Icons

Override icons for download states with `DownloadIconProvider`.

```kotlin
DownloadButton(
    contentItem = downloadModel,
    iconProvider = DownloadIconProvider { status ->
        when (status) {
            Constants.DOWNLOAD_STATUS_QUEUED -> R.drawable.ic_download_queued
            Constants.DOWNLOAD_STATUS_DOWNLOADING -> R.drawable.ic_downloading
            Constants.DOWNLOAD_STATUS_PAUSED -> R.drawable.ic_download_paused
            Constants.DOWNLOAD_STATUS_COMPLETED -> R.drawable.ic_download_done
            else -> R.drawable.ic_download
        }
    }
)
```

Status values are strings:

| Status | Meaning |
| --- | --- |
| `queued` | Waiting for download slot. |
| `downloading` | Actively downloading. |
| `paused` | User paused the download. |
| `completed` | Download finished. |
| `failed` | Download failed. |
| `removed` | Download was removed. |

## Receive Downloaded List Updates

`DownloadButton` can report the full persisted downloaded list whenever it changes.

```kotlin
var downloadedItems by remember {
    mutableStateOf(emptyList<DownloadedContentEntity>())
}

DownloadButton(
    contentItem = downloadModel,
    onDownloadedListUpdate = { list ->
        downloadedItems = list
    }
)
```

Host apps can use `DownloadedContentEntity.contentUrl`, `licenseUri`, `videoHeight`, `thumbnailUrl`, and `seasonImage` to build their offline playback screen.

The persisted entity also exposes operational fields:

| Field | Meaning |
| --- | --- |
| `queuedAt` | Timestamp used for queue ordering. |
| `failureCode` / `failureReason` | Final failure details after retries are exhausted. |
| `retryCount` / `maxRetryCount` | Retry visibility for UI and analytics. |
| `drmLicenseExpiresAt` | Host-provided DRM license expiry timestamp. |
| `drmLicenseLastRefreshAt` | Last known DRM license refresh timestamp. |
| `drmLicenseRefreshStatus` | Current DRM license refresh state. |
| `drmOfflineKeySetId` | Persisted Widevine offline license key set for DRM MPD playback. |
| `drmOfflineKeySetIdBase64` | Base64 `NO_WRAP` copy of the offline key set id for restore/renewal metadata. |
| `drmScheme` | DRM scheme, currently `widevine` for supported offline DRM. |
| `drmKeyId` | Host-provided Widevine key id metadata. |
| `contentMimeType` | MIME type used in the Media3 download request. |

## Manual Controls

The default button opens a menu for active downloads:

- Pause
- Resume
- Cancel

The host app usually does not need to call helper APIs directly. If a custom UI needs manual control, use:

```kotlin
ReelDownloadHelper.pauseDownload(context, contentId)
ReelDownloadHelper.resumeDownload(context, contentId)
ReelDownloadHelper.cancelDownload(context, contentId)
```

Resume uses the persisted download row, so it keeps previous progress, selected HLS/DASH stream keys, DRM license URL, and original source URL.

For DRM license renewal, rebuild a fresh license URL and call:

```kotlin
DownloadRepository
    .instance(context)
    .renewWidevineOfflineLicense(contentId, freshLicenseUri, expiresAt)
```

The repository prepares the MPD, requests a new Widevine offline license, replaces `drmOfflineKeySetId` and `drmOfflineKeySetIdBase64`, and updates expiry metadata.

## ProGuard And R8

The SDK ships consumer ProGuard rules for Media3 download classes, WorkManager workers, Kotlin metadata, Compose warnings, and SDK models. Third-party apps normally do not need extra rules.

If the host app minifies aggressively and serializes SDK models directly, keep the model package:

```proguard
-keep class com.app.mtvdownloader.model.** { *; }
```

## Common Integration Rules

- Use a stable unique `id` per content item.
- Do not reuse the same `id` for different videos.
- Provide only valid, reachable URLs.
- For HLS-backed DRM items, provide `hlsUrl`; for MPD-only DRM downloads, provide `mpdUrl`, `drm = "1"`, and a valid license URL in `drmToken`.
- DRM MPD downloads created before offline `keySetId` support should be downloaded again to support true offline playback.
- Request Android 13+ notification permission before starting downloads.
- Test HLS, DASH, MP4, and DRM behavior on a real Android device.
- Test app kill, network switch, pause, resume, cancel, and low-storage behavior before release.

## Debug Logs

Use logcat with the downloader tags when a download starts and then returns to the idle/failed state:

```bash
adb logcat -v time MtvDrmOffline:I DownloadWorker:D ReelDownloadHelper:D OfflineDrmLicenseUtil:D DownloadUtil:D ExoPlayerImplInternal:E WorkManager:E '*:S'
```

For DRM MPD failures, look for stage names such as `DRM_MANIFEST_PREPARE_FAILED`, `DRM_INIT_DATA_MISSING`, `DRM_OFFLINE_LICENSE_FAILED`, or `DRM_KEYSETID_EMPTY`. HTTP `401` means the Widevine license URL, token, encoded payload, entitlement, offline-license policy, package name, or signing certificate is being rejected by the license server.

## Troubleshooting

| Problem | Check |
| --- | --- |
| Button shows unsupported source | Confirm `id` and one of `hlsUrl`, `mpdUrl`, or `mp4Url` are non-empty and have a supported extension. |
| DRM download does not start | If HLS is available, confirm `hlsUrl` is present and reachable. For MPD-only DRM, confirm `drm = "1"`, `mpdUrl` is non-empty, `drmToken` is a valid Widevine license URL that returns persistent/offline licenses, and the app package/signing is allowed by the license backend. |
| DRM download plays online but not offline | Delete and re-download old DRM content so the SDK can persist `drmOfflineKeySetId`. Also confirm the license server allows offline playback duration. |
| Notification does not appear | Request `POST_NOTIFICATIONS` on Android 13+ and call `DownloadSdk.init(application)`. |
| Quality selector is empty | Confirm the HLS/MPD manifest exposes video variants/tracks and is reachable by the app. |
| MP4 has no quality options | Expected. MP4 downloads as the original progressive file. |
| Download restarts or duplicates | Use the same stable `id` for the same content and avoid changing IDs across recompositions. |

## Minimal Third-Party Checklist

1. Add JitPack.
2. Add the `mtvdownloader` dependency.
3. Add permissions.
4. Call `DownloadSdk.init()` from `Application`.
5. Request notification permission on Android 13+.
6. Create a valid `DownloadModel`.
7. Render `DownloadButton(contentItem = model)`.
8. For paid content, pass a `DownloadMonetizationGate`.
9. Test HLS, MPD, MP4, and DRM on device.
