package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.logic.JsonQuizBuilder
import com.example.logic.JsonQuizQuestion
import com.example.logic.JsonQuizType
import com.example.logic.QuizMaterial
import com.example.logic.QuizSource
import com.example.logic.QuizSourceFilter
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
 * The dialog is a full-screen experience with a single unified header and a
 * sticky bottom dock:
 *  - SETUP: the shared JSON import panel (one design with the player and the
 *    Online tab), one "quiz configuration" card (source / mode / count) and
 *    the start button docked at the bottom, always reachable without scroll.
 *  - QUIZ: the question card and lettered options fill the screen; the dock
 *    is a FIXED-height answer bar that crossfades from "pick an option" to
 *    the right/wrong feedback + next button, so answering never shifts the
 *    layout. The correct option pops, a wrong pick micro-shakes.
 *  - RESULT: a big colour-coded percentage, per-type accuracy badges and the
 *    wrong list with one-tap (or add-all) Leitner export.
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
    // The quiz's own split of the JSON world: the LOCAL film's package
    // (parked while a clip is open) and the merged package of every online
    // clip that ever got a learning JSON.
    val localJson by viewModel.localFilmJson.collectAsState()
    val onlineJson by viewModel.onlineQuizJson.collectAsState()
    // The book reader's own imported material, so the quiz can be filtered to
    // the book, the film or both instead of always using the subtitle file.
    val bookMaterial by viewModel.bookQuizMaterial.collectAsState()

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.loadJsonSubtitleFromUri(it) } }

    // Everything below is rememberSaveable: an accidental trip to Recents
    // (or the system reclaiming the activity, or process death) must not eat
    // a running quiz. The dealt deck itself is serialised to one JSON string
    // (JsonQuizBuilder.encodeState), so position, answers and score all come
    // back exactly as they were.
    var phase by rememberSaveable(stateSaver = PhaseSaver) { mutableStateOf(QuizPhase.SETUP) }
    var questionCount by rememberSaveable { mutableIntStateOf(10) }
    var questions by rememberSaveable(stateSaver = QuestionDeckSaver) {
        mutableStateOf<List<JsonQuizQuestion>>(emptyList())
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    // Deck index + verdict of every answered question — the single source the
    // header score, the result screen and the wrong list are derived from.
    var answered by rememberSaveable(stateSaver = AnsweredSaver) {
        mutableStateOf<List<Pair<Int, Boolean>>>(emptyList())
    }
    var inlineMessage by remember { mutableStateOf<String?>(null) }
    var sourceFilter by rememberSaveable(stateSaver = SourceFilterSaver) {
        mutableStateOf(QuizSourceFilter.BOTH)
    }
    var blurQuiz by rememberSaveable { mutableStateOf(false) }
    var leitnerAdded by rememberSaveable(stateSaver = StringSetSaver) {
        mutableStateOf<Set<String>>(emptySet())
    }
    // Derived: every answered question paired with its verdict.
    val results = remember(answered, questions) {
        answered.mapNotNull { (deckIndex, correct) -> questions.getOrNull(deckIndex)?.to(correct) }
    }

    // A restored QUIZ phase must have its deck; a decode failure (or a JSON
    // cleared mid-quiz) drops the learner back on the setup step.
    LaunchedEffect(phase, questions) {
        if (phase == QuizPhase.QUIZ && questions.isEmpty()) phase = QuizPhase.SETUP
    }

    // A freshly imported chapter is quizzed without restarting the app.
    LaunchedEffect(Unit) {
        viewModel.refreshBookQuizMaterial()
        viewModel.refreshOnlineQuizJson()
    }

    /** Every package the learner can be quizzed on, tagged with its source. */
    fun materials(): List<QuizMaterial> = buildList {
        if (sourceFilter.matches(QuizSource.MOVIE)) {
            localJson?.let { add(QuizMaterial(QuizSource.MOVIE, it)) }
        }
        if (sourceFilter.matches(QuizSource.ONLINE)) {
            onlineJson?.let { add(QuizMaterial(QuizSource.ONLINE, it)) }
        }
        if (sourceFilter.matches(QuizSource.BOOK)) {
            bookMaterial?.let { add(it) }
        }
    }

    // Availability is what the builder can REALLY deal for each source (a
    // lone word has no distractors and so offers no question), so the chips
    // and the start button never promise a quiz the file cannot fill.
    // Availability follows the mode: in blur mode only sentence → translation
    // questions exist, so the chips (and the start button) count those alone.
    val movieAvailable = remember(localJson, blurQuiz) {
        localJson?.let { JsonQuizBuilder.availableQuestions(it, blurQuiz) } ?: 0
    }
    val onlineAvailable = remember(onlineJson, blurQuiz) {
        onlineJson?.let { JsonQuizBuilder.availableQuestions(it, blurQuiz) } ?: 0
    }
    val bookAvailable = remember(bookMaterial, blurQuiz) {
        bookMaterial?.let { JsonQuizBuilder.availableQuestions(listOf(it), blurQuiz) } ?: 0
    }
    val available = when (sourceFilter) {
        QuizSourceFilter.BOOK -> bookAvailable
        QuizSourceFilter.MOVIE -> movieAvailable
        QuizSourceFilter.ONLINE -> onlineAvailable
        QuizSourceFilter.BOTH -> bookAvailable + movieAvailable + onlineAvailable
    }
    // Preset counts the material can actually fill, plus "everything".
    val counts = remember(available) {
        (listOf(10, 20, 40).filter { preset -> preset < available } +
            if (available > 0) listOf(available) else emptyList())
            .distinct()
            .sorted()
    }
    // Keep the chosen count inside what is offered.
    LaunchedEffect(counts) {
        if (counts.isNotEmpty() && questionCount !in counts) questionCount = counts.last()
    }

    fun startQuiz(newSeed: Boolean) {
        val usable = materials()
        if (usable.isEmpty()) {
            inlineMessage = when {
                sourceFilter == QuizSourceFilter.BOOK -> strings.quizNoBookMaterial
                sourceFilter == QuizSourceFilter.ONLINE -> strings.quizNoOnlineMaterial
                localJson == null && onlineJson == null && bookMaterial == null -> strings.jsonQuizNoSource
                else -> strings.jsonQuizEmpty
            }
            return
        }
        val seed = if (newSeed) Random.nextLong() else 20260912L
        val built = JsonQuizBuilder.build(
            materials = usable,
            maxQuestions = questionCount,
            seed = seed,
            blurPeek = blurQuiz,
        )
        if (built.isEmpty()) {
            inlineMessage = strings.jsonQuizEmpty
            return
        }
        inlineMessage = null
        questions = built
        index = 0
        selected = null
        answered = emptyList()
        leitnerAdded = emptySet()
        phase = QuizPhase.QUIZ
    }

    /** Pushes one wrong question into the Leitner box and marks it as done. */
    fun addQuestionToLeitner(question: JsonQuizQuestion) {
        val word = question.word ?: question.prompt
        val definition = buildString {
            append(question.answer)
            question.note?.let { append("\n").append(it) }
            question.contextSentence?.let {
                append("\n").append(strings.jsonQuizContextLabel).append(" ").append(it)
            }
        }
        viewModel.addWordToLeitner(word, definition)
        leitnerAdded = leitnerAdded + question.id
    }

    // This is NOT a Compose Dialog on purpose. A Dialog opens its own window,
    // and on several devices a trip to Recents dismisses that window — and
    // with it the whole running quiz — no matter what was saved. Rendered as
    // a plain fullscreen overlay inside the app's own view tree (the host is
    // MainScreen), the quiz is just composable state: it survives Recents,
    // activity recreation and process death through rememberSaveable.
    BackHandler(onBack = onDismiss)
    // The overlay slides up into place instead of popping in. Saveable, so a
    // restored quiz composes immediately at full opacity.
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    AnimatedVisibility(
        visible = entered,
        enter = slideInVertically(animationSpec = tween(280)) { it / 5 } + fadeIn(tween(280)),
        exit = fadeOut(tween(120)),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().imePadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {

                    // ── Unified header: one line, no clutter ──
                    // During the quiz it collapses into progress + score; on
                    // the other steps it is the title with the BETA tag.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SoftIconButton(
                            icon = Icons.Filled.Close,
                            contentDescription = strings.close,
                            onClick = onDismiss,
                            size = 36.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        val headerQuestion = questions.getOrNull(index)
                        if (phase == QuizPhase.QUIZ && headerQuestion != null) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = strings.jsonQuizProgress(index + 1, questions.size),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    val hasAnswered = selected != null
                                    val correctCount = results.count { it.second }
                                    StatusPill(
                                        text = strings.jsonQuizScoreLive(
                                            correctCount,
                                            index + if (hasAnswered) 1 else 0
                                        ),
                                        tone = if (correctCount > 0) PillTone.Positive else PillTone.Neutral
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        if (questions.isEmpty()) 0f
                                        else (index + 1).toFloat() / questions.size.toFloat()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        } else {
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
                        }
                    }

                    inlineMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 2.dp)
                        )
                    }

                    // ── Content: slides between the three steps ──
                    AnimatedContent(
                        targetState = phase,
                        transitionSpec = {
                            (slideInHorizontally(animationSpec = tween(240)) { it / 6 } +
                                fadeIn(tween(240))) togetherWith
                                (slideOutHorizontally(animationSpec = tween(180)) { -it / 6 } +
                                    fadeOut(tween(180)))
                        },
                        modifier = Modifier.weight(1f),
                        label = "quizPhase"
                    ) { currentPhase ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            when (currentPhase) {
                                QuizPhase.SETUP -> SetupStep(
                                    strings = strings,
                                    pkg = jsonPackage,
                                    fileName = jsonFileName,
                                    questionCount = questionCount,
                                    counts = counts,
                                    onQuestionCountChange = { questionCount = it },
                                    onPickFile = {
                                        fileLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                                    },
                                    onImportPaste = { text -> viewModel.importJsonSubtitleText(text) },
                                    sourceFilter = sourceFilter,
                                    onSourceFilterChange = { sourceFilter = it },
                                    blurQuiz = blurQuiz,
                                    onBlurQuizChange = { blurQuiz = it },
                                    bookAvailable = bookAvailable,
                                    movieAvailable = movieAvailable,
                                    onlineAvailable = onlineAvailable,
                                )

                                QuizPhase.QUIZ -> {
                                    val question = questions.getOrNull(index)
                                    if (question != null) {
                                        QuizStep(
                                            strings = strings,
                                            question = question,
                                            selected = selected,
                                            onSelect = { option ->
                                                if (selected == null) {
                                                    selected = option
                                                    val correct = option == question.answer
                                                    answered = answered + (index to correct)
                                                    // Nothing is added to the Leitner box
                                                    // automatically: the answer dock offers the
                                                    // export and the learner decides — in every
                                                    // mode, blur included.
                                                }
                                            }
                                        )
                                    } else {
                                        // Defensive: the deck ran out without a result
                                        // step being set (e.g. the JSON was cleared
                                        // mid-quiz).
                                        ResultStep(
                                            strings = strings,
                                            results = results,
                                            leitnerAdded = leitnerAdded,
                                            onAddToLeitner = { question -> addQuestionToLeitner(question) },
                                            onAddAllToLeitner = { },
                                            onRetry = { phase = QuizPhase.SETUP },
                                            onNewQuestions = { phase = QuizPhase.SETUP },
                                            onBackToSetup = { phase = QuizPhase.SETUP }
                                        )
                                    }
                                }

                                QuizPhase.RESULT -> ResultStep(
                                    strings = strings,
                                    results = results,
                                    leitnerAdded = leitnerAdded,
                                    onAddToLeitner = { question -> addQuestionToLeitner(question) },
                                    onAddAllToLeitner = {
                                        // One pass over the mistakes: skip what is
                                        // already in the box and never add the same
                                        // word twice (it can be asked both ways).
                                        val seenWords = mutableSetOf<String>()
                                        results
                                            .filter { notCorrect -> !notCorrect.second }
                                            .map { it.first }
                                            .filter { q ->
                                                (q.word ?: q.prompt).isNotBlank() &&
                                                    q.answer.isNotBlank() &&
                                                    q.id !in leitnerAdded
                                            }
                                            .filter { q -> seenWords.add((q.word ?: q.prompt).trim().lowercase()) }
                                            .forEach { q -> addQuestionToLeitner(q) }
                                    },
                                    onRetry = { startQuiz(newSeed = false) },
                                    onNewQuestions = { startQuiz(newSeed = true) },
                                    onBackToSetup = { phase = QuizPhase.SETUP }
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    // ── Sticky bottom dock: always the same place, never a
                    // layout shift when the feedback appears ──
                    when (phase) {
                        QuizPhase.SETUP -> Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            DockHairline()
                            GradientButton(
                                text = strings.jsonQuizStartBtn,
                                icon = Icons.Filled.Check,
                                onClick = { startQuiz(newSeed = true) },
                                enabled = available > 0,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }

                        QuizPhase.QUIZ -> {
                            val dockQuestion = questions.getOrNull(index)
                            if (dockQuestion != null) {
                                QuizAnswerDock(
                                    strings = strings,
                                    question = dockQuestion,
                                    selected = selected,
                                    addedToLeitner = dockQuestion.id in leitnerAdded,
                                    onAddToLeitner = { addQuestionToLeitner(dockQuestion) },
                                    onNext = {
                                        if (index + 1 >= questions.size) {
                                            phase = QuizPhase.RESULT
                                        } else {
                                            index = index + 1
                                            selected = null
                                        }
                                    }
                                )
                            }
                        }

                        QuizPhase.RESULT -> Unit
                    }
                }
            }
        }
    }

