package com.example.zonos.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.components.SentenceStudyLabels
import com.example.ui.theme.AppStrings
import com.example.zonos.EngineStatus
import com.example.zonos.LoadProgress
import com.example.zonos.ModelsLocation
import com.example.zonos.ZonosTtsController
import com.example.zonos.ZonosTtsController.SpeechPhase
import com.example.zonos.ZonosTtsController.TtsNotice
import com.example.zonos.formatEta
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The app-wide TTS controller, provided by MainScreen. Null (the default)
 * hides every Zonos control, so a surface that is composed outside the
 * main screen (previews, tests) simply shows no speaker.
 */
val LocalZonosTts = staticCompositionLocalOf<ZonosTtsController?> { null }

/**
 * The small "read aloud with Zonos" button that sits next to a subtitle
 * cue or a book sentence. [key] identifies the sentence: while it is the
 * one being generated/played the icon becomes a spinner and a tap stops
 * it. Only one sentence is ever generated at a time (the controller
 * stops the previous one), which is the rule for the book reader.
 */
@Composable
fun ZonosSpeakButton(
    key: String,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    size: Int = 18,
    contentDescription: String? = null,
    /** Runs when a tap starts speech (not on stop) — the reader uses it to focus the sentence. */
    onSpeak: (() -> Unit)? = null,
) {
    val tts = LocalZonosTts.current ?: return
    if (text.isBlank()) return
    val playingKey by tts.playingKey.collectAsState()
    val replaying by tts.replaying.collectAsState()
    val isThis = playingKey == key && !replaying
    IconButton(
        onClick = {
            if (isThis) {
                tts.stop()
            } else {
                onSpeak?.invoke()
                tts.speak(key, text)
            }
        },
        modifier = modifier.size((size + 12).dp),
    ) {
        if (isThis) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(size.dp),
                    strokeWidth = 2.dp,
                    color = tint,
                )
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size((size * 0.55f).dp),
                )
            }
        } else {
            Icon(
                imageVector = Icons.Filled.RecordVoiceOver,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(size.dp),
            )
        }
    }
}

/**
 * "Hear it again": plays the take generated earlier for [key] without
 * generating (instant, same reading). Composes nothing until a take for
 * this key exists, so it appears right after the first playback and goes
 * away when the engine page drops it. Same spinner/stop behaviour as
 * [ZonosSpeakButton] while it plays.
 */
@Composable
fun ZonosReplayButton(
    key: String,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    size: Int = 18,
    contentDescription: String? = null,
    onSpeak: (() -> Unit)? = null,
) {
    val tts = LocalZonosTts.current ?: return
    if (text.isBlank()) return
    val takes by tts.takes.collectAsState()
    if (key !in takes) return
    val playingKey by tts.playingKey.collectAsState()
    val replaying by tts.replaying.collectAsState()
    val isThis = playingKey == key && replaying
    IconButton(
        onClick = {
            if (isThis) {
                tts.stop()
            } else {
                onSpeak?.invoke()
                tts.replay(key, text)
            }
        },
        modifier = modifier.size((size + 12).dp),
    ) {
        if (isThis) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(size.dp),
                    strokeWidth = 2.dp,
                    color = tint,
                )
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size((size * 0.55f).dp),
                )
            }
        } else {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(size.dp),
            )
        }
    }
}

/**
 * The pair every reader line gets: generate-and-play, then (once a take
 * exists) play-again. [labels] gives the two content descriptions.
 */
@Composable
fun ZonosLineButtons(
    key: String,
    text: String,
    labels: SentenceStudyLabels,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    size: Int = 16,
    onSpeak: (() -> Unit)? = null,
) {
    if (LocalZonosTts.current == null || text.isBlank()) return
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        ZonosSpeakButton(
            key = key,
            text = text,
            tint = tint,
            size = size,
            contentDescription = labels.zonosSpeak,
            onSpeak = onSpeak,
        )
        ZonosReplayButton(
            key = key,
            text = text,
            tint = tint,
            size = size,
            contentDescription = labels.zonosReplay,
            onSpeak = onSpeak,
        )
    }
}

/**
 * Floating card at the top of the screen while a requested playback is
 * waiting for the model to load (percent + time left) or for the
 * sentence to be generated (elapsed). Hidden while audio plays and when
 * nothing is requested. The X stops the request (the model keeps
 * loading in the background, so the next tap is faster).
 */
