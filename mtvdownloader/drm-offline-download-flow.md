# DRM Offline Download Flow

This document explains how the app downloads and plays DRM protected video/audio content, and what another downloader SDK must implement to handle the same content correctly.

## Summary

The app uses Google ExoPlayer 2.x offline APIs for downloads:

- `DownloadHelper` prepares the stream manifest and selected tracks.
- `OfflineLicenseHelper` requests a Widevine offline license before media bytes are downloaded.
- ExoPlayer `DownloadManager` downloads encrypted media segments into `SimpleCache`.
- The ExoPlayer `DownloadRequest` stores the Widevine `keySetId`.
- Playback uses the cached media plus the offline `keySetId`; it does not use the online license URL while offline.

For DRM content, downloading the MPD/M3U8 and media segments is not enough. The downloader must also acquire and persist a Widevine offline license key set.

## Current Implementation Files

Main files:

- `app/src/main/java/com/sspt/aol/downloader/helper/ExoVideoDownloadHelper.java`
- `app/src/main/java/com/sspt/aol/downloader/DownloadVideo.kt`
- `app/src/main/java/com/sspt/aol/downloader/DownloadTracker.kt`
- `app/src/main/java/com/sspt/aol/downloader/MyDownloadService.kt`
- `app/src/main/java/com/sspt/aol/downloader/utils/DownloadUtils.java`
- `app/src/main/java/com/sspt/aol/player/MediaPlayer.kt`
- `app/src/main/java/com/sspt/aol/player/playerManger/PlayerManager.kt`
- `app/src/main/java/com/sspt/aol/ui/dashboard/home/details/VideoDetailsActivity.kt`
- `app/src/main/java/com/sspt/aol/ui/dashboard/home/details/AudioDetailsActivity.kt`
- `app/src/main/java/com/sspt/aol/ui/dashboard/download/DownloadFragment.kt`
- `app/src/main/java/com/sspt/aol/ui/dashboard/download/DownloadSeasonFragment.kt`
- `app/src/main/java/com/sspt/aol/utils/DrmLicenseUrlBuilder.kt`

Local database:

- Entity: `app/src/main/java/com/sspt/aol/data/local/roomdb/entities/DownloadedVideo.kt`
- DAO: `app/src/main/java/com/sspt/aol/data/local/roomdb/daos/DownloadedVideosDao.kt`
- DB: `app/src/main/java/com/sspt/aol/data/local/roomdb/dbs/DownloadedVideoDatabase.kt`

## Dependencies

The app uses ExoPlayer 2.19.1 modules:

- `com.google.android.exoplayer:exoplayer-core`
- `com.google.android.exoplayer:exoplayer-ui`
- `com.google.android.exoplayer:exoplayer-dash`
- `com.google.android.exoplayer:exoplayer-hls`
- `com.google.android.exoplayer:exoplayer-smoothstreaming`
- `com.google.android.exoplayer:extension-workmanager`
- `com.google.android.exoplayer:extension-cronet`

Current code imports `com.google.android.exoplayer2.*`, not `androidx.media3.*`.

## Supported DRM Type

Current DRM download support is Widevine:

- DRM UUID: `C.WIDEVINE_UUID`
- DRM type string sent to license server: `widevine`
- License URL base: `BuildConfig.DRM_LICENSE_URL`
- Current DRM download routing treats `.mpd` DASH streams as DRM.

Current code does not route `.m3u8` HLS through the DRM offline license path.

## License URL Contract

The app builds the Widevine license URL in `DrmLicenseUrlBuilder.buildLicenseUrl(...)`.

Query params:

- `user_id`: generated device GUID from `GUIDGenerator.generateGUID(context)`
- `type`: `widevine`
- `authorization`: auth token from `PrefManager(TOKEN)`
- `payload`: Base64 no-wrap encoded JSON

Payload JSON:

```json
{
  "content_id": "<content id>",
  "k_id": "<widevine key id from stream/content API>",
  "user_id": "<logged in AOL user id>",
  "package_id": "<active package id>",
  "licence_duration": "<seconds>",
  "security_level": "0",
  "rental_duration": "0",
  "content_type": "1 for paid, 0 for free",
  "download": "1",
  "can_renew": true
}
```

`licence_duration` is calculated from content download-expiry days. If the value is empty or zero, the app defaults to 30 days.

Important:

- The payload field name is `licence_duration`, not `license_duration`.
- The DRM key id field is `k_id`.
- Offline downloads must send `download = "1"`.
- Do not log the full license URL in production because it contains authorization and payload data.

## Download Flow

### 1. Build and save local download metadata

