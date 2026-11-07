package com.example.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.AppFontState
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.Typography as AppTypography
import com.example.ui.theme.forAppLanguage
import com.example.ui.theme.localizedFor
import com.example.ui.theme.withFontFamily
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The spotlight layer of the interactive tutorial. Emits nothing while no
 * tutorial is running. While one is: a dark scrim over the whole screen with
 * a rounded hole cut around the current step's control, a pulsing ring on
 * that hole, and the explanation card placed below the control (or above it
 * when there is no room, or in the middle when the step has no control on
 * the screen). Touches never reach the app underneath; Back closes the
 * tutorial.
 *
 * Must be the last child of the screen's root so it draws over everything,
 * including the navigation chrome.
 */
@Composable
fun TutorialOverlay(
    controller: TutorialController,
    appLanguage: AppLanguage,
    modifier: Modifier = Modifier,
) {
    if (!controller.isActive) return
    val step = controller.currentStep ?: return
    val topic = controller.currentTopic ?: return
    val language = controller.languageFor(appLanguage)
    val context = LocalContext.current
    val res = remember(language, context) { context.applicationContext.localizedFor(language).resources }

    BackHandler { controller.finish() }

    val density = LocalDensity.current
    var overlayCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // The control's window bounds, converted into this overlay's own space and
    // padded a little so the ring never touches the control's edge. A missing
    // or empty rectangle means the control is not on the screen right now.
    val targetRect = step.target?.let { controller.targets[it] }
    val coordinates = overlayCoordinates
    val hole: Rect? = if (
        targetRect != null && coordinates != null && coordinates.isAttached &&
        targetRect.width > 1f && targetRect.height > 1f
    ) {
        val topLeft = coordinates.windowToLocal(targetRect.topLeft)
        val pad = with(density) { 6.dp.toPx() }
        val width = coordinates.size.width.toFloat()
        val height = coordinates.size.height.toFloat()
        val raw = Rect(
            left = topLeft.x - pad,
            top = topLeft.y - pad,
            right = topLeft.x + targetRect.width + pad,
            bottom = topLeft.y + targetRect.height + pad,
        )
        val clamped = Rect(
            left = max(0f, raw.left),
            top = max(0f, raw.top),
            right = min(width, raw.right),
            bottom = min(height, raw.bottom),
        )
        if (clamped.width > 1f && clamped.height > 1f) clamped else null
    } else {
        null
    }

    val pulseTransition = rememberInfiniteTransition(label = "tutorialPulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pulse",
    )
    val ringColor = MaterialTheme.colorScheme.primary
    val scrimColor = Color.Black.copy(alpha = 0.68f)
    val cornerPx = with(density) { 18.dp.toPx() }
    val strokePx = with(density) { 2.5.dp.toPx() }
    val pulseReachPx = with(density) { 12.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayCoordinates = it }
    ) {
        // Scrim + hole + ring. The empty tap handler makes this layer swallow
        // every touch that is not on the card, so nothing underneath reacts.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { } }
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawBehind {
                    drawRect(scrimColor)
                    if (hole != null) {
                        val radius = CornerRadius(cornerPx, cornerPx)
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = hole.topLeft,
                            size = hole.size,
                            cornerRadius = radius,
                            blendMode = BlendMode.Clear,
                        )
                        val reach = pulse * pulseReachPx
                        drawRoundRect(
                            color = ringColor.copy(alpha = (1f - pulse) * 0.9f),
                            topLeft = Offset(hole.left - reach, hole.top - reach),
                            size = Size(hole.width + 2 * reach, hole.height + 2 * reach),
                            cornerRadius = CornerRadius(cornerPx + reach, cornerPx + reach),
                            style = Stroke(width = strokePx),
                        )
                        drawRoundRect(
                            color = ringColor,
                            topLeft = hole.topLeft,
                            size = hole.size,
                            cornerRadius = radius,
                            style = Stroke(width = strokePx),
                        )
                    }
                }
        )

        // The card, placed relative to the hole by TutorialCardHost.
        TutorialCardHost(hole = hole, modifier = Modifier.fillMaxSize()) {
            val typography = remember(language) {
                AppTypography.forAppLanguage(language).withFontFamily(
                    AppFontState.resolvedAppFamily(fa = language == AppLanguage.FA)
                )
            }
            val direction = if (language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme(typography = typography) {
                    TutorialCard(
                        topicTitle = res.getString(topic.title),
                        topic = topic,
                        title = res.getString(step.title),
                        body = res.getString(step.body),
                        progress = res.getString(R.string.tutorial_step_of, controller.stepIndex + 1, controller.currentSteps.size),
                        notOnScreen = if (step.target != null && hole == null) res.getString(R.string.tutorial_not_on_screen) else null,
                        language = language,
                        onLanguage = { controller.setLanguage(it) },
                        skipLabel = res.getString(R.string.tutorial_skip),
                        backLabel = res.getString(R.string.tutorial_back),
                        nextLabel = res.getString(if (controller.isLastStep) R.string.tutorial_finish else R.string.tutorial_next),
                        showBack = !controller.isFirstStep,
                        onSkip = { controller.finish() },
                        onBack = { controller.back() },
                        onNext = { controller.next() },
                    )
                }
            }
        }
    }
}

