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
