package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentRed
import com.example.ui.theme.AppDesignStyle
import com.example.ui.theme.AppDesignStyleState
import com.example.ui.theme.NeoBrutalismAccent
import com.example.ui.theme.isMaterial3Design
import com.example.ui.theme.isNeobrutalismDesign
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * The app's shared visual vocabulary — and the place where the design
 * languages actually diverge.
 *
 * Every screen is built from these pieces, so each of the Material designs
 * uses the same implementation:
 *  - Langosphere (default): frosted glass, brand gradients, springy press
 *    animations, very round corners.
 *  - Material Design 3 (Settings ▸ Theme ▸ App design): the real Material
 *    components and tokens — flat tonal surfaces from the surface-container
 *    ramp, the M3 shape scale, Button / FilledTonalIconButton /
 *    SegmentedButton / CircularProgressIndicator, and no gradients or
 *    decorative shadows at all.
 *  - Material You (the M3 Expressive design from the material-3-skill): the
 *    same Material component vocabulary, but with spring motion, expressive
 *    shapes, emphasized type and the adaptive M3 NavigationBar / NavigationRail
 *    with the Material Symbols icon set.
 *  - Neobrutalism: ink-black 2-4dp borders, hard offset shadows (zero blur),
 *    zero-radius corners, loud flat color blocks and flat ink icons — the
 *    visual anatomy of the neubrutalism design scale (neubrutalism.com).
 *
 * Because every screen goes through these functions, switching the design
 * re-skins the entire app, not just the theme colors.
 */

// ── Neobrutalism helpers ──
// Depth in neubrutalism is never a blur: it is an ink rectangle offset by a
// few dp behind the panel ("box-shadow: 4px 4px 0 0 #000"), plus a hard ink
// border. Everything below builds on [NeoBlock] and [Modifier.neoHardShadow].

/** Draws a hard, zero-blur offset shadow behind the content rectangle. */
fun Modifier.neoHardShadow(
    color: Color,
    offset: Dp = 4.dp,
): Modifier = drawBehind {
    val o = offset.toPx()
    drawRect(
        color = color,
        topLeft = Offset(o, o),
        size = Size(size.width, size.height),
    )
}

/**
 * The neobrutalist building block: a flat colored rectangle with a thick
 * ink border and a hard offset shadow. Pressing it scales it down slightly
 * (the classic "pushed into the page" feel).
 */
@Composable
fun NeoBlock(
    modifier: Modifier = Modifier,
    container: Color,
    borderColor: Color,
    borderWidth: Dp = 2.dp,
    shadowOffset: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    // Where the inner content sits inside the block. Blocks that must stay
    // content-sized (e.g. buttons next to text in a Row) pass an intrinsic
    // child and Center here; callers that want a full-width block still pass
    // Modifier.fillMaxWidth() on the block itself.
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "neo-press",
    )
    val haptics = LocalHapticFeedback.current
    val resolved = if (enabled) container else borderColor.copy(alpha = 0.30f)
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .neoHardShadow(color = borderColor, offset = if (pressed) shadowOffset / 2 else shadowOffset)
            .background(resolved)
            .border(borderWidth, borderColor)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        enabled = enabled,
                        interactionSource = interaction,
                        indication = null,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        },
                    )
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        contentAlignment = contentAlignment,
    ) {
        content()
    }
}

// Loud status colors of the neubrutalist palette (the neobrutalism skill's
// success/warning/danger tokens), used for [StatusPill] tones. Text on them
// is ink-black, exactly like the guide's colored badges.
private val NeoSuccessColor = Color(0xFF16A34A)
private val NeoWarningColor = Color(0xFFD97706)
private val NeoDangerColor = Color(0xFFDC2626)

/** The Langosphere gradient (primary → tertiary → secondary); a flat primary
 *  fill in Material 3 / Material You and in the neobrutalist skin (which
 *  never draws gradients). */
@Composable
fun brandBrush(alpha: Float = 1f): Brush {
    val scheme = MaterialTheme.colorScheme
    if (isMaterial3Design() || isNeobrutalismDesign()) {
        return SolidColor(scheme.primary.copy(alpha = alpha))
    }
    return Brush.linearGradient(
        colors = listOf(
            scheme.primary.copy(alpha = alpha),
            scheme.tertiary.copy(alpha = alpha),
            scheme.secondary.copy(alpha = alpha),
        )
    )
}

