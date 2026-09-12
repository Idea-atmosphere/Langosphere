// Top-level build file where you can add configuration options common to all sub-projects/modules.
//
// Langosphere is free software only. Do not add plugins here that pull in
// proprietary tooling (Firebase, Google Play Services, Crashlytics); the app
// must stay buildable from source by F-Droid.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
}
