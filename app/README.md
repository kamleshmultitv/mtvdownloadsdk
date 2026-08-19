# Sample App Integration Guide

This `app` module is the host-app example for the MTV player and downloader SDKs. It shows how a third-party Android app can play HLS/DASH/live content, configure ads/skip-intro/next-episode behavior, request notification permission, initialize the downloader SDK, gate downloads with monetization, track download analytics, and play downloaded content.

## Module Role

The sample app is not the downloadable SDK artifact. It is a reference implementation for app developers.

Current sample runtime package/application id is `com.sspt.aol` for AOL DRM license-server compatibility testing. The Kotlin namespace remains `com.app.sample`.

| Area | Implementation |
| --- | --- |
| Player UI | `MtvVideoPlayerSdk` from the player SDK |
| Download UI | `DownloadButton` from `mtvdownloader` |
| Download engine | `mtvdownloader` module |
| API/content mapping | `FileUtils` |
| Download monetization example | `SampleDownloadMonetization` |
| SDK initialization | `AppClass` |
| Notification permission | `MainActivity` |

## Dependencies

The sample app currently uses:

```kotlin
implementation(libs.mtvplayersdk)
implementation(project(":mtvdownloader"))
```

For a real third-party app, replace the local project dependency with the published downloader dependency:

```kotlin
implementation("com.github.kamleshmultitv:mtvdownloader:download-1.1.0")
```

## Required Android Setup

The sample app manifest declares internet access and uses the SDK-merged downloader permissions/service from `mtvdownloader`.

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

The downloader SDK manifest contributes:

- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_DATA_SYNC`
- `POST_NOTIFICATIONS`
- `MediaDownloadService`

`MainActivity` requests notification permission on Android 13 and above:

```kotlin
NotificationPermission.requestIfRequired(this)
```

## SDK Initialization

`AppClass` initializes the downloader SDK once when the app starts.

```kotlin
DownloadSdk.init(
    application = this,
    config = DownloadSdkConfig(
        maxCacheBytes = 1024L * 1024L * 1024L,
        maxParallelDownloads = 1,
        minRetryCount = 3
    ),
    analyticsListener = object : DownloadAnalyticsListener {
        override fun onDownloadRequested(contentItem: DownloadModel) {
            Log.d("DownloadAnalytics", "Requested: ${contentItem.id}")
        }

        override fun onDownloadCompleted(contentId: String) {
            Log.d("DownloadAnalytics", "Completed: $contentId")
        }
    }
)
```

In production, use this listener to send events to Firebase, Mixpanel, Segment, or your backend analytics.

## App Flow

1. `MainActivity` enables edge-to-edge UI, requests notification permission, and renders `ContentScreen`.
2. `ContentViewModel` loads paged content from the configured API.
3. `FileUtils.buildPlayerContentList()` maps API content to player SDK `PlayerModel`.
4. `MtvVideoPlayerSdk` plays the selected content.
5. `ContentList` renders rows with `ContentCard`.
6. `ContentCard` builds a downloader `DownloadModel` and renders `DownloadButton`.
7. `DownloadButton` handles monetization, quality selection, queueing, progress, pause, resume, and cancel.
8. Completed downloads are shown in `DownloadedContentList`.
9. Selecting a downloaded item opens `DownloadPlayer`.

## Playback Configuration Panel

The floating action button opens a configuration panel implemented by `CardWithTextFieldsAndButton`.

It can apply:

- Override playback URL.
- DASH DRM token.
- Live/VOD detection.
- IMA ads toggle and ad tag URL.
- Skip intro settings.
- Next episode prompt settings.

If no URL is entered, submitted settings apply to existing API content. If a URL is entered, the app plays that override stream.

## Mapping API Content To PlayerModel

Player content is built in `FileUtils.buildPlayerContentList()`.

Important mapping:

```kotlin
PlayerModel(
    hlsUrl = hls,
    mpdUrl = mpd,
    drmToken = getDrmToken(context, content, offline = true),
    imageUrl = thumbnailUrl,
    episodeTitle = content.title.orEmpty(),
    description = content.shortDesc.orEmpty(),
    srt = content.subtitle?.firstOrNull()?.srt.orEmpty(),
    adsConfig = AdsConfig(enableAds = false),
    skipIntro = SkipIntro(enableSkipIntro = false),
    nextEpisode = NextEpisode(enableNextEpisode = false)
)
```

For DRM playback and downloads, `getDrmToken()` builds the Widevine license URL from API metadata and device ID. API-list playback does not force `drm = "1"` into the video SDK, so the player can keep using the same online playback route that worked before. Offline downloader requests still use the AOL-compatible payload: `content_id`, `k_id`, logged-in user id, active package id, `licence_duration`, `download = "1"`, and `can_renew = true`, then Base64 encodes the payload with `NO_WRAP`.

The sample resolves the DRM payload `user_id` in this order: configured `ApiConstant.DRM_USER_ID`, JWT `user_id`, then the legacy AOL fallback. It does not use JWT `owner_id` as the DRM user because the license payload expects the logged-in AOL user id. It resolves `package_id` in this order: configured `ApiConstant.DRM_PACKAGE_ID`, API `ContentItem.packageId`, then fallback. If the license server still returns HTTP `401` after a valid token, confirm the exact AOL user/package id, package name, signing SHA-256, and offline entitlement expected by the license backend.

`ApiConstant.DRM_AUTHORIZATION_SOURCE` controls which token representation is sent to the license server:

- `stored`: sends `ApiConstant.TOKEN` as-is. This is the default AOL-reference behavior.
- `jwt`: sends the decoded JWT when `TOKEN` is Base64-wrapped.
- `claim_token`: sends the nested JWT `token` claim when present.

The sample logs only `authorizationSource`, never the token value.

## Mapping API Content To DownloadModel

Download content is built in `FileUtils.buildDownloadContentList()`.

The sample maps:

- `hlsUrl` from `ContentItem.hlsUrl`
- `mpdUrl` from `ContentItem.url` when it ends with `.mpd`
- `mp4Url` from `ContentItem.url` when it ends with `.mp4` or `.m4v`
- `drm = "1"` from API DRM flag
- `drmToken` from `getDrmToken(context, contentItem, offline = true)`
- `drmKeyId` from `ContentItem.kId`
- `drmLicenseExpiresAt` from `downloadExpiry`
- poster/title/subtitle metadata from API fields

Example:

```kotlin
DownloadModel(
    id = contentItem.id.orEmpty(),
    seasonId = contentItem.seasonId.orEmpty(),
    hlsUrl = hlsUrl,
    mpdUrl = mpdUrl,
    mp4Url = mp4Url,
    drm = contentItem.drm,
    drmToken = getDrmToken(context, contentItem, offline = true),
    drmLicenseExpiresAt = drmLicenseExpiresAt,
    drmKeyId = contentItem.kId,
    imageUrl = imageUrl,
    title = contentItem.title.orEmpty(),
    description = contentItem.shortDesc.orEmpty()
)
```

When a DRM-flagged item also has an HLS URL, the downloader uses HLS first so it follows the same playable source route as online playback and does not request a Widevine offline license. If HLS is missing, DRM MPD still uses the Widevine offline-license flow and must reach `DRM_OFFLINE_LICENSE_SUCCESS` before segment download starts.

## Download Button Usage

`ContentCard` renders the SDK download button.

```kotlin
DownloadButton(
    contentItem = downloadModel,
    customQualitySelector = { qualities, onSelect, onDismiss ->
        CustomQualitySelectorBottomSheet(
            qualities = qualities,
            onDismiss = onDismiss,
            onQualitySelected = onSelect
        )
    },
    iconProvider = DownloadIconProvider { status ->
        when (status) {
            DOWNLOAD_STATUS_PAUSED -> R.drawable.ic_download_pause
            DOWNLOAD_STATUS_QUEUED -> R.drawable.ic_downlaod_queue
            DOWNLOAD_STATUS_DOWNLOADING -> R.drawable.ic_downloading
            DOWNLOAD_STATUS_COMPLETED -> R.drawable.ic_download_done
            else -> R.drawable.ic_download
        }
    },
    monetizationGate = SampleDownloadMonetization.gate,
    onDownloadedListUpdate = { list ->
        downloadContentList(list)
    }
)
```

## Monetization Example

`SampleDownloadMonetization` demonstrates the app-side policy layer.

Current sample behavior:

- Allows normal content immediately.
- Treats IDs starting with `premium_` as premium content.
- Runs a rewarded-ad placeholder for premium content.
- Returns `true` only after the placeholder completes.

Production apps should replace the placeholder with real business logic:

- Login check.
- Subscription entitlement check.
- Rental/package validation.
- Rewarded ad completion.
- Backend authorization.
- DRM license availability.

The SDK gate is a UX/control layer. Paid content must still be protected by backend authorization and DRM/license policy.

## Download Analytics

The sample registers a `DownloadAnalyticsListener` in `AppClass`.

Available events include:

- Download requested.
- Monetization allowed.
- Monetization blocked.
- Download enqueued.
- Download started.
- Download progress.
- Download paused.
- Download resumed.
- Download cancelled.
- Download completed.
- Download failed with code/message.

Use these callbacks for app analytics, user messaging, and support diagnostics.

## Downloaded Content Screen

The app collects the SDK-provided downloaded list through:

```kotlin
onDownloadedListUpdate = { list ->
    downloadContentList(list)
}
```

`DownloadedContentList` displays completed items. `DownloadPlayer` converts a `DownloadedContentEntity` back into a player `PlayerModel` with `FileUtils.buildContentListFromDownloaded()`.

Persisted download fields available to the app include:

- `contentId`
- `contentUrl`
- `licenseUri`
- `downloadProgress`
- `downloadStatus`
- `localFilePath`
- `videoHeight`
- `videoBitrate`
- `queuedAt`
- `failureCode`
- `failureReason`
- `retryCount`
- `maxRetryCount`
- `drmLicenseExpiresAt`
- `drmLicenseRefreshStatus`
- `drmOfflineKeySetId`
- `drmOfflineKeySetIdBase64`
- `drmKeyId`
- `contentMimeType`

## Picture-In-Picture

`MainActivity` implements `PipListener`.

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

The activity manifest enables PiP:

```xml
android:supportsPictureInPicture="true"
android:resizeableActivity="true"
```

## Build And Run

Compile the sample app:

```bash
sh gradlew :app:compileDebugKotlin
```

Build the debug APK:

```bash
sh gradlew :app:assembleDebug
```

Install from Android Studio or with `adb` after building the APK.

Check downloader and DRM logs:

```bash
adb logcat -v time SampleDrmLicense:I MtvDrmOffline:I DownloadWorker:D ReelDownloadHelper:D OfflineDrmLicenseUtil:D DownloadUtil:D ExoPlayerImplInternal:E WorkManager:E '*:S'
```

If DRM MPD download starts and then fails, check the stage name. Common stages are `DRM_AUTH_TOKEN_EXPIRED`, `DRM_MANIFEST_PREPARE_FAILED`, `DRM_INIT_DATA_MISSING`, `DRM_OFFLINE_LICENSE_FAILED`, `DRM_KEYSETID_EMPTY`, and `DRM_SEGMENT_DOWNLOAD_FAILED`. HTTP `401` at `DRM_OFFLINE_LICENSE_FAILED` means the license server rejected the generated URL/token/payload, offline policy, package name, or signing certificate.

If online DRM playback also fails with HTTP `401`, the failure is before playback and before offline segment download. Compare these log fields with the working AOL app/backend configuration: `authorizationSource`, `payloadUserIdSource`, `packageIdSource`, `download`, `canRenew`, `DRM_PACKAGE_SIGNING_CHECK package`, and `certSha256`.

## Test Checklist

Before using this flow in a production app, test:

- HLS playback.
- DASH/MPD playback.
- MP4 playback if your API supplies MP4 URLs.
- Live playback.
- DRM playback.
- HLS download with quality selection.
- DASH/MPD download with quality selection.
- MP4 progressive download.
- DRM MPD download and license expiry.
- Pause, resume, cancel.
- App background and app kill during download.
- Network switch and airplane mode.
- Android 13+ notification permission.
- Android 14+ foreground service behavior.
- Low-storage/cache eviction behavior.
- Monetization allowed and blocked flows.
- Analytics events.

## Important Files

| File | Purpose |
| --- | --- |
| `AppClass.kt` | Initializes downloader SDK config and analytics. |
| `MainActivity.kt` | Requests notification permission and handles PiP. |
| `ContentBody.kt` | Hosts player, list, downloads, and override panel. |
| `ContentCard.kt` | Builds `DownloadButton` integration. |
| `SampleDownloadMonetization.kt` | App-side monetization gate sample. |
| `FileUtils.kt` | Maps API content to player/download models and builds DRM token. |
| `DownloadPlayer.kt` | Plays downloaded content. |
| `DownloadedContentList.kt` | Shows downloaded items. |

## Production Notes

- Keep API tokens and DRM secrets out of source code.
- Move sample URLs/tokens to secure remote config or backend APIs.
- Replace the rewarded-ad placeholder with the real ad SDK.
- Use server-side entitlement checks for paid downloads.
- Ask the DRM/backend team to whitelist the production package name and signing certificate used by the consuming app.
- Keep `DownloadModel.id` stable for each content item.
- Avoid using the same ID for different videos.
- Call `DownloadSdk.init()` before rendering download UI.
- Request notification permission before starting downloads on Android 13+.
