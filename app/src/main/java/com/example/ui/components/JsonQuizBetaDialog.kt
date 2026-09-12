package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.logic.JsonQuizBuilder
import com.example.logic.JsonQuizQuestion
import com.example.logic.JsonQuizType
import com.example.logic.SubtitleJsonParser
import com.example.logic.autoTextDirection
import com.example.model.JsonSubtitlePackage
import com.example.ui.screens.AppViewModel
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentRed
import com.example.ui.theme.AppStrings
import kotlin.random.Random

/** Which step of the beta quiz the dialog is showing. */
private enum class QuizPhase { SETUP, QUIZ, RESULT }

/**
 * BETA section of the Leitner tab: "Quiz from JSON".
 *
 * A card in the Leitner tab opens this dialog. It takes the app's AI learning
 * JSON as its input — the file that is already imported, a JSON file picked
 * from storage, or a raw AI answer pasted from any chat — and turns the words,
 * sentences and grammar points inside it into a multiple-choice test
 * (see [JsonQuizBuilder]).
 *
 * Pasting an AI answer works even when the model wrapped it the way the app's
 * own prompt templates ask it to: a leading `CHUNK 1-50` line, markdown fences
 * or a sentence of commentary are detected and stripped, and the JSON is
 * imported into the app automatically, so the quiz always runs on the same data
 * the player uses.
 *
 * It is marked BETA on purpose: the question generator is local and offline,
 * and how much material a package holds depends entirely on which prompt mode
 * produced it (a translation-only file has no words to ask about).
 */
@Composable
fun JsonQuizBetaCard(
    strings: AppStrings,
    pkg: JsonSubtitlePackage?,
    fileName: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        tint = MaterialTheme.colorScheme.tertiary,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = strings.jsonQuizCardTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            BetaBadge(strings)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = strings.jsonQuizCardDesc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (pkg != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = strings.jsonQuizLoadedInfo(fileName, pkg.subtitles.size, pkg.chunks.size),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        GradientButton(
            text = strings.jsonQuizOpenBtn,
            icon = Icons.Filled.Style,
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Small "BETA" tag used by experimental sections. */
@Composable
private fun BetaBadge(strings: AppStrings) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.tertiary)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = strings.jsonQuizBetaBadge,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiary
        )
    }
}

