package com.example.zonos.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.AppStrings
import com.example.zonos.ModelsLocation
import com.example.zonos.ZonosTtsController
import com.example.zonos.ZonosTtsController.TtsNotice
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
    val isThis = playingKey == key
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
