package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.BookSentence
import com.example.model.JsonWord

/**
 * The pedagogical drawer as a dialog, for the paths that open a lesson without
 * an inline row to expand — "Analyze Sentence" from a highlight popup, or from
 * the page/PDF view where there is no sentence list to expand into.
 *
 * [BookSentencePane] renders the same material inline; this is the same
 * content in a focused surface, not a second source of truth.
 *
 * [knownWords] lets the Leitner action show what is already in the box, so
 * tapping twice is visibly a no-op rather than silently creating a duplicate
 * (`LeitnerBoxManager.addCard` already refuses duplicates and returns false).
 */
@Composable
fun SentenceLessonDialog(
    sentence: BookSentence,
    onDismiss: () -> Unit,
    onAddWordToLeitner: (JsonWord) -> Unit,
    knownWords: Set<String> = emptySet(),
    leitnerLabel: String = "افزودن به جعبهٔ لایتنر",
    alreadyInBoxLabel: String = "در جعبهٔ لایتنر هست",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = sentence.location.ifBlank { "#${sentence.id}" },
                style = MaterialTheme.typography.titleSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(text = sentence.english, style = MaterialTheme.typography.bodyLarge)

                val translation = sentence.translation
                if (!translation.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = translation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                val pronunciation = sentence.pronunciation
                if (!pronunciation.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = pronunciation,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val lesson = sentence.lesson
                if (lesson != null) {
                    LessonRow("توضیح", lesson.explanation)
                    LessonRow("Grammar", lesson.grammar)
                    LessonRow("گرامر", lesson.grammarTranslation)
                    LessonRow("Structure", lesson.structure)
                }
                LessonRow("یادداشت", sentence.notes)

                if (sentence.words.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    sentence.words.forEach { word ->
                        val key = word.word.orEmpty().trim().lowercase()
                        LessonWordRow(
                            word = word,
                            isKnown = key.isNotEmpty() && key in knownWords,
                            onAdd = { onAddWordToLeitner(word) },
                            leitnerLabel = leitnerLabel,
                            alreadyInBoxLabel = alreadyInBoxLabel,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("بستن") }
        },
    )
}

@Composable
private fun LessonRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LessonWordRow(
    word: JsonWord,
    isKnown: Boolean,
    onAdd: () -> Unit,
    leitnerLabel: String,
    alreadyInBoxLabel: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = word.word.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                val pronunciation = word.pronunciation
                if (!pronunciation.isNullOrBlank()) {
                    Text(
                        text = pronunciation,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val translation = word.translation
                if (!translation.isNullOrBlank()) {
                    Text(text = translation, style = MaterialTheme.typography.bodyMedium)
                }
                val meaning = word.meaningInContext
                if (!meaning.isNullOrBlank()) {
                    Text(
                        text = meaning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val extra = word.extraExplanation
                if (!extra.isNullOrBlank()) {
                    Text(
                        text = extra,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                word.examples.forEach { example ->
                    Text(text = "• $example", style = MaterialTheme.typography.bodySmall)
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
