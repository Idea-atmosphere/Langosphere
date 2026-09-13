package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.logic.AiPromptTemplates
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.PillTone
import com.example.ui.components.SectionHeader
import com.example.ui.components.SoftIconButton
import com.example.ui.components.StatusPill
import com.example.ui.components.brandBrush
import com.example.ui.theme.AppStrings
import com.example.ui.theme.LanguagePairState

/**
 * Settings > "Tutorial & AI Learning".
 *
 * This screen is how a learner actually gets a JSON learning package, so it
 * has to answer three questions without any outside help: which level, which
 * kind of package, and what exactly do I paste where. It manages:
 *  - the learning level (A1..C2) used for every explanation in the app,
 *  - the "use dictionary even when JSON data exists" toggle,
 *  - the prompt generator with six modes and a chunk size for long films,
 *  - a step by step usage guide.
 *
 * Every generated prompt targets the app's JSON parser format, so the answer
 * can go straight into the player's "JSON subtitle" slot.
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

    // The level used for the generated prompt starts at the learner's level but
    // can be changed on its own, so a B1 learner can still build an A2 package
    // for a friend without changing their own setting.
    var promptLevel by remember { mutableStateOf(learningLevel.uppercase()) }
    var selectedMode by remember { mutableStateOf(AiPromptTemplates.PromptMode.TRANSLATION_LEARNING) }
    var chunkSize by remember { mutableStateOf(50) }
    var showGuide by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }

    // The prompt teaches whichever pair the learner typed in the two fields
    // just below (free text, any language to any language, used exactly as
    // written). Reading the observable state here means editing the pair
    // rebuilds the prompt live.
    val sourceLanguage = LanguagePairState.source
    val targetLanguage = LanguagePairState.target
    val isPackageMode = AiPromptTemplates.producesSubtitlePackage(selectedMode)
    val prompt = remember(promptLevel, selectedMode, chunkSize, targetLanguage, sourceLanguage) {
        AiPromptTemplates.buildPrompt(
            level = promptLevel,
            mode = selectedMode,
            targetLanguage = targetLanguage,
            chunkSize = chunkSize,
            sourceLanguage = sourceLanguage
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Pinned header so the close button never scrolls away.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.tutorialTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(brandBrush())
                        )
                    }
                    SoftIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = strings.close,
                        onClick = onDismiss,
                        size = 36.dp
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    // -- Learning level --
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tint = MaterialTheme.colorScheme.primary,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Text(
                            text = strings.tutorialLearningLevelTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = strings.tutorialLearningLevelDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
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
                                    label = {
                                        Text(
                                            text = strings.levelName(level),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // -- Dictionary toggle --
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                                    text = if (useDictionaryWithJson) {
                                        strings.dictionaryJsonToggleDescOn
                                    } else {
                                        strings.dictionaryJsonToggleDescOff
                                    },
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
                    }

                    // -- Usage guide, collapsed by default --
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tint = MaterialTheme.colorScheme.tertiary,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.howToUseTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { showGuide = !showGuide }) {
                                Text(
                                    text = if (showGuide) {
                                        strings.hideBtn
                                    } else {
                                        strings.showBtn
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        AnimatedVisibility(visible = showGuide) {
                            Column {
                                strings.usageSteps.forEachIndexed { index, step ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                        Text(
                                            text = "${index + 1}.",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = step,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // -- Prompt modes --
                    SectionHeader(
                        title = strings.promptModeTitle,
                        subtitle = strings.promptModesSubtitle
                    )
                    AiPromptTemplates.PromptMode.values().forEach { mode ->
                        PromptModeCard(
                            title = strings.promptModeName(mode),
                            description = strings.promptModeDesc(mode),
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode }
                        )
                    }

                    // -- The language pair: source → target --
                    // Typed exactly as the learner wants them; the preview and
                    // the pills below show what the pair changes.
                    LanguagePairFields(
                        strings = strings,
                        showPreview = true
                    )

                    // -- Generator --
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tint = MaterialTheme.colorScheme.primary,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Text(
                            text = strings.promptGeneratorTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = strings.promptGeneratorDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusPill(
                                text = strings.promptSourcePill(sourceLanguage),
                                tone = PillTone.Neutral
                            )
                            StatusPill(
                                text = strings.promptTargetPill(targetLanguage),
                                tone = PillTone.Accent
                            )
                            StatusPill(
                                text = if (isPackageMode) {
                                    strings.importableJsonPill
                                } else {
                                    strings.forChatUsePill
                                },
                                tone = if (isPackageMode) PillTone.Positive else PillTone.Neutral
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
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
                                    label = {
                                        Text(
                                            text = strings.levelName(level),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }

                        // Chunk size only matters for the modes that eat a whole file.
                        if (isPackageMode) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = strings.cuesPerRequestLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = strings.chunkSizeHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AiPromptTemplates.CHUNK_SIZES.forEach { size ->
                                    FilterChip(
                                        selected = chunkSize == size,
                                        onClick = { chunkSize = size },
                                        label = {
                                            Text(
                                                text = strings.chunkSizeLabel(size),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        GradientButton(
                            text = strings.copyPromptBtn,
                            onClick = {
                                val clipboard = context
                                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("langosphere_prompt", prompt)
                                )
                                Toast.makeText(context, strings.promptCopiedToast, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.promptPreviewTitle,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { showPreview = !showPreview }) {
                                Text(
                                    text = if (showPreview) {
                                        strings.hideBtn
                                    } else {
                                        strings.showBtn
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        AnimatedVisibility(visible = showPreview) {
                            SelectionContainer {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(
                                        text = prompt,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp)
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(strings.close, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PromptModeCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
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