`VideoDetailsActivity.saveVideoDatabase(...)` and `AudioDetailsActivity.saveVideoDatabase(...)` create a `DownloadedVideo` row before starting the actual ExoPlayer download.

Important columns:

- `CONTENT_ID`: app content id
- `URL`: stream URL
- `DOWNLOAD_STATUS`: pending, queued, completed, etc.
- `token`: generated DRM license URL
- `drm`: DRM metadata from API
- `kid`: Widevine key id
- `keySetId`: Base64 offline license key set id, initially null

Current behavior:

- Initial insert sets `keySetId = null`.
- ExoPlayer's internal `DownloadRequest` still receives the raw `keySetId`.
- Room `keySetId` is mainly populated later during renew flows.

### 2. Create DRM media item

`ExoVideoDownloadHelper.downloadVideo(...)` decides whether to use the DRM media item.

For DASH DRM, it creates:

```kotlin
MediaItem.Builder()
    .setUri(mediaUrl)
    .setMimeType(MimeTypes.APPLICATION_MPD)
    .setDrmLicenseUri(licenseUrl)
    .setDrmUuid(C.WIDEVINE_UUID)
    .build()
```

For non-DASH URLs, it creates a normal non-DRM media item.

Current limitation:

- `FileUtils.getMimeTypeFromExtension(url)` returns true only when the URL extension is `mpd`.
- If the URL is DRM HLS, or a signed DASH URL whose extension is hidden by query params, this detection can fail.

### 3. Prepare manifest and detect DRM data

`DownloadTracker.StartDownloadDialogHelper` calls:

```kotlin
downloadHelper.prepare(this)
```

After preparation:

- It scans track formats for `format.drmInitData`.
- If no DRM init data exists, it starts a normal non-DRM download.
- If DRM init data exists, it requires schema data in the manifest.

Current code requires DRM scheme data/PSSH in the manifest. If the stream only provides DRM init data in segments, this flow rejects it.

### 4. Acquire Widevine offline license

For DRM streams, `DownloadTracker.WidevineOfflineLicenseFetchTask` calls:

```kotlin
val offlineLicenseHelper = OfflineLicenseHelper.newWidevineInstance(
    drmConfiguration.licenseUri.toString(),
    drmConfiguration.forceDefaultLicenseUri,
    dataSourceFactory,
    drmConfiguration.licenseRequestHeaders,
    DrmSessionEventListener.EventDispatcher()
)

val keySetId: ByteArray = offlineLicenseHelper.downloadLicense(format)
```

The returned `keySetId` is the critical offline DRM value.

Do not confuse:

- License URL/token: used to ask server for rights.
- `keySetId`: local Widevine offline key reference used for offline playback.
- Media segments: still encrypted in cache.

### 5. Select tracks

The app selects one audio or video rendition based on preferences:

- Audio: highest/lowest bitrate depending on HD/SD preference.
- Video: selected by height/bitrate, preferring 720/480/360 depending on HD/SD preference.

It calls:

```kotlin
helper.clearTrackSelections(0)
helper.addTrackSelection(0, qualitySelected)
```

### 6. Build ExoPlayer DownloadRequest with keySetId

After track selection, the app creates a `DownloadRequest`:

```kotlin
val downloadRequest = downloadHelper
    .getDownloadRequest(contentId, Util.getUtf8Bytes(estimatedContentLength.toString()))
    .copyWithKeySetId(keySetId)
```

This is the point where DRM download becomes usable offline. The `DownloadRequest` must include `keySetId`.

### 7. Start ExoPlayer DownloadService

`DownloadTracker.startDownload(...)` calls:

```kotlin
DownloadService.sendAddDownload(
    applicationContext,
    MyDownloadService::class.java,
    downloadRequest,
    true
)
```

`MyDownloadService` returns the singleton ExoPlayer `DownloadManager` from `DownloadUtils.getDownloadManager(this)`.

### 8. Download storage

`DownloadUtils` configures storage:

- Download root: `context.getExternalFilesDir(null)` or `context.filesDir`
- Cache directory: `<root>/downloads`
- Cache: `SimpleCache`
- Cache evictor: `NoOpCacheEvictor`
- Index/database: `StandaloneDatabaseProvider`
- Data source: `CacheDataSource` for playback and cache reads

The downloaded media remains encrypted. ExoPlayer reads it from cache and decrypts during playback using the offline license.

### 9. Progress and completion

Download progress comes from `DownloadTracker.DownloadManagerListener` and `ExoVideoDownloadHelper.onDownloadsChanged(...)`.

`VideoDetailsActivity.downloadStarted(...)` updates Room:

