package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import com.example.logic.autoTextDirection
import com.example.ui.components.GlassCard
import com.example.ui.theme.AppStrings
import com.example.ui.theme.LanguagePairState

/**
 * The "source language → target language" pair, edited inside
 * Settings ▸ Tutorial & AI Learning, right above the prompt generator.
 *
 * Both fields are free text and whatever the learner types is what the app
 * uses — in the generated prompt and in every label that used to hardcode
 * "English" / "Persian" (see [LanguagePairState] and the language-aware
 * getters in [AppStrings]). Nothing translates a typed name into another
 * spelling: writing "آلمانی" puts "آلمانی" in the prompt, writing "German"
 * puts "German" in it.
 *
 * Each field has a small circled "!" that reveals what the field actually
 * means, because "source" and "target" are easy to mix up.
 *
 * Changes are persisted as they are typed (like every other setting in this
 * app), so there is no "Save" step to forget.
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

    // Re-read the stored pair when it changes somewhere else (e.g. a reset).
    val storedSource = LanguagePairState.sourceRaw
    val storedTarget = LanguagePairState.targetRaw
    LaunchedEffect(storedSource, storedTarget) {
        // Compare trimmed: setSource()/setTarget() store the trimmed text, and
        // rewriting the field on every trailing space would fight the keyboard.
        if (sourceText.trim() != storedSource) sourceText = storedSource
        if (targetText.trim() != storedTarget) targetText = storedTarget
    }

    val sourceName = sourceText.trim().ifEmpty { LanguagePairState.DEFAULT_SOURCE }
    val targetName = targetText.trim().ifEmpty { LanguagePairState.DEFAULT_TARGET }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        tint = MaterialTheme.colorScheme.primary,
        contentPadding = PaddingValues(16.dp)
    ) {
        // ── Source language ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.sourceLanguageLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            HelpDot(
                active = showSourceHelp,
                contentDescription = strings.languageHelpCd,
                onClick = { showSourceHelp = !showSourceHelp }
            )
        }
        if (showSourceHelp) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.sourceLanguageHelp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = sourceText,
            onValueChange = { value ->
                sourceText = value
                LanguagePairState.setSource(context, value)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(strings.sourceLanguageHint, style = MaterialTheme.typography.bodySmall) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                textAlign = TextAlign.Start,
                textDirection = sourceText.autoTextDirection()
            ),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Target language ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.targetLanguageLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            HelpDot(
                active = showTargetHelp,
                contentDescription = strings.languageHelpCd,
                onClick = { showTargetHelp = !showTargetHelp }
            )
        }
        if (showTargetHelp) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.targetLanguageHelp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = targetText,
            onValueChange = { value ->
                targetText = value
                LanguagePairState.setTarget(context, value)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(strings.targetLanguageHint, style = MaterialTheme.typography.bodySmall) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                textAlign = TextAlign.Start,
                textDirection = targetText.autoTextDirection()
            ),
            shape = RoundedCornerShape(14.dp)
        )

        if (showPreview) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = strings.languagePairPreviewTitle,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.languagePairArrow(sourceName, targetName),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = strings.languagePairSubtitlePreview(sourceName, targetName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = strings.languagePairPromptPreview(sourceName, targetName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = strings.languagePairQuizPreview(sourceName, targetName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (sourceName.equals(targetName, ignoreCase = true)) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = strings.languageSameWarning,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * The circled "!" next to a field label: one tap shows (or hides) what that
 * field means. Drawn by hand instead of using an icon so it reads as a plain
 * exclamation mark in every design language the app has.
 */
@Composable
private fun HelpDot(
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    val background = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    }
    val glyph = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "!",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = glyph
        )
    }
}