/**
 * Langosphere: a frosted "glass" panel — soft vertical tint plus a light
 * hairline on top, which reads as depth without a heavy shadow.
 *
 * Material 3: a filled card — one flat tonal surface from the
 * surface-container ramp, with the M3 medium shape (12dp). Depth is tonal,
 * as the spec requires, and the optional [tint] is folded into the container
 * color instead of becoming a gradient.
 *
 * Neobrutalism: a white raised surface with a 2dp ink border and a hard 4dp
 * offset shadow. An optional [tint] becomes a flat translucent color wash on
 * white (e.g. a soft yellow/indigo card), never a gradient.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    cornerRadius: Dp = 22.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    if (isNeobrutalismDesign()) {
        val wash = tint?.let { it.copy(alpha = 0.14f) }
        val container = wash?.compositeOver(scheme.surfaceContainerLowest) ?: scheme.surfaceContainerLowest
        NeoBlock(
            modifier = modifier,
            container = container,
            borderColor = scheme.outline,
            borderWidth = 2.dp,
            shadowOffset = 4.dp,
            onClick = onClick,
            contentPadding = contentPadding,
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
        return
    }

    if (isMaterial3Design()) {
        val shape = MaterialTheme.shapes.medium
        val container = if (tint != null) {
            tint.copy(alpha = 0.14f).compositeOver(scheme.surfaceContainerLow)
        } else {
            scheme.surfaceContainerLow
        }
        Column(
            modifier = modifier
                .clip(shape)
                .background(container)
                .then(clickable)
                .padding(contentPadding),
            content = content,
        )
        return
    }

    val base = tint ?: scheme.surfaceVariant
    val shape = RoundedCornerShape(cornerRadius)

    Column(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(base.copy(alpha = 0.58f), base.copy(alpha = 0.26f)),
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        scheme.onSurface.copy(alpha = 0.10f),
                        Color.Transparent,
                    )
                ),
                shape = shape,
            )
            .then(clickable)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Section title. Langosphere puts a gradient bar on the leading edge;
 * Material 3 uses a plain title + supporting text, the way M3 list and
 * settings headers are specified. Neobrutalism keeps the leading edge but
 * with a square yellow block (never a gradient/rounded pill).
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val material3 = isMaterial3Design()
    val neo = isNeobrutalismDesign()

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!material3) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(if (subtitle == null) 20.dp else 34.dp)
                    // The neobrutalist accent bar is a square yellow block
                    // (SolidColor so the branch stays a Brush like brandBrush).
                    .clip(if (neo) RoundedCornerShape(0.dp) else CircleShape)
                    .background(
                        if (neo) SolidColor(NeoBrutalismAccent)
                        else brandBrush()
                    )
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (material3) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = if (material3) FontWeight.Normal else FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = if (material3) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

enum class PillTone { Neutral, Positive, Negative, Accent, Warning }

/**
 * Small status chip. Langosphere tints it with the app's accent colors;
 * Material 3 maps every tone onto a real M3 container/on-container role
 * pair, so nothing is hardcoded and contrast follows the scheme.
 * Neobrutalism draws it as a flat loud color block with an ink border and
 * ink-black glyphs, like the guide's badges.
 */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: PillTone = PillTone.Neutral,
) {
    val scheme = MaterialTheme.colorScheme

    if (isNeobrutalismDesign()) {
        val container: Color
        val content: Color
        when (tone) {
            PillTone.Positive -> {
                container = NeoSuccessColor
                content = Color.Black
            }
            PillTone.Negative -> {
                container = NeoDangerColor
                content = Color.Black
            }
            PillTone.Warning -> {
                container = NeoWarningColor
                content = Color.Black
            }
            PillTone.Accent -> {
                container = NeoBrutalismAccent
                content = Color.Black
            }
            PillTone.Neutral -> {
                container = scheme.surfaceContainerLowest
                content = scheme.onSurface
            }
        }
        Row(
            modifier = modifier
                .border(1.5.dp, scheme.outline)
                .background(container)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(13.dp), tint = content)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        return
    }

    if (isMaterial3Design()) {
        val container: Color
        val onContainer: Color
        when (tone) {
            PillTone.Positive -> {
                container = scheme.secondaryContainer
                onContainer = scheme.onSecondaryContainer
            }
            PillTone.Negative -> {
                container = scheme.errorContainer
                onContainer = scheme.onErrorContainer
            }
            PillTone.Warning -> {
                container = scheme.tertiaryContainer
                onContainer = scheme.onTertiaryContainer
            }
            PillTone.Accent -> {
                container = scheme.primaryContainer
                onContainer = scheme.onPrimaryContainer
            }
            PillTone.Neutral -> {
                container = scheme.surfaceContainerHighest
                onContainer = scheme.onSurfaceVariant
            }
        }
        Row(
            modifier = modifier
                .clip(MaterialTheme.shapes.small)
                .background(container)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(14.dp), tint = onContainer)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = onContainer,
                maxLines = 1,
            )
        }
        return
    }

    val color = when (tone) {
        PillTone.Positive -> AccentGreen
        PillTone.Negative -> AccentRed
        PillTone.Warning -> AccentAmber
        PillTone.Accent -> scheme.tertiary
        PillTone.Neutral -> scheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.32f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(13.dp), tint = color)
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * Primary call to action. Langosphere: brand gradient that squishes when
 * pressed. Material 3: a real filled [Button] with the spec's shape, state
 * layers and ripple. Neobrutalism: a flat yellow color block with an ink
 * border, hard offset shadow and ink-black text/icon (guide buttons).
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme

    if (isNeobrutalismDesign()) {
        NeoBlock(
            modifier = modifier,
            container = if (enabled) NeoBrutalismAccent else scheme.surfaceVariant,
            borderColor = scheme.outline,
            borderWidth = 2.dp,
            shadowOffset = 4.dp,
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            // Content stays intrinsic (NOT fillMaxWidth): a fill-max row inside
            // the box would make the button grab the whole row width when it
            // sits next to text (e.g. the smart-pause reset row), squashing
            // the text into a vertical letter column. Center keeps wide
            // (caller fillMaxWidth) buttons looking right.
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (enabled) Color.Black else scheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) Color.Black else scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        return
    }

    if (isMaterial3Design()) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
        return
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "gradient-button-press",
    )
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(18.dp)
    val contentColor = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(
                if (enabled) brandBrush() else SolidColor(scheme.surfaceVariant.copy(alpha = 0.5f))
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = contentColor)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/**
 * Secondary action in headers and toolbars. Langosphere: a circular tinted
 * halo. Material 3: a [FilledTonalIconButton], the spec's answer for exactly
 * this role. Neobrutalism: a square white block with an ink border and a
 * small hard shadow, holding a flat ink icon.
 */
