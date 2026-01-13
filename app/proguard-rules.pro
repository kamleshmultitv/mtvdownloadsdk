####################################
# KEEP GENERIC TYPE INFORMATION
# (THIS FIXES YOUR CRASH)
####################################
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

####################################
# KEEP LINE NUMBERS (you already had this)
####################################
-keepattributes SourceFile,LineNumberTable

####################################
# GSON (generic parsing safety)
####################################
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

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
# YOUR VIDEO SDK (public + internal)
####################################
-keep class com.app.videosdk.** { *; }
-dontwarn com.app.videosdk.**

####################################
# Kotlin metadata (safe)
####################################
-keep class kotlin.Metadata { *; }


