package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.logic.KnownWordsStore
import com.example.logic.TtsSpeaker
import com.example.logic.autoTextAlign
import com.example.logic.autoTextDirection
import com.example.logic.isPersianText
import com.example.logic.resolveAlign
import com.example.logic.resolveDirection
import com.example.model.JsonWord
import com.example.model.SubtitleLearningState
import com.example.ui.theme.AppStrings
import com.example.ui.theme.neoAccent
import com.example.ui.theme.isNeobrutalismDesign
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val listState = rememberLazyListState()
    val isWordMode = state.targetWord != null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var popupVisible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val popupHeight = (LocalConfiguration.current.screenHeightDp * 0.92f).dp

    LaunchedEffect(Unit) { popupVisible = true }
    val closePopup = {
        if (!closing) {
            closing = true
            popupVisible = false
            scope.launch {
                delay(220)
                onDismiss()
            }
        }
    }

    LaunchedEffect(Unit) {
        KnownWordsStore.ensureLoaded(context)
        TtsSpeaker.ensureInit(context)
    }
    DisposableEffect(Unit) {
        onDispose { TtsSpeaker.stop() }
    }
    // Clicking a vocabulary word replaces the sentence lesson with a word
    // lesson in the same sheet. Do not leave the new content scrolled to the
    // old lesson's position.
    LaunchedEffect(state.targetWord, state.sentenceEnglish) {
        listState.scrollToItem(0)
    }

    val neo = isNeobrutalismDesign()
    val popupShape = if (neo) {
        RoundedCornerShape(0.dp)
    } else {
        RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    }
    // Fixed modal surface: unlike ModalBottomSheet it never steals a vertical
    // swipe from the lesson list or jumps between expanded/dismissed states.
    Dialog(
        onDismissRequest = closePopup,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(
                visible = popupVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(260)
                ) + fadeIn(tween(180)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(220)
                ) + fadeOut(tween(160))
            ) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(popupHeight),
                shape = popupShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            Spacer(modifier = Modifier.height(10.dp))

            // Only the sheet's header mirrors for Persian (the title reads
            // from the right, the close button lands on the left). The lesson
            // CONTENT keeps its designed layout: every sentence, translation
            // and word already picks its own direction per text
            // (autoTextDirection); mirroring the cards would left-align the
            // Persian translations and scramble mixed lines.
            CompositionLocalProvider(
                LocalLayoutDirection provides if (strings.isEn) LayoutDirection.Ltr else LayoutDirection.Rtl
            ) {
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
                                    Modifier.background(neoAccent())
                                } else {
                                    Modifier.background(brandBrush())
                                }
                            )
                    )
                }
                SoftIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = strings.close,
                    onClick = closePopup,
                    size = 44.dp
                )
            }
            }  // header RTL

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                state = listState,
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
                onClick = closePopup,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 18.dp)
            )
        }
    }
        }
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
    // In the Persian interface the controls row reads from the right: the
    // speak button sits on the right end and the "I know this" toggle on
    // the left — the mirrored order of the English row.
    val speakGroup: @Composable RowScope.() -> Unit = {
        SoftIconButton(
            icon = Icons.Filled.PlayArrow,
            contentDescription = strings.speakCd,
            onClick = { TtsSpeaker.speak(context, text) },
            size = 34.dp
        )
        Spacer(modifier = Modifier.width(6.dp))
        TextButton(
            onClick = { TtsSpeaker.speak(context, text, slow = true) },
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text(
                text = strings.slowlyBtn,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!knownWord.isNullOrBlank()) {
            val known = KnownWordsStore.words.contains(KnownWordsStore.normalize(knownWord))
            val knownButton: @Composable () -> Unit = {
                TextButton(
                    onClick = { KnownWordsStore.toggle(context, knownWord) },
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text(
                        text = if (known) {
                            strings.knownMarked
                        } else {
                            strings.iKnowThis
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (known) FontWeight.Bold else FontWeight.Normal,
                        color = if (known) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isEn) {
                speakGroup()
                Spacer(modifier = Modifier.weight(1f))
                knownButton()
            } else {
                knownButton()
                Spacer(modifier = Modifier.weight(1f))
                speakGroup()
            }
        } else {
            speakGroup()
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
    // The JSON-declared languages steer the content's direction (see
    // SubtitleLearningState); null falls back to per-text detection.
    val srcRtl = state.sourceLanguageRtl
    val tgtRtl = state.targetLanguageRtl

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
                    level = level,
                    targetRtl = tgtRtl
                )
            }
        }
        jsonSub.pronunciation?.takeIf { it.isNotBlank() }?.let { pronunciation ->
            item { InfoRow(label = strings.lessonPronunciationLabel, value = pronunciation) }
        }
        jsonSub.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            item { InfoRow(label = strings.lessonNotesLabel, value = notes, valueRtl = tgtRtl) }
        }
        if (jsonSub.words.isNotEmpty()) {
            vocabularyItems(
                strings = strings,
                words = jsonSub.words,
                sentence = state.sentenceEnglish,
                translation = state.translation,
                onWordClick = onWordClick,
                sourceRtl = srcRtl,
                targetRtl = tgtRtl
            )
        }
    } else {
        item { FallbackNotice(strings.noJsonLessonFallback) }
        if (!state.translation.isNullOrBlank()) {
            item { InfoRow(label = strings.lessonTranslationLabel, value = state.translation, valueRtl = tgtRtl) }
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
    val srcRtl = state.sourceLanguageRtl
    val tgtRtl = state.targetLanguageRtl
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = MaterialTheme.colorScheme.primary,
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
        val sentenceLabel = strings.lessonSentenceLabel
        Text(
            text = sentenceLabel,
            style = MaterialTheme.typography.labelMedium.copy(
                textAlign = sentenceLabel.autoTextAlign(),
                textDirection = sentenceLabel.autoTextDirection()
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = state.sentenceEnglish,
            style = MaterialTheme.typography.bodyLarge.copy(
                textAlign = resolveAlign(state.sentenceEnglish, srcRtl),
                textDirection = resolveDirection(state.sentenceEnglish, srcRtl)
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
                    textDirection = resolveDirection(state.translation, tgtRtl)
                ),
                color = MaterialTheme.colorScheme.secondary,
                textAlign = resolveAlign(state.translation, tgtRtl),
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
    level: String,
    /** The JSON's declared target-language direction; null = per-text detection. */
    targetRtl: Boolean? = null
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
        if (!grammar.isNullOrBlank()) {
            val grammarLine = "${strings.lessonGrammarLabel}: $grammar"
            Text(
                text = grammarLine,
                style = MaterialTheme.typography.titleSmall.copy(
                    textAlign = grammarLine.autoTextAlign(),
                    textDirection = grammarLine.autoTextDirection()
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (!grammarTranslation.isNullOrBlank()) {
                Text(
                    text = grammarTranslation,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDirection = resolveDirection(grammarTranslation, targetRtl)
                    ),
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = resolveAlign(grammarTranslation, targetRtl),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        if (!explanation.isNullOrBlank()) {
            LabeledBlock(label = strings.lessonExplanationLabel, value = explanation, valueRtl = targetRtl)
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
private fun LabeledBlock(label: String, value: String, valueRtl: Boolean? = null) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(
            textAlign = label.autoTextAlign(),
            textDirection = label.autoTextDirection()
        ),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = value,
        // User/AI content: the JSON's declared language wins; without a
        // declaration the paragraph's own script decides.
        style = MaterialTheme.typography.bodyMedium.copy(
            textAlign = resolveAlign(value, valueRtl),
            textDirection = resolveDirection(value, valueRtl)
        ),
        color = MaterialTheme.colorScheme.onSurface
    )
}

private fun LazyListScope.vocabularyItems(
    strings: AppStrings,
    words: List<JsonWord>,
    sentence: String,
    translation: String?,
    onWordClick: (String, String, String?) -> Unit,
    sourceRtl: Boolean? = null,
    targetRtl: Boolean? = null
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
            onClick = { onWordClick(word.word, sentence, translation) },
            sourceRtl = sourceRtl,
            targetRtl = targetRtl
        )
    }
}

@Composable
private fun VocabularyWordCard(
    word: JsonWord,
    strings: AppStrings,
    onClick: () -> Unit,
    sourceRtl: Boolean? = null,
    targetRtl: Boolean? = null
) {
    val context = LocalContext.current
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = MaterialTheme.colorScheme.secondary,
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onClick
    ) {
        // An RTL word reads from the right: its speak button sits on the
        // right and the part-of-speech/translation column on the left —
        // the mirrored order of the LTR arrangement.
        val wordRtl = sourceRtl ?: word.word.isPersianText()
        val playButton: @Composable () -> Unit = {
            SoftIconButton(
                icon = Icons.Filled.PlayArrow,
                contentDescription = strings.speakCd,
                onClick = { TtsSpeaker.speak(context, word.word) },
                size = 32.dp
            )
        }
        val wordColumn: @Composable RowScope.() -> Unit = {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = word.word,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textAlign = resolveAlign(word.word, sourceRtl),
                        textDirection = resolveDirection(word.word, sourceRtl)
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
        }
        val pillsColumn: @Composable () -> Unit = {
            Column(horizontalAlignment = if (wordRtl) Alignment.Start else Alignment.End) {
                word.partOfSpeech?.let { pos ->
                    StatusPill(text = strings.partOfSpeechName(pos), tone = PillTone.Accent)
                }
                word.translation?.takeIf { it.isNotBlank() }?.let { tr ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tr,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDirection = resolveDirection(tr, targetRtl)
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = resolveAlign(tr, targetRtl)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (wordRtl) {
                pillsColumn()
                Spacer(modifier = Modifier.width(8.dp))
                wordColumn()
                Spacer(modifier = Modifier.width(8.dp))
                playButton()
            } else {
                playButton()
                Spacer(modifier = Modifier.width(8.dp))
                wordColumn()
                Spacer(modifier = Modifier.width(8.dp))
                pillsColumn()
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
    // Same declared-language steering as the sentence view.
    val srcRtl = state.sourceLanguageRtl
    val tgtRtl = state.targetLanguageRtl

    item { WordHeaderCard(state, strings, jsonWord, level) }

    if (jsonWord == null) {
        item { FallbackNotice(strings.noJsonWordData) }
    } else {
        jsonWord.meaningInContext?.takeIf { it.isNotBlank() }?.let { meaning ->
            item { InfoCard(strings.meaningInContextLabel, meaning, valueRtl = tgtRtl) }
        }
        jsonWord.extraExplanation?.takeIf { it.isNotBlank() }?.let { explanation ->
            item { InfoCard(strings.extraExplanationLabel, explanation, valueRtl = tgtRtl) }
        }
        if (jsonWord.examples.isNotEmpty()) {
            item { ExamplesCard(strings, jsonWord.examples, sourceRtl = srcRtl) }
        }
    }

    item { SentenceContextCard(state, strings, onWordClick) }
}

@Composable
private fun ExamplesCard(strings: AppStrings, examples: List<String>, sourceRtl: Boolean? = null) {
    val context = LocalContext.current
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
        val examplesTitle = strings.examplesLabel
        Text(
            text = examplesTitle,
            style = MaterialTheme.typography.labelMedium.copy(
                textAlign = examplesTitle.autoTextAlign(),
                textDirection = examplesTitle.autoTextDirection()
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        examples.forEachIndexed { index, example ->
            // An RTL example reads from the right: its number sits on the
            // right and its speak button on the left (the mirrored order of
            // the LTR row below).
            val exampleRtl = sourceRtl ?: example.isPersianText()
            val number: @Composable () -> Unit = {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            val exampleText: @Composable RowScope.() -> Unit = {
                Text(
                    text = example,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textAlign = resolveAlign(example, sourceRtl),
                        textDirection = resolveDirection(example, sourceRtl)
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            val speakButton: @Composable () -> Unit = {
                SoftIconButton(
                    icon = Icons.Filled.PlayArrow,
                    contentDescription = strings.speakCd,
                    onClick = { TtsSpeaker.speak(context, example) },
                    size = 30.dp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (exampleRtl) {
                    speakButton()
                    Spacer(modifier = Modifier.width(6.dp))
                    exampleText()
                    Spacer(modifier = Modifier.width(6.dp))
                    number()
                } else {
                    number()
                    Spacer(modifier = Modifier.width(6.dp))
                    exampleText()
                    Spacer(modifier = Modifier.width(6.dp))
                    speakButton()
                }
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
    val srcRtl = state.sourceLanguageRtl
    val tgtRtl = state.targetLanguageRtl
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = MaterialTheme.colorScheme.primary,
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
        // An RTL word reads from the right: the word column sits on the
        // right and the pills (part of speech, level) on the left — the
        // mirrored order of the LTR arrangement.
        val wordRtl = srcRtl ?: (state.targetWord ?: "").isPersianText()
        val wordColumn: @Composable RowScope.() -> Unit = {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.targetWord ?: "",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        textAlign = resolveAlign(state.targetWord ?: "", srcRtl),
                        textDirection = resolveDirection(state.targetWord ?: "", srcRtl)
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
        }
        val pillsColumn: @Composable () -> Unit = {
            Column(horizontalAlignment = if (wordRtl) Alignment.Start else Alignment.End) {
                jsonWord?.partOfSpeech?.let { pos ->
                    StatusPill(text = strings.partOfSpeechName(pos), tone = PillTone.Accent)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                StatusPill(text = "${strings.lessonLevelLabel}: $level", tone = PillTone.Neutral)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (wordRtl) {
                pillsColumn()
                Spacer(modifier = Modifier.width(8.dp))
                wordColumn()
            } else {
                wordColumn()
                Spacer(modifier = Modifier.width(8.dp))
                pillsColumn()
            }
        }
        jsonWord?.translation?.takeIf { it.isNotBlank() }?.let { tr ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tr,
                style = MaterialTheme.typography.titleMedium.copy(
                    textDirection = resolveDirection(tr, tgtRtl)
                ),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = resolveAlign(tr, tgtRtl),
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
    val tgtRtl = state.targetLanguageRtl
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
        val sentenceLabel = strings.lessonSentenceLabel
        Text(
            text = sentenceLabel,
            style = MaterialTheme.typography.labelMedium.copy(
                textAlign = sentenceLabel.autoTextAlign(),
                textDirection = sentenceLabel.autoTextDirection()
            ),
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
                    textDirection = resolveDirection(state.translation, tgtRtl)
                ),
                color = MaterialTheme.colorScheme.secondary,
                textAlign = resolveAlign(state.translation, tgtRtl),
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
private fun InfoRow(label: String, value: String, valueRtl: Boolean? = null) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
        LabeledBlock(label = label, value = value, valueRtl = valueRtl)
    }
}

@Composable
private fun InfoCard(label: String, value: String, valueRtl: Boolean? = null) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = MaterialTheme.colorScheme.primary,
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                textAlign = label.autoTextAlign(),
                textDirection = label.autoTextDirection()
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            // User/AI content: the JSON's declared language wins; without a
            // declaration the paragraph's own script decides.
            style = MaterialTheme.typography.bodyMedium.copy(
                textAlign = resolveAlign(value, valueRtl),
                textDirection = resolveDirection(value, valueRtl)
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
            style = MaterialTheme.typography.bodySmall.copy(
                textAlign = text.autoTextAlign(),
                textDirection = text.autoTextDirection()
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
}