@Composable
fun SoftIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    enabled: Boolean = true,
    size: Dp = 38.dp,
) {
    val scheme = MaterialTheme.colorScheme

    if (isNeobrutalismDesign()) {
        val resolved = if (enabled) (tint ?: scheme.onSurface) else scheme.onSurfaceVariant.copy(alpha = 0.45f)
        NeoBlock(
            modifier = modifier.size(size),
            container = scheme.surfaceContainerLowest,
            borderColor = scheme.outline,
            borderWidth = 1.5.dp,
            shadowOffset = 2.dp,
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(0.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(size * 0.5f),
                    tint = resolved,
                )
            }
        }
        return
    }

    if (isMaterial3Design()) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier.size(size),
            enabled = enabled,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = scheme.secondaryContainer,
                contentColor = tint ?: scheme.onSecondaryContainer,
            ),
        ) {
            Icon(icon, contentDescription, Modifier.size(size * 0.5f))
        }
        return
    }

    val resolved = (tint ?: scheme.primary).let {
        if (enabled) it else scheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 900f),
        label = "soft-icon-press",
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(resolved.copy(alpha = 0.13f))
            .border(1.dp, resolved.copy(alpha = 0.24f), CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(size * 0.48f), tint = resolved)
    }
}

/**
 * Empty state. Langosphere floats the icon inside a slowly breathing halo;
 * Material 3 uses a calm tonal circle with title + supporting text and no
 * ambient animation. Neobrutalism stamps the icon on a flat yellow square
 * with an ink border (no animation, no gradient).
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val scheme = MaterialTheme.colorScheme

    if (isNeobrutalismDesign()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(NeoBrutalismAccent)
                        .border(2.dp, scheme.outline),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                if (description != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        return
    }

    if (isMaterial3Design()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                if (description != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "empty-state")
    val pulse by transition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "empty-pulse",
    )

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(104.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                scheme.primary.copy(alpha = 0.22f),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = size.minDimension / 2f * pulse,
                        ),
                        radius = size.minDimension / 2f * pulse,
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = scheme.primary.copy(alpha = 0.75f),
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Segmented control. Langosphere uses a liquid indicator that squashes and
 * stretches while it slides; Material 3 uses the real
 * [SingleChoiceSegmentedButtonRow] with the spec's connected item shapes.
 * Neobrutalism uses a square boxed bar: the whole control is outlined in
 * ink and the selected segment is a flat yellow block with ink-black text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedPills(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current

    if (isNeobrutalismDesign()) {
        val selected = selectedIndex.coerceIn(0, items.size - 1)
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp)
                .border(2.dp, scheme.outline)
                .background(scheme.surfaceContainerLowest),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, label ->
                val isSelected = index == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (isSelected) NeoBrutalismAccent else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (index != selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onSelect(index)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.Black else scheme.onSurface,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
        return
    }

    if (isMaterial3Design()) {
        SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
            items.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = items.size),
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                    },
                )
            }
        }
        return
    }

    val shape = RoundedCornerShape(20.dp)
    val target = selectedIndex.coerceIn(0, items.size - 1).toFloat()
    val position by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 320f),
        label = "segment-position",
    )

    val indicatorStart = scheme.primary
    val indicatorEnd = scheme.tertiary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(scheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, scheme.onSurface.copy(alpha = 0.06f), shape)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cell = size.width / items.size
            val travel = position - floor(position)
            val stretch = sin(travel * PI.toFloat())
            val pillW = cell * 0.94f * (1f + 0.10f * stretch)
            val pillH = size.height * 0.78f * (1f - 0.10f * stretch)
            val cx = cell * (position + 0.5f)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(indicatorStart, indicatorEnd),
                    start = Offset(cx - pillW / 2f, 0f),
                    end = Offset(cx + pillW / 2f, size.height),
                ),
                topLeft = Offset(cx - pillW / 2f, (size.height - pillH) / 2f),
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(pillH / 2f),
            )
        }

        Row(modifier = Modifier.matchParentSize()) {
            items.forEachIndexed { index, label ->
                val nearness = (1f - abs(position - index)).coerceIn(0f, 1f)
                val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(shape)
                        .selectable(
                            selected = index == selectedIndex,
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Tab,
                            onClick = {
                                if (index != selectedIndex) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                onSelect(index)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (nearness > 0.5f) FontWeight.Bold else FontWeight.Medium,
                        color = lerp(scheme.onSurfaceVariant, scheme.onPrimary, nearness),
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * Progress ring with arbitrary centered content: a gradient arc in the
 * Langosphere design, the standard Material 3
 * [CircularProgressIndicator] in the Material Design 3 one, and a flat
 * single-color (no gradient) arc in the neobrutalist skin.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 6.dp,
    content: @Composable () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "progress-ring",
    )

    if (isNeobrutalismDesign()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val stroke = strokeWidth.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = scheme.surfaceContainerHighest,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                if (animated > 0f) {
                    drawArc(
                        color = scheme.primary,
                        startAngle = -90f,
                        sweepAngle = 360f * animated,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                }
            }
            content()
        }
        return
    }

    if (isMaterial3Design()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { animated },
                modifier = Modifier.matchParentSize(),
                color = scheme.primary,
                trackColor = scheme.surfaceContainerHighest,
                strokeWidth = strokeWidth,
            )
            content()
        }
        return
    }

    val track = scheme.onSurfaceVariant.copy(alpha = 0.16f)
    val ringStart = scheme.primary
    val ringEnd = scheme.tertiary

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (animated > 0f) {
                drawArc(
                    brush = Brush.linearGradient(
                        colors = listOf(ringStart, ringEnd),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/**
 * Fades content out at the top and bottom edges instead of cutting it off
 * with a hard line — used on every scrollable reading surface in the
 * Langosphere design. Material 3 (and Material You) let content scroll
 * cleanly under the chrome instead, so the fade is skipped entirely there —
 * and so does neobrutalism, whose surfaces must stay crisp and unblurred.
 */
fun Modifier.fadingEdges(topFade: Dp = 18.dp, bottomFade: Dp = 24.dp): Modifier {
    val style = AppDesignStyleState.style
    if (style == AppDesignStyle.MATERIAL3 ||
        style == AppDesignStyle.MATERIAL_YOU ||
        style == AppDesignStyle.NEOBRUTALISM
    ) return this
    return this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val top = topFade.toPx()
            val bottom = bottomFade.toPx()
            if (top > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startY = 0f,
                        endY = top,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (bottom > 0f && size.height > bottom) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startY = size.height - bottom,
                        endY = size.height,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}
