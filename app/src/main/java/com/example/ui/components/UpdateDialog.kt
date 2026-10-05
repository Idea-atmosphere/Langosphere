package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.logic.AppUpdateManager
import com.example.logic.ReleaseNotesParser
import com.example.ui.theme.AppStrings
import kotlinx.coroutines.delay

/**
 * The in-app update dialog: shows the new version against the installed one,
 * renders the GitHub release notes (markdown) as the changelog, and drives
 * the download → install flow.
 *
 * Where the update goes:
 *
 * 1. "Update now" checks Android 8.0+'s "Install unknown apps" grant first;
 *    if it is missing, the system settings screen is opened and the dialog
 *    explains what to do once the user comes back.
 * 2. With the grant in place, the APK is handed to the system DownloadManager
 *    (progress notification included) and polled here for a progress bar.
 * 3. When the download completes, the file is shared with the system package
 *    installer through FileProvider (FLAG_GRANT_READ_URI_PERMISSION), so the
 *    installer can read the APK out of the app's private storage.
 *
 * Styling follows the app's existing dialogs (DonateDialog / AboutDialog):
 * a Material 3 AlertDialog, localized copy through [AppStrings], and a
 * scrollable text column capped in height.
 */

/** Where the dialog's flow currently is. */
private enum class UpdateStage {
    /** Changelog + "Update now" / "Later". */
    CONFIRM,

    /** The APK is coming down through the system DownloadManager. */
    DOWNLOADING,

    /** The installer was launched; it now owns the screen. */
    DONE,

    /** The download or the installer hand-off failed. */
    FAILED
}

