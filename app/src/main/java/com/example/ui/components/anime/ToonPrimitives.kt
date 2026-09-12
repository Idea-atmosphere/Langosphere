package com.example.ui.components.anime

import android.provider.Settings
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AnimeColors
import com.example.ui.theme.toonOutlineStroke

/**
 * The ANIME (toon) building blocks — the anatomy of the cartoon skin.
 *
 * Everything in this design is made of four ingredients, and each one has a
 * primitive here:
 *
 *  1. **Ink outline** — every surface is drawn around with a thick
 *     dark-indigo line ([Modifier.inkBorder]). 3dp on cards/buttons/sheets,
 *     2dp on chips and small controls.
 *  2. **Hard shadow** — depth is never a blur. It is a solid copy of the
 *     shape offset down-right with zero blur ([Modifier.inkShadow]). Cards
 *     and buttons use 4dp, chips 3dp. Material elevation and tonal
 *     elevation are never used in this skin.
 *  3. **Halftone** — the manga screentone: a grid of small ink dots painted
 *     behind headers, the tab bar and the app-bar band ([Modifier.halftone]).
 *  4. **Pop motion** — a stiff, low-damping spring. Pressing a surface
 *     scales it to 0.94 and pushes it *into* its own shadow: the surface
 *     translates down-right while the shadow shrinks to 1dp
 *     ([Modifier.toonPress]).
 *
 * Colors always come from `MaterialTheme.colorScheme` so light/dark is
 * handled by the scheme (see animeLightColors() / animeDarkColors()): `outline`
 * carries the ink, and every `on*` role that lands on a pastel is Ink — the
 * skin never puts white text on a pastel fill.
 */

// ── Motion tokens ──

/** The "pop" spring: enter animations and press feedback. */
fun <T> toonPopSpring() = spring<T>(dampingRatio = 0.55f, stiffness = 400f)

/** The softer spring used for layout changes (sliding indicators, reflow). */
fun <T> toonLayoutSpring() = spring<T>(dampingRatio = 0.7f, stiffness = 250f)

/**
 * True when the user (or the system) has animations switched off.
 *
 * The toon skin leans hard on never-ending idle loops — a bobbing mascot,
 * pulsing sparkles, floating hero art — which is exactly the kind of motion
 * that has to stop when `Settings.Global.ANIMATOR_DURATION_SCALE` is 0.
 * Every infinite transition in this package is gated on this.
 */
@Composable
fun reduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f) == 0f
    }
}

// ── Color helpers ──

/** The ink color for outlines/shadows in the current mode. */
@Composable
@ReadOnlyComposable
fun toonInk(): Color = MaterialTheme.colorScheme.outline

/** The default card/sheet fill: white on paper, Night2 on the dark canvas. */
@Composable
@ReadOnlyComposable
fun toonSurface(): Color = MaterialTheme.colorScheme.surface

/** Body/label color that always sits legibly on a toon fill. */
@Composable
@ReadOnlyComposable
fun toonOnFill(): Color = MaterialTheme.colorScheme.onSurface

/** True while the toon skin is rendering its dark (night) canvas. */
@Composable
@ReadOnlyComposable
fun toonIsNight(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * Picks the legible content color for anything drawn on [fill].
 *
 * The toon skin's rule is "ink on pastel, never white", but that only holds
 * while the fill is actually light. In dark mode the soft washes are deep
 * (see [toonSoft]) and ink-on-deep-plum would be unreadable.
 *
 * Rather than guess from a luminance threshold — which misclassifies the
 * mid-tone accents like Sakura and Lavender — this computes the actual WCAG
 * contrast ratio of both candidates and returns the winner. A new fill can
 * therefore never silently ship an unreadable label.
 */
@Composable
@ReadOnlyComposable
fun toonOn(fill: Color): Color =
    if (contrastRatio(AnimeColors.Ink, fill) >= contrastRatio(AnimeColors.OnNight, fill)) {
        AnimeColors.Ink
    } else {
        AnimeColors.OnNight
    }

/** WCAG 2.1 relative-luminance contrast ratio between two opaque colors. */
private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05f) / (lo + 0.05f)
}