/**
 * Places its single child below the hole when it fits, else above it, else
 * on whichever side has more room; with no hole the child is centred. The
 * child is at most 420dp wide and keeps a 16dp margin from every edge.
 */
@Composable
private fun TutorialCardHost(
    hole: Rect?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val margin = 16.dp.roundToPx()
        val gap = 14.dp.roundToPx()
        val maxWidth = min(constraints.maxWidth - 2 * margin, 420.dp.roundToPx()).coerceAtLeast(0)
        val maxHeight = (constraints.maxHeight - 2 * margin).coerceAtLeast(0)
        val placeable = measurables.firstOrNull()?.measure(
            Constraints(minWidth = 0, maxWidth = maxWidth, minHeight = 0, maxHeight = maxHeight)
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (placeable == null) return@layout
            val x = (constraints.maxWidth - placeable.width) / 2
            val y = if (hole == null) {
                (constraints.maxHeight - placeable.height) / 2
            } else {
                val below = (hole.bottom + gap).roundToInt()
                val above = (hole.top - gap).roundToInt() - placeable.height
                when {
                    below + placeable.height <= constraints.maxHeight - margin -> below
                    above >= margin -> above
                    // Nothing fits cleanly (a very tall control): take the
                    // side with more space and let the card overlap a little.
                    hole.top > constraints.maxHeight - hole.bottom -> margin
                    else -> constraints.maxHeight - margin - placeable.height
                }
            }
            placeable.place(x, y.coerceIn(0, (constraints.maxHeight - placeable.height).coerceAtLeast(0)))
        }
    }
}

@Composable
private fun TutorialCard(
    topicTitle: String,
    topic: TutorialTopic,
    title: String,
    body: String,
    progress: String,
    notOnScreen: String?,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    skipLabel: String,
    backLabel: String,
    nextLabel: String,
    showBack: Boolean,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = scheme.surface,
        contentColor = scheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = topic.icon,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = topicTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = progress,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
            )
            if (notOnScreen != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = notOnScreen,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.tertiary,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            TutorialLanguageChips(language = language, onLanguage = onLanguage)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onSkip) { Text(skipLabel) }
                Spacer(modifier = Modifier.weight(1f))
                if (showBack) {
                    OutlinedButton(onClick = onBack) { Text(backLabel) }
                }
                Button(onClick = onNext) { Text(nextLabel) }
            }
        }
    }
}

/** The tutorial's own language switch — independent of the app language, one tap to change. */
@Composable
fun TutorialLanguageChips(
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanguageChip(label = "فارسی", selected = language == AppLanguage.FA, onClick = { onLanguage(AppLanguage.FA) })
        LanguageChip(label = "English", selected = language == AppLanguage.EN, onClick = { onLanguage(AppLanguage.EN) })
    }
}

@Composable
private fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else {
            null
        },
    )
}
