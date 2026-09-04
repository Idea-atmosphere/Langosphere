package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui.components.neoHardShadow
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.NeoBrutalismAccent
import com.example.ui.theme.isNeobrutalismDesign
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Langosphere player chrome.
 *
 * The player used to rely on ExoPlayer's built-in controller, which is
 * switched OFF whenever smart-pause mode is on — meaning there was no seek
 * bar, no timeline and no clock at all in the mode most people actually
 * use. These components provide that chrome in the app's own visual
 * language, for both video and audio.
 */

/**
 * Fade wrapper for the auto-hiding chrome.
 *
 * It deliberately lives at the top level: calling AnimatedVisibility
 * directly inside a Box that is nested in a Column makes Kotlin resolve the
 * ColumnScope overload, which the layout DSL marker then rejects ("cannot be
 * called in this context with an implicit receiver"). With no layout scope
 * in sight, the plain overload is picked.
 */
@Composable
fun ChromeFade(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        content()
    }
}

/** Dark gradient behind the chrome so white icons stay readable on any frame. */
@Composable
fun PlayerScrim(fromTop: Boolean, modifier: Modifier = Modifier, height: Dp = 104.dp) {
    val colors = if (fromTop) {
        listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
    } else {
        listOf(Color.Transparent, Color.Black.copy(alpha = 0.74f))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(colors))
    )
}

/** Round translucent icon button used for every overlay action.
 *
 *  In the neobrutalist design it becomes a flat square chip: cream/raised
 *  surface with an ink border and a hard offset shadow; the active state is
 *  the loud yellow block with an ink-black glyph. */
@Composable
fun PlayerGlassButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    active: Boolean = false
) {
    val accent = MaterialTheme.colorScheme.primary
    if (isNeobrutalismDesign()) {
        Box(
            modifier = modifier
                .size(size)
                .neoHardShadow(MaterialTheme.colorScheme.outline, offset = 2.dp)
                .background(
                    if (active) NeoBrutalismAccent
                    else MaterialTheme.colorScheme.surfaceContainerLowest
                )
                .border(2.dp, MaterialTheme.colorScheme.outline)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) Color.Black else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(size * 0.46f)
            )
        }
        return
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) accent.copy(alpha = 0.32f) else Color.Black.copy(alpha = 0.44f))
            .border(
                width = 1.dp,
                color = if (active) accent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.16f),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) accent else Color.White,
            modifier = Modifier.size(size * 0.46f)
        )
    }
}

/**
 * Text counterpart of [PlayerGlassButton].
 *
 * Used for controls whose value IS the label (playback speed, A-B repeat),
 * where an icon would say less than the text itself.
 */