/**
 * Resolves one of the palette's soft washes for the current theme.
 *
 * The `*Soft` constants are near-white tints meant for the paper canvas.
 * Used as-is at night they become glaring light patches that also break
 * every label drawn on them, so this maps each to its deep `*SoftDark`
 * counterpart — same hue, same role, correct luminance.
 */
@Composable
@ReadOnlyComposable
fun toonSoft(soft: Color): Color = if (!toonIsNight()) soft else when (soft) {
    AnimeColors.SakuraSoft -> AnimeColors.SakuraSoftDark
    AnimeColors.SkySoft -> AnimeColors.SkySoftDark
    AnimeColors.SunnySoft -> AnimeColors.SunnySoftDark
    AnimeColors.MintSoft -> AnimeColors.MintSoftDark
    AnimeColors.LavenderSoft -> AnimeColors.LavenderSoftDark
    AnimeColors.Paper -> AnimeColors.Night2
    AnimeColors.Paper2 -> AnimeColors.Night3
    else -> soft
}

// ── Modifiers ──

/**
 * Thick ink outline. Prefer this over [Modifier.border] so every toon
 * surface picks up the same width scale and the same ink color source.
 */
@Composable
fun Modifier.inkBorder(
    width: Dp = 3.dp,
    shape: Shape = RoundedCornerShape(20.dp),
    color: Color = toonInk(),
): Modifier = border(width, color, shape)

/**
 * The hard, zero-blur offset shadow. A solid copy of [shape] is painted
 * behind the content, pushed [offset] down and to the right (mirrored to
 * down-left in RTL so the light source stays consistent with the layout).
 *
 * The outline is resolved inside [drawWithCache], so the [Path]/[Outline] is
 * built once per size change instead of on every frame.
 */
@Composable
fun Modifier.inkShadow(
    offset: Dp = 4.dp,
    shape: Shape = RoundedCornerShape(20.dp),
    color: Color = toonInk(),
): Modifier {
    val direction = LocalLayoutDirection.current
    return this.drawWithCache {
        val outline = shape.createOutline(size, direction, this)
        val dx = if (direction == LayoutDirection.Rtl) -offset.toPx() else offset.toPx()
        val dy = offset.toPx()
        onDrawBehind {
            if (offset > 0.dp) {
                translate(left = dx, top = dy) {
                    drawToonOutline(outline, color)
                }
            }
        }
    }
}

/** Paints a resolved [Outline] in a flat color (no blur, no gradient). */
private fun DrawScope.drawToonOutline(outline: Outline, color: Color) {
    when (outline) {
        is Outline.Rectangle -> drawRect(
            color = color,
            topLeft = Offset(outline.rect.left, outline.rect.top),
            size = Size(outline.rect.width, outline.rect.height),
        )
        is Outline.Rounded -> {
            val rr = outline.roundRect
            drawRoundRect(
                color = color,
                topLeft = Offset(rr.left, rr.top),
                size = Size(rr.width, rr.height),
                cornerRadius = CornerRadius(rr.topLeftCornerRadius.x, rr.topLeftCornerRadius.y),
            )
        }
        is Outline.Generic -> drawPath(outline.path, color)
    }
}

/**
 * Press-into-the-shadow feedback: the surface scales to 0.94 and slides
 * (+2dp, +2dp) toward its own shadow, while hover (Chromebook / mouse) lifts
 * it (-2dp, -2dp) instead. Pair it with [inkShadow] driven by
 * [toonShadowOffset] so the shadow shrinks as the surface presses down.
 */
@Composable
fun Modifier.toonPress(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val active = enabled && pressed
    val lifted = enabled && !pressed && hovered

    val scale by animateFloatAsState(
        targetValue = if (active) 0.94f else 1f,
        animationSpec = toonPopSpring(),
        label = "toon-press-scale",
    )
    val shift by animateDpAsState(
        targetValue = when {
            active -> 2.dp
            lifted -> (-2).dp
            else -> 0.dp
        },
        animationSpec = toonPopSpring(),
        label = "toon-press-shift",
    )
    val direction = LocalLayoutDirection.current
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        translationX = if (direction == LayoutDirection.Rtl) -shift.toPx() else shift.toPx()
        translationY = shift.toPx()
    }
}

