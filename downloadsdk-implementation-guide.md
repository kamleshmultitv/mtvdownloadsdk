# Download SDK Implementation Guide

This guide is for a third-party Android app that wants to add MTV Downloader SDK support for HLS, MPD/DASH, MP4, and DRM download flows.

The SDK handles queueing, Media3 downloads, foreground notifications, progress, pause, resume, cancel, quality selection, Room state, and downloaded-list callbacks. The host app is responsible for passing correct content metadata and playing downloaded content with its player.

## Requirements

Use these app settings for the current SDK release:

| Requirement | Value |
| --- | --- |
| Android minSdk | `24` or higher |
| Recommended compileSdk | `36` |
| JDK | `17` |
| UI | Compose for `DownloadButton` |
| Downloader artifact | `download-1.1.1` |

If the app is not Compose-first, it can still use the SDK by hosting `DownloadButton` inside `ComposeView`.

## Quick Start

Minimum implementation order:

1. Add `maven("https://jitpack.io")`.
2. Add `implementation("com.github.kamleshmultitv:mtvdownloader:download-1.1.1")`.
3. Enable Compose if using `DownloadButton`.
4. Call `DownloadSdk.init()` in `Application.onCreate()`.
5. Request notification permission from the activity.
6. Build a stable `DownloadModel`.
7. Render `DownloadButton(contentItem = model)`.

Minimal button:

```kotlin
DownloadButton(
    contentItem = DownloadModel(
        id = content.id,
        title = content.title,
        hlsUrl = content.hlsUrl,
        mpdUrl = content.mpdUrl,
        mp4Url = content.mp4Url,
        imageUrl = content.thumbnailUrl
    )
)
```

Files normally changed in the app:

| File | Change |
| --- | --- |
| `settings.gradle.kts` | Add JitPack. |
| App `build.gradle.kts` | Add downloader dependency and enable Compose if needed. |
| `AndroidManifest.xml` | Add internet permission and register `Application`. |
| `Application` class | Call `DownloadSdk.init()`. |
| Main/player activity | Request notification permission. |
| Content row/card | Build `DownloadModel` and render `DownloadButton`. |
| Downloads screen | Read `DownloadedContentEntity` list and open downloaded playback. |

## 1. Add Repository

Add JitPack in the app project's `settings.gradle.kts`.

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

## 2. Add Dependency

Add the SDK in the app module `build.gradle.kts`.

```kotlin
dependencies {
    implementation("com.github.kamleshmultitv:mtvdownloader:download-1.1.1")
}
```

If Gradle cannot resolve this coordinate, open the JitPack page for the repository and use the exact dependency shown there. For multi-module resolution it may be:

```kotlin
implementation("com.github.kamleshmultitv.mtvdownloadsdk:mtvdownloader:download-1.1.1")
```

## 3. Enable Compose

`DownloadButton` is a Compose UI component. Enable Compose in the app module if it is not already enabled.

```kotlin
android {
    buildFeatures {
        compose = true
    }
}
```

If the app is XML/View based, host the button inside a `ComposeView`.

```kotlin
composeView.setContent {
    DownloadButton(contentItem = downloadModel)
}
```

## 4. Add Manifest Setup

The app must have internet permission.

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

The SDK manifest contributes foreground-service and notification permissions plus `MediaDownloadService`. If manifest merge is disabled or restricted in the app, add these manually:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Register the app `Application` class if it is not already registered.

```xml
<application
    android:name=".AppClass"
    ...>
</application>
```

## 5. Initialize SDK

Initialize once in `Application.onCreate()`.

```kotlin
import android.app.Application
import com.app.mtvdownloader.init.DownloadSdk
import com.app.mtvdownloader.model.DownloadAnalyticsListener
import com.app.mtvdownloader.model.DownloadModel
import com.app.mtvdownloader.model.DownloadSdkConfig

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
                override fun onDownloadCompleted(contentId: String) {
                    // Optional analytics
                }

                override fun onDownloadFailed(
                    contentId: String,
                    errorCode: String?,
                    errorMessage: String?
                ) {
                    // Optional analytics/support logging
                }
            }
        )
    }
}
```

Recommended defaults:

| Config | Value | Reason |
| --- | --- | --- |
| `maxCacheBytes` | `1024L * 1024L * 1024L` | 1 GB cache for offline media. |
| `maxParallelDownloads` | `1` | One active download avoids resume/progress conflicts. |
| `minRetryCount` | `3` | Retry transient network failures. |

## 6. Request Notification Permission

Call this from the app activity before downloads are started.