@Composable
fun PlayerTextPill(
    text: String,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    height: Dp = 40.dp
) {
    val accent = MaterialTheme.colorScheme.primary
    if (isNeobrutalismDesign()) {
        // Square text chip: raised surface + ink border; active = yellow
        // block with ink-black label.
        Box(
            modifier = modifier
                .height(height)
                .widthIn(min = height + 8.dp)
                .neoHardShadow(MaterialTheme.colorScheme.outline, offset = 2.dp)
                .background(
                    if (active) NeoBrutalismAccent
                    else MaterialTheme.colorScheme.surfaceContainerLowest
                )
                .border(2.dp, MaterialTheme.colorScheme.outline)
                .clickable(onClickLabel = contentDescription) { onClick() }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (active) Color.Black else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        return
    }
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier = modifier
            .height(height)
            .widthIn(min = height + 8.dp)
            .clip(shape)
            .background(if (active) accent.copy(alpha = 0.32f) else Color.Black.copy(alpha = 0.44f))
            .border(
                width = 1.dp,
                color = if (active) accent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.16f),
                shape = shape
            )
            .clickable(onClickLabel = contentDescription) { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (active) accent else Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** "1x", "0.75x", "1.25x" — never localized digits, never trailing zeros. */
fun formatSpeedLabel(speed: Float): String {
    val rounded = (speed * 100f).roundToInt() / 100f
    val text = if (rounded % 1f == 0f) {
        String.format(Locale.US, "%.0f", rounded)
    } else {
        String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
    }
    return text + "\u00D7"
}

/**
 * Scrubbable seek bar with a buffered track.
 *
 * Tapping jumps straight to a position; dragging moves the thumb with the
 * finger and only seeks on release, so scrubbing stays smooth instead of
 * firing dozens of seeks. When an A-B loop is set, its range is painted on
 * the track so the repeated section is visible at a glance.
 */
@Composable
fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    modifier: Modifier = Modifier,
    loopStartMs: Long? = null,
    loopEndMs: Long? = null,
    onSeek: (Long) -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val trackTop = MaterialTheme.colorScheme.tertiary
    val density = LocalDensity.current
    var trackWidth by remember { mutableStateOf(1f) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    val playFraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufferFraction = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownFraction = dragFraction ?: playFraction
    val thumbSize by animateFloatAsState(
        targetValue = if (dragFraction != null) 18f else 12f,
        animationSpec = tween(durationMillis = 140),
        label = "seekThumb"
    )
    val thumbPx = with(density) { thumbSize.dp.toPx() }
    val neo = isNeobrutalismDesign()
    // Neobrutalism: square, unblurred track and a flat yellow progress fill.
    val barShape = if (neo) RoundedCornerShape(0.dp) else RoundedCornerShape(3.dp)
    val thumbShape = if (neo) RoundedCornerShape(0.dp) else CircleShape
    val progressBrush: Brush =
        if (neo) SolidColor(NeoBrutalismAccent)
        else Brush.horizontalGradient(listOf(trackTop, accent))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .onGloballyPositioned { trackWidth = it.size.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(durationMs) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        dragFraction = (offset.x / w).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        val fraction = dragFraction
                        if (fraction != null && durationMs > 0) onSeek((fraction * durationMs).toLong())
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                    onDrag = { change, amount ->
                        change.consume()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        dragFraction = ((dragFraction ?: 0f) + amount.x / w).coerceIn(0f, 1f)
                    }
                )
            }
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0) {
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        onSeek(((offset.x / w).coerceIn(0f, 1f) * durationMs).toLong())
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (neo) 6.dp else 5.dp)
                .clip(barShape)
                .background(Color.White.copy(alpha = 0.20f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(bufferFraction.coerceIn(0.002f, 1f))
                .height(if (neo) 6.dp else 5.dp)
                .clip(barShape)
                .background(Color.White.copy(alpha = 0.30f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(shownFraction.coerceIn(0.002f, 1f))
                .height(if (neo) 6.dp else 5.dp)
                .clip(barShape)
                .background(progressBrush)
        )
        if (loopStartMs != null && loopEndMs != null && durationMs > 0 && loopEndMs > loopStartMs) {
            val loopStartFraction = (loopStartMs.toFloat() / durationMs).coerceIn(0f, 1f)
            val loopEndFraction = (loopEndMs.toFloat() / durationMs).coerceIn(loopStartFraction, 1f)
            val loopWidthPx = ((loopEndFraction - loopStartFraction) * trackWidth).coerceAtLeast(4f)
            Box(
                modifier = Modifier
                    .offset { IntOffset((loopStartFraction * trackWidth).roundToInt(), 0) }
                    .width(with(density) { loopWidthPx.toDp() })
                    .height(9.dp)
                    .clip(if (neo) RoundedCornerShape(0.dp) else RoundedCornerShape(4.dp))
                    .background(AccentAmber.copy(alpha = 0.62f))
            )
        }
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (shownFraction * trackWidth - thumbPx / 2f).roundToInt().coerceAtLeast(0),
                        0
                    )
                }
                .size(thumbSize.dp)
                .clip(thumbShape)
                .background(Color.White)
        )
    }
}