@Composable
fun UpdateDialog(
    updateInfo: AppUpdateManager.UpdateInfo,
    currentVersionName: String,
    strings: AppStrings,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var stage by remember { mutableStateOf(UpdateStage.CONFIRM) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var downloadFileName by remember { mutableStateOf<String?>(null) }
    var progressPercent by remember { mutableStateOf<Int?>(null) }
    var permissionHint by remember { mutableStateOf(false) }

    fun beginDownload() {
        val download = AppUpdateManager.startApkDownload(context, updateInfo)
        if (download == null) {
            stage = UpdateStage.FAILED
            return
        }
        downloadId = download.downloadId
        downloadFileName = download.fileName
        progressPercent = null
        permissionHint = false
        stage = UpdateStage.DOWNLOADING
    }

    fun onUpdateNowClicked() {
        if (!AppUpdateManager.canRequestPackageInstalls(context)) {
            // Android 8.0+ : the user must explicitly allow this app to
            // install packages. Open the system screen for it and explain
            // what to do when they come back; the button stays available,
            // so the second tap simply proceeds.
            permissionHint = true
            AppUpdateManager.openInstallPermissionSettings(context)
        } else {
            beginDownload()
        }
    }

    // Poll the system download while it runs. The loop is tied to the
    // download id, so a retry replaces it, and it is cancelled automatically
    // when the dialog leaves composition.
    LaunchedEffect(downloadId, downloadFileName) {
        val id = downloadId ?: return@LaunchedEffect
        while (true) {
            when (val status = AppUpdateManager.queryDownload(context, id)) {
                is AppUpdateManager.DownloadStatus.Successful -> {
                    // COLUMN_LOCAL_URI is a file:// URI when the destination
                    // was a file path; the installer needs the FileProvider
                    // content:// URI instead.
                    val apkUri = status.localUri
                        ?: downloadFileName?.let { AppUpdateManager.downloadedApkContentUri(context, it) }
                    stage = if (apkUri != null && AppUpdateManager.installApk(context, apkUri)) {
                        UpdateStage.DONE
                    } else {
                        UpdateStage.FAILED
                    }
                    return@LaunchedEffect
                }

                is AppUpdateManager.DownloadStatus.Failed -> {
                    stage = UpdateStage.FAILED
                    return@LaunchedEffect
                }

                is AppUpdateManager.DownloadStatus.Running -> {
                    progressPercent = ((status.bytesSoFar * 100) / status.totalBytes).toInt()
                }

                is AppUpdateManager.DownloadStatus.Pending -> {
                    progressPercent = null
                }
            }
            delay(500)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.updateDialogTitle) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        strings.updateCurrentVersionLabel(currentVersionName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "→",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        strings.updateNewVersionLabel(updateInfo.version.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                val sizeText = AppUpdateManager.formatByteSize(updateInfo.apkSizeBytes)
                if (sizeText.isNotEmpty()) {
                    Text(
                        strings.updateSizeLabel(sizeText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                Text(
                    strings.updateChangelogTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (updateInfo.releaseNotes.isNotBlank()) {
                    MarkdownChangelog(updateInfo.releaseNotes)
                } else {
                    Text(
                        updateInfo.releaseName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when (stage) {
                    UpdateStage.DOWNLOADING -> {
                        val percent = progressPercent
                        if (percent != null) {
                            LinearProgressIndicator(
                                progress = { percent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                strings.updateDownloadingLabel(percent),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                strings.updateDownloadingNoProgress,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    UpdateStage.DONE -> Text(
                        strings.updateDownloadedLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    UpdateStage.FAILED -> Text(
                        strings.updateFailedMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    UpdateStage.CONFIRM -> {}
                }

                if (permissionHint && stage == UpdateStage.CONFIRM) {
                    Text(
                        strings.updatePermissionHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        },
        confirmButton = {
            when (stage) {
                UpdateStage.CONFIRM -> TextButton(onClick = { onUpdateNowClicked() }) {
                    Text(strings.updateNowBtn)
                }

                UpdateStage.DOWNLOADING -> TextButton(
                    onClick = {
                        downloadId?.let { AppUpdateManager.cancelDownload(context, it) }
                        downloadId = null
                        progressPercent = null
                        stage = UpdateStage.CONFIRM
                    }
                ) { Text(strings.updateCancelBtn) }

                UpdateStage.DONE -> TextButton(onClick = onDismiss) {
                    Text(strings.updateCloseBtn)
                }

                UpdateStage.FAILED -> TextButton(onClick = { onUpdateNowClicked() }) {
                    Text(strings.updateRetryBtn)
                }
            }
        },
        dismissButton = {
            if (stage == UpdateStage.CONFIRM || stage == UpdateStage.FAILED) {
                TextButton(onClick = onDismiss) { Text(strings.updateLaterBtn) }
            }
        }
    )
}

/**
 * Renders the release notes as a column of styled markdown blocks
 * (headings, list items, code, dividers), matching the app's Material 3
 * type scale so the changelog inherits whichever design skin is active.
 */
@Composable
private fun MarkdownChangelog(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { ReleaseNotesParser.parse(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is ReleaseNotesParser.Block.Heading -> MarkdownLine(
                    spans = block.spans,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.Bold
                )

                is ReleaseNotesParser.Block.Paragraph -> MarkdownLine(
                    spans = block.spans,
                    style = MaterialTheme.typography.bodySmall
                )

                is ReleaseNotesParser.Block.ListItem -> MarkdownLine(
                    spans = block.spans,
                    style = MaterialTheme.typography.bodySmall,
                    prefix = "•  "
                )

                is ReleaseNotesParser.Block.CodeBlock -> Text(
                    text = block.code,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp)
                )

                ReleaseNotesParser.Block.Divider -> HorizontalDivider()
            }
        }
    }
}

/**
 * One line of styled markdown text. Link spans are styled and made tappable
 * by hit-testing the text layout in [pointerInput] — the pre-1.7 pattern,
 * which works on every Compose version this app builds with.
 */
@Composable
private fun MarkdownLine(
    spans: List<ReleaseNotesParser.Span>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    prefix: String? = null
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val uriHandler = LocalUriHandler.current

    val styled = remember(spans, linkColor, codeBackground, prefix) {
        val linkRanges = mutableListOf<Pair<IntRange, String>>()
        val annotated = buildAnnotatedString {
            if (prefix != null) append(prefix)
            for (span in spans) {
                val start = length
                append(span.text)
                val end = length
                if (span.linkUrl != null) {
                    addStyle(
                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                        start,
                        end
                    )
                    linkRanges.add((start until end) to span.linkUrl)
                }
                if (span.bold) {
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                }
                if (span.italic) {
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                }
                if (span.code) {
                    addStyle(
                        SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground),
                        start,
                        end
                    )
                }
            }
        }
        annotated to linkRanges
    }
    val (annotated, linkRanges) = styled

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotated,
        style = if (fontWeight != null) style.copy(fontWeight = fontWeight) else style,
        onTextLayout = { layoutResult = it },
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures { offset ->
                val layout = layoutResult ?: return@detectTapGestures
                val charIndex = layout.getOffsetForPosition(offset)
                val url = linkRanges.firstOrNull { charIndex in it.first }?.second
                    ?: return@detectTapGestures
                runCatching { uriHandler.openUri(url) }
            }
        }
    )
}
