package com.example.ui.components.anime

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AnimeColors

/**
 * The ANIME navigation: a floating "sticker" bar.
 *
 * On phones it is a rounded pill card hovering 12dp above the bottom edge
 * with a 16dp side margin, filled white (Night2 in dark), outlined in 3dp of
 * ink, carrying a hard 4dp offset shadow and a faint halftone screentone.
 *
 * The selected destination gets a Sunny rounded-rect blob that *slides*
 * behind its icon on the layout spring, the icon tilts -8° and scales 1.1,
 * and its label fades in. Idle tabs stay slightly dimmed — ink on the paper
 * canvas, the light on-surface tone on the night canvas — so the active tab
 * is unmistakable in both themes.
 *
 * The bar is fed the same fractional [indicatorPosition] as the Langosphere
 * liquid bar (e.g. `2.35` = 35% of the way from tab 2 to tab 3), so the blob
 * tracks the finger frame by frame during a pager swipe instead of snapping
 * once the swipe settles.
 */

/** One destination of the toon bar. [avatar] renders Sora instead of an
 *  icon, which is how the assistant tab is identified. */
data class ToonTabItem(
    val title: String,
    val icon: ImageVector,
    val avatar: Boolean = false,
)

@Composable
fun ToonTabBar(
    items: List<ToonTabItem>,
    indicatorPosition: () -> Float,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val position = indicatorPosition().coerceIn(0f, (items.size - 1).toFloat())
    val selectedIndex = Math.round(position).coerceIn(0, items.size - 1)
    val shape = RoundedCornerShape(percent = 50)
    val blob = AnimeColors.Sunny
    val ink = toonInk()
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp)
            .inkShadow(offset = 4.dp, shape = shape)
            .clip(shape)
            .background(toonSurface())
            .halftone(alpha = 0.08f)
            .inkBorder(3.dp, shape)
            // The sliding blob is painted behind the row so it can move
            // continuously between cells without relayout.
            .drawBehind {
                val cell = size.width / items.size
                val blobW = cell * 0.74f
                val blobH = size.height * 0.68f
                val cx = cell * (position + 0.5f)
                val topLeft = Offset(cx - blobW / 2f, (size.height - blobH) / 2f)
                val radius = CornerRadius(14.dp.toPx())
                drawRoundRect(color = blob, topLeft = topLeft, size = Size(blobW, blobH), cornerRadius = radius)
                drawRoundRect(
                    color = ink,
                    topLeft = topLeft,
                    size = Size(blobW, blobH),
                    cornerRadius = radius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                ToonTabCell(
                    item = item,
                    // 1 while the blob is centred here, 0 a whole tab away —
                    // the icon tilt, scale and label fade all interpolate on
                    // this so a swipe morphs smoothly.
                    nearness = (1f - kotlin.math.abs(position - index)).coerceIn(0f, 1f),
                    selected = index == selectedIndex,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = {
                        if (index != selectedIndex) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onTabSelected(index)
                    },
                )
            }
        }
    }
}

/**
 * The wide-window variant: the same sticker pill turned vertical, pinned to
 * the leading edge as a rail.
 */
@Composable
fun ToonTabRail(
    items: List<ToonTabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val shape = RoundedCornerShape(percent = 50)
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 12.dp, top = 16.dp, bottom = 16.dp, end = 4.dp)
            .width(84.dp)
            .inkShadow(offset = 4.dp, shape = shape)
            .clip(shape)
            .background(toonSurface())
            .halftone(alpha = 0.08f)
            .inkBorder(3.dp, shape)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 64.dp)
                    .then(
                        if (selected) {
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(AnimeColors.Sunny)
                                .inkBorder(2.dp, RoundedCornerShape(14.dp))
                        } else Modifier
                    ),
            ) {
                ToonTabCell(
                    item = item,
                    nearness = if (selected) 1f else 0f,
                    selected = selected,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    onClick = {
                        if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTabSelected(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun ToonTabCell(
    item: ToonTabItem,
    nearness: Float,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    // The ✦ pops on the frame the selection lands, then settles — the spring
    // overshoot is what sells it as a "pop" rather than a fade.
    val sparklePop by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = toonPopSpring(),
        label = "toon-tab-sparkle",
    )
    val surfaceContent = toonOn(toonSurface())
    val blobContent = toonOn(AnimeColors.Sunny)
    // On paper both colors resolve to ink; on the night canvas the idle tabs
    // must flip to the light on-surface tone while the selected one keeps the
    // ink-on-Sunny contrast of the moving blob.
    val iconTint = lerp(surfaceContent.copy(alpha = 0.74f), blobContent, nearness)
    val labelTint = lerp(surfaceContent, blobContent, nearness)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (item.avatar) {
                // The assistant tab is Sora herself. This avatar ignores the
                // "Show Sora" toggle: it is the tab's identity, not decor.
                SoraAvatar(
                    size = 26.dp,
                    borderWidth = 2.dp,
                    respectMascotSetting = false,
                    modifier = Modifier.graphicsLayer {
                        rotationZ = -8f * nearness
                        val scale = 1f + 0.10f * nearness
                        scaleX = scale
                        scaleY = scale
                    },
                )
            } else {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = iconTint,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            rotationZ = -8f * nearness
                            val scale = 1f + 0.10f * nearness
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
            if (sparklePop > 0.01f) {
                Text(
                    text = "✦",
                    style = MaterialTheme.typography.labelSmall,
                    color = AnimeColors.Sakura,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .graphicsLayer {
                            translationX = 14.dp.toPx()
                            translationY = -8.dp.toPx()
                            scaleX = sparklePop
                            scaleY = sparklePop
                            alpha = sparklePop
                            rotationZ = 25f * (1f - sparklePop)
                        },
                )
            }
        }
        if (nearness > 0.02f) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = labelTint,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .graphicsLayer { alpha = nearness },
            )
        }
    }
}

/**
 * The toon app bar: a halftone paper band carrying the outlined
 * "Langosphere" logotype, with a circular Sora badge on the trailing edge
 * that opens Settings.
 */
@Composable
fun ToonAppBar(
    title: String,
    onSettings: () -> Unit,
    settingsContentDescription: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .halftone(alpha = 0.10f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToonOutlinedTitle(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fillColor = AnimeColors.Sakura,
            strokeWidth = 7f,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            trailing()
            Spacer(Modifier.width(8.dp))
        }
        // The badge itself is a decorative drawing, so the description and
        // the Button role live on this 48dp hit target instead.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onSettings,
                )
                .semantics { contentDescription = settingsContentDescription },
            contentAlignment = Alignment.Center,
        ) {
            SoraAvatar(size = 40.dp, borderWidth = 2.dp, respectMascotSetting = false)
        }
    }
}
