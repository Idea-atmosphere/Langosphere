package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.logic.AiPromptTemplates
import com.example.ui.theme.AppStrings

/**
 * Settings ▸ "Tutorial & AI Learning" section.
 *
 * Manages the AI-learning prompts and JSON learning instructions:
 *  - the user's learning level (A1..C2) used everywhere for
 *    level-appropriate explanations,
 *  - the "Use Dictionary When JSON Learning Data Exists" toggle,
 *  - the JSON Learning Prompt Generator (ready-to-copy AI prompts per level),
 *  - the three prompt modes: Translation Only / Translation + Learning /
 *    Word Analysis.
 *
 * Every prompt produced here targets the app's JSON parser format
 * (logic/SubtitleJsonParser.kt), so the AI output can be imported straight
 * into the video player's JSON subtitle slot.
 */
@Composable
fun TutorialAiDialog(
    strings: AppStrings,
    learningLevel: String,
    useDictionaryWithJson: Boolean,
    onLearningLevelChange: (String) -> Unit,
    onDictionaryToggleChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // The level selected for the generated prompt; starts at the user's
    // learning level and can be changed independently.
    var promptLevel by remember { mutableStateOf(learningLevel.uppercase()) }
    var selectedMode by remember { mutableStateOf(AiPromptTemplates.PromptMode.TRANSLATION_LEARNING) }
    val prompt = remember(promptLevel, selectedMode) {
        AiPromptTemplates.buildPrompt(promptLevel, selectedMode)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.extraLarge,
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.tutorialTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = strings.close,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Section 1: learning level ──
                SectionTitle(strings.tutorialLearningLevelTitle)
                Text(
                    text = strings.tutorialLearningLevelDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AiPromptTemplates.LEVELS.forEach { level ->
                        FilterChip(
                            selected = learningLevel.equals(level, ignoreCase = true),
                            onClick = { onLearningLevelChange(level) },
                            label = { Text(strings.levelName(level), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(14.dp))

                // ── Section 2: dictionary toggle ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.dictionaryJsonToggleTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (useDictionaryWithJson) strings.dictionaryJsonToggleDescOn else strings.dictionaryJsonToggleDescOff,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = useDictionaryWithJson,
                        onCheckedChange = onDictionaryToggleChange
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(14.dp))

                // ── Section 3: prompt modes ──
                SectionTitle(strings.promptModeTitle)
                Spacer(modifier = Modifier.height(8.dp))

                val modes = listOf(
                    AiPromptTemplates.PromptMode.TRANSLATION_ONLY to Pair(strings.modeTranslationOnlyTitle, strings.modeTranslationOnlyDesc),
                    AiPromptTemplates.PromptMode.TRANSLATION_LEARNING to Pair(strings.modeTranslationLearningTitle, strings.modeTranslationLearningDesc),
                    AiPromptTemplates.PromptMode.WORD_ANALYSIS to Pair(strings.modeWordAnalysisTitle, strings.modeWordAnalysisDesc)
                )
                modes.forEach { (mode, labels) ->
                    PromptModeCard(
                        title = labels.first,
                        description = labels.second,
                        selected = selectedMode == mode,
                        onClick = { selectedMode = mode }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(14.dp))

                // ── Section 4: JSON Learning Prompt Generator ──
                SectionTitle(strings.promptGeneratorTitle)
                Text(
                    text = strings.promptGeneratorDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = strings.promptLevelLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AiPromptTemplates.LEVELS.forEach { level ->
                        FilterChip(
                            selected = promptLevel.equals(level, ignoreCase = true),
                            onClick = { promptLevel = level },
                            label = { Text(strings.levelName(level), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = strings.promptPreviewTitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                SelectionContainer {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ai_learning_prompt", prompt))
                        Toast.makeText(context, strings.promptCopiedToast, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(strings.copyPromptBtn, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(strings.close, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun PromptModeCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