/** Transport controls: clock, seek bar, skip back / play-pause / skip forward. */
@Composable
fun PlayerControls(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    skipSeconds: Int,
    playPauseDescription: String?,
    modifier: Modifier = Modifier,
    loopStartMs: Long? = null,
    loopEndMs: Long? = null,
    onPlayPause: () -> Unit,
    onSkip: (Int) -> Unit,
    onSeek: (Long) -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val pulse by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 1.07f,
        animationSpec = tween(durationMillis = 220),
        label = "playPulse"
    )
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatClock(positionMs),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatClock(durationMs),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium
            )
        }
        PlayerSeekBar(
            positionMs = positionMs,
            durationMs = durationMs,
            bufferedMs = bufferedMs,
            loopStartMs = loopStartMs,
            loopEndMs = loopEndMs,
            onSeek = onSeek
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerGlassButton(
                icon = Icons.Default.FastRewind,
                contentDescription = null,
                onClick = { onSkip(-skipSeconds) },
                size = 42.dp
            )
            Spacer(modifier = Modifier.width(18.dp))
            if (isNeobrutalismDesign()) {
                // The neo play/pause is the loud yellow block: square, ink
                // border, hard shadow, ink-black glyph.
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .graphicsLayer { scaleX = pulse; scaleY = pulse }
                        .neoHardShadow(MaterialTheme.colorScheme.outline, offset = 4.dp)
                        .background(NeoBrutalismAccent)
                        .border(2.dp, MaterialTheme.colorScheme.outline)
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = playPauseDescription,
                        tint = Color.Black,
                        modifier = Modifier.size(30.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .graphicsLayer { scaleX = pulse; scaleY = pulse }
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.62f))))
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = playPauseDescription,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(18.dp))
            PlayerGlassButton(
                icon = Icons.Default.FastForward,
                contentDescription = null,
                onClick = { onSkip(skipSeconds) },
                size = 42.dp
            )
        }
    }
}

/** Feedback badge shown after a double-tap skip, so the jump is visible. */
@Composable
fun SeekPulseBadge(
    visible: Boolean,
    forward: Boolean,
    seconds: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.72f),
        exit = fadeOut() + scaleOut(targetScale = 1.22f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .background(
                    Color.Black.copy(alpha = 0.55f),
                    if (isNeobrutalismDesign()) RoundedCornerShape(0.dp) else RoundedCornerShape(40.dp)
                )
                .then(
                    if (isNeobrutalismDesign()) {
                        Modifier.border(1.5.dp, Color.White.copy(alpha = 0.35f))
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (forward) Icons.Default.FastForward else Icons.Default.FastRewind,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = String.format(Locale.US, "%ds", seconds),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Grab handle for the divider between the video and the subtitle list. The
 * drag itself is applied by the caller; this only draws the affordance.
 */
@Composable
fun SplitDragHandle(active: Boolean, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val width by animateFloatAsState(
        targetValue = if (active) 64f else 42f,
        animationSpec = tween(durationMillis = 180),
        label = "splitHandle"
    )
    Box(
        modifier = modifier.fillMaxWidth().height(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(width.dp)
                .height(if (isNeobrutalismDesign()) 6.dp else 5.dp)
                .clip(if (isNeobrutalismDesign()) RoundedCornerShape(0.dp) else RoundedCornerShape(3.dp))
                .background(
                    if (active) accent
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
        )
    }
}

/** Backdrop for audio files: spinning vinyl, cover art and the file name. */
@Composable
fun AudioArtworkStage(
    albumArt: android.graphics.Bitmap?,
    isPlaying: Boolean,
    isFullScreen: Boolean,
    fileName: String,
    modifier: Modifier = Modifier,
    subtitleContent: @Composable () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val glow = MaterialTheme.colorScheme.tertiary
    val infiniteTransition = rememberInfiniteTransition(label = "record_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val discSize = if (isFullScreen) 170.dp else 115.dp
    val artSize = if (isFullScreen) 152.dp else 102.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07070C))
            .background(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                    radius = 900f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(modifier = Modifier.size(discSize), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = if (isPlaying) rotationAngle else 0f }
                        .border(
                            width = 3.dp,
                            brush = Brush.sweepGradient(listOf(accent, glow, accent.copy(alpha = 0.2f), accent)),
                            shape = CircleShape
                        )
                )
                if (albumArt != null) {
                    Image(
                        bitmap = albumArt.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(artSize)
                            .graphicsLayer { rotationZ = if (isPlaying) rotationAngle * 0.3f else 0f }
                            .clip(CircleShape)
                            .border(2.dp, Color.Black.copy(alpha = 0.6f), CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = com.example.R.drawable.img_rhythm_cover),
                        contentDescription = null,
                        modifier = Modifier
                            .size(artSize)
                            .graphicsLayer { rotationZ = if (isPlaying) rotationAngle * 0.3f else 0f }
                            .clip(CircleShape)
                            .border(2.dp, Color.Black.copy(alpha = 0.6f), CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                )
            }
            if (fileName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            subtitleContent()
        }
    }
}

/** Clock formatter that supports videos longer than an hour and never uses localized digits. */
fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0L)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
