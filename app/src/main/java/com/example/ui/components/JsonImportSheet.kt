package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.logic.JsonQuizBuilder
import com.example.logic.SubtitleJsonParser
import com.example.logic.autoTextDirection
import com.example.model.JsonSubtitlePackage
import com.example.ui.theme.AppStrings

/**
 * The reusable JSON-import core: current-file status, the two
 * import options and the paste field with live detection. The Leitner
 * quiz's setup step embeds exactly this panel, so the JSON selection looks
 * and behaves identically everywhere.
 */
@Composable
fun JsonImportPanel(
    strings: AppStrings,
    pkg: JsonSubtitlePackage?,
    fileName: String,
    onPickFile: () -> Unit,
    onImportPaste: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPasteField by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }

    // Live CHUNK feedback for the paste field, so the user can see that the
    // marker line is recognised before importing.
    val pasteChunk = remember(pasteText) {
        if (pasteText.isBlank()) null else SubtitleJsonParser.detectChunkMarker(pasteText)
    }
    val pasteDetected = remember(pasteText) { SubtitleJsonParser.looksLikeSubtitleJson(pasteText) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ── What is loaded right now ──
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            tint = if (pkg != null) MaterialTheme.colorScheme.primary else null,
            contentPadding = PaddingValues(14.dp)
        ) {
            if (pkg != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = strings.jsonQuizLoadedInfo(fileName, pkg.subtitles.size, pkg.chunks.size),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = strings.jsonQuizCountTitle + ": " + JsonQuizBuilder.availableQuestions(pkg),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = strings.jsonQuizNoSource,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── The two ways to bring the JSON in ──
        JsonImportOption(
            icon = Icons.Filled.AttachFile,
            accent = MaterialTheme.colorScheme.primary,
            title = strings.selectJsonFileOption,
            description = strings.selectJsonFileDesc,
            onClick = onPickFile
        )
        JsonImportOption(
            icon = Icons.Filled.ContentPaste,
            accent = MaterialTheme.colorScheme.secondary,
            title = strings.pasteJsonOption,
            description = strings.pasteJsonDesc,
            onClick = { showPasteField = !showPasteField },
            expanded = showPasteField
        )

        if (showPasteField) {
            OutlinedTextField(
                value = pasteText,
                onValueChange = { pasteText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(strings.jsonQuizPasteFieldLabel, style = MaterialTheme.typography.bodySmall)
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Start,
                    textDirection = pasteText.autoTextDirection()
                ),
                minLines = 5,
                maxLines = 10,
                shape = RoundedCornerShape(14.dp)
            )
            when {
                pasteChunkLabel(pasteChunk) != null -> Text(
                    text = "${strings.jsonChunkDetectedLabel} ${pasteChunkLabel(pasteChunk)} — ${strings.jsonChunkAutoStripHint}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                pasteDetected -> Text(
                    text = strings.jsonDetectedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                pasteText.isNotBlank() -> Text(
                    text = strings.jsonNotSubtitleJson,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            GradientButton(
                text = strings.jsonQuizImportPasteBtn,
                onClick = {
                    onImportPaste(pasteText.trim())
                    pasteText = ""
                    showPasteField = false
                },
                enabled = pasteDetected,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** One large two-line option row of the import panel. */
@Composable
private fun JsonImportOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    title: String,
    description: String,
    onClick: () -> Unit,
    expanded: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, if (expanded) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun pasteChunkLabel(chunk: com.example.model.JsonChunkInfo?): String? = chunk?.shortLabel