/**
 * The shadow offset that goes with [toonPress]: the resting [resting] size,
 * shrunk to 1dp while pressed and grown to 6dp while hovered.
 */
@Composable
fun toonShadowOffset(
    interactionSource: MutableInteractionSource,
    resting: Dp = 4.dp,
    enabled: Boolean = true,
): Dp {
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val target = when {
        !enabled -> resting
        pressed -> 1.dp
        hovered -> 6.dp
        else -> resting
    }
    val offset by animateDpAsState(
        targetValue = target,
        animationSpec = toonPopSpring(),
        label = "toon-shadow-offset",
    )
    return offset
}

/**
 * Manga screentone: 1.3dp ink dots on a [spacing] grid. Ink at 14% on the
 * paper canvas, white at 8% on the night canvas (the ink would disappear
 * there). Drawn in [drawWithCache] so the dot grid is only recomputed when
 * the size changes.
 */
@Composable
fun Modifier.halftone(
    alpha: Float = 0.14f,
    spacing: Dp = 10.dp,
    radius: Dp = 1.3.dp,
): Modifier {
    val night = toonIsNight()
    val dot = if (night) Color.White.copy(alpha = alpha * 0.57f) else toonInk().copy(alpha = alpha)
    return this.drawWithCache {
        val step = spacing.toPx().coerceAtLeast(1f)
        val r = radius.toPx()
        val cols = (size.width / step).toInt() + 2
        val rows = (size.height / step).toInt() + 2
        onDrawBehind {
            clipRect {
                for (row in 0 until rows) {
                    // Offset every other row by half a step: a staggered grid
                    // reads as a real screentone, a square one reads as a bug.
                    val offsetX = if (row % 2 == 0) 0f else step / 2f
                    for (col in 0 until cols) {
                        drawCircle(
                            color = dot,
                            radius = r,
                            center = Offset(col * step + offsetX, row * step),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Manga speed lines: near-radial ink streaks fanning out from the centre,
 * used behind hero art and the mascot. Kept very light (6% ink) so it reads
 * as energy rather than noise.
 */
@Composable
fun Modifier.speedLines(alpha: Float = 0.06f, lines: Int = 72): Modifier {
    val night = toonIsNight()
    val stroke = if (night) Color.White.copy(alpha = alpha) else toonInk().copy(alpha = alpha)
    return this.drawWithCache {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = kotlin.math.hypot(size.width, size.height)
        // One wedge per line: 4° of gap, 1° of ink — a repeating conic
        // pattern built as cached paths instead of a shader.
        val path = Path().apply {
            for (i in 0 until lines) {
                val a0 = (i * 360f / lines) * (Math.PI / 180f).toFloat()
                val a1 = a0 + (1f * (Math.PI / 180f)).toFloat()
                val inner = outer * 0.28f
                moveTo(cx + inner * kotlin.math.cos(a0), cy + inner * kotlin.math.sin(a0))
                lineTo(cx + outer * kotlin.math.cos(a0), cy + outer * kotlin.math.sin(a0))
                lineTo(cx + outer * kotlin.math.cos(a1), cy + outer * kotlin.math.sin(a1))
                lineTo(cx + inner * kotlin.math.cos(a1), cy + inner * kotlin.math.sin(a1))
                close()
            }
        }
        onDrawBehind {
            clipRect { drawPath(path, stroke) }
        }
    }
}

// ── Surfaces ──

/**
 * The toon card: a flat fill, a 3dp ink outline and a hard 4dp offset
 * shadow, at the 20dp "medium" radius of the anime shape scale. Clickable
 * cards additionally press into their own shadow.
 */
@Composable
fun ToonCard(
    modifier: Modifier = Modifier,
    fill: Color? = null,
    shape: Shape = RoundedCornerShape(20.dp),
    borderWidth: Dp = 3.dp,
    shadowOffset: Dp = 4.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    // Soft washes are remapped for the night canvas; anything else (a
    // saturated accent, or null for the default surface) passes through.
    val resolvedFill = fill?.let { toonSoft(it) } ?: toonSurface()
    val offset = if (onClick != null) {
        toonShadowOffset(interaction, resting = shadowOffset)
    } else {
        shadowOffset
    }
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.toonPress(interaction) else Modifier)
            .inkShadow(offset = offset, shape = shape)
            .clip(shape)
            .background(resolvedFill)
            .inkBorder(borderWidth, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        },
                    )
                } else Modifier
            )
            .padding(contentPadding),
        content = content,
    )
}

/**
 * The toon call to action: a pill with an ink outline, a hard shadow and a
 * bold ink label. [fill] is always a saturated pastel from the palette
 * (Sakura by default) because the label is ink — never white — so the pair
 * clears WCAG AA.
 *
 * Sized to a 48dp minimum height so it is always a comfortable touch target.
 */
@Composable
fun ToonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    fill: Color = AnimeColors.Sakura,
    enabled: Boolean = true,
    outlined: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(percent = 50)
    val offset = toonShadowOffset(interaction, resting = 4.dp, enabled = enabled)
    val haptics = LocalHapticFeedback.current

    val resolvedFill = toonSoft(fill)
    val container = when {
        !enabled -> scheme.surfaceVariant
        outlined -> scheme.surface
        else -> resolvedFill
    }
    val content = when {
        !enabled -> scheme.onSurfaceVariant
        // On the night canvas an "outlined" button keeps the fill as its
        // label color so the accent still identifies the action.
        outlined -> if (toonIsNight()) fill else scheme.onSurface
        else -> toonOn(resolvedFill)
    }

    Row(
        modifier = modifier
            .toonPress(interaction, enabled = enabled)
            .inkShadow(offset = if (enabled) offset else 0.dp, shape = shape)
            .clip(shape)
            .background(container)
            .inkBorder(3.dp, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .defaultMinSize(minHeight = 48.dp)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = content)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A round toon icon button — the 48dp circular arrows, the play button, the
 * speaker. Same anatomy as [ToonButton], just circular.
 */
@Composable
fun ToonIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = AnimeColors.Sunny,
    size: Dp = 48.dp,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val offset = toonShadowOffset(interaction, resting = 4.dp, enabled = enabled)
    // Icon buttons can sit on either a bright accent block or the plain
    // surface. On the night canvas that surface is deep indigo, so a fixed
    // ink-black glyph disappears into it; pick the content color from the
    // actual fill just like the other toon primitives do.
    val resolvedFill = if (enabled) toonSoft(fill) else scheme.surfaceVariant
    val content = if (enabled) toonOn(resolvedFill) else scheme.onSurfaceVariant
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .size(size)
            .toonPress(interaction, enabled = enabled)
            .inkShadow(offset = if (enabled) offset else 0.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(resolvedFill)
            .inkBorder(3.dp, CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(size * 0.45f), tint = content)
    }
}

/**
 * A toon chip: the small selectable/status pill. Thinner outline (2dp) and a
 * smaller hard shadow (3dp) than a card, so chips read as one level lighter
 * in the hierarchy. Unselected chips drop to the plain surface.
 */
@Composable
fun ToonChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    fill: Color = AnimeColors.Sunny,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(percent = 50)
    val offset = if (onClick != null) {
        toonShadowOffset(interaction, resting = 3.dp)
    } else 3.dp

    val resolvedFill = toonSoft(fill)
    val container = if (selected) resolvedFill else scheme.surface
    val content = if (selected) toonOn(resolvedFill) else scheme.onSurface
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .then(if (onClick != null) Modifier.toonPress(interaction) else Modifier)
            .inkShadow(offset = offset, shape = shape)
            .clip(shape)
            .background(container)
            .inkBorder(2.dp, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        },
                    )
                } else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(14.dp), tint = content)
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
        )
    }
}

