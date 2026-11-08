package com.example.ui.components

import android.widget.Toast
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
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

/**
 * The in-app update dialog: shows the new version against the installed one,
 * renders the GitHub release notes (markdown) as the changelog, and hands the
 * APK's download link to the browser.
 *
 * Nothing is downloaded or installed by the app itself. "Download in
 * browser" opens the release asset's `browser_download_url`; the browser
 * downloads the file and the user installs it from the browser's download
 * notification, like any other APK. Two smaller actions cover the rest:
 * the release page on GitHub, and copying the download link (for a download
 * manager, or when no browser could be opened).
 *
 * Dismissal has two flavours: [onLater] (the "Later" button, tapping
 * outside, Back) marks this version as skipped so the launch check stops
 * asking until a newer release appears; [onClose] (after the link was opened)
 * only closes the dialog, so an update that was downloaded but not installed
 * is offered again next time.
 *
 * Styling follows the app's existing dialogs (DonateDialog / AboutDialog):
 * a Material 3 AlertDialog, localized copy through [AppStrings], and a
 * scrollable text column capped in height.
 */

/** Where the dialog's flow currently is. */
private enum class UpdateStage {
    /** Changelog + "Download in browser" / "Later". */
    CONFIRM,

    /** The link was handed to the browser; the download runs there. */
    OPENED,

    /** No app on the device could open the link. */
    FAILED
}

@Composable
fun UpdateDialog(
    updateInfo: AppUpdateManager.UpdateInfo,
    currentVersionName: String,
    strings: AppStrings,
    onLater: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var stage by remember { mutableStateOf(UpdateStage.CONFIRM) }

    fun openDownload() {
        stage = if (AppUpdateManager.openApkDownload(context, updateInfo)) {
            UpdateStage.OPENED
        } else {
            UpdateStage.FAILED
        }
    }

    fun copyLink() {
        clipboard.setText(AnnotatedString(updateInfo.apkUrl))
        Toast.makeText(context, strings.updateLinkCopiedToast, Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = { if (stage == UpdateStage.OPENED) onClose() else onLater() },
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
                    UpdateStage.OPENED -> Text(
                        strings.updateOpenedInBrowser,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    UpdateStage.FAILED -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            strings.updateFailedMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            updateInfo.apkUrl,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    UpdateStage.CONFIRM -> {}
                }

                // Secondary actions, kept out of the button bar: the release
                // page (notes, every asset) and the raw link (download
                // managers, or no browser at all).
                Column(horizontalAlignment = Alignment.Start) {
                    TextButton(
                        onClick = { AppUpdateManager.openReleasePage(context, updateInfo) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(strings.updateOpenReleasePage, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        onClick = { copyLink() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(strings.updateCopyLinkBtn, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            when (stage) {
                UpdateStage.CONFIRM,
                UpdateStage.FAILED -> TextButton(onClick = { openDownload() }) {
                    Text(strings.updateNowBtn)
                }

                UpdateStage.OPENED -> TextButton(onClick = onClose) {
                    Text(strings.updateCloseBtn)
                }
            }
        },
        dismissButton = {
            if (stage != UpdateStage.OPENED) {
                TextButton(onClick = onLater) { Text(strings.updateLaterBtn) }
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
