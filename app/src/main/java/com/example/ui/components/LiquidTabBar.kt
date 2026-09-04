package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.NeoBrutalismAccent
import com.example.ui.theme.isMaterial3Design
import com.example.ui.theme.isNeobrutalismDesign
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * A single entry of the [LiquidTabBar].
 */
data class LiquidTabItem(
    val title: String,
    val icon: ImageVector,
)

/**
 * The app's main navigation bar — one per design language.
 *
 * Material Design 3 design: a standard Material [TabRow] with icon + label
 * tabs and the spec's own sliding underline indicator, plus the divider
 * that separates the tabs from the content.
 *
 * Langosphere design: instead of the flat underline, the selected tab is
 * marked by a *liquid blob*: a soft squircle whose outline is generated from
 * a superellipse modulated by two sine waves, so it never stops gently
 * wobbling ("breathing"). While the user swipes between pages the blob
 * squashes and stretches like a drop of water being pulled sideways, with
 * two small satellite droplets separating from its sides at the peak of the
 * movement.
 *
 * The bar is driven by [indicatorPosition], a *fractional* page position
 * (e.g. `2.35` means "35% of the way from tab 2 to tab 3"), so it follows the
 * finger frame-by-frame rather than snapping between tabs. It is passed as a
 * lambda on purpose: the fast-changing pager offset is then only read inside
 * this composable and never invalidates the whole screen.
 *
 * Neobrutalism design: a chunky square top tab bar — outlined in ink, with
 * the selected destination stamped as a flat yellow block with a hard
 * offset shadow ([NeoTopTabBar]).
 */
@Composable
fun LiquidTabBar(
    items: List<LiquidTabItem>,
    indicatorPosition: () -> Float,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val lastIndex = (items.size - 1).toFloat()
    val position = indicatorPosition().coerceIn(0f, lastIndex)
    val selectedIndex = position.roundToInt().coerceIn(0, items.size - 1)

    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current

    if (isNeobrutalismDesign()) {
        NeoTopTabBar(
            items = items,
            selectedIndex = selectedIndex,
            onTabSelected = onTabSelected,
            modifier = modifier,
        )
        return
    }

    if (isMaterial3Design()) {
        Material3TabBar(
            items = items,
            selectedIndex = selectedIndex,
            onTabSelected = onTabSelected,
            modifier = modifier,
        )
        return
    }

    // Two never-ending animations give the blob its "alive" feel: `phase`
    // rotates the wobble waves around the outline and `breath` scales it
    // very slightly up and down.
    val transition = rememberInfiniteTransition(label = "liquid-tab")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "liquid-phase",
    )
    val breath by transition.animateFloat(
        initialValue = 0.965f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "liquid-breath",
    )

    // 0 while resting on a tab, 1 exactly halfway between two tabs.
    val travel = position - floor(position)
    val stretch = sin(travel * PI.toFloat())

    val blobStart = scheme.primary
    val blobMid = scheme.tertiary
    val blobEnd = scheme.secondary
    val trackTop = scheme.surfaceVariant.copy(alpha = 0.72f)
    val trackMid = scheme.surface.copy(alpha = 0.92f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(trackTop, trackMid, trackTop),
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        scheme.primary.copy(alpha = 0.30f),
                        Color.Transparent,
                        scheme.tertiary.copy(alpha = 0.26f),
                    )
                ),
                shape = RoundedCornerShape(28.dp),
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cell = size.width / items.size
            val cx = cell * (position + 0.5f)
            val cy = size.height / 2f

            val halfW = cell * 0.46f * (1f + 0.32f * stretch) * breath
            val halfH = size.height * 0.40f * (1f - 0.15f * stretch) * breath
            val wobble = 0.035f + 0.055f * stretch

            // Soft halo underneath so the blob looks like it is glowing
            // through the frosted bar instead of being pasted on top of it.
            drawPath(
                path = liquidBlobPath(cx, cy, halfW * 1.20f, halfH * 1.34f, phase * 0.75f, wobble * 1.5f),
                brush = Brush.radialGradient(
                    colors = listOf(blobStart.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = halfW * 2.0f,
                ),
            )

            // Satellite droplets that pinch off while the blob travels.
            if (stretch > 0.06f) {
                val dropletR = halfH * 0.20f * stretch
                val dropletX = halfW * 1.18f
                drawCircle(
                    color = blobStart.copy(alpha = 0.38f * stretch),
                    radius = dropletR,
                    center = Offset(cx - dropletX, cy),
                )
                drawCircle(
                    color = blobMid.copy(alpha = 0.38f * stretch),
                    radius = dropletR,
                    center = Offset(cx + dropletX, cy),
                )
            }

            // The blob itself.
            drawPath(
                path = liquidBlobPath(cx, cy, halfW, halfH, phase, wobble),
                brush = Brush.linearGradient(
                    colors = listOf(blobStart, blobMid, blobEnd),
                    start = Offset(cx - halfW, cy - halfH),
                    end = Offset(cx + halfW, cy + halfH),
                ),
            )

            // Glossy highlight in the upper half sells the "wet" look.
            drawPath(
                path = liquidBlobPath(
                    cx = cx,
                    cy = cy - halfH * 0.36f,
                    halfW = halfW * 0.72f,
                    halfH = halfH * 0.32f,
                    phase = phase * 1.7f + 1.2f,
                    wobble = wobble * 1.3f,
                ),
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.34f),
                        Color.White.copy(alpha = 0.02f),
                    ),
                    startY = cy - halfH,
                    endY = cy,
                ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                // 1 when the blob is centred on this tab, 0 once it is a full
                // tab away — used to cross-fade colors, size and weight so the
                // labels morph continuously during a swipe.
                val nearness = (1f - abs(position - index)).coerceIn(0f, 1f)
                val contentColor = lerp(scheme.onSurfaceVariant, scheme.onPrimary, nearness)
                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .selectable(
                            selected = index == selectedIndex,
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Tab,
                            onClick = {
                                if (index != selectedIndex) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                onTabSelected(index)
                            },
                        ),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = contentColor,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                val scale = 1f + 0.16f * nearness
                                scaleX = scale
                                scaleY = scale
                                translationY = -3f * nearness
                            },
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = item.title,
                        color = contentColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (nearness > 0.5f) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer { alpha = 0.70f + 0.30f * nearness },
                    )
                }
            }
        }
    }
}

