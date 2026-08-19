plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.app.videosdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt")
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    // ✅ Java target (must match Kotlin)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // ✅ Kotlin 2.x compiler options
    kotlin {
        compilerOptions {
            jvmTarget.set(
                org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // ✅ REQUIRED for AGP 8+ + JitPack
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

/**
 * ✅ Maven Publish (KEEP OUTSIDE android {})
 * This publishes ONLY videosdk (not app)
 */

dependencies {
    testImplementation(libs.junit)

    // Core
    api(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.material3.android)

    // Compose
    api(libs.ui)
    api(libs.androidx.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.paging.compose)
    implementation(libs.ui.tooling.preview)
    implementation(libs.androidx.compose.animation.core)
    debugImplementation(libs.ui.tooling)

    // Media3 / ExoPlayer
    api(libs.androidx.media3.common)
    api(libs.androidx.media3.datasource)
    api(libs.androidx.media3.exoplayer)
    api(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.cast)
    implementation(libs.androidx.media3.session)

    // Cast
    implementation(libs.androidx.mediarouter)
    implementation(libs.play.services.cast.framework)

    // Others
    implementation(libs.google.gson)
    implementation(libs.google.accompanist.systemuicontroller)
    implementation(libs.coil.compose)

    implementation(libs.androidx.media3.exoplayer.ima)
    implementation(libs.interactivemedia)
    implementation(libs.play.services.ads)
    implementation(libs.androidx.media3.transformer)

}