- During download: `DOWNLOAD_PENDING`, progress value
- On completion: `DOWNLOAD_COMPLETED`, progress `100`, `isDownloaded = true`
- On failure: remove cached download and delete Room row

## Offline Playback Flow

Playback must restore both cached media and offline license.

### 1. Find ExoPlayer DownloadRequest

Playback calls:

```kotlin
val downloadRequest = DownloadUtils
    .getDownloadTracker(context)
    .getDownloadRequest(MediaItem.fromUri(url).localConfiguration?.uri)
```

If a request exists, the app treats this URL as downloaded.

### 2. Build offline DRM media item

For downloaded DASH DRM, the app builds a media item with:

- same URL
- mime type `APPLICATION_MPD`
- Widevine DRM config
- no online license URL

Then it calls `MediaPlayer.setDownloadProperties(...)`.

### 3. Attach offline keySetId

`MediaPlayer.setDownloadProperties(...)` copies download metadata into the media item:

- `mediaId`
- `uri`
- `customCacheKey`
- `mimeType`
- `streamKeys`
- DRM `keySetId`

The key-set source priority is:

1. If `DownloadedVideo.keySetId` is non-empty and not `"null"`, Base64 decode it and use that.
2. Otherwise use `downloadRequest.keySetId`.

This means current playback can work immediately after download because ExoPlayer's download index has the `keySetId`, even if Room `keySetId` is still null.

### 4. Use DRM-capable ExoPlayer

For DRM content, `MediaPlayer.getExoplayer(..., isDrm = true)` uses:

- `DefaultMediaSourceFactory`
- `DownloadUtils.getDataSourceFactory(context)` for cached media
- `DefaultDrmSessionManagerProvider`
- `DownloadUtils.getHttpDataSourceFactory(context)` for DRM HTTP requests

## License Renewal Flow

When a downloaded item expires, `DownloadFragment` and `DownloadSeasonFragment` run the renew flow:

1. Build a new DRM media item with a fresh license URL.
2. Prepare `DownloadHelper` for the media item.
3. Find DRM format with `MediaPlayer.getFirstFormatWithDrmInitData(...)`.
4. Call `OfflineLicenseHelper.downloadLicense(format)`.
5. Base64 encode the new `keySetId`.
6. Save it into Room using `downloadedVideosDao.updateKeySetId(...)`.
7. Update local expiry date using `downloadedVideosDao.updateExpiryDate(...)`.

Playback after renewal uses Room `keySetId`, so it can use the renewed offline license.

## Required Contract for MTV Downloader SDK

The MTV downloader SDK must support all of these operations for Widevine offline downloads:

1. Accept the original DASH MPD URL and the exact Widevine license URL generated by this app.
2. Prepare the manifest and read DRM init data/PSSH.
3. Request an offline Widevine license, not a streaming-only license.
4. Return or internally persist the Widevine `keySetId`.
5. Build its download request/cache entry with that `keySetId`.
6. Persist enough download metadata to restore playback after app restart.
7. During offline playback, attach `keySetId` to the DRM configuration.
8. Use a cache/data source that reads the downloaded encrypted segments.
9. Support license renewal and replace the stored `keySetId`.
10. Delete cached media and release offline license when the user removes the download, if the SDK exposes release support.

If the SDK only downloads MPD and segments, DRM playback will fail because the segments stay encrypted.

## Minimal Pseudocode for Another SDK

```kotlin
val mediaItem = MediaItem.Builder()
    .setUri(mpdUrl)
    .setMimeType(MimeTypes.APPLICATION_MPD)
    .setDrmConfiguration(
        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
            .setLicenseUri(licenseUrl)
            .build()
    )
    .build()

val helper = DownloadHelper.forMediaItem(
    context,
    mediaItem,
    DefaultRenderersFactory(context),
    httpDataSourceFactory
)

helper.prepare(object : DownloadHelper.Callback {
    override fun onPrepared(helper: DownloadHelper) {
        val drmFormat = findFirstFormatWithDrmInitData(helper)

        val keySetId = if (drmFormat != null) {
            OfflineLicenseHelper
                .newWidevineInstance(
                    licenseUrl,
                    false,
                    httpDataSourceFactory,
                    emptyMap(),
                    DrmSessionEventListener.EventDispatcher()
                )
                .downloadLicense(drmFormat)
        } else {
            null
        }

        val downloadRequest = helper
            .getDownloadRequest(contentId, estimatedBytes)
            .copyWithKeySetId(keySetId)

        startDownload(downloadRequest)
        saveKeySetId(Base64.encodeToString(keySetId, Base64.NO_WRAP))
    }

    override fun onPrepareError(helper: DownloadHelper, e: IOException) {
        // Report manifest/network failure.
    }
})
```