```kotlin
import com.app.mtvdownloader.utils.NotificationPermission

NotificationPermission.requestIfRequired(this)
```

This is required on Android 13+ for download progress notifications.

## 7. Build DownloadModel

Every downloadable content item needs a stable `DownloadModel`.

```kotlin
import com.app.mtvdownloader.model.DownloadModel

val model = DownloadModel(
    id = content.id,
    seasonId = content.seasonId,
    title = content.title,
    description = content.description,
    seasonTitle = content.seasonTitle,
    imageUrl = content.thumbnailUrl,
    hlsUrl = content.hlsUrl,
    mpdUrl = content.mpdUrl,
    mp4Url = content.mp4Url,
    srt = content.subtitleUrl
)
```

Required:

- `id`: stable unique content id.
- At least one URL: `hlsUrl`, `mpdUrl`, or `mp4Url`.

Rules:

- Never generate a random id for downloads.
- Never change the id between pause and resume.
- Do not reuse one id for different videos.
- Pass all available URLs. The SDK chooses the correct source.

Field reference:

| Field | Required | Meaning |
| --- | --- | --- |
| `id` | Yes | Stable content id used for queue, resume, cancel, and DB primary key. |
| `hlsUrl` / `mpdUrl` / `mp4Url` | One required | Download source URLs. Pass all available formats. |
| `title` | Recommended | Used in UI, toasts, and notifications. |
| `imageUrl` | Recommended | Poster/thumbnail for app downloads screen. |
| `seasonId`, `seasonTitle` | Optional | Episodic grouping metadata. |
| `srt` | Optional | Subtitle URL metadata for app playback mapping. |
| `drm` | DRM only | Set to `"1"` for DRM content. |
| `drmToken` | DRM MPD only | Full Widevine license URL, not only an auth token. |
| `drmKeyId` | DRM recommended | Widevine KID from backend. |
| `drmScheme` | DRM recommended | Use `"widevine"`. |
| `drmLicenseExpiresAt` | DRM optional | License expiry timestamp in millis. |

## 8. Format Examples

HLS:

```kotlin
DownloadModel(
    id = "content_100",
    title = "HLS Video",
    hlsUrl = "https://example.com/video/master.m3u8"
)
```

MPD/DASH:

```kotlin
DownloadModel(
    id = "content_101",
    title = "DASH Video",
    mpdUrl = "https://example.com/video/manifest.mpd"
)
```

MP4:

```kotlin
DownloadModel(
    id = "content_102",
    title = "MP4 Video",
    mp4Url = "https://example.com/video/file.mp4"
)
```

DRM MPD:

```kotlin
DownloadModel(
    id = "content_103",
    title = "DRM Video",
    mpdUrl = "https://example.com/video/manifest.mpd",
    drm = "1",
    drmToken = widevineLicenseUrl,
    drmKeyId = content.kId,
    drmScheme = "widevine",
    drmLicenseExpiresAt = licenseExpiryMillis
)
```

DRM content with HLS and MPD:

```kotlin
DownloadModel(
    id = "content_104",
    title = "DRM HLS Preferred Video",
    hlsUrl = "https://example.com/video/master.m3u8",
    mpdUrl = "https://example.com/video/manifest.mpd",
    drm = "1",
    drmToken = widevineLicenseUrl,
    drmKeyId = content.kId,
    drmScheme = "widevine"
)
```

When DRM content has `hlsUrl`, the SDK downloads HLS first and does not request a Widevine offline license. When DRM content has only MPD, the SDK acquires a Widevine offline license, saves the `keySetId`, and then downloads DASH segments.

## 9. Add DownloadButton

Use `DownloadButton` in the content row/card.

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.app.mtvdownloader.local.entity.DownloadedContentEntity
import com.app.mtvdownloader.model.DownloadModel
import com.app.mtvdownloader.ui.DownloadButton

@Composable
fun ContentRow(
    content: ContentItem,
    onDownloadedListChanged: (List<DownloadedContentEntity>) -> Unit
) {
    val downloadModel = remember(content.id) {
        DownloadModel(
            id = content.id,
            title = content.title,
            hlsUrl = content.hlsUrl,
            mpdUrl = content.mpdUrl,
            mp4Url = content.mp4Url,
            imageUrl = content.thumbnailUrl
        )
    }

    DownloadButton(
        contentItem = downloadModel,
        onDownloadedListUpdate = onDownloadedListChanged
    )
}
```

The button automatically shows:

- Download icon before start.
- Quality selector for HLS/MPD.
- Progress ring while downloading.
- Pause/Resume/Cancel menu for active items.
- Completed icon after download.
- Failure state and toast if download fails.

## 10. Optional Custom Icons And Colors

```kotlin
import androidx.compose.ui.graphics.Color
import com.app.mtvdownloader.provider.DownloadIconProvider
import com.app.mtvdownloader.utils.Constants

