# 📥 MTV Downloader SDK (Android)

[![JitPack](https://jitpack.io/v/kamleshmultitv/mtv-downloader.svg)](https://jitpack.io/#kamleshmultitv/mtv-downloader)

A modern Android Download SDK built with Media3 and Jetpack Compose, designed for reliable background downloads, HLS quality selection, and a fully customizable UI.

## ✨ Features

*   📥 **HLS Video Downloads**: Efficiently download HLS streams.
*   🎚 **Quality Selection**: Supports both "Auto" and custom quality selection.
*   📊 **Progress Tracking**: Real-time download progress updates.
*   ⏯ **Download Controls**: Pause, resume, and cancel downloads.
*   🧵 **Queue Management**: Manages a queue of downloads.
*   🔔 **Foreground Service**: Reliable downloads using a foreground service.
*   🎨 **Customizable UI**: Default UI is provided, but it's fully customizable.
*   🖼 **Custom Icons**: Provide custom icons for different download states.
*   🧠 **Robust Background Handling**: Safely handles downloads in the background and when the app is killed.
*   🧩 **Compose-first API**: Designed with Jetpack Compose in mind.

## 📦 Installation

1.  **Add JitPack Repository**

    In your project-level `settings.gradle` or `build.gradle`:

    ```groovy
    repositories {
        maven { url "https://jitpack.io" }
    }
    ```

2.  **Add SDK Dependency**

    ```groovy
    dependencies {
        implementation "com.github.kamleshmultitv:mtv-downloader:<latest-version>"
    }
    ```
    *Replace `<latest-version>` with the latest version from JitPack.*

## ⚙️ Android Setup (Required)

### Permissions

Add the following permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

⚠️ **Android 13+**: The `POST_NOTIFICATIONS` permission must be requested at runtime.

### Jetpack Compose

Enable Jetpack Compose in your `build.gradle`:

```groovy
android {
    buildFeatures {
        compose true
    }
}
```

## 🚀 Quick Start (Default Setup)

For the simplest integration, the SDK can handle everything automatically.

```kotlin
DownloadButton(
    contentItem = downloadModel
)
```

This setup uses:
*   ✔ Default icons
*   ✔ Default quality selector
*   ✔ SDK-managed download state

### DownloadModel

You must provide a valid `DownloadModel` to the SDK.

**Example:**
```kotlin
val downloadModel = DownloadModel(
    id = "content_123",
    title = "Sample Video",
    hlsUrl = "https://example.com/stream.m3u8",
    imageUrl = "",
    isLive = false
)
```

### Example Content Card (Compose)

Here is a recommended usage pattern inside lists, feeds, or reels.

```kotlin
@Composable
fun ContentCard(
    content: ContentItem,
    onPlay: () -> Unit
) {
    val context = LocalContext.current

    val downloadModel = remember(content) {
        buildDownloadContentList(context, content)
    } ?: return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .clickable { onPlay() },
            text = content.title,
            fontSize = 16.sp
        )

        DownloadButton(contentItem = downloadModel)
    }
}
```

## 🛠️ Customization (Optional)

### Custom Quality Selector

You can provide your own bottom sheet or dialog for quality selection.

**SDK Contract:**
```kotlin
typealias CustomQualitySelector =
@Composable (
    qualities: List<DownloadQuality>,
    onSelect: (DownloadQuality) -> Unit,
    onDismiss: () -> Unit
) -> Unit
```

**Usage:**
```kotlin
DownloadButton(
    contentItem = downloadModel,
    customQualitySelector = { qualities, onSelect, onDismiss ->
        CustomQualitySelectorBottomSheet(
            qualities = qualities,
            onDismiss = onDismiss,
            onQualitySelected = onSelect
        )
    }
)
```
📌 If `customQualitySelector` is not provided, the SDK uses its default quality selector UI.

### Custom Download Icons

Override the default download icons based on the download status.

**Icon Provider Interface:**
```kotlin
fun interface DownloadIconProvider {
    fun iconFor(status: Int?): Int
}
```

**Usage:**
```kotlin
DownloadButton(
    contentItem = downloadModel,
    iconProvider = DownloadIconProvider { status ->
        when (status) {
            DownloadWorker.DOWNLOAD_STATUS_DOWNLOADING -> R.drawable.ic_downloading
            DownloadWorker.DOWNLOAD_STATUS_COMPLETED -> R.drawable.ic_download_done
            else -> R.drawable.ic_download
        }
    }
)
```
📌 If `iconProvider` is not provided, the SDK's default icons are used.

## 🔁 Download States

The SDK manages the following download states:

| State       | Description         |
|-------------|---------------------|
| `QUEUED`      | Waiting in queue    |
| `DOWNLOADING` | Actively downloading|
| `PAUSED`      | User paused         |
| `COMPLETED`   | Download finished   |
| `FAILED`      | Download failed     |


## 🧠 Architecture Overview

*   **Media3 DownloadManager**: Core download engine.
*   **Foreground DownloadService**: Ensures downloads continue in the background.
*   **Room Database**: For persistence of download state.
*   **WorkManager**: Fallback for ensuring download consistency.
*   **Compose-based UI**: Modern, declarative UI.
*   **SDK-owned State Management**: The client app does not need to manage download threads, services, notifications, or storage paths.

## ⚠️ Important Notes

*   Always use `applicationContext` internally.
*   Do not recreate `DownloadButton` with different IDs for the same content.
*   Ensure HLS URLs are reachable.
*   For DRM content, a valid DRM token is required.

## 🧪 Testing Checklist

The SDK is designed to handle the following scenarios safely:
*   ✔ App backgrounded
*   ✔ App killed
*   ✔ Device reboot
*   ✔ Network switch (Wi-Fi ↔ Mobile)
*   ✔ Multiple downloads queued

## 📚 FAQ

**❓ Do I need to manage notifications?**

No. The SDK handles notifications automatically.

**❓ Can downloads resume after an app restart?**

Yes. Downloads persist across app restarts.

**❓ Can the app icon be hidden?**

If installed as a system app, the icon can be hidden (this is an OS-level feature).

## 🛣️ Roadmap

*   Remember last selected quality
*   Auto-best quality selection
*   Download analytics hooks
*   DRM license refresh
*   Lottie animated icons
*   XML (View-based) support

## 🤝 Support

*   [GitHub Issues](https://github.com/kamleshmultitv/mtv-downloader/issues)
*   SDK Support Team

## 📄 License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details.

---

🚀 **MTV Downloader SDK** – Built for reliable, scalable, and customizable Android downloads.
