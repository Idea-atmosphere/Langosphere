package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ── Default app palette (Material 3 Expressive) ──
// A calm, modern palette: cool off-whites instead of stark white, muted
// blue/teal/violet accents, and soft deep-blue charcoal in dark mode.
// Designed for long sessions: no aggressive saturated colors, WCAG-friendly
// contrast between text and surfaces in both light and dark variants.
// This is the app's DEFAULT color layer. On Android 12+ the theme switches
// to the device's Material You dynamic colors (wallpaper-based) by default;
// these palettes remain the fallback for older devices (and whenever the
// dynamic color option is off), so both variants are kept in sync.
val LightPrimary = Color(0xFF315FA0)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD7E5F8)
val LightOnPrimaryContainer = Color(0xFF0F2D54)
val LightSecondary = Color(0xFF3F7D6E)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFD5EFE8)
val LightOnSecondaryContainer = Color(0xFF12382F)
val LightTertiary = Color(0xFF6F5AA8)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFE9E0F6)
val LightOnTertiaryContainer = Color(0xFF2A1B4A)
val LightBackground = Color(0xFFF3F6FA)
val LightOnBackground = Color(0xFF1C2430)
val LightSurface = Color(0xFFFAFCFF)
val LightOnSurface = Color(0xFF1C2430)
val LightSurfaceVariant = Color(0xFFE3E9F2)
val LightOnSurfaceVariant = Color(0xFF4A5568)
val LightOutline = Color(0xFF8B97A8)
val LightError = Color(0xFFBE4B3F)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFBE3DF)
val LightOnErrorContainer = Color(0xFF3E100A)

val DarkPrimary = Color(0xFF9DB8F2)
val DarkOnPrimary = Color(0xFF10274E)
val DarkPrimaryContainer = Color(0xFF274B80)
val DarkOnPrimaryContainer = Color(0xFFD6E3FF)
val DarkSecondary = Color(0xFF8CD3BE)
val DarkOnSecondary = Color(0xFF0E352C)
val DarkSecondaryContainer = Color(0xFF2C5549)
val DarkOnSecondaryContainer = Color(0xFFC0EFE2)
val DarkTertiary = Color(0xFFC3ACEA)
val DarkOnTertiary = Color(0xFF2E1B52)
val DarkTertiaryContainer = Color(0xFF4A3672)
val DarkOnTertiaryContainer = Color(0xFFE7DDFF)
val DarkBackground = Color(0xFF10141C)
val DarkOnBackground = Color(0xFFE7EBF3)
val DarkSurface = Color(0xFF161B26)
val DarkOnSurface = Color(0xFFE7EBF3)
val DarkSurfaceVariant = Color(0xFF232A38)
val DarkOnSurfaceVariant = Color(0xFFB9C2D2)
val DarkOutline = Color(0xFF7E8AA0)
val DarkError = Color(0xFFF2B8B2)
val DarkOnError = Color(0xFF601410)
val DarkErrorContainer = Color(0xFF5C2B28)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

// ── Shared Accent Colors — desaturated versions of common signal colors so long reading/viewing sessions feel less harsh on the eyes ──
val AccentGreen = Color(0xFF5FA777)
val AccentRed = Color(0xFFD97A6C)
val AccentCyan = Color(0xFF4FB3BF)
val AccentIndigo = Color(0xFF7B87D6)
val AccentAmber = Color(0xFFE0A85A)

// ── Subtitle text colors for LIGHT surfaces ──
// The video overlay always sits on a black backdrop, so it keeps using
// White/AccentAmber there. But the subtitle LIST below the player uses the
// theme background, where white text is unreadable in light mode. These are
// the default colors for subtitle text drawn on light surfaces (the user's
// custom SubtitleColorState choice always overrides them).
val SubtitleEnOnLight = Color(0xFF1C1E26)   // near-black slate — ~15:1 contrast on the paper-white background
val SubtitleFaOnLight = Color(0xFF8A5A00)   // deep amber-brown — readable Persian gold on light surfaces
