package com.example.zonos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.logic.autoTextAlign
import com.example.logic.autoTextDirection
import com.example.ui.components.SoftIconButton
import com.example.ui.theme.AppStrings
import com.example.zonos.EngineStatus
import com.example.zonos.ZonosTtsController
import com.example.zonos.ZonosTtsController.SpeechPhase
import com.example.zonos.formatEta
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The Zonos player popup — the one place where a sentence's on-device
 * voice is controlled. Opened by every Zonos button (cue cards, reader
 * lines, sentence boxes) through [ZonosTtsController.openPlayer]; hosted
 * once in MainScreen next to the banner.
 *
 * It shows the sentence (and its translation when there is one), the
 * phase of the request (model loading with percent + time left,
 * generating with elapsed time, playing with a seek bar), and the
 * controls: generate again (a new reading of the same text), from the
 * start (the cached take again), −5 s / +5 s, pause / resume, and the
 * playback speed (0.5–2.0×, pitch preserved, remembered).
 *
 * Closing the popup does not interrupt a take that is playing or a
 * model that is loading (the banner takes over the progress); a paused
 * take is stopped, so the sentence's button does not keep its spinner.
 */
@Composable
fun ZonosPlayerSheet(tts: ZonosTtsController, strings: AppStrings) {
    val request by tts.player.collectAsState()
    val req = request ?: return

    val scope = rememberCoroutineScope()
    var popupVisible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { popupVisible = true }
    val closePopup = {
        if (!closing) {
            closing = true
            popupVisible = false
            scope.launch {
                delay(220)
                tts.closePlayer()
            }
        }
    }

    val playingKey by tts.playingKey.collectAsState()
    val phase by tts.speechPhase.collectAsState()
    val progress by tts.loadProgress.collectAsState()
    val playback by tts.playback.collectAsState()
    val takes by tts.takes.collectAsState()
    val rate by tts.playbackRate.collectAsState()
    val engineState by tts.engine.state.collectAsState()

    val isThis = playingKey == req.key
    val loading = isThis && phase == SpeechPhase.LOADING_MODEL
    val generating = isThis && phase == SpeechPhase.GENERATING
    val busy = loading || generating
    val pb = if (isThis && phase == SpeechPhase.PLAYING) playback else null
    val hasTake = req.key in takes

    // Elapsed time of the generation phase (there is no byte progress for it).
    var generatingSince by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(req.key, generating) {
        generatingSince = System.currentTimeMillis()
        now = generatingSince
        while (generating) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    val scheme = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = closePopup,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(
                visible = popupVisible,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(260)) + fadeIn(tween(180)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(220)) + fadeOut(tween(160)),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = scheme.surface,
                    tonalElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 18.dp),
                    ) {
                        // ── Header ──
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.RecordVoiceOver,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = strings.ttsPlayerTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            SoftIconButton(
                                icon = Icons.Filled.Close,
                                contentDescription = strings.close,
                                onClick = { closePopup() },
                                size = 40.dp,
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // ── The sentence (+ translation) ──
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            DirectionalText(
                                text = req.text,
                                color = scheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            val tr = req.translation
                            if (tr != null) {
                                Spacer(Modifier.height(8.dp))
                                DirectionalText(
                                    text = tr,
                                    color = scheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Normal,
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                        Spacer(Modifier.height(12.dp))

                        // ── Phase: loading / generating / seek bar ──
                        when {
                            loading -> {
                                val lp = progress
                                PhaseLine(
                                    title = strings.ttsLoadingTitle,
                                    detail = if (lp != null) loadProgressLine(lp, strings) else strings.ttsLoadingPreparing(formatEta(0L)),
                                )
                                Spacer(Modifier.height(8.dp))
                                val fraction = lp?.fraction
                                if (fraction != null && engineState.status == EngineStatus.LOADING) {
                                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                            generating -> {
                                PhaseLine(title = strings.ttsGenerating(formatEta(now - generatingSince)), detail = null)
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            else -> {
                                SeekBar(
                                    positionMs = pb?.positionMs ?: 0L,
                                    durationMs = pb?.durationMs ?: 0L,
                                    enabled = pb != null && (pb.durationMs) > 0L,
                                    onSeek = { tts.seekTo(it) },
                                )
                                if (pb == null) {
                                    Text(
                                        text = if (hasTake) strings.ttsPlayerFinished else strings.ttsPlayerNotGenerated,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // ── Transport ──
                        // Always LTR: «back» stays on the left and «forward» on
                        // the right in the Persian UI too, like every player.
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TransportButton(
                                    icon = Icons.Filled.Refresh,
                                    label = strings.ttsPlayerRegenerate,
                                    enabled = !busy,
                                    onClick = { tts.regenerate() },
                                )
                                TransportButton(
                                    icon = Icons.Filled.Replay5,
                                    label = strings.ttsPlayerBack,
                                    enabled = pb != null,
                                    onClick = { tts.seekBy(-ZonosTtsController.SKIP_MS) },
                                )
                                // Centre: cancel while busy, pause / resume while
                                // playing, otherwise play (the cached take, or a
                                // first generation).
                                val centreIcon = when {
                                    busy -> Icons.Filled.Stop
                                    pb != null && !pb.paused -> Icons.Filled.Pause
                                    else -> Icons.Filled.PlayArrow
                                }
                                val centreLabel = when {
                                    busy -> strings.ttsStop
                                    pb != null && !pb.paused -> strings.ttsPlayerPause
                                    else -> strings.ttsPlayerPlay
                                }
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(scheme.primary)
                                        .clickable {
                                            when {
                                                busy -> tts.stop()
                                                pb != null && !pb.paused -> tts.pause()
                                                pb != null -> tts.resume()
                                                hasTake -> tts.restart()
                                                else -> tts.regenerate()
                                            }
                                        }
                                        .semantics { contentDescription = centreLabel },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (busy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(34.dp),
                                            strokeWidth = 2.5.dp,
                                            color = scheme.onPrimary,
                                        )
                                    }
                                    Icon(
                                        imageVector = centreIcon,
                                        contentDescription = null,
                                        tint = scheme.onPrimary,
                                        modifier = Modifier.size(if (busy) 16.dp else 30.dp),
                                    )
                                }
                                TransportButton(
                                    icon = Icons.Filled.Forward5,
                                    label = strings.ttsPlayerForward,
                                    enabled = pb != null,
                                    onClick = { tts.seekBy(ZonosTtsController.SKIP_MS) },
                                )
                                TransportButton(
                                    icon = Icons.Filled.Replay,
                                    label = strings.ttsPlayerRestart,
                                    enabled = !busy && (hasTake || pb != null),
                                    onClick = { tts.restart() },
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // ── Speed ──
                        Text(
                            text = strings.ttsPlayerRate(formatRate(rate)),
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurface,
                        )
                        Slider(
                            value = rate,
                            onValueChange = { tts.setPlaybackRate(snapRate(it)) },
                            valueRange = ZonosTtsController.RATE_MIN..ZonosTtsController.RATE_MAX,
                            steps = 5,
                        )
                        Text(
                            text = strings.ttsPlayerRateHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** A sentence in its own script direction (the original is usually LTR, a Persian translation RTL). */
@Composable
private fun DirectionalText(
    text: String,
    color: Color,
    style: TextStyle,
    fontWeight: FontWeight,
) {
    Text(
        text = text,
        style = style.copy(textDirection = text.autoTextDirection()),
        textAlign = text.autoTextAlign(),
        color = color,
        fontWeight = fontWeight,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PhaseLine(title: String, detail: String?) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = scheme.onSurface,
    )
    if (detail != null) {
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

/**
 * Position slider + "0:04 / 0:11". The thumb follows the finger while it
 * is down and the engine is asked to seek only on release, so the bar
 * does not fight the 250 ms time events during a drag.
 */
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, enabled: Boolean, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shown = dragging ?: fraction
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = shown,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { f -> onSeek((f * durationMs).toLong()) }
                    dragging = null
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val shownMs = if (enabled) (shown * durationMs).toLong() else 0L
                Text(
                    text = formatEta(shownMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (enabled) formatEta(durationMs) else "–:––",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun TransportButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (enabled) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.35f)
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = if (enabled) 0.12f else 0.05f))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** The slider's 7 stops: 0.5, 0.75, 1, 1.25, 1.5, 1.75, 2. */
internal fun snapRate(raw: Float): Float =
    (Math.round(raw.coerceIn(ZonosTtsController.RATE_MIN, ZonosTtsController.RATE_MAX) * 4f) / 4f)

/** "1.0", "1.25", "0.75", "2.0" — Latin digits everywhere, like the timers. */
internal fun formatRate(rate: Float): String {
    val s = String.format(Locale.US, "%.2f", rate)
    return if (s.endsWith("0")) s.dropLast(1) else s
}
