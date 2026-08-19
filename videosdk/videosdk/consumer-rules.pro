####################################
# KEEP GENERIC TYPE INFORMATION (CRITICAL)
####################################
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes *Annotation*

####################################
# KEEP SDK PUBLIC API
####################################
-keep class com.app.videosdk.** { *; }

####################################
# KEEP SDK MODELS (Gson-safe)
####################################
-keep class com.app.videosdk.model.** {
    <fields>;
    <init>(...);
}

####################################
# GOOGLE IMA SDK (MANDATORY)
####################################
-keep class com.google.ads.interactivemedia.** { *; }
-dontwarn com.google.ads.interactivemedia.**

####################################
# MEDIA3 / EXOPLAYER
####################################
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

####################################
# GOOGLE CAST
####################################
-keep class com.google.android.gms.cast.** { *; }
-dontwarn com.google.android.gms.cast.**

####################################
# GSON (minimal & safe)
####################################
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-dontwarn com.google.gson.**

####################################
# KOTLIN METADATA (SAFE)
####################################
-keep class kotlin.Metadata { *; }

####################################
# COMPOSE (SDK SAFETY)
####################################
-dontwarn androidx.compose.**
