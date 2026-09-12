package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Langosphere", appName)
  }

  // This case asserts nothing: it launches the real MainActivity and prints
  // its semantics tree, which was useful while writing the UI by hand. Under
  // Robolectric onRoot() cannot resolve a single compose root for the full
  // activity, so printToString() throws and the case can never pass.
  //
  // It is kept, disabled, instead of deleted, because the underlying question
  // (does the whole app tree compose from a cold start?) is worth answering.
  // The right place for that is an instrumented test in app/src/androidTest,
  // running on a real device or emulator, where the activity, its windows and
  // its dialogs behave normally.
  @Ignore("Debug-only print; needs an instrumented test, not Robolectric.")
  @Test
  fun `start main activity and print semantics`() {
    ShadowLog.stream = System.out

    // allow some composition to happen
    composeTestRule.waitForIdle()

    val semanticTree = composeTestRule.onRoot().printToString()
    println("SEMANTICS: $semanticTree")
  }
}