/** The 1dp hairline that separates the sticky dock from the content. */
@Composable
private fun DockHairline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    )
}

/**
 * Step 1: the data (shared JSON import panel) and, below it, ONE configuration
 * card — source, mode and count. The start button itself is docked at the
 * bottom of the screen, so it is reachable without scrolling.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupStep(
    strings: AppStrings,
    pkg: JsonSubtitlePackage?,
    fileName: String,
    questionCount: Int,
    counts: List<Int>,
    onQuestionCountChange: (Int) -> Unit,
    onPickFile: () -> Unit,
    onImportPaste: (String) -> Unit,
    sourceFilter: QuizSourceFilter,
    onSourceFilterChange: (QuizSourceFilter) -> Unit,
    blurQuiz: Boolean,
    onBlurQuizChange: (Boolean) -> Unit,
    bookAvailable: Int,
    movieAvailable: Int,
    onlineAvailable: Int,
) {
    // The shared import panel the quiz's own setup embeds: current file at a
    // glance, pick a file, or paste an AI answer with live CHUNK detection.
    // (The player tabs' JSON attach uses the plain two-option chooser — the
    // quiz is where "import and build the quiz" belongs.)
    JsonImportPanel(
        strings = strings,
        pkg = pkg,
        fileName = fileName,
        onPickFile = onPickFile,
        onImportPaste = onImportPaste
    )

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        // ── Source: book, film, or both ──
        Text(
            text = strings.quizSourceTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Four sources now — book, film, online, all — so the chips wrap
        // instead of squeezing on narrow screens.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                QuizSourceFilter.BOOK to (strings.quizSourceBook to bookAvailable),
                QuizSourceFilter.MOVIE to (strings.quizSourceMovie to movieAvailable),
                QuizSourceFilter.ONLINE to (strings.quizSourceOnline to onlineAvailable),
                QuizSourceFilter.BOTH to (strings.quizSourceBoth to bookAvailable + movieAvailable + onlineAvailable),
            ).forEach { (filter, labelAndCount) ->
                val (label, count) = labelAndCount
                FilterChip(
                    selected = sourceFilter == filter,
                    onClick = { onSourceFilterChange(filter) },
                    // The count says how many questions the source can really
                    // deal; an empty one is shown but not pickable.
                    enabled = count > 0,
                    label = {
                        Text(
                            text = "$label · $count",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                )
            }
        }
        if (sourceFilter == QuizSourceFilter.BOOK && bookAvailable == 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = strings.quizNoBookMaterial,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        // ── Quiz type: normal, or the blur / fast-guess variant ──
        Text(
            text = strings.quizModeTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = !blurQuiz,
                onClick = { onBlurQuizChange(false) },
                label = { Text(strings.quizModeStandard, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            )
            FilterChip(
                selected = blurQuiz,
                onClick = { onBlurQuizChange(true) },
                label = { Text(strings.quizModeBlur, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            )
        }
        if (blurQuiz) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = strings.quizBlurHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        // ── Question count ──
        Text(
            text = strings.jsonQuizCountTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (counts.isEmpty()) {
            Text(
                text = strings.jsonQuizNoSource,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                counts.forEach { count ->
                    FilterChip(
                        selected = questionCount == count,
                        onClick = { onQuestionCountChange(count) },
                        label = {
                            Text(strings.jsonQuizCountChip(count), style = MaterialTheme.typography.labelSmall)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Step 2: the question card and the lettered options. Deliberately minimal —
 * progress and score live in the header, feedback and the next button live in
 * the dock, so this step is ONLY about reading and picking.
 */