/**
 * The toon switch: an ink-outlined track whose thumb is a solid ink circle,
 * stamped with a tiny ✦ once it is on.
 */
@Composable
fun ToonSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onColor: Color = AnimeColors.Mint,
) {
    val scheme = MaterialTheme.colorScheme
    val trackShape = RoundedCornerShape(percent = 50)
    val ink = toonInk()
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 0.dp,
        animationSpec = toonPopSpring(),
        label = "toon-switch-thumb",
    )
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            // 48dp tall hit area around a 30dp visual track.
            .size(width = 56.dp, height = 48.dp)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(!checked)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 54.dp, height = 30.dp)
                .inkShadow(offset = 3.dp, shape = trackShape)
                .clip(trackShape)
                .background(
                    when {
                        !enabled -> scheme.surfaceVariant
                        checked -> onColor
                        else -> scheme.surface
                    }
                )
                .inkBorder(2.dp, trackShape),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 3.dp)
                    .graphicsLayer { translationX = thumbOffset.toPx() }
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (enabled) ink else scheme.onSurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Text(
                        text = "✦",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (toonIsNight()) AnimeColors.Sunny else Color.White,
                    )
                }
            }
        }
    }
}

/**
 * A candy-striped progress bar: an ink-outlined track with a filled bar
 * carrying diagonal stripes, plus an optional ink thumb (used as the video
 * scrubber's handle).
 */