@Composable
fun ZonosSpeechBanner(tts: ZonosTtsController, strings: AppStrings, modifier: Modifier = Modifier) {
    val playingKey by tts.playingKey.collectAsState()
    val phase by tts.speechPhase.collectAsState()
    val progress by tts.loadProgress.collectAsState()
    val engineState by tts.engine.state.collectAsState()
    val core by tts.coreStatus.collectAsState()
    val visible = playingKey != null && (phase == SpeechPhase.LOADING_MODEL || phase == SpeechPhase.GENERATING)

    // Elapsed time of the generation phase (there is no byte progress for it).
    var generatingSince by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(playingKey, phase, visible) {
        generatingSince = System.currentTimeMillis()
        now = generatingSince
        while (visible) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
    ) {
        val scheme = MaterialTheme.colorScheme
        Surface(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = scheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (phase == SpeechPhase.LOADING_MODEL) Icons.Filled.RecordVoiceOver else Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val lp = progress
                        val loading = phase == SpeechPhase.LOADING_MODEL
                        Text(
                            text = if (loading) strings.ttsLoadingTitle else strings.ttsGenerating(formatEta(now - generatingSince)),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                        )
                        if (loading) {
                            Text(
                                text = if (lp != null) loadProgressLine(lp, strings) else strings.ttsLoadingPreparing(formatEta(0L)),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { tts.stop() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = strings.ttsStop, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
                if (phase == SpeechPhase.LOADING_MODEL) {
                    Spacer(Modifier.height(8.dp))
                    val fraction = progress?.fraction
                    if (fraction != null && engineState.status == EngineStatus.LOADING) {
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(end = 8.dp))
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(end = 8.dp))
                    }
                    if (progress?.fraction == null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = strings.ttsLoadingFirstTime(formatBytes(core.def.totalBytes)),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** One line for a [LoadProgress]: "42% · about 1:20 left", "Preparing… 0:05 elapsed", … */
fun loadProgressLine(lp: LoadProgress, strings: AppStrings): String {
    val elapsed = formatEta(lp.elapsedMs)
    val fraction = lp.fraction
    return when {
        lp.finalizing -> strings.ttsLoadingFinalizing(elapsed)
        fraction != null && lp.etaMs != null -> strings.ttsLoadingPercent((fraction * 100).toInt().coerceIn(0, 100), formatEta(lp.etaMs))
        fraction != null -> strings.ttsLoadingPercentOnly((fraction * 100).toInt().coerceIn(0, 100))
        lp.etaMs != null && lp.etaFromHistory -> strings.ttsLoadingAbout(formatEta(lp.etaMs), formatEta(lp.etaMs + lp.elapsedMs))
        else -> strings.ttsLoadingPreparing(elapsed)
    }
}

/**
 * Hosts the hidden engine WebView (1 dp, invisible) so it lives in the
 * window and can run WebGPU + audio. Compose it once, near the root of
 * MainScreen; the WebView is built with the Activity context the factory
 * receives (system popups need it) and survives recompositions. Nothing
 * is built until the model pack is on the phone, so learners who never
 * use the feature pay nothing for it.
 */
@Composable
fun ZonosEngineHost(tts: ZonosTtsController, modifier: Modifier = Modifier) {
    val core by tts.coreStatus.collectAsState()
    if (!core.installed) return
    AndroidView(
        factory = { ctx -> tts.engine.attachedWebView(ctx) },
        modifier = modifier.size(1.dp).alpha(0f),
    )
}

/** Shows every controller notice as a toast, in the app language. */
@Composable
fun ZonosNoticeEffect(tts: ZonosTtsController, strings: AppStrings) {
    val context = LocalContext.current
    val notice by tts.notice.collectAsState()
    LaunchedEffect(notice) {
        val n = notice ?: return@LaunchedEffect
        Toast.makeText(context, ttsNoticeText(n, strings), Toast.LENGTH_LONG).show()
        tts.dismissNotice()
    }
}

/** Localized text for a controller notice. */
fun ttsNoticeText(n: TtsNotice, s: AppStrings): String = when (n) {
    TtsNotice.CoreMissing -> s.ttsNoticeCoreMissing
    is TtsNotice.CoreIncomplete -> s.ttsNoticeCoreIncomplete(n.missingFiles, formatBytes(n.missingBytes))
    TtsNotice.EngineNotReady -> s.ttsNoticeEngineNotReady
    is TtsNotice.GenerateFailed -> s.ttsNoticeGenerateFailed(n.detail)
    TtsNotice.DownloadBusy -> s.ttsNoticeDownloadBusy
    is TtsNotice.DownloadDone -> buildString {
        append(if (n.completedOnly) s.ttsNoticeCompleteDone else s.ttsNoticeDownloadDone)
        if (n.cacheCleared > 0) append(' ').append(s.ttsNoticeCacheCleared(n.cacheCleared))
    }
    TtsNotice.DownloadStopped -> s.ttsNoticeDownloadStopped
    is TtsNotice.DownloadFailed -> s.ttsNoticeDownloadFailed(n.detail)
    TtsNotice.NameClash -> s.ttsNoticeNameClash(ModelsLocation.MODELS_RELATIVE_PATH)
    TtsNotice.NeedsFolderPick -> s.ttsNoticeNeedsFolderPick(ModelsLocation.MODELS_RELATIVE_PATH)
    TtsNotice.VerifyOk -> s.ttsNoticeVerifyOk
    is TtsNotice.VerifyBad -> s.ttsNoticeVerifyBad(n.detail)
    is TtsNotice.Deleted -> s.ttsNoticeDeleted(formatBytes(n.freedBytes))
    is TtsNotice.Imported -> buildString {
        append(s.ttsNoticeImported(n.files))
        if (n.skipped > 0) append(' ').append(s.ttsNoticeImportedSkipped(n.skipped))
        if (n.stillIncomplete) append(' ').append(s.ttsNoticeImportedIncomplete)
    }
    TtsNotice.ImportNothing -> s.ttsNoticeImportNothing
    is TtsNotice.ImportFailed -> s.ttsNoticeImportFailed(n.detail)
    is TtsNotice.Located -> s.ttsNoticeLocated(n.files)
    TtsNotice.LocateNothing -> s.ttsNoticeLocateNothing
    is TtsNotice.LocateFailed -> s.ttsNoticeLocateFailed(n.detail)
    is TtsNotice.ExportSaved -> s.ttsNoticeExportSaved(n.destination)
    is TtsNotice.ExportFailed -> s.ttsNoticeExportFailed(n.detail)
}

/** "920 MB", "21.6 MB", "327 KB" — compact, locale-neutral digits. */
fun formatBytes(bytes: Long): String {
    val mb = bytes / 1_048_576.0
    return when {
        bytes >= 100L * 1_048_576 -> String.format(Locale.US, "%.0f MB", mb)
        bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", mb)
        bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