@Composable
private fun QuizStep(
    strings: AppStrings,
    question: JsonQuizQuestion,
    selected: String?,
    onSelect: (String) -> Unit
) {
    val answered = selected != null
    // Blur / fast guess, recall-first: the sentence (or word) is ALWAYS shown
    // and the meanings stay under a veil, so the learner recalls the meaning
    // from memory first and only then taps to see the options. Re-armed on
    // every new question.
    var optionsRevealed by remember(question.id) { mutableStateOf(false) }
    val veilActive = question.blurPrompt && !optionsRevealed

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            tint = MaterialTheme.colorScheme.primary,
            contentPadding = PaddingValues(16.dp)
        ) {
            // RTL/LTR discipline: an English prompt (word or sentence) reads
            // strictly left-to-right; a Persian prompt (translation → word)
            // keeps the RTL flow of the surrounding Persian UI.
            val promptDirection =
                if (question.promptIsSourceLanguage) LayoutDirection.Ltr else LayoutDirection.Rtl
            CompositionLocalProvider(LocalLayoutDirection provides promptDirection) {
                Text(
                    text = question.prompt,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
                question.contextSentence?.takeIf {
                    question.type == JsonQuizType.WORD_TO_TRANSLATION ||
                        question.type == JsonQuizType.TRANSLATION_TO_WORD
                }?.let { context ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${strings.jsonQuizContextLabel} $context",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Options: lettered badges for fast scanning, micro-interactions
        // on the answer (correct pops, a wrong pick shakes). In blur mode the
        // whole block sits under a veil until the learner asks for it. ──
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (veilActive) Modifier.blur(12.dp) else Modifier),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                question.options.forEachIndexed { optionIndex, option ->
                val isAnswer = option == question.answer
                val isPicked = option == selected

                val container by animateColorAsState(
                    targetValue = when {
                        !answered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        isAnswer -> AccentGreen.copy(alpha = 0.20f)
                        isPicked -> AccentRed.copy(alpha = 0.20f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
                    },
                    animationSpec = tween(220),
                    label = "optContainer"
                )
                val border by animateColorAsState(
                    targetValue = when {
                        !answered -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        isAnswer -> AccentGreen
                        isPicked -> AccentRed
                        else -> Color.Transparent
                    },
                    animationSpec = tween(220),
                    label = "optBorder"
                )
                val letterBackground by animateColorAsState(
                    targetValue = when {
                        !answered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        isAnswer -> AccentGreen.copy(alpha = 0.25f)
                        isPicked -> AccentRed.copy(alpha = 0.25f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    },
                    animationSpec = tween(220),
                    label = "optLetterBg"
                )

                // The correct option pops back to full size; the wrong pick
                // shakes once. Both are keyed to the pick, not the option, so
                // they fire exactly once per question.
                val pop = remember(option) { Animatable(1f) }
                val shake = remember(option) { Animatable(0f) }
                LaunchedEffect(selected) {
                    if (selected != null) {
                        when {
                            isAnswer -> {
                                pop.snapTo(0.94f)
                                pop.animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = 400f
                                    )
                                )
                            }
                            isPicked -> {
                                shake.animateTo(
                                    targetValue = 0f,
                                    animationSpec = keyframes {
                                        durationMillis = 340
                                        0f at 0
                                        -12f at 40
                                        10f at 90
                                        -7f at 140
                                        5f at 195
                                        -3f at 250
                                        0f at 340
                                    }
                                )
                            }
                        }
                    }
                }

                Surface(
                    onClick = { onSelect(option) },
                    enabled = !answered && !veilActive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationX = shake.value
                            scaleX = pop.value
                            scaleY = pop.value
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = container,
                    border = BorderStroke(1.5.dp, border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // The letter badge: A/B/C/D in a soft circle, so the
                        // options scan fast even in the mixed RTL/LTR layout.
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(letterBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ('A' + optionIndex).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    answered && isAnswer -> AccentGreen
                                    answered && isPicked -> AccentRed
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textDirection = option.autoTextDirection()
                            ),
                            color = if (answered && !isAnswer && !isPicked) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
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
            if (veilActive) {
                // The veil: recall the meaning first, tap to deal the options.
                Surface(
                    onClick = { optionsRevealed = true },
                    modifier = Modifier.matchParentSize(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strings.jsonQuizRevealOptions,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * The quiz's sticky bottom dock. FIXED height on purpose: the hint before the
 * answer and the feedback after it crossfade inside the same frame, so the
 * question above never moves a pixel when the learner picks an option.
 */
@Composable
private fun QuizAnswerDock(
    strings: AppStrings,
    question: JsonQuizQuestion,
    selected: String?,
    addedToLeitner: Boolean,
    onAddToLeitner: () -> Unit,
    onNext: () -> Unit
) {
    val answered = selected != null
    val correct = selected == question.answer
    val dockColor by animateColorAsState(
        targetValue = when {
            !answered -> MaterialTheme.colorScheme.surface
            correct -> AccentGreen.copy(alpha = 0.14f)
            else -> AccentRed.copy(alpha = 0.14f)
        },
        animationSpec = tween(260),
        label = "dockColor"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(dockColor)
    ) {
        DockHairline()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
        ) {
            Crossfade(
                targetState = answered,
                animationSpec = tween(200),
                label = "dockContent"
            ) { isAnswered ->
                if (!isAnswered) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.jsonQuizSelectHint,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (correct) Icons.Filled.Check else Icons.Filled.Close,
                                contentDescription = null,
                                tint = if (correct) AccentGreen else AccentRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (correct) {
                                    strings.jsonQuizCorrect
                                } else {
                                    strings.jsonQuizWrong(question.answer)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        question.note?.let { note ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // Nothing ships to the Leitner box on its own: a wrong
                        // answer only OFFERS the export (both modes), and the
                        // learner decides with a tap.
                        val canAdd = (question.word ?: question.prompt).isNotBlank() &&
                            question.answer.isNotBlank()
                        when {
                            correct || addedToLeitner || !canAdd -> {
                                if (!correct && addedToLeitner) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = AccentGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = strings.jsonQuizAddedToLeitner,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AccentGreen
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                GradientButton(
                                    text = strings.jsonQuizNextBtn,
                                    onClick = onNext,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            else -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onAddToLeitner,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(
                                            text = strings.jsonQuizAddToLeitnerBtn,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    GradientButton(
                                        text = strings.jsonQuizNextBtn,
                                        onClick = onNext,
                                        modifier = Modifier.weight(1.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One answered question's type + verdict, for the result breakdown. */
private data class TypeStat(val correct: Int, val total: Int)

private fun statOf(
    results: List<Pair<JsonQuizQuestion, Boolean>>,
    vararg types: JsonQuizType
): TypeStat {
    val matching = results.filter { it.first.type in types }
    return TypeStat(matching.count { it.second }, matching.size)
}

/**
 * Step 3: the score at a glance (big colour-coded percentage), per-type
 * accuracy badges, and the wrong list — exportable to the Leitner box one by
 * one or all at once.
 */
@Composable
private fun ResultStep(
    strings: AppStrings,
    results: List<Pair<JsonQuizQuestion, Boolean>>,
    leitnerAdded: Set<String>,
    onAddToLeitner: (JsonQuizQuestion) -> Unit,
    onAddAllToLeitner: () -> Unit,
    onRetry: () -> Unit,
    onNewQuestions: () -> Unit,
    onBackToSetup: () -> Unit
) {
    val total = results.size
    val correctCount = results.count { it.second }
    val percent = if (total == 0) 0 else (correctCount * 100) / total
    val wrongQuestions = results.filter { !it.second }.map { it.first }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
            Spacer(modifier = Modifier.height(10.dp))
            // The score at a glance: a big percentage, coloured by how it
            // went — green from 80, the theme's tertiary mid-range, red low.
            val percentColor = when {
                percent >= 80 -> AccentGreen
                percent >= 50 -> MaterialTheme.colorScheme.tertiary
                else -> AccentRed
            }
            Text(
                text = "$percent٪",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = percentColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            // Granular breakdown: how the learner did per question type.
            val vocab = statOf(
                results,
                JsonQuizType.WORD_TO_TRANSLATION,
                JsonQuizType.TRANSLATION_TO_WORD
            )
            val sentences = statOf(results, JsonQuizType.SENTENCE_TO_TRANSLATION)
            val grammar = statOf(results, JsonQuizType.SENTENCE_TO_GRAMMAR)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (vocab.total > 0) {
                    StatusPill(
                        text = strings.jsonQuizStatVocab(vocab.correct, vocab.total),
                        tone = if (vocab.correct == vocab.total) PillTone.Positive else PillTone.Neutral
                    )
                }
                if (sentences.total > 0) {
                    StatusPill(
                        text = strings.jsonQuizStatSentence(sentences.correct, sentences.total),
                        tone = if (sentences.correct == sentences.total) PillTone.Positive else PillTone.Neutral
                    )
                }
                if (grammar.total > 0) {
                    StatusPill(
                        text = strings.jsonQuizStatGrammar(grammar.correct, grammar.total),
                        tone = if (grammar.correct == grammar.total) PillTone.Positive else PillTone.Neutral
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = strings.jsonQuizResult(correctCount, total, percent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
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
                contentPadding = PaddingValues(16.dp)
            ) {
                Text(
                    text = strings.jsonQuizWrongListTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                // The master action: everything not yet in the box, one tap.
                val allAdded = wrongQuestions.all { question ->
                    question.id in leitnerAdded ||
                        (question.word ?: question.prompt).isBlank() ||
                        question.answer.isBlank()
                }
                if (allAdded) {
                    Text(
                        text = strings.jsonQuizAllAdded,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentGreen,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedButton(
                        onClick = onAddAllToLeitner,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            strings.jsonQuizAddAllLeitner,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
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
                        val added = question.id in leitnerAdded
                        OutlinedButton(
                            onClick = { onAddToLeitner(question) },
                            enabled = !added &&
                                (question.word ?: question.prompt).isNotBlank() &&
                                question.answer.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            if (added) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = strings.jsonQuizAddedToLeitner,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Text(
                                    strings.jsonQuizAddToLeitnerBtn,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── saved-state savers: the whole quiz survives Recents and process death ──

private val PhaseSaver = Saver<QuizPhase, String>(
    save = { it.name },
    restore = { name -> QuizPhase.entries.firstOrNull { it.name == name } ?: QuizPhase.SETUP }
)

private val SourceFilterSaver = Saver<QuizSourceFilter, String>(
    save = { it.key },
    restore = { key -> QuizSourceFilter.from(key) }
)

private val QuestionDeckSaver = Saver<List<JsonQuizQuestion>, String>(
    save = { JsonQuizBuilder.encodeState(it) },
    restore = { JsonQuizBuilder.decodeState(it) }
)

private val AnsweredSaver = Saver<List<Pair<Int, Boolean>>, ArrayList<String>>(
    save = { list -> ArrayList(list.map { "${'$'}{it.first}|${'$'}{if (it.second) 1 else 0}" }) },
    restore = { list ->
        list.mapNotNull { entry ->
            val parts = entry.split("|")
            val deckIndex = parts.getOrNull(0)?.toIntOrNull()
            if (deckIndex != null) deckIndex to (parts.getOrNull(1) == "1") else null
        }
    }
)

private val StringSetSaver = Saver<Set<String>, ArrayList<String>>(
    save = { ArrayList(it) },
    restore = { it.toSet() }
)