/** The beta quiz dialog itself. See [JsonQuizBetaCard] for the entry point. */
@Composable
fun JsonQuizBetaDialog(
    viewModel: AppViewModel,
    strings: AppStrings,
    onDismiss: () -> Unit
) {
    val jsonPackage by viewModel.jsonSubtitles.collectAsState()
    val jsonFileName by viewModel.jsonSubFileName.collectAsState()

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.loadJsonSubtitleFromUri(it) } }

    var phase by remember { mutableStateOf(QuizPhase.SETUP) }
    var questionCount by remember { mutableIntStateOf(10) }
    var questions by remember { mutableStateOf<List<JsonQuizQuestion>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    var correctCount by remember { mutableIntStateOf(0) }
    var wrongQuestions by remember { mutableStateOf<List<JsonQuizQuestion>>(emptyList()) }
    var inlineMessage by remember { mutableStateOf<String?>(null) }
    var showPasteField by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }

    // Live CHUNK feedback for the paste field, so the user can see that the
    // marker line is recognised before importing.
    val pasteChunk = remember(pasteText) {
        if (pasteText.isBlank()) null else SubtitleJsonParser.detectChunkMarker(pasteText)
    }
    val pasteDetected = remember(pasteText) { SubtitleJsonParser.looksLikeSubtitleJson(pasteText) }

    fun startQuiz(newSeed: Boolean) {
        val pkg = jsonPackage
        if (pkg == null) {
            inlineMessage = strings.jsonQuizNoSource
            return
        }
        val seed = if (newSeed) Random.nextLong() else 20260912L
        val built = JsonQuizBuilder.build(pkg, maxQuestions = questionCount, seed = seed)
        if (built.isEmpty()) {
            inlineMessage = strings.jsonQuizEmpty
            return
        }
        inlineMessage = null
        questions = built
        index = 0
        selected = null
        correctCount = 0
        wrongQuestions = emptyList()
        phase = QuizPhase.QUIZ
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

                // ── Header ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = strings.jsonQuizDialogTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            BetaBadge(strings)
                        }
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
                    inlineMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    when (phase) {
                        QuizPhase.SETUP -> SetupStep(
                            strings = strings,
                            pkg = jsonPackage,
                            fileName = jsonFileName,
                            questionCount = questionCount,
                            onQuestionCountChange = { questionCount = it },
                            onPickFile = {
                                fileLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                            },
                            showPasteField = showPasteField,
                            onTogglePasteField = { showPasteField = !showPasteField },
                            pasteText = pasteText,
                            onPasteTextChange = { pasteText = it },
                            pasteChunkLabel = pasteChunk?.shortLabel,
                            pasteDetected = pasteDetected,
                            onImportPaste = {
                                viewModel.importJsonSubtitleText(pasteText.trim())
                                pasteText = ""
                                showPasteField = false
                            },
                            onStart = { startQuiz(newSeed = true) }
                        )

                        QuizPhase.QUIZ -> {
                            val question = questions.getOrNull(index)
                            if (question != null) {
                                QuizStep(
                                    strings = strings,
                                    question = question,
                                    position = index + 1,
                                    total = questions.size,
                                    correctCount = correctCount,
                                    selected = selected,
                                    onSelect = { option ->
                                        if (selected == null) {
                                            selected = option
                                            if (option == question.answer) {
                                                correctCount = correctCount + 1
                                            } else {
                                                wrongQuestions = wrongQuestions + question
                                            }
                                        }
                                    },
                                    onNext = {
                                        if (index + 1 >= questions.size) {
                                            phase = QuizPhase.RESULT
                                        } else {
                                            index = index + 1
                                            selected = null
                                        }
                                    }
                                )
                            } else {
                                // Defensive: the deck ran out without a result
                                // step being set (e.g. the JSON was cleared
                                // mid-quiz).
                                ResultStep(
                                    strings = strings,
                                    correctCount = correctCount,
                                    total = questions.size,
                                    wrongQuestions = wrongQuestions,
                                    onAddToLeitner = { },
                                    onRetry = { phase = QuizPhase.SETUP },
                                    onNewQuestions = { phase = QuizPhase.SETUP },
                                    onBackToSetup = { phase = QuizPhase.SETUP }
                                )
                            }
                        }

                        QuizPhase.RESULT -> ResultStep(
                            strings = strings,
                            correctCount = correctCount,
                            total = questions.size,
                            wrongQuestions = wrongQuestions,
                            onAddToLeitner = { question ->
                                val word = question.word ?: question.prompt
                                val definition = buildString {
                                    append(question.answer)
                                    question.note?.let { append("\n").append(it) }
                                    question.contextSentence?.let {
                                        append("\n").append(strings.jsonQuizContextLabel).append(" ").append(it)
                                    }
                                }
                                viewModel.addWordToLeitner(word, definition)
                            },
                            onRetry = { startQuiz(newSeed = false) },
                            onNewQuestions = { startQuiz(newSeed = true) },
                            onBackToSetup = { phase = QuizPhase.SETUP }
                        )
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

/** Step 1: choose what to be tested on, and how many questions. */
@Composable
private fun SetupStep(
    strings: AppStrings,
    pkg: JsonSubtitlePackage?,
    fileName: String,
    questionCount: Int,
    onQuestionCountChange: (Int) -> Unit,
    onPickFile: () -> Unit,
    showPasteField: Boolean,
    onTogglePasteField: () -> Unit,
    pasteText: String,
    onPasteTextChange: (String) -> Unit,
    pasteChunkLabel: String?,
    pasteDetected: Boolean,
    onImportPaste: () -> Unit,
    onStart: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = MaterialTheme.colorScheme.primary,
        contentPadding = PaddingValues(14.dp)
    ) {
        Text(
            text = strings.jsonQuizSourceTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (pkg != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(text = strings.jsonQuizLoadedInfo(fileName, pkg.subtitles.size, pkg.chunks.size), tone = PillTone.Positive)
            }
            val available = JsonQuizBuilder.availableQuestions(pkg)
            Text(
                text = strings.jsonQuizCountTitle + ": " + available,
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

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onPickFile,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.jsonQuizPickFileBtn, style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = onTogglePasteField,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.jsonQuizPasteBtn, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (showPasteField) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = strings.jsonQuizPasteHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = pasteText,
                onValueChange = onPasteTextChange,
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
            Spacer(modifier = Modifier.height(6.dp))
            when {
                pasteChunkLabel != null -> Text(
                    text = "${strings.jsonChunkDetectedLabel} $pasteChunkLabel — ${strings.jsonChunkAutoStripHint}",
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
            Spacer(modifier = Modifier.height(8.dp))
            GradientButton(
                text = strings.jsonQuizImportPasteBtn,
                onClick = onImportPaste,
                enabled = pasteDetected,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Question count
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp)
    ) {
        Text(
            text = strings.jsonQuizCountTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        val available = pkg?.let { JsonQuizBuilder.availableQuestions(it) } ?: 0
        // Preset counts, plus "everything the file can offer". A count the file
        // cannot fill is not offered at all.
        val counts = mutableListOf<Int>()
        listOf(10, 20, 40).forEach { preset ->
            if (available == 0 || preset < available) counts.add(preset)
        }
        if (available > 0) counts.add(available)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            counts.distinct().sorted().forEach { count ->
                FilterChip(
                    selected = questionCount == count,
                    onClick = { onQuestionCountChange(count) },
                    label = {
                        Text(strings.jsonQuizCountChip(count), style = MaterialTheme.typography.labelSmall)
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        GradientButton(
            text = strings.jsonQuizStartBtn,
            icon = Icons.Filled.Check,
            onClick = onStart,
            enabled = pkg != null,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Step 2: one question, its options, and the immediate feedback. */
@Composable
private fun QuizStep(
    strings: AppStrings,
    question: JsonQuizQuestion,
    position: Int,
    total: Int,
    correctCount: Int,
    selected: String?,
    onSelect: (String) -> Unit,
    onNext: () -> Unit
) {
    val answered = selected != null

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(
                text = strings.jsonQuizProgress(position, total),
                tone = PillTone.Accent
            )
            Spacer(modifier = Modifier.width(6.dp))
            StatusPill(
                text = strings.jsonQuizScoreLive(correctCount, position - if (answered) 0 else 1),
                tone = PillTone.Neutral
            )
            Spacer(modifier = Modifier.width(6.dp))
            StatusPill(text = quizTypeLabel(strings, question.type), tone = PillTone.Neutral)
        }

        Spacer(modifier = Modifier.height(12.dp))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            tint = MaterialTheme.colorScheme.primary,
            contentPadding = PaddingValues(16.dp)
        ) {
            Text(
                text = question.prompt,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            question.contextSentence?.takeIf { question.type == JsonQuizType.WORD_TO_TRANSLATION || question.type == JsonQuizType.TRANSLATION_TO_WORD }?.let { context ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${strings.jsonQuizContextLabel} $context",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            question.options.forEach { option ->
                val isAnswer = option == question.answer
                val isPicked = option == selected
                val container = when {
                    !answered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    isAnswer -> AccentGreen.copy(alpha = 0.22f)
                    isPicked -> AccentRed.copy(alpha = 0.22f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
                }
                val border = when {
                    !answered -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    isAnswer -> AccentGreen
                    isPicked -> AccentRed
                    else -> Color.Transparent
                }
                Surface(
                    onClick = { onSelect(option) },
                    enabled = !answered,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = container,
                    border = BorderStroke(1.5.dp, border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (answered && isAnswer) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        } else if (answered && isPicked) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = AccentRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        if (answered) {
            Spacer(modifier = Modifier.height(10.dp))
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                tint = if (selected == question.answer) AccentGreen else AccentRed,
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    text = if (selected == question.answer) {
                        strings.jsonQuizCorrect
                    } else {
                        strings.jsonQuizWrong(question.answer)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                question.note?.let { note ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            GradientButton(
                text = strings.jsonQuizNextBtn,
                onClick = onNext,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/** Step 3: the score, plus one tap to push every missed word into the Leitner box. */
@Composable
private fun ResultStep(
    strings: AppStrings,
    correctCount: Int,
    total: Int,
    wrongQuestions: List<JsonQuizQuestion>,
    onAddToLeitner: (JsonQuizQuestion) -> Unit,
    onRetry: () -> Unit,
    onNewQuestions: () -> Unit,
    onBackToSetup: () -> Unit
) {
    val percent = if (total == 0) 0 else (correctCount * 100) / total

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            tint = MaterialTheme.colorScheme.primary,
            contentPadding = PaddingValues(16.dp)
        ) {
            Text(
                text = strings.jsonQuizFinishTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = strings.jsonQuizResult(correctCount, total, percent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(strings.jsonQuizRetryBtn, style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onNewQuestions,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(strings.jsonQuizNewQuestionsBtn, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onBackToSetup, modifier = Modifier.fillMaxWidth()) {
                Text(strings.jsonQuizBackToSourceBtn, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (wrongQuestions.isNotEmpty()) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    text = strings.jsonQuizWrongListTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                wrongQuestions.forEach { question ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = question.word ?: question.prompt,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2
                            )
                            Text(
                                text = question.answer,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if ((question.word ?: question.prompt).isNotBlank() && question.answer.isNotBlank()) {
                            OutlinedButton(
                                onClick = { onAddToLeitner(question) },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(strings.jsonQuizAddToLeitnerBtn, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun quizTypeLabel(strings: AppStrings, type: JsonQuizType): String = when (type) {
    JsonQuizType.WORD_TO_TRANSLATION -> strings.jsonQuizTypeWord
    JsonQuizType.TRANSLATION_TO_WORD -> strings.jsonQuizTypeReverse
    JsonQuizType.SENTENCE_TO_TRANSLATION -> strings.jsonQuizTypeSentence
    JsonQuizType.SENTENCE_TO_GRAMMAR -> strings.jsonQuizTypeGrammar
}