For offline playback:

```kotlin
val offlineMediaItem = MediaItem.Builder()
    .setUri(mpdUrl)
    .setMimeType(MimeTypes.APPLICATION_MPD)
    .setDrmConfiguration(
        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
            .setKeySetId(savedKeySetIdBytes)
            .build()
    )
    .build()

player.setMediaItem(offlineMediaItem)
player.prepare()
player.play()
```

If using Media3, use the equivalent `androidx.media3.*` classes and APIs.

## Common DRM Download Failures

### License request fails before download starts

Likely causes:

- `authorization` token is missing or expired.
- `payload` is not Base64 no-wrap encoded.
- `content_id` or `k_id` does not match the MPD's DRM data.
- License server did not grant offline rights.
- `download = "1"` is missing.
- `licence_duration` is invalid.
- Device does not support Widevine or offline licenses.

### `format.drmInitData` is null

Likely causes:

- MPD does not contain Widevine PSSH/scheme data.
- SDK is using an HLS URL but current flow expects DASH MPD.
- URL detection failed because of query params or CDN URL shape.

Current AOL code does not support DRM init data that only appears later in media segments.

### Download succeeds but offline playback fails

Likely causes:

- `keySetId` was not attached to the offline media item.
- Stored `keySetId` was Base64 decoded incorrectly.
- App data was cleared or app was reinstalled; Widevine key set ids are local device/app references and may become invalid.
- License expired and was not renewed.
- Playback is using a normal network data source instead of the ExoPlayer cache data source.
- SDK downloaded media into a different cache/index than playback uses.

### MTV SDK downloads non-DRM content but fails on DRM content

Most likely cause:

- The SDK downloads files/segments but does not acquire a Widevine offline license and does not return a `keySetId`.

The fix is not only a URL/header change. The SDK must implement the offline license lifecycle.

## Review Notes and Risks in Current Code

1. Initial downloads do not save Room `keySetId`.

   The ExoPlayer `DownloadRequest` contains `keySetId`, but `DownloadedVideo.keySetId` remains null until a renew flow. If another SDK relies only on Room data, it will miss the offline DRM key-set reference.

2. DRM detection is too narrow.

   `FileUtils.getMimeTypeFromExtension(...)` returns true only for `.mpd`. DRM HLS, signed URLs with query params, or extensionless CDN URLs can be misclassified as non-DRM.

3. Renewal uses a different HTTP data source than normal downloads.

   Download flow uses `DownloadUtils.getHttpDataSourceFactory(...)`, which may be Cronet. Renewal uses `DefaultHttpDataSource.Factory()` directly. If cookies, headers, TLS behavior, or networking stack differ, license renewal can fail differently from initial download.

4. Offline license release is not visible in delete flow.

   Delete removes the ExoPlayer download and Room row. If a valid `keySetId` is available, a complete DRM lifecycle should also release the offline license through `OfflineLicenseHelper.releaseLicense(keySetId)`.

5. Full DRM tokens must not be logged.

   Logs should redact `authorization`, `payload`, and user identifiers. The download tracker already sanitizes some DRM logs, but every call site should follow that pattern.

## Checklist for MTV SDK Integration

Before testing a DRM download in MTV SDK, confirm:

- The input stream URL is a DASH MPD for current AOL DRM flow.
- The MPD contains Widevine PSSH/scheme data.
- `k_id` is present and matches the stream.
- The app passes a fresh AOL auth token.
- The license URL includes `download = "1"`.
- The license server response grants offline Widevine rights.
- The SDK exposes or stores `keySetId`.
- Playback attaches `keySetId` to Widevine DRM configuration.
- Download cache/index used by download is the same one used by playback.
- Expired downloads renew license and update `keySetId`.
- Delete flow removes cached media and releases the offline license if supported.

## Recommended Fixes Before Handing to Another SDK

1. Save initial `DownloadRequest.keySetId` into Room after the offline license is acquired or after the download request is created.
2. Replace URL-extension DRM detection with explicit API metadata plus ExoPlayer `Util.inferContentType(Uri)`.
3. Treat DRM support as independent from container type. DASH Widevine is supported now; HLS FairPlay/Widevine/ClearKey should be explicitly unsupported or implemented.
4. Use the same HTTP data source factory for initial license, renewal license, and DRM playback.
5. Add structured error reporting for these stages: manifest prepare, DRM init data missing, offline license request, download request enqueue, media cache download, offline playback.
6. Add a delete path that releases offline license when `keySetId` is known.
