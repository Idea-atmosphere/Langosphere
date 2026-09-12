package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.logic.SubtitleJsonParser
import com.example.model.JsonChunkInfo
import com.example.model.JsonSubtitlePackage
import com.example.ui.theme.AppStrings

/**
 * The "CHUNK" panel of the subtitle import section.
 *
 * A long film is produced by the AI one chunk at a time, and every chunk that
 * carries the `CHUNK 1-50` marker is merged into the loaded JSON package
 * automatically (see [com.example.logic.SubtitleJsonParser.mergePackages]).
 * This panel makes that merge visible and reversible:
 *
 *  - one row per merged chunk: its range, how many lines it contributed and
 *    which file/chat it came from,
 *  - a delete button per chunk, so one bad AI answer can be dropped without
 *    losing the rest of the film,
 *  - an export button, so the merged result can be saved to Downloads as one
 *    standard JSON file,
 *  - a "remove JSON only" button that clears the JSON slot and leaves the
 *    plain source/target subtitle files alone.
 *
 * It is shown for every loaded JSON package; the chunk list simply stays empty
 * for a normal single-file import.
 */
@Composable
fun JsonChunkPanel(
    strings: AppStrings,
    pkg: JsonSubtitlePackage,
    fileName: String,
    onRemoveChunk: (JsonChunkInfo) -> Unit,
    onRemoveJson: () -> Unit,
    onExportJson: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chunks = pkg.chunks

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        tint = MaterialTheme.colorScheme.tertiary,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.jsonChunksTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            StatusPill(text = strings.jsonChunkLines(pkg.subtitles.size), tone = PillTone.Neutral)
            if (pkg.isMerged) {
                Spacer(modifier = Modifier.width(6.dp))
                StatusPill(
                    text = "${strings.jsonChunkMergedBadge} • ${chunks.size}",
                    tone = PillTone.Accent
                )
            }
        }

        Text(
            text = if (chunks.isEmpty()) {
                if (fileName.isBlank()) strings.jsonChunksHint else "$fileName • ${strings.jsonChunksHint}"
            } else {
                strings.jsonChunksSummary(chunks.size, pkg.subtitles.size)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (chunks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            // A horizontally scrollable stack of chunk chips keeps the import
            // section compact even for a film split into thirty answers.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                chunks.forEach { chunk ->
                    val chunkKeys = chunk.subtitleKeys.toSet()
                    ChunkChip(
                        strings = strings,
                        chunk = chunk,
                        contributedLines = if (chunkKeys.isEmpty()) 0 else pkg.subtitles.count {
                            SubtitleJsonParser.subtitleKey(it) in chunkKeys
                        },
                        onRemove = { onRemoveChunk(chunk) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onExportJson,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.jsonExportBtn, style = MaterialTheme.typography.labelSmall)
            }
            TextButton(
                onClick = onRemoveJson,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = strings.jsonRemoveOnlyJsonBtn,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** One merged chunk: its range, its line count, and its own delete button. */
@Composable
private fun ChunkChip(
    strings: AppStrings,
    chunk: JsonChunkInfo,
    contributedLines: Int,
    onRemove: () -> Unit
) {
    GlassCard(
        cornerRadius = 14.dp,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    text = chunk.shortLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                val detail = buildString {
                    if (contributedLines > 0) append(strings.jsonChunkLines(contributedLines))
                    if (chunk.sourceName.isNotBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(chunk.sourceName)
                    }
                }
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            SoftIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = strings.jsonRemoveChunkCd,
                onClick = onRemove,
                tint = MaterialTheme.colorScheme.error,
                size = 28.dp
            )
        }
    }
}