DownloadButton(
    contentItem = downloadModel,
    downloadProgressColor = Color(0xFF00C853),
    downloadProgressTrackColor = Color.White.copy(alpha = 0.24f),
    iconProvider = DownloadIconProvider { status ->
        when (status) {
            Constants.DOWNLOAD_STATUS_PAUSED -> R.drawable.ic_download_pause
            Constants.DOWNLOAD_STATUS_QUEUED -> R.drawable.ic_download_queue
            Constants.DOWNLOAD_STATUS_DOWNLOADING -> R.drawable.ic_downloading
            Constants.DOWNLOAD_STATUS_COMPLETED -> R.drawable.ic_download_done
            else -> R.drawable.ic_download
        }
    }
)
```

If you do not pass an `iconProvider`, the SDK uses its default icons.

## 11. Optional Custom Quality Selector

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
                    }
                )
            }
        }
    }
)
```

If there is only one quality, the SDK starts that quality automatically. MP4 has no quality selector.

## 12. Optional Monetization Gate

Use this when downloads require login, subscription, rental entitlement, or rewarded ad completion.

```kotlin
import com.app.mtvdownloader.model.DownloadMonetizationGate

val gate = DownloadMonetizationGate { context, content ->
    val isLoggedIn = accountManager.isLoggedIn()
    val canDownload = entitlementRepository.canDownload(content.id.orEmpty())

    if (!isLoggedIn || !canDownload) {
        return@DownloadMonetizationGate false
    }

    rewardedAdController.showAndWait(context)
}

DownloadButton(
    contentItem = downloadModel,
    monetizationGate = gate
)
```

Return `true` to allow the download. Return `false` to block it.

## 13. Read Downloaded List

`DownloadButton` returns the persisted downloaded list whenever it changes.

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

Use this list to build the app's Downloads screen.

Important fields:

| Field | Meaning |
| --- | --- |
| `contentId` | Stable id passed in `DownloadModel.id`. |
| `contentUrl` | Original HLS/MPD/MP4 URL used by Media3 download. |
| `contentMimeType` | Download MIME type. |
| `downloadProgress` | Progress from `0` to `100`. |
| `downloadStatus` | `queued`, `downloading`, `paused`, `completed`, `failed`, or `removed`. |
| `licenseUri` | Widevine license URL for DRM MPD, empty for non-DRM/HLS. |
| `drmOfflineKeySetId` | Raw Widevine offline keySetId for DRM MPD playback. |
| `drmOfflineKeySetIdBase64` | Base64 keySetId for restore after app restart. |
| `failureCode` / `failureReason` | Failure information for support. |

## 14. Play Downloaded Content

The downloader SDK downloads and persists content. Playback is handled by the host app's player.

If using the MTV VideoPlayer SDK, build a player model from `DownloadedContentEntity` and pass:

```kotlin
downloadManager = DownloadUtil.getDownloadManager(context)
downloadCache = DownloadUtil.getDownloadCache(context)
drmOfflineKeySetId = entity.drmOfflineKeySetId
drmOfflineKeySetIdBase64 = entity.drmOfflineKeySetIdBase64
```

Set the playback URL by MIME type:

- HLS: set `hlsUrl`.
- MPD/DASH: set `mpdUrl`.
- MP4: set `videoUrl`.

For DRM MPD offline playback, the player must use the offline `keySetId`; it should not request a new online license while offline.

Complete VideoPlayer SDK mapping example:

```kotlin
private fun DownloadedContentEntity.urlIf(
    mimeType: String,
    vararg extensions: String
): String? {
    if (contentMimeType == mimeType) return contentUrl
    val path = contentUrl.substringBefore("?").substringBefore("#")
    return contentUrl.takeIf {
        extensions.any { extension ->
            path.endsWith(extension, ignoreCase = true)
        }
    }
}

fun buildOfflinePlayerModel(
    context: Context,
    entity: DownloadedContentEntity
): PlayerModel {
    return PlayerModel(
        id = entity.contentId,
        hlsUrl = entity.urlIf("application/x-mpegURL", ".m3u8"),
        mpdUrl = entity.urlIf("application/dash+xml", ".mpd"),
        videoUrl = entity.urlIf("video/mp4", ".mp4", ".m4v"),
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
}
```

