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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The spotlight layer of the interactive tutorial. Emits nothing while no
 * tutorial is running. While one is: a dark scrim over the whole screen with
 * a rounded hole cut around the current step's control, a pulsing ring on
 * that hole, and the explanation card placed below the control (or above it
 * when there is no room, or in the middle when the step has no control on
 * the screen). Back closes the tutorial.
 *
 * The control inside the hole stays LIVE: touches there reach the real
 * button, so the user can do what the step describes (open the menu, pick a
 * file, tap a video card). Touches on the scrim are swallowed. Steps that
 * expect an action can move on by themselves ([TutorialStep.advanceWhen]),
 * and when the spotlighted control disappears because the user opened
 * something in its place (a manager page, the player), the overlay steps
 * aside into hands-on mode — only a small «Continue» bar stays — so nothing
 * the user opened is ever locked behind the scrim. «Try it» on the card does
 * the same on request.
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
    val handsOn = controller.handsOn

    // Back closes the tour — except in hands-on mode, where Back must keep
    // working for whatever the user opened (a manager page, a sub-screen).
    BackHandler(enabled = !handsOn) { controller.finish() }

    val density = LocalDensity.current
    var overlayCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // ── Self-advancing steps ──
    // The step moves on when its `advanceWhen` control shows up — but only
    // when that control was NOT there when the step began (otherwise the
    // step would skip itself before it could be read); `skipIfMet` steps
    // skip on purpose in that case ("open a video" with a clip already open).
    val advanceKey = step.advanceWhen
    val advanceMet = advanceKey != null && controller.targets.isPresent(advanceKey)
    val advanceMetAtEntry = remember(step.id) { advanceMet }
    var advanceArmed by remember(step.id) { mutableStateOf(!advanceMet) }
    LaunchedEffect(step.id, advanceMet) {
        if (advanceKey == null) return@LaunchedEffect
        if (!advanceMet) {
            advanceArmed = true
            return@LaunchedEffect
        }
        if (step.skipIfMet && advanceMetAtEntry) {
            if (controller.movingForward) controller.next() else controller.back()
            return@LaunchedEffect
        }
        if (advanceArmed) {
            // A short beat, so the user sees what their tap did before the
            // next card appears.
            delay(450)
            controller.next()
        }
    }

    // ── Automatic hands-on ──
    // The spotlighted control was on the screen and then vanished for a
    // while: the user opened something in its place (the instance manager,
    // the watch cache, the player, a sub-tab). Step aside instead of dimming
    // and blocking what they opened. (A brief flicker — a control being
    // re-created — is ignored by the delay.)
    val targetKey = step.target
    val targetPresent = targetKey != null && controller.targets.isPresent(targetKey)
    var targetSeen by remember(step.id) { mutableStateOf(false) }
    LaunchedEffect(step.id, targetPresent, handsOn) {
        if (handsOn) {
            // After «Continue» the control has to be seen again before its
            // disappearance can step aside once more — otherwise a control
            // that stayed hidden would bounce the card straight back out.
            targetSeen = false
            return@LaunchedEffect
        }
        if (targetPresent) {
            targetSeen = true
            return@LaunchedEffect
        }
        if (targetSeen) {
            delay(600)
            controller.enterHandsOn()
        }
    }

    // The control's window bounds, converted into this overlay's own space and
    // padded a little so the ring never touches the control's edge. A missing
    // or empty rectangle means the control is not on the screen right now.
    val targetRect = targetKey?.let { controller.targets[it] }
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

    val typography = remember(language) {
        AppTypography.forAppLanguage(language).withFontFamily(
            AppFontState.resolvedAppFamily(fa = language == AppLanguage.FA)
        )
    }
    val direction = if (language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr

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

    // The root has no pointer input of its own: in hands-on mode the whole
    // screen underneath stays usable, and otherwise only the blockers and
    // the card are hit-testable, which is what leaves the hole live.
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayCoordinates = it }
    ) {
        if (handsOn) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme(typography = typography) {
                    HandsOnBar(
                        title = res.getString(step.title),
                        continueLabel = res.getString(R.string.tutorial_continue),
                        onContinue = { controller.leaveHandsOn() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 10.dp),
                    )
                }
            }
            return@Box
        }

        // Scrim + hole + ring: drawing only, never hit-tested.
        Box(
            modifier = Modifier
                .fillMaxSize()
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

        // Touch blockers around the hole: everything the scrim covers is
        // swallowed here; the hole itself is a gap between them, so taps
        // there fall through to the real control.
        TutorialBlockers(hole = hole, modifier = Modifier.fillMaxSize())

        // The card, placed relative to the hole by TutorialCardHost.
        TutorialCardHost(hole = hole, modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme(typography = typography) {
                    TutorialCard(
                        topicTitle = res.getString(topic.title),
                        topic = topic,
                        title = res.getString(step.title),
                        body = res.getString(step.body),
                        actionHint = step.actionHint?.let { res.getString(it) },
                        progress = res.getString(R.string.tutorial_step_of, controller.stepIndex + 1, controller.currentSteps.size),
                        notOnScreen = if (targetKey != null && hole == null) res.getString(R.string.tutorial_not_on_screen) else null,
                        language = language,
                        onLanguage = { controller.setLanguage(it) },
                        skipLabel = res.getString(R.string.tutorial_skip),
                        backLabel = res.getString(R.string.tutorial_back),
                        nextLabel = res.getString(if (controller.isLastStep) R.string.tutorial_finish else R.string.tutorial_next),
                        tryItLabel = res.getString(R.string.tutorial_try_it),
                        showTryIt = targetKey != null || step.actionHint != null,
                        showBack = !controller.isFirstStep,
                        onSkip = { controller.finish() },
                        onBack = { controller.back() },
                        onNext = { controller.next() },
                        onTryIt = { controller.enterHandsOn() },
                    )
                }
            }
        }
    }
}

