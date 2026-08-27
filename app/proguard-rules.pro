# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# ---- Moshi ----
-keep class com.squareup.moshi.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.example.model.** { *; }

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# ---- Kotlin Coroutines ----
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ---- OkHttp ----
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ---- ExoPlayer / Media3 ----
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ---- Jsoup ----
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# ---- PDFBox Android ----
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# ---- Firebase (BOM declared but may be used later) ----
-dontwarn com.google.firebase.**

# ---- General ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
