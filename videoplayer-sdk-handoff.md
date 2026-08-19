# VideoPlayer SDK Handoff

This document lists the VideoPlayer SDK-side changes/requirements needed to support MTV downloader offline playback for HLS, MPD, and MP4 content. It is intended for the standalone VideoPlayer SDK codebase.

## Goal

The VideoPlayer SDK must play online content normally and must also play content downloaded by `mtvdownloader` without switching to another player.

For downloaded DRM DASH content, the player must restore the persisted Widevine offline license using `keySetId`. It must not try to acquire a fresh online license when the user is offline.

## Public Model Contract

`PlayerModel` must expose and preserve these fields:

```kotlin
val id: String?
val hlsUrl: String?
val mpdUrl: String?
val videoUrl: String?
val liveUrl: String?
val drm: String?
val drmToken: String?
val cacheFactory: CacheDataSource.Factory?
val downloadManager: DownloadManager?
val downloadCache: SimpleCache?
val drmOfflineKeySetId: ByteArray?
val drmOfflineKeySetIdBase64: String?
```

The host app passes `downloadManager`, `downloadCache`, and the saved DRM keySetId only for downloaded/offline playback.

## Online Playback

Online source resolution should follow this order:

1. If `drm == "1"` and `mpdUrl` exists, play `mpdUrl` as Widevine DASH.
2. If `drm != "1"` and `hlsUrl` exists, play `hlsUrl`.
3. If `liveUrl` exists, play `liveUrl`.
4. If `videoUrl` exists, play `videoUrl`.

When `drmToken` is present for online DRM playback, build the Media3 DRM configuration with:

```kotlin
MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
    .setLicenseUri(drmToken)
    .setForceDefaultLicenseUri(true)
    .setMultiSession(true)
    .build()
```

Important: the app should not force `drm = "1"` for normal API-list playback unless it really wants the player to choose MPD DRM. If `drm = "1"` is set, the resolver should choose MPD and the license server must accept the license URL.

## Offline Playback

Before creating the media item, check whether the content has a completed Media3 download:

```kotlin
val download = content.downloadManager
    ?.downloadIndex
    ?.getDownload(content.id.toString())

val completedDownload = download?.takeIf { it.state == Download.STATE_COMPLETED }
```

If a completed download exists:

- Use the app-provided `downloadCache` or `cacheFactory`.
- Use a `CacheDataSource` with `FLAG_BLOCK_ON_CACHE`.
- For HLS and MP4 downloads, use `completedDownload.request.toMediaItem()`.
- For DASH/MPD DRM downloads, rebuild the media item with the offline Widevine `keySetId`.

The offline DRM keySetId restore order should be:

1. `completedDownload.request.keySetId`
2. `content.drmOfflineKeySetId`
3. Base64-decoded `content.drmOfflineKeySetIdBase64`

For offline DASH DRM playback, set the keySetId like this:

```kotlin
MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
    .setKeySetId(keySetId)
    .build()
```

Do not set the online license URL as an offline fallback inside this offline DASH media item. Offline playback should use the persisted Widevine license.

## Recovery And Logs

Add SDK logs around these stages:

- `isOffline=<true|false>, contentId=<id>, downloadExists=<true|false>`
- `Using offline MediaItem`
- `Using online MediaItem: <uri>`
- `DRM_OFFLINE_PLAYBACK_PREPARE contentId=<id> keySetBytes=<size>`
- `DRM_OFFLINE_PLAYBACK_FAILED contentId=<id> reason=missing_keySetId`
- Media3 playback errors with `errorCodeName`, message, and cause class.

Handle these DRM errors explicitly:

- `ERROR_CODE_DRM_CONTENT_ERROR`: offline license missing or unusable.
- `ERROR_CODE_DRM_LICENSE_EXPIRED`: offline license expired.
- `ERROR_CODE_DRM_PROVISIONING_FAILED`: device provisioning failed.

Online fallback may be attempted only when an online playable URL exists. It should not hide the missing-keySetId bug for true offline playback testing.

## Acceptance Checklist

- Online HLS plays when `drm` is not forced to `"1"`.
- Online MPD DRM plays when the host app passes a valid license URL.
- Downloaded HLS plays from the Media3 download cache.
- Downloaded MP4 plays from the Media3 download cache.
- Downloaded DRM MPD plays offline using a persisted keySetId.
- Offline DRM playback still works after app restart.
- Airplane-mode playback works for completed non-DRM downloads.
- Airplane-mode playback works for completed DRM MPD downloads when keySetId exists and the license has not expired.

## Files Mirrored In This Repo

The equivalent implementation in this repo is currently in:

- `videosdk/src/main/java/com/app/videosdk/model/PlayerModel.kt`
- `videosdk/src/main/java/com/app/videosdk/player/PlaybackSourceResolver.kt`
- `videosdk/src/main/java/com/app/videosdk/player/PlayerFactory.kt`