/**
 * The neobrutalist top navigation: a square, ink-outlined bar where the
 * active destination is a flat yellow block (ink border + hard offset
 * shadow) with ink-black icon and label; inactive tabs stay plain ink text.
 * No gradients, no wobble, no rounded corners.
 */
@Composable
private fun NeoTopTabBar(
    items: List<LiquidTabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val ink = scheme.outline
    val safeIndex = selectedIndex.coerceIn(0, items.size - 1)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLowest)
            .border(2.dp, ink)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = index == safeIndex
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .neoHardShadow(color = if (isSelected) ink else Color.Transparent, offset = 3.dp)
                    .background(if (isSelected) NeoBrutalismAccent else Color.Transparent)
                    .border(if (isSelected) 2.dp else 0.dp, ink)
                    .selectable(
                        selected = isSelected,
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Tab,
                        onClick = {
                            if (index != safeIndex) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onTabSelected(index)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = if (isSelected) Color.Black else scheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.Black else scheme.onSurface,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * The Material Design 3 navigation for the four sections: primary tabs with
 * an icon above the label, the spec's sliding indicator, tonal surface
 * colors and the standard divider. No gradients, no blob, no glow.
 */
@Composable
private fun Material3TabBar(
    items: List<LiquidTabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        items.forEachIndexed { index, item ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onTabSelected(index) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        modifier = Modifier.size(24.dp),
                    )
                },
                text = {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                },
            )
        }
    }
}

/**
 * Material 3 primary navigation — a bottom [NavigationBar] for compact (phone)
 * windows. Per the material-design-3-ui skill, top-level destinations on a
 * compact screen use a bottom navigation bar, not tabs.
 */
@Composable
fun M3NavigationBar(
    items: List<LiquidTabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = scheme.surfaceContainer,
        contentColor = scheme.onSurface,
    ) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = index == selectedIndex,
                onClick = { onTabSelected(index) },
                icon = { Icon(item.icon, item.title) },
                label = {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                },
                alwaysShowLabel = true,
            )
        }
    }
}

/**
 * Material 3 primary navigation — a side [NavigationRail] for medium and
 * expanded (tablet / desktop) windows. Per the skill's adaptive guidance, the
 * navigation adapts its presentation as the window widens, without redefining
 * the app's destinations.
 */
@Composable
fun M3NavigationRail(
    items: List<LiquidTabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        containerColor = scheme.surfaceContainer,
        contentColor = scheme.onSurface,
    ) {
        items.forEachIndexed { index, item ->
            NavigationRailItem(
                selected = index == selectedIndex,
                onClick = { onTabSelected(index) },
                icon = { Icon(item.icon, item.title) },
                label = {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                },
                alwaysShowLabel = true,
            )
        }
    }
}

/**
 * Builds the outline of the liquid blob.
 *
 * The base shape is a superellipse (a rounded rectangle without hard corner
 * arcs), sampled at [STEPS] angles. Every sample is then pushed in/out by two
 * sine waves of different frequencies, which is what turns a static squircle
 * into a slowly churning drop of liquid.
 *
 * @param wobble how far the outline may deviate from the base shape (0 = a
 *   plain squircle, 0.1 = clearly liquid).
 */
private fun liquidBlobPath(
    cx: Float,
    cy: Float,
    halfW: Float,
    halfH: Float,
    phase: Float,
    wobble: Float,
): Path {
    val path = Path()
    val exponent = 4.5f
    for (i in 0..STEPS) {
        val t = (i.toFloat() / STEPS) * (2f * PI).toFloat()
        val c = cos(t)
        val s = sin(t)
        val rx = halfW * sign(c) * abs(c).pow(2f / exponent)
        val ry = halfH * sign(s) * abs(s).pow(2f / exponent)
        val ripple = 1f + wobble * (sin(3f * t + phase) * 0.55f + sin(5f * t - phase * 1.4f) * 0.30f)
        val x = cx + rx * ripple
        val y = cy + ry * ripple
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private const val STEPS = 72
