package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.logic.autoTextDirection
import com.example.ui.components.GlassCard
import com.example.ui.theme.AppStrings
import com.example.ui.theme.LanguagePairState
import com.example.ui.theme.LanguageWeight
import com.example.ui.theme.LanguageWeightState

/**
 * The "source language → target language" pair, edited inside
 * Settings ▸ Prompts, right above the prompt generator.
 *
 * Both fields are free text and whatever the learner types is what the app
 * uses — in the generated prompt and in every label that used to hardcode
 * "English" / "Persian" (see [LanguagePairState] and the language-aware
 * getters in [AppStrings]). Nothing translates a typed name into another
 * spelling.
 *
 * Also hosts the per-language font-weight pickers (Regular / Medium / Bold /
 * Black) so the learner can make the source or the target stand out more in
 * subtitles, reader and cards.
 */
@Composable
fun LanguagePairFields(
    strings: AppStrings,
    modifier: Modifier = Modifier,
    showPreview: Boolean = false
) {
    val context = LocalContext.current
    var sourceText by remember { mutableStateOf(LanguagePairState.sourceRaw) }
    var targetText by remember { mutableStateOf(LanguagePairState.targetRaw) }
    var showSourceHelp by remember { mutableStateOf(false) }
    var showTargetHelp by remember { mutableStateOf(false) }

    val storedSource = LanguagePairState.sourceRaw
    val storedTarget = LanguagePairState.targetRaw
    LaunchedEffect(storedSource, storedTarget) {
        if (sourceText.trim() != storedSource) sourceText = storedSource
        if (targetText.trim() != storedTarget) targetText = storedTarget
    }

    val sourceName = sourceText.trim().ifEmpty { LanguagePairState.DEFAULT_SOURCE }
    val targetName = targetText.trim().ifEmpty { LanguagePairState.DEFAULT_TARGET }
    val isFa = remember(strings) { strings.close.any { it.code in 0x0600..0x06FF } }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        tint = MaterialTheme.colorScheme.primary,
        contentPadding = PaddingValues(16.dp)
    ) {
        // ── Source language ──
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = strings.sourceLanguageLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            HelpDot(active = showSourceHelp, contentDescription = strings.languageHelpCd, onClick = { showSourceHelp = !showSourceHelp })
        }
        if (showSourceHelp) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = strings.sourceLanguageHelp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = sourceText,
            onValueChange = { value -> sourceText = value; LanguagePairState.setSource(context, value) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(strings.sourceLanguageHint, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Start, textDirection = sourceText.autoTextDirection()),
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Source weight
        Text(
            text = if (isFa) "وزن فونت زبان اصلی" else "Source language weight",
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LanguageWeight.values().forEach { w ->
                FilterChip(
                    selected = LanguageWeightState.sourceWeight == w,
                    onClick = { LanguageWeightState.setSourceWeight(context, w) },
                    label = { Text(LanguageWeight.label(w, isFa), style = MaterialTheme.typography.labelSmall.copy(fontWeight = w.weight), maxLines = 1, softWrap = false) }
                )
            }
        }
        // Preview of weight
        Text(
            text = sourceName,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = LanguageWeightState.sourceWeight.weight),
            color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Target language ──
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = strings.targetLanguageLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            HelpDot(active = showTargetHelp, contentDescription = strings.languageHelpCd, onClick = { showTargetHelp = !showTargetHelp })
        }
        if (showTargetHelp) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = strings.targetLanguageHelp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = targetText,
            onValueChange = { value -> targetText = value; LanguagePairState.setTarget(context, value) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(strings.targetLanguageHint, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Start, textDirection = targetText.autoTextDirection()),
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isFa) "وزن فونت زبان هدف" else "Target language weight",
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LanguageWeight.values().forEach { w ->
                FilterChip(
                    selected = LanguageWeightState.targetWeight == w,
                    onClick = { LanguageWeightState.setTargetWeight(context, w) },
                    label = { Text(LanguageWeight.label(w, isFa), style = MaterialTheme.typography.labelSmall.copy(fontWeight = w.weight), maxLines = 1, softWrap = false) }
                )
            }
        }
        Text(
            text = targetName,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = LanguageWeightState.targetWeight.weight),
            color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis
        )

        if (showPreview) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = strings.languagePairPreviewTitle, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 2)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = strings.languagePairArrow(sourceName, targetName), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = strings.languagePairSubtitlePreview(sourceName, targetName), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(text = strings.languagePairPromptPreview(sourceName, targetName), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(text = strings.languagePairQuizPreview(sourceName, targetName), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (sourceName.equals(targetName, ignoreCase = true)) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = strings.languageSameWarning, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 3)
            }
        }
    }
}

@Composable
private fun HelpDot(active: Boolean, contentDescription: String, onClick: () -> Unit) {
    val background = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val glyph = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier.size(22.dp).clip(CircleShape).background(background).clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "!", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = glyph)
    }
}
