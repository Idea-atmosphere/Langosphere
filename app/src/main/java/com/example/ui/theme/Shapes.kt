package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// A softer, more modern rounded-corner scale used app-wide for buttons,
// cards, dialogs, and bottom sheets (wired into MaterialTheme in Theme.kt).
// The radii are deliberately generous: combined with the glass surfaces in
// ui/components/LangosphereUi.kt this is what gives the app its rounded,
// "liquid" feel rather than a boxy Material default look.
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// The ANIME (toon) shape scale. Rounder than every other design in the app:
// a cartoon sticker is defined by its generous, even radii plus the thick
// ink outline the toon components draw on top (see
// ui/components/anime/ToonPrimitives.kt). Depth in this skin is NEVER a
// Material elevation/tonal surface — it is the hard, zero-blur offset
// shadow painted by Modifier.inkShadow().
val AnimeShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)
