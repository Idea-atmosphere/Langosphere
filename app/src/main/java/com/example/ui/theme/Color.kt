package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ── Default app palette (Material 3 Expressive) ──
// A calm but modern palette built on an indigo / teal / violet triad:
//   • primary   — indigo, used for actions and the brand gradient's start
//   • secondary — teal, used for "content exists" states
//   • tertiary  — violet, the brand gradient's counterpart
// Light mode uses cool off-whites instead of stark white; dark mode uses a
// near-black blue-tinted charcoal that looks much deeper on OLED panels while
// keeping WCAG-friendly text contrast. Designed for long reading sessions, so
// nothing is fully saturated.
// This is the app's DEFAULT color layer. On Android 12+ the theme switches
// to the device's Material You dynamic colors (wallpaper-based) by default;
// these palettes remain the fallback for older devices (and whenever the
// dynamic color option is off), so both variants are kept in sync.
val LightPrimary = Color(0xFF3A5AD4)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFDDE3FF)
val LightOnPrimaryContainer = Color(0xFF101C4F)
val LightSecondary = Color(0xFF2F7D6B)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFCFF0E6)
val LightOnSecondaryContainer = Color(0xFF0A342A)
val LightTertiary = Color(0xFF7A4FD1)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFEBE0FF)
val LightOnTertiaryContainer = Color(0xFF2A1360)
val LightBackground = Color(0xFFF4F6FC)
val LightOnBackground = Color(0xFF181B26)
val LightSurface = Color(0xFFFBFCFF)
val LightOnSurface = Color(0xFF181B26)
val LightSurfaceVariant = Color(0xFFE4E8F5)
val LightOnSurfaceVariant = Color(0xFF474F63)
val LightOutline = Color(0xFF8A93A8)
val LightError = Color(0xFFC0463A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFE3DE)
val LightOnErrorContainer = Color(0xFF3E0F09)

val DarkPrimary = Color(0xFFAFC1FF)
val DarkOnPrimary = Color(0xFF13224F)
val DarkPrimaryContainer = Color(0xFF2B4181)
val DarkOnPrimaryContainer = Color(0xFFDDE3FF)
val DarkSecondary = Color(0xFF82D8C0)
val DarkOnSecondary = Color(0xFF00382C)
val DarkSecondaryContainer = Color(0xFF23594A)
val DarkOnSecondaryContainer = Color(0xFFC5F3E5)
val DarkTertiary = Color(0xFFCDB4FF)
val DarkOnTertiary = Color(0xFF321269)
val DarkTertiaryContainer = Color(0xFF4B3186)
val DarkOnTertiaryContainer = Color(0xFFEBE0FF)
val DarkBackground = Color(0xFF0B0E15)
val DarkOnBackground = Color(0xFFE6E9F5)
val DarkSurface = Color(0xFF12161F)
val DarkOnSurface = Color(0xFFE6E9F5)
val DarkSurfaceVariant = Color(0xFF1F2532)
val DarkOnSurfaceVariant = Color(0xFFBAC2D6)
val DarkOutline = Color(0xFF7C879F)
val DarkError = Color(0xFFF4B4AC)
val DarkOnError = Color(0xFF5A120C)
val DarkErrorContainer = Color(0xFF5E2A25)
val DarkOnErrorContainer = Color(0xFFFFDAD5)

// ── Shared Accent Colors — desaturated versions of common signal colors so long reading/viewing sessions feel less harsh on the eyes ──
val AccentGreen = Color(0xFF52A87A)
val AccentRed = Color(0xFFDD7A6C)
val AccentCyan = Color(0xFF43B4C2)
val AccentIndigo = Color(0xFF7B8AE8)
val AccentAmber = Color(0xFFE1A855)

// ── Subtitle text colors for LIGHT surfaces ──
// The video overlay always sits on a black backdrop, so it keeps using
// White/AccentAmber there. But the subtitle LIST below the player uses the
// theme background, where white text is unreadable in light mode. These are
// the default colors for subtitle text drawn on light surfaces (the user's
// custom SubtitleColorState choice always overrides them).
val SubtitleEnOnLight = Color(0xFF1C1E26)   // near-black slate — ~15:1 contrast on the paper-white background
val SubtitleFaOnLight = Color(0xFF8A5A00)   // deep amber-brown — readable Persian gold on light surfaces
