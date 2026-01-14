####################################
# KEEP GENERIC TYPE INFORMATION (CRITICAL)
####################################
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes *Annotation*


####################################
# KEEP SDK MODELS (Gson-safe)
####################################
-keep class com.app.mtvdownloader.model.** {
    <fields>;
    <init>(...);
}


####################################
# MEDIA3 / EXOPLAYER (DOWNLOAD SAFE)
####################################
-keep class androidx.media3.exoplayer.offline.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.scheduler.** { *; }
-dontwarn androidx.media3.**


####################################
# DOWNLOAD SERVICE / WORKERS (CRITICAL)
####################################
-keep class * extends androidx.media3.exoplayer.offline.DownloadService
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker


####################################
# GSON (MINIMAL & SAFE)
####################################
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-dontwarn com.google.gson.**


####################################
# KOTLIN / COROUTINES (SAFE)
####################################
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**


####################################
# COMPOSE (SDK SAFETY)
####################################
-dontwarn androidx.compose.**
