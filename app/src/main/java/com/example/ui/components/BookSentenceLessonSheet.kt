package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.logic.TtsSpeaker
import com.example.logic.autoTextDirection
import com.example.logic.autoTextAlign
import com.example.model.BookSentence
import com.example.model.JsonWord
import com.example.ui.theme.isNeobrutalismDesign
import com.example.ui.theme.neoAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Reuse existing UI primitives from video subtitle lesson sheet
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPill
import com.example.ui.components.PillTone
import com.example.ui.components.SoftIconButton
import com.example.ui.components.GradientButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.brandBrush
import com.example.ui.components.fadingEdges

/**
 * Lesson sheet for book sentences — bottom sheet popup exactly like
 * SubtitleLearningSheet's model.
 *
 * Triggered by the "درس جمله" button in BookSentencePane (same as
 * JsonSubtitleRow's lesson button in video subtitles).
 *
 * Shows:
 * - original sentence + translation
 * - pronunciation
 * - lesson: explanation, grammar, grammarTranslation, structure, notes
 * - word list with TTS and add-to-Leitner
 */
@Composable
fun BookSentenceLessonSheet(
    sentence: BookSentence,
    onDismiss: () -> Unit,
    onAddWordToLeitner: (JsonWord) -> Unit,
    knownWords: Set<String> = emptySet(),
    leitnerLabel: String = "افزودن به جعبهٔ لایتنر",
    alreadyInBoxLabel: String = "در جعبهٔ لایتنر هست",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var popupVisible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val popupHeight = (LocalConfiguration.current.screenHeightDp * 0.92f).dp
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        popupVisible = true
        TtsSpeaker.ensureInit(context)
    }

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

    val neo = isNeobrutalismDesign()
    val popupShape = if (neo) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)

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
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(260)) + fadeIn(tween(180)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(220)) + fadeOut(tween(160))
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(popupHeight),
                    shape = popupShape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sentence.location.ifBlank { "#${sentence.id} • درس جمله" },
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
                                            if (neo) Modifier.background(neoAccent())
                                            else Modifier.background(brandBrush())
                                        )
                                )
                            }
                            SoftIconButton(
                                icon = Icons.Filled.Close,
                                contentDescription = "بستن",
                                onClick = closePopup,
                                size = 44.dp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f).fillMaxWidth().fadingEdges(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Sentence card: original + translation
                            item {
                                GlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    tint = MaterialTheme.colorScheme.primary,
                                    cornerRadius = 22.dp,
                                    contentPadding = PaddingValues(16.dp)
                                ) {
                                    Text(
                                        text = "جملهٔ اصلی",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = sentence.english,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            textAlign = sentence.english.autoTextAlign(),
                                            textDirection = sentence.english.autoTextDirection()
                                        ),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    sentence.translation?.takeIf { it.isNotBlank() }?.let { tr ->
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = tr,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                textDirection = tr.autoTextDirection()
                                            ),
                                            color = MaterialTheme.colorScheme.secondary,
                                            textAlign = tr.autoTextAlign(),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    sentence.pronunciation?.takeIf { it.isNotBlank() }?.let { pron ->
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = pron,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (sentence.english.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            SoftIconButton(
                                                icon = Icons.Filled.PlayArrow,
                                                contentDescription = "پخش",
                                                onClick = { TtsSpeaker.speak(context, sentence.english) },
                                                size = 34.dp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "شنیدن جمله",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    // Level / difficulty pills
                                    if (!sentence.level.isNullOrBlank() || !sentence.difficulty.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            sentence.level?.takeIf { it.isNotBlank() }?.let {
                                                StatusPill(text = "سطح: $it", tone = PillTone.Accent)
                                            }
                                            sentence.difficulty?.takeIf { it.isNotBlank() }?.let {
                                                StatusPill(text = it, tone = PillTone.Warning)
                                            }
                                        }
                                    }
                                }
                            }

                            // Lesson details
                            val lesson = sentence.lesson
                            // Lesson details - mission: grammar existence must NOT be condition for grammarTranslation
                            // Each field independent, none lost
                            if (lesson != null) {
                                item {
                                    GlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        cornerRadius = 22.dp,
                                        contentPadding = PaddingValues(16.dp)
                                    ) {
                                        lesson.grammar?.takeIf { it.isNotBlank() }?.let { grammar ->
                                            Text(
                                                text = "Grammar: $grammar",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }
                                        // grammarTranslation independent of grammar (mission requirement)
                                        lesson.grammarTranslation?.takeIf { it.isNotBlank() }?.let { gt ->
                                            LabeledBlock(label = "ترجمه گرامر", value = gt)
                                            Spacer(modifier = Modifier.height(10.dp))
                                        }
                                        lesson.explanation?.takeIf { it.isNotBlank() }?.let { exp ->
                                            LabeledBlock(label = "توضیح", value = exp)
                                            Spacer(modifier = Modifier.height(10.dp))
                                        }
                                        lesson.structure?.takeIf { it.isNotBlank() }?.let { struct ->
                                            LabeledBlock(label = "Structure", value = struct)
                                            Spacer(modifier = Modifier.height(10.dp))
                                        }
                                    }
                                }
                            }

                            sentence.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                                item {
                                    GlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        cornerRadius = 20.dp,
                                        contentPadding = PaddingValues(14.dp)
                                    ) {
                                        LabeledBlock(label = "یادداشت", value = notes)
                                    }
                                }
                            }

                            sentence.simplified?.takeIf { it.isNotBlank() }?.let { simp ->
                                item {
                                    GlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        cornerRadius = 20.dp,
                                        contentPadding = PaddingValues(14.dp)
                                    ) {
                                        LabeledBlock(label = "ساده‌شده", value = simp)
                                    }
                                }
                            }

                            // Vocabulary
                            if (sentence.words.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "واژگان",
                                        subtitle = "روی بلندگو بزنید تا بشنوید، + برای لایتنر",
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                items(sentence.words) { word ->
                                    val key = word.word.orEmpty().trim().lowercase()
                                    val isKnown = key.isNotEmpty() && key in knownWords
                                    VocabularyCard(
                                        word = word,
                                        isKnown = isKnown,
                                        onAdd = { onAddWordToLeitner(word) },
                                        leitnerLabel = leitnerLabel,
                                        alreadyInBoxLabel = alreadyInBoxLabel,
                                    )
                                }
                            }
                        }

                        GradientButton(
                            text = "بستن",
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
        style = MaterialTheme.typography.bodyMedium.copy(
            textAlign = value.autoTextAlign(),
            textDirection = value.autoTextDirection()
        ),
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun VocabularyCard(
    word: JsonWord,
    isKnown: Boolean,
    onAdd: () -> Unit,
    leitnerLabel: String,
    alreadyInBoxLabel: String,
) {
    val context = LocalContext.current
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = MaterialTheme.colorScheme.secondary,
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SoftIconButton(
                icon = Icons.Filled.PlayArrow,
                contentDescription = "پخش",
                onClick = { TtsSpeaker.speak(context, word.word) },
                size = 32.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = word.word.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    word.pronunciation?.takeIf { it.isNotBlank() }?.let { pron ->
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = pron,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    word.partOfSpeech?.takeIf { it.isNotBlank() }?.let { pos ->
                        Spacer(modifier = Modifier.width(6.dp))
                        StatusPill(text = pos, tone = PillTone.Accent)
                    }
                }
                word.translation?.takeIf { it.isNotBlank() }?.let { tr ->
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
                word.meaningInContext?.takeIf { it.isNotBlank() }?.let { meaning ->
                    Text(
                        text = meaning,
                        style = MaterialTheme.typography.bodySmall.copy(
                            textDirection = meaning.autoTextDirection()
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = meaning.autoTextAlign(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                word.extraExplanation?.takeIf { it.isNotBlank() }?.let { extra ->
                    Text(
                        text = extra,
                        style = MaterialTheme.typography.bodySmall.copy(
                            textDirection = extra.autoTextDirection()
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = extra.autoTextAlign(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                word.examples.forEach { ex ->
                    Text(
                        text = "• $ex",
                        style = MaterialTheme.typography.bodySmall.copy(
                            textDirection = ex.autoTextDirection()
                        ),
                        textAlign = ex.autoTextAlign(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            IconButton(onClick = onAdd, enabled = !isKnown) {
                Icon(
                    imageVector = if (isKnown) Icons.Filled.Check else Icons.Filled.Add,
                    contentDescription = if (isKnown) alreadyInBoxLabel else leitnerLabel,
                )
            }
        }
    }
}
