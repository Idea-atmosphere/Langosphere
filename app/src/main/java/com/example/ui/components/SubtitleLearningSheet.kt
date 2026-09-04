package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.logic.KnownWordsStore
import com.example.logic.TtsSpeaker
import com.example.logic.autoTextAlign
import com.example.logic.autoTextDirection
import com.example.model.JsonWord
import com.example.model.SubtitleLearningState
import com.example.ui.theme.AppStrings
import com.example.ui.theme.NeoBrutalismAccent
import com.example.ui.theme.isNeobrutalismDesign

/**
 * Learning bottom sheet shown for subtitle interactions:
 *
 *  - Sentence click  -> the full lesson (translation, grammar, vocabulary,
 *    sentence structure, notes) for that English subtitle line.
 *  - Word click      -> word analysis (translation, meaning in this
 *    sentence, word role, extra explanation, examples).
 *
 * When a JSON learning file exists its data is always used first (passed in
 * via [state]); otherwise a graceful fallback is shown.
 *
 * Sentences and words can now be HEARD, not only read, and a word can be
 * marked as already known so it stops being offered as study material and
 * counts towards the coverage percentage.
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
    // Fully expanded on open so the first drag scrolls the lesson instead of
    // fighting the sheet's own drag-to-expand gesture.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isWordMode = state.targetWord != null
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        KnownWordsStore.ensureLoaded(context)
        TtsSpeaker.ensureInit(context)
    }
    DisposableEffect(Unit) {
        onDispose { TtsSpeaker.stop() }
    }

    val neo = isNeobrutalismDesign()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = if (neo) {
            RoundedCornerShape(0.dp)
        } else {
            RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
        },
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(if (neo) 6.dp else 5.dp)
                        .clip(if (neo) RoundedCornerShape(0.dp) else CircleShape)
                        .then(
                            if (neo) {
                                Modifier.background(NeoBrutalismAccent)
                            } else {
                                Modifier.background(brandBrush(alpha = 0.55f))
                            }
                        )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isWordMode) strings.wordLessonSheetTitle else strings.lessonSheetTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .height(if (neo) 4.dp else 3.dp)
                            .clip(if (neo) RoundedCornerShape(0.dp) else CircleShape)
                            .then(
                                if (neo) {
                                    Modifier.background(NeoBrutalismAccent)
                                } else {
                                    Modifier.background(brandBrush())
                                }
                            )
                    )
                }
                SoftIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = strings.close,
                    onClick = onDismiss,
                    size = 36.dp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().fadingEdges(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isWordMode) {
                    wordLearningItems(state, strings, learningLevel, onWordClick)
                } else {
                    sentenceLearningItems(state, strings, learningLevel, onWordClick)
                }
            }

            GradientButton(
                text = strings.closeSheetBtn,
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 18.dp)
            )
        }
    }
}

/**
 * Speak / speak-slowly controls, plus the "I already know this" switch for
 * single words. Hearing a word is the part that actually sticks.
 */
