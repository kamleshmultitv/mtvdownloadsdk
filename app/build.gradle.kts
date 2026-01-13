plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
   /* signingConfigs {
        create("release") {
            storeFile = file("D:\\playersdk\\kxoplayer.jks")
            storePassword = "Poojukamal21*"
            keyAlias = "kxoplayer"
            keyPassword = "Poojukamal21*"
        }
    }*/
    namespace = "com.app.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.app.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {

        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            buildConfigField(
                "String",
                "BASE_URL",
                "\"https://api.artofliving.app/artoflivingapi/v10/\""
            )
            buildConfigField(
                "String",
                "MASTER_JSON_URL",
                "\"https://static.artofliving.app/configration/717/master_prod.json\""
            )
            buildConfigField(
                "String",
                "DRM_LICENSE_URL",
                "\"https://widevine-dash.ezdrm.com/widevine-php/widevine-foreignkey.php?pX=63CF74&\""
            )
            buildConfigField(
                "String",
                "STATIC_AUTH_TOKEN",
                "\"abd07061a3dd9851e3c9dd551e68e26838b29e87b2baa479c0eb53c95cac2e6bd701b5588ca7a85de55c6504e0c84c44edc468ae6fdb7a48cf170ee055cd7b3a5960795cf0c3d2989f1aedec0d93fd9d\""
            )
          //  signingConfig = signingConfigs.getByName("release")
        }

        debug {
          //  signingConfig = signingConfigs.getByName("release") // ✅ REQUIRED
            isMinifyEnabled = false
            isShrinkResources = false

            buildConfigField(
                "String",
                "BASE_URL",
                "\"https://api.artofliving.app/artoflivingapi/v10/\""
            )
            buildConfigField(
                "String",
                "MASTER_JSON_URL",
                "\"https://static.artofliving.app/configration/717/master_prod.json\""
            )
            buildConfigField(
                "String",
                "DRM_LICENSE_URL",
                "\"https://widevine-dash.ezdrm.com/widevine-php/widevine-foreignkey.php?pX=63CF74&\""
            )
            buildConfigField(
                "String",
                "STATIC_AUTH_TOKEN",
                "\"abd07061a3dd9851e3c9dd551e68e26838b29e87b2baa479c0eb53c95cac2e6bd701b5588ca7a85de55c6504e0c84c44edc468ae6fdb7a48cf170ee055cd7b3a5960795cf0c3d2989f1aedec0d93fd9d\""
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11

        isCoreLibraryDesugaringEnabled = true

    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.paging.compose)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.adapter.rxjava2)
    implementation(libs.logging.interceptor)

    // SDK
    implementation(project(":videosdk"))
    implementation(project(":mtvdownloader"))
   //  implementation(libs.mtvplayersdk)

}