@Composable
fun ToonProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    fill: Color = AnimeColors.Sky,
    height: Dp = 16.dp,
    showThumb: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(percent = 50)
    val ink = toonInk()
    val clamped = progress.coerceIn(0f, 1f)
    val density = LocalDensity.current
    val stripeStep = with(density) { 10.dp.toPx() }
    val direction = LocalLayoutDirection.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .inkShadow(offset = 3.dp, shape = shape)
            .clip(shape)
            .background(scheme.surface)
            .inkBorder(2.dp, shape)
            .drawBehind {
                val barWidth = size.width * clamped
                if (barWidth <= 0f) return@drawBehind
                val left = if (direction == LayoutDirection.Rtl) size.width - barWidth else 0f
                clipRect(left = left, right = left + barWidth) {
                    drawRect(color = fill, topLeft = Offset(left, 0f), size = Size(barWidth, size.height))
                    // The candy stripes: 45° ink slashes at low alpha.
                    var x = -size.height
                    while (x < size.width + size.height) {
                        drawPath(
                            path = Path().apply {
                                moveTo(x, size.height)
                                lineTo(x + size.height, 0f)
                                lineTo(x + size.height + stripeStep / 2.5f, 0f)
                                lineTo(x + stripeStep / 2.5f, size.height)
                                close()
                            },
                            color = ink.copy(alpha = 0.16f),
                        )
                        x += stripeStep
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (showThumb) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val x = size.width * clamped
                        val cx = if (direction == LayoutDirection.Rtl) size.width - x else x
                        val r = size.height * 0.62f
                        drawCircle(color = ink, radius = r, center = Offset(cx, size.height / 2f))
                        drawCircle(color = fill, radius = r * 0.55f, center = Offset(cx, size.height / 2f))
                    }
            )
        }
    }
}

/**
 * The toon bottom-sheet body: 28dp top corners, an ink outline and a small
 * ink pill as the drag handle. Meant to be used as the *content* of a
 * [androidx.compose.material3.ModalBottomSheet] whose own container is
 * transparent and whose handle is disabled.
 */
@Composable
fun ToonSheet(
    modifier: Modifier = Modifier,
    fill: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill ?: toonSurface())
            .inkBorder(3.dp, shape),
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 46.dp, height = 6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(toonInk())
            )
        }
        content()
    }
}

/**
 * The outlined manga logotype effect: the glyphs are stroked in ink and the
 * fill is drawn on top, so titles read like a sticker. Restricted to hero
 * and app-bar titles on purpose — outlining body copy destroys legibility.
 */
@Composable
fun ToonOutlinedTitle(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium,
    // Null = pick per theme. On paper the glyphs are light inside an ink
    // edge; at night that would be a near-black edge around a near-black
    // fill, so the relationship inverts: a light edge around a dark fill.
    fillColor: Color? = null,
    strokeColor: Color? = null,
    strokeWidth: Float = 6f,
    maxLines: Int = 1,
) {
    val night = toonIsNight()
    val stroke = strokeColor ?: if (night) AnimeColors.OnNight else toonInk()
    val fill = fillColor ?: if (night) AnimeColors.Night else MaterialTheme.colorScheme.surface
    Box(modifier = modifier) {
        Text(
            text = text,
            style = style.toonOutlineStroke(stroke, strokeWidth),
            maxLines = maxLines,
        )
        Text(
            text = text,
            style = style,
            color = fill,
            maxLines = maxLines,
        )
    }
}