@Composable
private fun PronunciationRow(
    text: String,
    strings: AppStrings,
    knownWord: String? = null
) {
    val context = LocalContext.current
    val isEn = strings.isEn
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SoftIconButton(
            icon = Icons.Filled.PlayArrow,
            contentDescription = if (isEn) "Speak" else "خواندن",
            onClick = { TtsSpeaker.speak(context, text) },
            size = 34.dp
        )
        Spacer(modifier = Modifier.width(6.dp))
        TextButton(
            onClick = { TtsSpeaker.speak(context, text, slow = true) },
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text(
                text = if (isEn) "Slowly" else "آهسته",
                style = MaterialTheme.typography.labelMedium
            )
        }
        if (!knownWord.isNullOrBlank()) {
            Spacer(modifier = Modifier.weight(1f))
            val known = KnownWordsStore.words.contains(KnownWordsStore.normalize(knownWord))
            TextButton(
                onClick = { KnownWordsStore.toggle(context, knownWord) },
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text(
                    text = if (known) {
                        if (isEn) "Known" else "بلدم ✓"
                    } else {
                        if (isEn) "I know this" else "بلدم"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (known) FontWeight.Bold else FontWeight.Normal,
                    color = if (known) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// -- Sentence lesson view --
// Each card is its own LazyColumn item: only the visible part is composed,
// so long JSON lessons scroll smoothly.

private fun LazyListScope.sentenceLearningItems(
    state: SubtitleLearningState,
    strings: AppStrings,
    learningLevel: String,
    onWordClick: (String, String, String?) -> Unit
) {
    val jsonSub = state.jsonSubtitle
    val level = jsonSub?.level ?: learningLevel

    item { SentenceCard(state, strings, level) }

    if (jsonSub != null) {
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
        jsonSub.pronunciation?.takeIf { it.isNotBlank() }?.let { pronunciation ->
            item { InfoRow(label = strings.lessonPronunciationLabel, value = pronunciation) }
        }
        jsonSub.notes?.takeIf { it.isNotBlank() }?.let { notes ->
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
        item { FallbackNotice(strings.noJsonLessonFallback) }
        if (!state.translation.isNullOrBlank()) {
            item { InfoRow(label = strings.lessonTranslationLabel, value = state.translation) }
        }
        if (state.fallbackVocab.isNotEmpty()) {
            item {
                SectionHeader(
                    title = strings.lessonVocabLabel,
                    subtitle = strings.tapWordHint,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            itemsIndexed(state.fallbackVocab.entries.toList()) { _, entry ->
                FallbackVocabRow(entry.key, entry.value, onWordClick, state)
            }
        }
    }
}

@Composable
private fun SentenceCard(state: SubtitleLearningState, strings: AppStrings, level: String) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = MaterialTheme.colorScheme.primary,
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
        Text(
            text = strings.lessonSentenceLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = state.sentenceEnglish,
            style = MaterialTheme.typography.bodyLarge.copy(
                textAlign = state.sentenceEnglish.autoTextAlign(),
                textDirection = state.sentenceEnglish.autoTextDirection()
            ),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (!state.translation.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.translation,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDirection = state.translation.autoTextDirection()
                ),
                color = MaterialTheme.colorScheme.secondary,
                textAlign = state.translation.autoTextAlign(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (state.sentenceEnglish.isNotBlank()) {
            PronunciationRow(text = state.sentenceEnglish, strings = strings)
        }
        val jsonSub = state.jsonSubtitle
        if (jsonSub != null && (jsonSub.level != null || jsonSub.difficulty != null || jsonSub.id != null)) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(
                    text = "${strings.lessonLevelLabel}: ${jsonSub.level ?: level}",
                    tone = PillTone.Accent
                )
                jsonSub.difficulty?.let {
                    StatusPill(text = "${strings.lessonDifficultyLabel}: $it", tone = PillTone.Warning)
                }
                jsonSub.id?.let { StatusPill(text = "ID $it", tone = PillTone.Neutral) }
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
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
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
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDirection = grammarTranslation.autoTextDirection()
                    ),
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = grammarTranslation.autoTextAlign(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        if (!explanation.isNullOrBlank()) {
            LabeledBlock(label = strings.lessonExplanationLabel, value = explanation)
            Spacer(modifier = Modifier.height(10.dp))
        }
        if (!structure.isNullOrBlank()) {
            LabeledBlock(label = strings.lessonStructureLabel, value = structure)
            Spacer(modifier = Modifier.height(10.dp))
        }
        StatusPill(
            text = "${strings.lessonSentenceLevelNote} - ${strings.levelName(level)}",
            tone = PillTone.Positive
        )
    }
}

@Composable
private fun LabeledBlock(label: String, value: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = value,
        // User/AI content: auto RTL/LTR per paragraph.
        style = MaterialTheme.typography.bodyMedium.copy(
            textAlign = value.autoTextAlign(),
            textDirection = value.autoTextDirection()
        ),
        color = MaterialTheme.colorScheme.onSurface
    )
}

private fun LazyListScope.vocabularyItems(
    strings: AppStrings,
    words: List<JsonWord>,
    sentence: String,
    translation: String?,
    onWordClick: (String, String, String?) -> Unit
) {
    item {
        SectionHeader(
            title = strings.lessonVocabLabel,
            subtitle = strings.tapWordHint,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    items(words) { word ->
        VocabularyWordCard(
            word = word,
            strings = strings,
            onClick = { onWordClick(word.word, sentence, translation) }
        )
    }
}

@Composable
private fun VocabularyWordCard(word: JsonWord, strings: AppStrings, onClick: () -> Unit) {
    val context = LocalContext.current
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = MaterialTheme.colorScheme.secondary,
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SoftIconButton(
                icon = Icons.Filled.PlayArrow,
                contentDescription = if (strings.isEn) "Speak" else "خواندن",
                onClick = { TtsSpeaker.speak(context, word.word) },
                size = 32.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = word.word,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textAlign = word.word.autoTextAlign(),
                        textDirection = word.word.autoTextDirection()
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                word.pronunciation?.takeIf { it.isNotBlank() }?.let { ipa ->
                    Text(
                        text = ipa,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                word.partOfSpeech?.let { pos ->
                    StatusPill(text = strings.partOfSpeechName(pos), tone = PillTone.Accent)
                }
                word.translation?.takeIf { it.isNotBlank() }?.let { tr ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tr,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDirection = tr.autoTextDirection()
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = tr.autoTextAlign()
                    )
                }
            }
        }
    }
}

@Composable
private fun FallbackVocabRow(
    word: String,
    def: String,
    onWordClick: (String, String, String?) -> Unit,
    state: SubtitleLearningState
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = MaterialTheme.colorScheme.secondary,
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = { onWordClick(word, state.sentenceEnglish, state.translation) }
    ) {
        Text(
            text = word,
            style = MaterialTheme.typography.bodyMedium.copy(
                textAlign = word.autoTextAlign(),
                textDirection = word.autoTextDirection()
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = def,
            style = MaterialTheme.typography.bodySmall.copy(
                textDirection = def.autoTextDirection()
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = def.autoTextAlign(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// -- Word analysis view --

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
        jsonWord.meaningInContext?.takeIf { it.isNotBlank() }?.let { meaning ->
            item { InfoCard(strings.meaningInContextLabel, meaning) }
        }
        jsonWord.extraExplanation?.takeIf { it.isNotBlank() }?.let { explanation ->
            item { InfoCard(strings.extraExplanationLabel, explanation) }
        }
        if (jsonWord.examples.isNotEmpty()) {
            item { ExamplesCard(strings, jsonWord.examples) }
        }
    }

    item { SentenceContextCard(state, strings, onWordClick) }
}

@Composable
private fun ExamplesCard(strings: AppStrings, examples: List<String>) {
    val context = LocalContext.current
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
        Text(
            text = strings.examplesLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        examples.forEachIndexed { index, example ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = example,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textAlign = example.autoTextAlign(),
                        textDirection = example.autoTextDirection()
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                SoftIconButton(
                    icon = Icons.Filled.PlayArrow,
                    contentDescription = if (strings.isEn) "Speak" else "خواندن",
                    onClick = { TtsSpeaker.speak(context, example) },
                    size = 30.dp
                )
            }
        }
    }
}

@Composable
private fun WordHeaderCard(
    state: SubtitleLearningState,
    strings: AppStrings,
    jsonWord: JsonWord?,
    level: String
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = MaterialTheme.colorScheme.primary,
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.targetWord ?: "",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        textAlign = (state.targetWord ?: "").autoTextAlign(),
                        textDirection = (state.targetWord ?: "").autoTextDirection()
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                jsonWord?.pronunciation?.takeIf { it.isNotBlank() }?.let { ipa ->
                    Text(
                        text = ipa,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                jsonWord?.partOfSpeech?.let { pos ->
                    StatusPill(text = strings.partOfSpeechName(pos), tone = PillTone.Accent)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                StatusPill(text = "${strings.lessonLevelLabel}: $level", tone = PillTone.Neutral)
            }
        }
        jsonWord?.translation?.takeIf { it.isNotBlank() }?.let { tr ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tr,
                style = MaterialTheme.typography.titleMedium.copy(
                    textDirection = tr.autoTextDirection()
                ),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = tr.autoTextAlign(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        val target = state.targetWord
        if (!target.isNullOrBlank()) {
            PronunciationRow(text = target, strings = strings, knownWord = target)
        }
    }
}

@Composable
private fun SentenceContextCard(
    state: SubtitleLearningState,
    strings: AppStrings,
    onWordClick: (String, String, String?) -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
        Text(
            text = strings.lessonSentenceLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        ClickableContextSubText(
            text = state.sentenceEnglish,
            activeWord = state.targetWord ?: "",
            queryInput = state.targetWord ?: "",
            style = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
            ),
            onWordClick = { word -> onWordClick(word, state.sentenceEnglish, state.translation) },
            modifier = Modifier.fillMaxWidth()
        )
        if (!state.translation.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.translation,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDirection = state.translation.autoTextDirection()
                ),
                color = MaterialTheme.colorScheme.secondary,
                textAlign = state.translation.autoTextAlign(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (state.sentenceEnglish.isNotBlank()) {
            PronunciationRow(text = state.sentenceEnglish, strings = strings)
        }
    }
}

// -- Shared small pieces --

@Composable
private fun InfoRow(label: String, value: String) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
        LabeledBlock(label = label, value = value)
    }
}

@Composable
private fun InfoCard(label: String, value: String) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = MaterialTheme.colorScheme.primary,
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            // User/AI content: auto RTL/LTR per paragraph.
            style = MaterialTheme.typography.bodyMedium.copy(
                textAlign = value.autoTextAlign(),
                textDirection = value.autoTextDirection()
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FallbackNotice(text: String) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = MaterialTheme.colorScheme.tertiary,
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
}