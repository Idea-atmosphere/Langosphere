package com.example.ui.components.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AnimeColors
import com.example.ui.theme.AppDesignStyle
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot coverage for the toon primitives, in both light and dark.
 *
 * These are the three pieces every anime screen is assembled from — a
 * button, a card and the tab bar — so a diff here catches a regression in
 * the ink border, the hard shadow or the halftone before it reaches a
 * screen. Follows the same Roborazzi pattern as GreetingScreenshotTest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [35])
class ToonScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Composable
    private fun ToonShowcase() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ToonButton(text = "Open PDF", onClick = {}, fill = AnimeColors.Sakura)
            ToonCard(fill = AnimeColors.SunnySoft) {
                Text("Manga page", style = MaterialTheme.typography.titleMedium)
                Text("Tap any word", style = MaterialTheme.typography.bodyMedium)
            }
            ToonChip(text = "EN", selected = true, fill = AnimeColors.Sky)
            ToonProgress(progress = 0.45f)
            ToonTabBar(
                items = listOf(
                    ToonTabItem("Reader", Icons.Outlined.MenuBook),
                    ToonTabItem("Player", Icons.Filled.PlayArrow),
                    ToonTabItem("Sora", Icons.Filled.Style, avatar = true),
                    ToonTabItem("Leitner", Icons.Filled.Style),
                ),
                indicatorPosition = { 0f },
                onTabSelected = {},
            )
        }
    }

    private fun capture(mode: AppThemeMode, file: String) {
        composeTestRule.setContent {
            MyApplicationTheme(
                themeMode = mode,
                designStyle = AppDesignStyle.ANIME,
                // The toon skin never follows the wallpaper; pinning it off
                // here also keeps the screenshots deterministic.
                dynamicColor = false,
            ) {
                ToonShowcase()
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = file)
    }

    @Test
    fun toonPrimitives_light() {
        capture(AppThemeMode.LIGHT, "src/test/screenshots/toon_primitives_light.png")
    }

    @Test
    fun toonPrimitives_dark() {
        capture(AppThemeMode.DARK, "src/test/screenshots/toon_primitives_dark.png")
    }
}
