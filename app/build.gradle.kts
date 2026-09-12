plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

// Release signing is driven purely by environment variables, so no keystore,
// password or alias ever lives in the repository or in a Gradle file.
//
// When those variables are absent -- local development, a fork, or the
// pull-request CI job, which deliberately has access to no secrets at all --
// `canSignRelease` is false and the release build simply produces an unsigned
// APK instead of failing at configuration time.
val keystorePath = providers.environmentVariable("KEYSTORE_PATH").orNull
val storePasswordEnv = providers.environmentVariable("STORE_PASSWORD").orNull
val keyAliasEnv = providers.environmentVariable("KEY_ALIAS").orNull
val keyPasswordEnv = providers.environmentVariable("KEY_PASSWORD").orNull
val canSignRelease =
  !keystorePath.isNullOrBlank() &&
    !storePasswordEnv.isNullOrBlank() &&
    !keyAliasEnv.isNullOrBlank() &&
    !keyPasswordEnv.isNullOrBlank() &&
    file(keystorePath).exists()

// R8 code shrinking/obfuscation and resource shrinking stay ON for real
// releases. They can be disabled for a single build with:
//
//   ./gradlew -PenableMinify=false assembleRelease
//
// That is how the Release workflow's manual run isolates a crash that only
// reproduces in a shrunk build: if the unshrunk release APK runs fine, the
// cause is a missing keep rule in proguard-rules.pro; if it crashes too, the
// cause is in the application code and the stack trace is already readable.
// Do not flip the default to false -- an unshrunk public release ships every
// unused class and every original name.
val minifyRelease =
  providers.gradleProperty("enableMinify").map { it.toBoolean() }.getOrElse(true)

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.langosphere"
    minSdk = 24
    targetSdk = 36

    // The release workflow derives these from the git tag, e.g. tag v0.0.6 ->
    //   ./gradlew -PappVersionName=0.0.6 -PappVersionCode=6 assembleRelease
    // The fallbacks below are only used for local and debug builds.
    versionCode = providers.gradleProperty("appVersionCode").map(String::toInt).getOrElse(4)
    versionName = providers.gradleProperty("appVersionName").getOrElse("0.0.4")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    if (canSignRelease) {
      create("release") {
        storeFile = file(keystorePath!!)
        storePassword = storePasswordEnv
        keyAlias = keyAliasEnv
        keyPassword = keyPasswordEnv
        // v1 (JAR) signing is obsolete and only weakens the APK.
        enableV1Signing = false
        enableV2Signing = true
        enableV3Signing = true
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = minifyRelease
      isShrinkResources = minifyRelease
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = if (canSignRelease) signingConfigs.getByName("release") else null
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Langosphere is free software only: there is a single build, with no
// proprietary flavor and no closed-source dependency, so it can be built from
// source by F-Droid. Please keep it that way -- do not add Firebase, Google
// Play Services, or any other non-free artifact here.
//
// The AI assistant talks to an OpenAI-compatible endpoint whose base URL, model
// and API key are supplied by the user at runtime (see AiService.TranslationConfig).
// No API key is ever compiled into the app.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.exoplayer.dash)
  implementation(libs.jsoup)
  implementation(libs.pdfbox.android)
  implementation(libs.documentfile)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
