package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.JsonSubtitle
import com.example.model.JsonWord
import com.example.model.SubtitleLearningState
import com.example.ui.theme.AppStrings

/**
 * Learning bottom sheet shown for subtitle interactions:
 *
 *  - Sentence click  → the full lesson (translation, grammar, vocabulary,
 *    sentence structure, notes) for that English subtitle line.
 *  - Word click      → word analysis (translation, meaning in this sentence,
 *    word role, additional level-appropriate explanation, examples).
 *
 * When a JSON learning file exists, its data is ALWAYS used first (passed in
 * via [state]). When the JSON has no entry for the opened sentence/word, a
 * graceful fallback is shown (translation + dictionary-derived vocabulary,
 * or a friendly hint for words) — never a crash or a blank screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleLearningSheet(
    state: SubtitleLearningState,
    strings: AppStrings,
    learningLevel: String,
    onWordClick: (word: String, sentence: String, translation: String?) -> Unit,
    onDismiss: () -> Unit
) {
    // The sheet opens fully expanded (skipPartiallyExpanded) so the drag
    // immediately scrolls the lesson instead of first fighting the sheet's
    // own drag-to-expand gesture, and the content is a LazyColumn — the same
    // proven pattern as the dictionary sheet — so long JSON lessons with many
    // vocabulary cards scroll smoothly: only the visible cards are composed
    // while the video position updates keep recomposing the screen.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp)
        ) {
            item {
                // Drag handle
                Box(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.width(40.dp).height(4.dp),
                        shape = RoundedCornerShape(2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    ) {}
                }
            }

            item {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (state.targetWord != null) strings.wordLessonSheetTitle else strings.lessonSheetTitle,
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
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            if (state.targetWord != null) {
                wordLearningItems(
                    state = state,
                    strings = strings,
                    learningLevel = learningLevel,
                    onWordClick = onWordClick
                )
            } else {
                sentenceLearningItems(
                    state = state,
                    strings = strings,
                    learningLevel = learningLevel,
                    onWordClick = onWordClick
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(strings.closeSheetBtn, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Sentence lesson view ──
// Each card is its own LazyColumn item: only the visible part is composed,
// so long JSON lessons (many vocabulary cards) scroll smoothly instead of
// recomposing the whole sheet content on every frame.

private fun LazyListScope.sentenceLearningItems(
    state: SubtitleLearningState,
    strings: AppStrings,
    learningLevel: String,
    onWordClick: (String, String, String?) -> Unit
) {
    val jsonSub = state.jsonSubtitle
    val level = jsonSub?.level ?: learningLevel

    // The sentence itself
    item { SentenceCard(state, strings, level) }

    if (jsonSub != null) {
        // JSON learning data takes priority.
        jsonSub.lesson?.let { lesson ->
            item {
                LessonCard(
                    strings = strings,
                    explanation = lesson.explanation,
                    grammar = lesson.grammar,
                    grammarTranslation = lesson.grammarTranslation,
                    structure = lesson.structure,
                    level = level
                )
            }
        }
        jsonSub.pronunciation?.let { pronunciation ->
            item { InfoRow(label = strings.lessonPronunciationLabel, value = pronunciation) }
        }
        jsonSub.notes?.let { notes ->
            item { InfoRow(label = strings.lessonNotesLabel, value = notes) }
        }
        if (jsonSub.words.isNotEmpty()) {
            vocabularyItems(
                strings = strings,
                words = jsonSub.words,
                sentence = state.sentenceEnglish,
                translation = state.translation,
                onWordClick = onWordClick
            )
        }
    } else {
        // No JSON lesson for this sentence: graceful fallback using the
        // aligned translation + dictionary-derived vocabulary.
        item { FallbackNotice(strings.noJsonLessonFallback) }
        if (!state.translation.isNullOrBlank()) {
            item { InfoRow(label = strings.lessonTranslationLabel, value = state.translation) }
        }
        if (state.fallbackVocab.isNotEmpty()) {
            item {
                Text(
                    text = strings.lessonVocabLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            items(state.fallbackVocab.entries.toList()) { (word, def) ->
                FallbackVocabRow(word, def, strings, onWordClick, state)
            }
        }
    }
}

@Composable
private fun SentenceCard(state: SubtitleLearningState, strings: AppStrings, level: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = strings.lessonSentenceLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.sentenceEnglish,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!state.translation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.translation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            val jsonSub = state.jsonSubtitle
            if (jsonSub != null && (jsonSub.level != null || jsonSub.difficulty != null || jsonSub.id != null)) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LevelChip(strings, strings.lessonLevelLabel, jsonSub.level ?: level)
                    jsonSub.difficulty?.let { LevelChip(strings, strings.lessonDifficultyLabel, it) }
                    jsonSub.id?.let { LevelChip(strings, "ID", it) }
                }
            }
        }
    }
}

@Composable
private fun LessonCard(
    strings: AppStrings,
    explanation: String?,
    grammar: String?,
    grammarTranslation: String?,
    structure: String?,
    level: String
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (!grammar.isNullOrBlank()) {
                Text(
                    text = "${strings.lessonGrammarLabel}: $grammar",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (!grammarTranslation.isNullOrBlank()) {
                    Text(
                        text = grammarTranslation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            if (!explanation.isNullOrBlank()) {
                Text(
                    text = strings.lessonExplanationLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            if (!structure.isNullOrBlank()) {
                Text(
                    text = strings.lessonStructureLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = structure,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${strings.lessonSentenceLevelNote} — ${strings.levelName(level)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// Each vocabulary word is its own LazyColumn item so lessons with big word
// lists stay smooth while scrolling.

private fun LazyListScope.vocabularyItems(
    strings: AppStrings,
    words: List<JsonWord>,
    sentence: String,
    translation: String?,
    onWordClick: (String, String, String?) -> Unit
) {
    item {
        Text(
            text = strings.lessonVocabLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            text = strings.tapWordHint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
    }
    items(words) { word ->
        VocabularyWordCard(word = word, strings = strings, onClick = { onWordClick(word.word, sentence, translation) })
    }
}

@Composable
private fun VocabularyWordCard(word: JsonWord, strings: AppStrings, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = word.word,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                word.partOfSpeech?.let { pos ->
                    Text(
                        text = strings.partOfSpeechName(pos),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            word.translation?.let { tr ->
                Text(
                    text = tr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun FallbackVocabRow(
    word: String,
    def: String,
    strings: AppStrings,
    onWordClick: (String, String, String?) -> Unit,
    state: SubtitleLearningState
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { onWordClick(word, state.sentenceEnglish, state.translation) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)),
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = word,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = def,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── Word analysis view ──

private fun LazyListScope.wordLearningItems(
    state: SubtitleLearningState,
    strings: AppStrings,
    learningLevel: String,
    onWordClick: (String, String, String?) -> Unit
) {
    val jsonWord = state.jsonWord
    val level = state.jsonSubtitle?.level ?: learningLevel

    item { WordHeaderCard(state, strings, jsonWord, level) }

    if (jsonWord == null) {
        item { FallbackNotice(strings.noJsonWordData) }
    } else {
        jsonWord.meaningInContext?.let { meaning ->
            item { InfoCard(strings, strings.meaningInContextLabel, meaning) }
        }
        jsonWord.extraExplanation?.let { explanation ->
            item { InfoCard(strings, strings.extraExplanationLabel, explanation) }
        }
        if (jsonWord.examples.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = strings.examplesLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        jsonWord.examples.forEachIndexed { index, example ->
                            Text(
                                text = "${index + 1}. $example",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    // Sentence context — lets the user jump to other words in the sentence.
    item { SentenceContextCard(state, strings, onWordClick) }
}

@Composable
private fun WordHeaderCard(
    state: SubtitleLearningState,
    strings: AppStrings,
    jsonWord: JsonWord?,
    level: String
) {
    // Word header
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.targetWord ?: "",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    jsonWord?.partOfSpeech?.let { pos ->
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = strings.partOfSpeechName(pos),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    LevelChip(strings, strings.lessonLevelLabel, level)
                }
            }
            jsonWord?.translation?.let { tr ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = tr,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            jsonWord?.pronunciation?.let { ipa ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = ipa,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SentenceContextCard(
    state: SubtitleLearningState,
    strings: AppStrings,
    onWordClick: (String, String, String?) -> Unit
) {
    // Sentence context — lets the user jump to other words in the sentence.
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = strings.lessonSentenceLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            ClickableContextSubText(
                text = state.sentenceEnglish,
                activeWord = state.targetWord ?: "",
                queryInput = state.targetWord ?: "",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified),
                onWordClick = { word -> onWordClick(word, state.sentenceEnglish, state.translation) },
                modifier = Modifier.fillMaxWidth()
            )
            if (!state.translation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.translation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ── Shared small pieces ──

@Composable
private fun LevelChip(strings: AppStrings, label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun InfoCard(strings: AppStrings, label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun FallbackNotice(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp).fillMaxWidth()
        )
    }
}
