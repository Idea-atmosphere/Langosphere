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

// Unofficial test builds (.github/workflows/test-build.yml):
//
//   ./gradlew -PtestBuild=true [-PtestBuildId=r15.7d99234] assembleRelease
//
// turn the output into a separate app -- "Langosphere TEST", applicationId
// com.langosphere.test, always versionCode 1 / versionName "1-test.<id>" --
// so it installs NEXT TO the real app with its own data, and every new test
// build is simply reinstalled over the previous one. Test builds are signed
// with a dedicated test key, never the release key. Off by default; a normal
// or tagged release build never enters this path. Never set it for a release.
val isTestBuild =
  providers.gradleProperty("testBuild").map { it.toBoolean() }.getOrElse(false)
val testBuildId = providers.gradleProperty("testBuildId").orNull

android {
  namespace = "com.example"
  // 37 is required by androidx.core 1.19+ and Compose 1.12+. targetSdk is
  // unchanged, so runtime behaviour on devices does not change.
  compileSdk { version = release(37) }

  defaultConfig {
    applicationId = "com.langosphere"
    minSdk = 24
    targetSdk = 36

    // The release workflow derives these from the git tag, e.g. tag v0.0.6 ->
    //   ./gradlew -PappVersionName=0.0.6 -PappVersionCode=6 assembleRelease
    // The fallbacks below are only used for local and debug builds. Keep them
    // in sync with the newest released tag so the in-app updater (which
    // compares this value with the latest GitHub release) does not offer a
    // downgrade on a freshly checked-out debug build.
    versionCode = providers.gradleProperty("appVersionCode").map(String::toInt).getOrElse(98)
    versionName = providers.gradleProperty("appVersionName").getOrElse("0.0.98")
    if (isTestBuild) {
      // One fixed version for every test build: Android accepts an install
      // over the same versionCode, so each test build replaces the last one.
      // (SemanticVersion reads this as 1.0.0, which is above every real
      // release, so the in-app updater never offers a "downgrade".)
      versionCode = 1
      versionName = "1"
    }

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
    if (isTestBuild) {
      configureEach {
        applicationIdSuffix = ".test"
        versionNameSuffix = if (testBuildId.isNullOrBlank()) "-test" else "-test.$testBuildId"
        // The debug variant signs with the same test key, so debug and
        // release test builds can be installed over each other.
        if (canSignRelease) signingConfig = signingConfigs.getByName("release")
      }
    }
  }
  if (isTestBuild) {
    // Launcher label "Langosphere TEST" for both locales. Build-type source
    // sets take priority over src/main/res, so this overrides app_name without
    // touching the real strings.
    sourceSets {
      getByName("debug") { res.srcDir("src/unofficial/res") }
      getByName("release") { res.srcDir("src/unofficial/res") }
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
  // HLS is what Piped/Invidious hand out for streams that have no muxed
  // progressive file (the Online tab), so the player needs the HLS module.
  implementation(libs.androidx.media3.exoplayer.hls)
  // Document-start script injection into the YouTube embed frame (hides the
  // embed's own title bar / watermark / end screen). AndroidX, Apache-2.0.
  implementation(libs.androidx.webkit)
  // Thumbnails / channel avatars in the Online tab (Apache-2.0).
  implementation(libs.coil.compose)
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
