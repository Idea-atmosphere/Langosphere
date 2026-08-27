package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LeitnerCard
import com.example.ui.theme.AppStrings

/**
 * "جعبه لایتنر" (Leitner box) tab: review words that were added from the
 * dictionary bottom sheet using 5-box spaced repetition, browse every saved
 * card, and export everything as an Anki-compatible plain text file (see
 * logic/AnkiExporter.kt for the exact format).
 *
 * Words + definitions are added elsewhere (AppViewModel.addActiveWordToLeitner,
 * triggered from the "افزودن به جعبه لایتنر" button in
 * ui/components/DictionaryBottomSheet.kt) — this screen only reviews/manages
 * cards that already exist.
 */
@Composable
fun LeitnerScreen(viewModel: AppViewModel) {
    val allCards by viewModel.leitnerCards.collectAsState()
    val dueCards by viewModel.leitnerDueCards.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val strings = remember(appLanguage) { AppStrings(appLanguage) }

    var reviewMode by remember { mutableStateOf(true) } // true = review due cards, false = browse all
    var reviewIndex by remember { mutableIntStateOf(0) }
    var isRevealed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshLeitnerCards() }
    LaunchedEffect(dueCards) {
        if (reviewIndex >= dueCards.size) reviewIndex = 0
        isRevealed = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.leitnerTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = { viewModel.exportLeitnerToAnki() },
                enabled = allCards.isNotEmpty(),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.exportAnki)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            strings.leitnerSummary(allCards.size, dueCards.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = reviewMode, onClick = { reviewMode = true }, label = { Text(strings.reviewTodayChip(dueCards.size)) })
            FilterChip(selected = !reviewMode, onClick = { reviewMode = false }, label = { Text(strings.allCardsChip(allCards.size)) })
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (reviewMode) {
            if (dueCards.isEmpty()) {
                EmptyLeitnerState(
                    text = if (allCards.isEmpty())
                        strings.leitnerEmptyAddHint
                    else
                        strings.leitnerEmptyDoneToday,
                    modifier = Modifier.weight(1f)
                )
            } else {
                val card = dueCards[reviewIndex.coerceIn(0, dueCards.size - 1)]
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(strings.boxOfFive(card.boxLevel), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(card.word, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        if (isRevealed) {
                            HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            // Definitions can span many lines/senses, so this area must
                            // scroll independently instead of being clipped to whatever
                            // fits in the card's remaining height.
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = card.definition,
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                OutlinedButton(onClick = { isRevealed = true }) { Text(strings.showMeaning) }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (isRevealed) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.markLeitnerUnknown(card.id); isRevealed = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(strings.didntKnow)
                        }
                        Button(
                            onClick = { viewModel.markLeitnerKnown(card.id); isRevealed = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(strings.knewIt)
                        }
                    }
                }
            }
        } else {
            if (allCards.isEmpty()) {
                EmptyLeitnerState(text = strings.leitnerEmptyNoCards, modifier = Modifier.weight(1f))
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(allCards, key = { it.id }) { card ->
                        LeitnerCardRow(card = card, onDelete = { viewModel.deleteLeitnerCard(card.id) }, strings = strings)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLeitnerState(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Style, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LeitnerCardRow(card: LeitnerCard, onDelete: () -> Unit, strings: AppStrings) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(card.word, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            strings.boxLabel(card.boxLevel),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(card.definition, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = strings.deleteCd, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
