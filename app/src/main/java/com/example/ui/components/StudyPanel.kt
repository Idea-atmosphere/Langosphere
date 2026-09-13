package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.logic.KnownWordsStore
import com.example.ui.components.neoHardShadow
import com.example.ui.components.anime.inkBorder
import com.example.ui.components.anime.inkShadow
import com.example.ui.theme.AnimeColors
import com.example.ui.theme.AppStrings
import com.example.ui.theme.isAnimeDesign
import com.example.ui.theme.isNeobrutalismDesign
import kotlin.math.abs

/**
 * The study tools drawer of the player: playback speed, sentence looping,
 * pronunciation, listen mode and the coverage report.
 *
 * They live in one panel behind a single pill on purpose — seven separate
 * buttons across the top of a phone screen would be unusable.
 *
 * Langosphere: dark translucent glass panel. Neobrutalism: an opaque raised
 * panel with an ink border and a hard offset shadow (the cyber-brutalist
 * dark theme keeps light structural lines automatically via the scheme).
 */
@Composable
fun StudyPanel(
    currentSpeed: Float,
    strings: AppStrings,
    canLoopLine: Boolean,
    listenMode: Boolean,
    coverage: KnownWordsStore.CoverageStats?,
    onSelectSpeed: (Float) -> Unit,
    onLoopLine: () -> Unit,
    onToggleListen: () -> Unit,
    onMarkKnown: (String) -> Unit,
    modifier: Modifier = Modifier,
    canSpeak: Boolean = false,
    onSpeak: () -> Unit = {},
    onSpeakSlow: () -> Unit = {}
) {
    val speedRows = listOf(
        listOf(0.5f, 0.75f, 0.9f, 1.0f),
        listOf(1.25f, 1.5f, 1.75f, 2.0f)
    )
    val neo = isNeobrutalismDesign()
    val anime = isAnimeDesign()
    // Neo and toon panels are opaque: text follows the scheme so both light
    // (cream panel / ink) and dark (night panel / light ink) look deliberate.
    val panelShape = when {
        neo -> RoundedCornerShape(0.dp)
        anime -> RoundedCornerShape(20.dp)
        else -> RoundedCornerShape(22.dp)
    }
    val panelBg = if (neo || anime) {
        MaterialTheme.colorScheme.surface
    } else {
        Color.Black.copy(alpha = 0.66f)
    }
    val textPrimary = if (neo || anime) MaterialTheme.colorScheme.onSurface else Color.White
    val textSecondary =
        if (neo || anime) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.6f)
    val textFaint =
        if (neo || anime) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        else Color.White.copy(alpha = 0.45f)

    Column(
        modifier = modifier
            .widthIn(min = 220.dp, max = 300.dp)
            .then(
                if (anime) {
                    Modifier
                        .inkShadow(offset = 4.dp, shape = panelShape)
                        .clip(panelShape)
                        .background(panelBg)
                        .inkBorder(3.dp, panelShape)
                } else if (neo) {
                    Modifier
                        .neoHardShadow(MaterialTheme.colorScheme.outline, offset = 4.dp)
                        .background(panelBg)
                        .border(2.dp, MaterialTheme.colorScheme.outline)
                } else {
                    Modifier
                        .clip(panelShape)
                        .background(panelBg)
                        .border(1.dp, Color.White.copy(alpha = 0.14f), panelShape)
                }
            )
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PanelLabel(strings.studyPlaybackSpeed)
        Spacer(modifier = Modifier.height(8.dp))
        speedRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { value ->
                    PlayerTextPill(
                        text = formatSpeedLabel(value),
                        contentDescription = null,
                        onClick = { onSelectSpeed(value) },
                        active = abs(currentSpeed - value) < 0.01f,
                        height = 34.dp
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (canLoopLine) {
            PlayerTextPill(
                text = strings.studyLoopThisLine,
                contentDescription = null,
                onClick = onLoopLine,
                height = 34.dp
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (canSpeak) {
            PanelDivider()
            PanelLabel(strings.studyPronunciation)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlayerTextPill(
                    text = strings.studySpeakLine,
                    contentDescription = null,
                    onClick = onSpeak,
                    height = 32.dp
                )
                PlayerTextPill(
                    text = strings.studySlowBtn,
                    contentDescription = null,
                    onClick = onSpeakSlow,
                    height = 32.dp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        PanelDivider()
        PanelLabel(strings.studyListenMode)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = strings.studyListenDesc,
            color = textSecondary,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.height(6.dp))
        PlayerTextPill(
            text = if (listenMode) {
                strings.studyListenOn
            } else {
                strings.studyListenOff
            },
            contentDescription = null,
            onClick = onToggleListen,
            active = listenMode,
            height = 34.dp
        )

        if (coverage != null && coverage.totalTokens > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            PanelDivider()
            PanelLabel(strings.studyCoverage)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${coverage.percent}%",
                color = textPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = strings.studyCoverageKnownDesc,
                color = textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.studyMarkedKnown(coverage.knownUnique),
                color = textFaint,
                style = MaterialTheme.typography.labelSmall
            )

            if (coverage.topUnknown.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = strings.studyTopUnknownHint,
                    color = textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Fixed rows of three instead of a flow layout: predictable
                // and no experimental APIs.
                coverage.topUnknown.take(9).chunked(3).forEach { rowWords ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowWords.forEach { unknown ->
                            PlayerTextPill(
                                text = "${unknown.word} · ${unknown.count}",
                                contentDescription = null,
                                onClick = { onMarkKnown(unknown.word) },
                                height = 30.dp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun PanelLabel(text: String) {
    if (isAnimeDesign()) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = AnimeColors.Sakura,
        )
        return
    }
    val color = if (isNeobrutalismDesign()) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        Color.White.copy(alpha = 0.75f)
    }
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun PanelDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                if (isNeobrutalismDesign()) {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                } else {
                    Color.White.copy(alpha = 0.12f)
                }
            )
    )
    Spacer(modifier = Modifier.height(10.dp))
}