/**
 * The toon section header: an outlined title over a halftone band, with an
 * optional Sora peeking at the trailing edge.
 *
 * [mascot] only *requests* the mascot — whether it actually appears also
 * depends on the user's "Show Sora" setting, which [SoraAvatar] honours.
 */
@Composable
fun ToonHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    mascot: Boolean = false,
    band: Color = AnimeColors.SunnySoft,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    val resolvedBand = toonSoft(band)
    val onBand = toonOn(resolvedBand)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .inkShadow(offset = 4.dp, shape = shape)
            .clip(shape)
            .background(resolvedBand)
            .halftone(alpha = 0.10f)
            .inkBorder(3.dp, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            ToonOutlinedTitle(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                strokeWidth = 5f,
                maxLines = 2,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = onBand.copy(alpha = 0.78f),
                    maxLines = 2,
                )
            }
        }
        if (mascot) {
            Spacer(Modifier.width(10.dp))
            SoraAvatar(size = 44.dp)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/**
 * A speech-bubble surface: the toon skin's answer to a "message" card. The
 * tail is a small ink-outlined triangle drawn with a [Path] on the leading
 * or trailing edge, and it mirrors automatically in RTL.
 *
 * The tail is purely decorative, so it carries no semantics.
 */
@Composable
fun ToonBubble(
    modifier: Modifier = Modifier,
    tail: BubbleTail = BubbleTail.Left,
    fill: Color? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val ink = toonInk()
    val resolvedFill = fill?.let { toonSoft(it) } ?: toonSurface()
    val direction = LocalLayoutDirection.current
    // BubbleTail is expressed in *logical* terms (Left = the speaker's side),
    // so it has to mirror with the layout direction.
    val onStart = when (tail) {
        BubbleTail.Left -> direction == LayoutDirection.Ltr
        BubbleTail.Right -> direction == LayoutDirection.Rtl
        BubbleTail.None -> true
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(start = if (tail != BubbleTail.None && onStart) 10.dp else 0.dp,
                    end = if (tail != BubbleTail.None && !onStart) 10.dp else 0.dp)
                .inkShadow(offset = 4.dp, shape = shape)
                .clip(shape)
                .background(resolvedFill)
                .inkBorder(3.dp, shape)
                .padding(contentPadding),
            content = content,
        )
        if (tail != BubbleTail.None) {
            Box(
                modifier = Modifier
                    .align(if (onStart) Alignment.TopStart else Alignment.TopEnd)
                    .padding(top = 18.dp)
                    .size(width = 14.dp, height = 18.dp)
                    .drawBehind {
                        // Outline first, then the fill inset by the border
                        // width — that is what gives the tail the same 3dp
                        // ink edge as the bubble it grows out of.
                        val w = size.width
                        val h = size.height
                        val outline = Path().apply {
                            if (onStart) {
                                moveTo(w, 0f); lineTo(0f, h / 2f); lineTo(w, h)
                            } else {
                                moveTo(0f, 0f); lineTo(w, h / 2f); lineTo(0f, h)
                            }
                            close()
                        }
                        drawPath(outline, ink)
                        val inset = 3.dp.toPx()
                        val inner = Path().apply {
                            if (onStart) {
                                moveTo(w, inset * 1.6f)
                                lineTo(inset * 1.9f, h / 2f)
                                lineTo(w, h - inset * 1.6f)
                            } else {
                                moveTo(0f, inset * 1.6f)
                                lineTo(w - inset * 1.9f, h / 2f)
                                lineTo(0f, h - inset * 1.6f)
                            }
                            close()
                        }
                        drawPath(inner, resolvedFill)
                    },
            )
        }
    }
}

/** Which side a [ToonBubble]'s tail points from (mirrored in RTL). */
enum class BubbleTail { Left, Right, None }

/**
 * Vertical spacing helper so screens keep the same rhythm without repeating
 * magic numbers.
 */
@Composable
fun ToonSpacer(height: Dp = 12.dp) = Spacer(Modifier.height(height))

/** Minimum comfortable toon row height (also the a11y touch target). */
val ToonMinTouchTarget: Dp = 48.dp

/** A convenience wrapper enforcing the touch-target minimum. */
fun Modifier.toonTouchTarget(): Modifier = heightIn(min = ToonMinTouchTarget)
