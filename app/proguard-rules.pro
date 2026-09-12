# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# ---- Readable crash reports ----
# R8 still shrinks and optimises (the APK stays small and unused code is still
# removed), but it does NOT rename classes and methods.
#
# Renaming buys nothing here: this is a FOSS app whose source is public, so
# obfuscation hides no secret. What it does cost is every crash report turning
# into "ak.l(SourceFile:363)", which is only readable with the matching
# mapping.txt of that exact build. Since the app now shows crash reports to
# the user directly, those reports have to be readable on their own -- so no
# mapping file is produced or published any more.
-dontobfuscate

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

# ---- General ----
# Keep file names and line numbers in stack traces. (-renamesourcefileattribute
# is deliberately absent: it would replace LeitnerBoxManager.kt with the
# useless literal "SourceFile" in every trace.)
-keepattributes SourceFile,LineNumberTable