Required imports for this example:

```kotlin
import android.content.Context
import com.app.mtvdownloader.DownloadUtil
import com.app.mtvdownloader.local.entity.DownloadedContentEntity
import com.app.videosdk.model.PlayerModel
```

## 15. Manual Pause, Resume, Cancel

The default `DownloadButton` already handles this. If the app builds a custom UI, call:

```kotlin
import com.app.mtvdownloader.helper.ReelDownloadHelper

ReelDownloadHelper.pauseDownload(context, contentId)
ReelDownloadHelper.resumeDownload(context, contentId)
ReelDownloadHelper.cancelDownload(context, contentId)
```

Resume uses the persisted download row, selected stream keys, DRM license URL, original source URL, and content id. It should continue from the previous progress, not restart from 0.

## 16. DRM License Requirements

The app must provide `drmToken` as the full Widevine license URL. It is not just a bearer token.

The downloader SDK does not know how your backend creates DRM licenses. The app must build or request the final license URL before creating `DownloadModel`.

Example:

```kotlin
val drmLicenseUrl = drmRepository.buildWidevineLicenseUrl(
    contentId = content.id,
    keyId = content.kId,
    offline = true
)

val model = DownloadModel(
    id = content.id,
    mpdUrl = content.mpdUrl,
    drm = "1",
    drmToken = drmLicenseUrl,
    drmKeyId = content.kId,
    drmScheme = "widevine"
)
```

For MPD-only DRM downloads, the license server must allow persistent/offline licenses. If the server returns HTTP `401`, check:

- Package name.
- Debug/release signing SHA.
- User entitlement.
- Package/subscription id.
- Content id.
- KID.
- Token expiry.
- Offline download policy.
- Exact license URL payload.

DRM MPD must reach `DRM_OFFLINE_LICENSE_SUCCESS` before segment download starts.

## 17. Logcat

Use this while testing downloads:

```bash
adb logcat -v time MtvDrmOffline:I DownloadWorker:D ReelDownloadHelper:D OfflineDrmLicenseUtil:D DownloadUtil:D WorkManager:E ExoPlayerImplInternal:E '*:S'
```

For app-side DRM URL building, also include the app's DRM log tag.

Important logs:

| Log | Meaning |
| --- | --- |
| `DRM_OFFLINE_LICENSE_START` | SDK requested Widevine offline license. |
| `DRM_OFFLINE_LICENSE_SUCCESS` | SDK saved offline keySetId. |
| `DRM_OFFLINE_LICENSE_FAILED` | License acquisition failed. |
| `DRM_MANIFEST_PREPARE_FAILED` | MPD could not be prepared. |
| `DRM_INIT_DATA_MISSING` | MPD has no usable Widevine init data. |
| `Download failed` | Final SDK failure state. |

## 18. Third-Party App Checklist

1. Add JitPack.
2. Add downloader SDK dependency.
3. Enable Compose.
4. Add/confirm manifest permissions.
5. Register `Application` class.
6. Call `DownloadSdk.init()` once.
7. Request Android 13+ notification permission.
8. Build stable `DownloadModel` for every content row.
9. Pass all available URLs: HLS, MPD, MP4.
10. For DRM MPD-only content, pass `drm = "1"` and full `drmToken` license URL.
11. Render `DownloadButton`.
12. Add monetization gate if needed.
13. Store/display downloaded list from `onDownloadedListUpdate`.
14. Play downloaded items using the app player and SDK Media3 cache.
15. Test pause/resume/cancel and app restart.
16. Test HLS, MPD, MP4, DRM HLS fallback, and DRM MPD-only.
17. Test downloaded playback in airplane mode.

## 19. Common Issues

| Issue | Fix |
| --- | --- |
| Button says unsupported source | Pass stable `id` and at least one valid `hlsUrl`, `mpdUrl`, or `mp4Url`. |
| Notification not visible | Request `POST_NOTIFICATIONS` on Android 13+ and call `DownloadSdk.init()`. |
| Download restarts from 0 | Keep the same `DownloadModel.id`; do not recreate content with a different id. |
| Pause/resume affects another item | Use one unique id per content item. |
| Quality selector empty | Confirm manifest URL is reachable and contains variants/tracks. |
| MP4 has no quality selector | Expected; MP4 downloads as one file. |
| DRM MPD fails before download | License server rejected offline license request or MPD has no Widevine init data. |
| Downloaded DRM MPD does not play offline | Player must receive and use `drmOfflineKeySetId` or `drmOfflineKeySetIdBase64`. |
