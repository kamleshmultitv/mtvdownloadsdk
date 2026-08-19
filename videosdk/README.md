# 🎬 Mtv Video Player SDK (Android)

A modern **Android Video Player SDK** built with **Media3** and **Jetpack Compose**, designed for high‑performance video playback, reels, and feed‑based experiences.

---

## ✨ Features

* ▶️ HLS & DASH playback
* 🔐 Widevine DRM support
* 🎨 Jetpack Compose–based UI
* 🪟 Picture‑in‑Picture (PiP)
* 🔳 Fullscreen playback
* 📝 Subtitles (SRT)
* ⏩ Playback speed & quality selection
* IMA ads
* Dynamic OTT age-rating overlay sourced from the video API model

---

## 📦 Installation

### 1. Add JitPack Repository

In your **project‑level `settings.gradle` or `build.gradle`**:

```gradle
repositories {
    maven { url "https://jitpack.io" }
}
```

### 2. Add SDK Dependency

```gradle
dependencies {
    implementation "com.github.kamleshmultitv:mtvplayersdk:mobile-1.0.42"
}
```

---

## ⚙️ Android Setup (Required)

### Permissions

Add in **AndroidManifest.xml**:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### Enable Picture‑in‑Picture

```xml
<activity
    android:name=".MainActivity"
    android:supportsPictureInPicture="true"
    android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation" />
```

---

## 🎨 Jetpack Compose Setup

```gradle
android {
    buildFeatures {
        compose true
    }
 
}
```

## ⚠️ Required for IMA Ads support

If you use Ads (IMA), enable core library desugaring in your app level gradle:
```gradle
compileOptions {
coreLibraryDesugaringEnabled true
}

dependencies {
coreLibraryDesugaring "com.android.tools:desugar_jdk_libs:2.0.4"
}

```

---

## 🧩 SDK Composable API

```kotlin
@OptIn(UnstableApi::class)
@Composable
fun MtvVideoPlayerSdk(
    contentList: List<PlayerModel>? = null,
    index: Int? = 0,
    pipListener: PipListener? = null,
    onPlayerBack: (Boolean) -> Unit,
    setFullScreen: (Boolean) -> Unit
)
```

---

## 📦 PlayerModel

```kotlin
data class PlayerModel(
    val id: String? = null,
    val title: String? = null,
    val videoUrl: String? = null,
    val thumbnail: String? = null,
    val ageRating: String? = null,
    val contentRating: String? = null, // supported backend alias
    val hlsUrl: String? = null,
    val mpdUrl: String? = null,
    val liveUrl: String? = null,
    val drmToken: String? = null,
    val imageUrl: String? = null,
    val title: String? = null,
    val description: String? = null,
    val seasonTitle: String? = null,
    val seasonDescription: String? = null,
    val srt: String? = null,
    val spriteUrl: String? = null,
    val playbackSpeed: Float = 1.0f,
    val selectedSubtitle: String? = null,
    val selectedVideoQuality: Int = 1080,
    val isLive: Boolean = false,
    val adsConfig: AdsConfig? = null,
    val cuePoints: List<CuePoint> = emptyList()
)
```

The SDK displays `ageRating` exactly as received. If it is blank or absent,
`contentRating` is checked as the alternate API field. If neither contains text,
no badge is rendered. There is no default rating and no rating mapping.

---

## ▶️ SDK Usage (Compose)

Use `MtvVideoPlayerSdk` to play videos using a content list with full control over PiP, fullscreen, and navigation.

### Example Usage

```kotlin
MtvVideoPlayerSdk(
    contentList = contentList,
    index = selectedIndex.intValue,
    pipListener = pipListener,
    onPlayerBack = { /* handle back */ },
    setFullScreen = { isFullscreen ->
        // handle fullscreen change
    }
)
```

To replace the active video imperatively, pass a `PlayerController` to the composable:

```kotlin
val player = remember { PlayerController() }

MtvVideoPlayerSdk(
    controller = player,
    onPlayerBack = {},
    setFullScreen = {}
)

player.play(
    PlayerModel(
        id = apiVideo.id,
        title = apiVideo.title,
        videoUrl = apiVideo.videoUrl,
        thumbnail = apiVideo.thumbnail,
        ageRating = apiVideo.ageRating
    )
)
```

### XML / View usage

```xml
<com.app.videosdk.ui.MtvVideoPlayerView
    android:id="@+id/player"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

```kotlin
binding.player.play(video)

override fun onPictureInPictureModeChanged(inPip: Boolean) {
    super.onPictureInPictureModeChanged(inPip)
    binding.player.setInPictureInPictureMode(inPip)
}
```

Both player surfaces wait 2.5 seconds after playback starts, reveal the classification
from left to right, keep it visible for five seconds, collapse it from right to left,
then restore the content title in the same top-bar slot. The rating and title are never
shown together. The rating shows again after a replay and is suppressed in PiP. The
standalone `PlayerAgeRatingOverlay` composable and `PlayerAgeRatingOverlayView` are
also public for custom player layouts.

---

## 🪟 Picture‑in‑Picture

```kotlin
onEnterPip = {
    activity.enterPictureInPictureMode()
}
```

---

## 🧪 Supported Formats

* HLS (`.m3u8`)
* DASH (`.mpd`)
* MP4
* Widevine DRM streams

---

## ✅ Requirements

* Android API 24+
* Kotlin
* Jetpack Compose
* Media3

---

## 🛡️ Proguard (app-specific)

```proguard
# App-specific ProGuard rules only

# Keep line numbers for crash reports (optional)
-keepattributes SourceFile,LineNumberTable

# Add app-only rules here if needed

# ================= R8 AUTO-GENERATED MISSING RULES =================
# Generated by Android Gradle Plugin – safe to keep

-dontwarn com.app.videosdk.listener.PipListener
-dontwarn com.app.videosdk.model.PlayerModel
-dontwarn com.app.videosdk.ui.MtvVideoPlayerSdkKt
```

---

## 🤝 Support

* GitHub Issues
* SDK Support Team

---

🚀 **Mtv Video Player SDK** – Built for scalable, high‑performance Android video experiences.