/**
 * Up to four touch-swallowing rectangles around [hole] (one full-screen one
 * when there is no hole). They are the only hit-testable part of the scrim,
 * so a touch inside the hole reaches the app underneath.
 */
@Composable
private fun TutorialBlockers(hole: Rect?, modifier: Modifier = Modifier) {
    val blocker = Modifier.pointerInput(Unit) { detectTapGestures { } }
    Layout(
        content = {
            Box(blocker)
            Box(blocker)
            Box(blocker)
            Box(blocker)
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val rects: List<Rect> = if (hole == null) {
            listOf(Rect(0f, 0f, width.toFloat(), height.toFloat()), Rect.Zero, Rect.Zero, Rect.Zero)
        } else {
            listOf(
                Rect(0f, 0f, width.toFloat(), hole.top),
                Rect(0f, hole.bottom, width.toFloat(), height.toFloat()),
                Rect(0f, hole.top, hole.left, hole.bottom),
                Rect(hole.right, hole.top, width.toFloat(), hole.bottom),
            )
        }
        val placeables = measurables.mapIndexed { index, measurable ->
            val rect = rects[index]
            measurable.measure(
                Constraints.fixed(
                    width = rect.width.roundToInt().coerceIn(0, width),
                    height = rect.height.roundToInt().coerceIn(0, height),
                )
            )
        }
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val rect = rects[index]
                if (placeable.width > 0 && placeable.height > 0) {
                    placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
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
    // The overlay spans the whole window (the app is edge-to-edge), so the
    // card keeps clear of the status and navigation bars by itself. The
    // insets are applied inside the measure pass rather than as padding, so
    // the hole — given in the overlay's own coordinates — stays correct.
    val insets = WindowInsets.systemBars
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val margin = 16.dp.roundToPx()
        val gap = 14.dp.roundToPx()
        val topEdge = margin + insets.getTop(this)
        val bottomEdge = constraints.maxHeight - margin - insets.getBottom(this)
        val sideInset = insets.getLeft(this, layoutDirection) + insets.getRight(this, layoutDirection)
        val maxWidth = min(constraints.maxWidth - 2 * margin - sideInset, 420.dp.roundToPx()).coerceAtLeast(0)
        val maxHeight = (bottomEdge - topEdge).coerceAtLeast(0)
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
                    below + placeable.height <= bottomEdge -> below
                    above >= topEdge -> above
                    // Nothing fits cleanly (a very tall control): take the
                    // side with more space and let the card overlap a little.
                    hole.top > constraints.maxHeight - hole.bottom -> topEdge
                    else -> bottomEdge - placeable.height
                }
            }
            placeable.place(x, y.coerceIn(0, (constraints.maxHeight - placeable.height).coerceAtLeast(0)))
        }
    }
}

/** The compact bar of hands-on mode: the step's title and «Continue». */
@Composable
private fun HandsOnBar(
    title: String,
    continueLabel: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = scheme.inverseSurface,
        contentColor = scheme.inverseOnSurface,
        shadowElevation = 8.dp,
        modifier = modifier
            .widthIn(max = 260.dp)
            // The bar itself swallows touches; everything else on the screen is live.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.TouchApp,
                contentDescription = null,
                tint = scheme.inversePrimary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.width(6.dp))
            TextButton(onClick = onContinue) {
                Text(continueLabel, color = scheme.inversePrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TutorialCard(
    topicTitle: String,
    topic: TutorialTopic,
    title: String,
    body: String,
    actionHint: String?,
    progress: String,
    notOnScreen: String?,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    skipLabel: String,
    backLabel: String,
    nextLabel: String,
    tryItLabel: String,
    showTryIt: Boolean,
    showBack: Boolean,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onTryIt: () -> Unit,
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
            Column(
                modifier = Modifier
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                if (actionHint != null && notOnScreen == null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.TouchApp,
                            contentDescription = null,
                            tint = scheme.tertiary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = actionHint,
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.tertiary,
                        )
                    }
                }
                if (notOnScreen != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = notOnScreen,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.tertiary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TutorialLanguageChips(
                    language = language,
                    onLanguage = onLanguage,
                    modifier = Modifier.weight(1f),
                )
                if (showTryIt) {
                    TextButton(onClick = onTryIt) {
                        Icon(
                            imageVector = Icons.Filled.TouchApp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(tryItLabel)
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
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
