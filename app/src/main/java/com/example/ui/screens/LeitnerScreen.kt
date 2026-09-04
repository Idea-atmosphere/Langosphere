package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logic.autoTextAlign
import com.example.logic.autoTextDirection
import com.example.model.LeitnerCard
import com.example.ui.components.EmptyState
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.PillTone
import com.example.ui.components.ProgressRing
import com.example.ui.components.SegmentedPills
import com.example.ui.components.SoftIconButton
import com.example.ui.components.StatusPill
import com.example.ui.components.fadingEdges
import com.example.ui.components.neoHardShadow
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentRed
import com.example.ui.theme.AppStrings
import com.example.ui.theme.NeoBrutalismAccent
import com.example.ui.theme.isNeobrutalismDesign
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

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
 *
 * The review deck is a pager: swipe left/right to move between today's cards
 * and tap a card to flip it in 3D and reveal the meaning. The box level is
 * shown only as dots, and the pill on the card says which card of how many
 * you are looking at.
 */
@Composable
fun LeitnerScreen(viewModel: AppViewModel) {
    val allCards by viewModel.leitnerCards.collectAsState()
    val dueCards by viewModel.leitnerDueCards.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val strings = remember(appLanguage) { AppStrings(appLanguage) }

    var reviewMode by remember { mutableStateOf(true) } // true = review due cards, false = browse all
    var isRevealed by remember { mutableStateOf(false) }

    // The review deck. pageCount is read lazily, so answering a card (which
    // removes it from dueCards) shrinks the deck without resetting anything.
    val deckState = rememberPagerState(
        initialPage = 0,
        pageCount = { dueCards.size.coerceAtLeast(1) },
    )

    LaunchedEffect(Unit) { viewModel.refreshLeitnerCards() }
    LaunchedEffect(dueCards.size) {
        isRevealed = false
        if (deckState.currentPage > dueCards.lastIndex.coerceAtLeast(0)) {
            deckState.scrollToPage(0)
        }
    }
    // A card is always shown face-down when you swipe to it.
    LaunchedEffect(deckState.currentPage) { isRevealed = false }

    // A card sitting in the last box counts as mastered — that ratio is what
    // the ring in the header visualises.
    val mastered = allCards.count { it.boxLevel >= MAX_BOX_LEVEL }
    val masteredRatio = if (allCards.isEmpty()) 0f else mastered.toFloat() / allCards.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // ── Header: progress ring + counts + export ──
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            tint = MaterialTheme.colorScheme.primary,
            contentPadding = PaddingValues(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    progress = masteredRatio,
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 6.dp,
                ) {
                    Text(
                        text = "${(masteredRatio * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.leitnerTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = strings.leitnerSummary(allCards.size, dueCards.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SoftIconButton(
                    icon = Icons.Filled.Download,
                    contentDescription = strings.exportAnki,
                    onClick = { viewModel.exportLeitnerToAnki() },
                    enabled = allCards.isNotEmpty(),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SegmentedPills(
            items = listOf(
                strings.reviewTodayChip(dueCards.size),
                strings.allCardsChip(allCards.size),
            ),
            selectedIndex = if (reviewMode) 0 else 1,
            onSelect = { reviewMode = it == 0 },
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (reviewMode) {
            if (dueCards.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Style,
                    title = if (allCards.isEmpty()) strings.leitnerEmptyAddHint else strings.leitnerEmptyDoneToday,
                    modifier = Modifier.weight(1f),
                )
            } else {
                val currentIndex = deckState.currentPage.coerceIn(0, dueCards.lastIndex)
                val card = dueCards[currentIndex]

                HorizontalPager(
                    state = deckState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    pageSpacing = 12.dp,
                ) { page ->
                    val pageCard = dueCards.getOrNull(page)
                    if (pageCard != null) {
                        FlashCard(
                            card = pageCard,
                            position = page + 1,
                            total = dueCards.size,
                            // Only the card you are actually on can be flipped,
                            // so a neighbour never scrolls in face-up.
                            isRevealed = isRevealed && page == deckState.currentPage,
                            onFlip = { isRevealed = !isRevealed },
                            strings = strings,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val distance = ((deckState.currentPage - page) +
                                        deckState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                                    val closeness = 1f - distance
                                    alpha = 0.5f + 0.5f * closeness
                                    val scale = 0.92f + 0.08f * closeness
                                    scaleX = scale
                                    scaleY = scale
                                },
                        )
                    }
                }

                if (dueCards.size > 1) {
                    Text(
                        text = if (strings.isEn) {
                            "Swipe left or right to move between cards • tap to flip"
                        } else {
                            "برای جابه‌جایی بین کارت‌ها به چپ یا راست بکش • برای دیدن معنی بزن"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }

                AnimatedVisibility(
                    visible = isRevealed,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SoftActionButton(
                            text = strings.didntKnow,
                            icon = Icons.Filled.Close,
                            color = AccentRed,
                            onClick = {
                                viewModel.markLeitnerUnknown(card.id)
                                isRevealed = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                        GradientButton(
                            text = strings.knewIt,
                            icon = Icons.Filled.Check,
                            onClick = {
                                viewModel.markLeitnerKnown(card.id)
                                isRevealed = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        } else {
            if (allCards.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Style,
                    title = strings.leitnerEmptyNoCards,
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(allCards, key = { it.id }) { card ->
                        LeitnerCardRow(
                            card = card,
                            onDelete = { viewModel.deleteLeitnerCard(card.id) },
                            strings = strings,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The review flashcard. Front shows the word, which card of how many it is,
 * and its box level as dots; tapping flips the card 180° around its vertical
 * axis to show the definition.
 *
 * Only one face is composed at a time and the back face is counter-rotated,
 * otherwise the revealed text would render mirrored.
 */
@Composable
private fun FlashCard(
    card: LeitnerCard,
    position: Int,
    total: Int,
    isRevealed: Boolean,
    onFlip: () -> Unit,
    strings: AppStrings,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(
        targetValue = if (isRevealed) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 200f),
        label = "flashcard-flip",
    )
    val shape = RoundedCornerShape(28.dp)
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
            }
            .then(
                if (isNeobrutalismDesign()) {
                    // Neobrutalist flashcard: a flat raised card with an ink
                    // border and a hard offset shadow instead of the soft
                    // gradient glass. The 3D flip is unchanged.
                    Modifier
                        .neoHardShadow(scheme.outline, offset = 6.dp)
                        .background(scheme.surfaceContainerLowest)
                        .border(2.dp, scheme.outline)
                } else {
                    Modifier
                        .clip(shape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    scheme.primary.copy(alpha = 0.16f),
                                    scheme.tertiary.copy(alpha = 0.10f),
                                    scheme.surfaceVariant.copy(alpha = 0.40f),
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(scheme.onSurface.copy(alpha = 0.10f), Color.Transparent)
                            ),
                            shape = shape,
                        )
                }
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onFlip,
            ),
    ) {
        if (rotation <= 90f) {
            FlashCardFront(card = card, position = position, total = total, strings = strings)
        } else {
            Box(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                FlashCardBack(card = card, strings = strings)
            }
        }
    }
}

@Composable
private fun FlashCardFront(
    card: LeitnerCard,
    position: Int,
    total: Int,
    strings: AppStrings,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Which card of today's deck this is — the old "box 1 of 5" text
            // is gone, the boxes are shown by the dots below instead.
            StatusPill(
                text = if (strings.isEn) "Card $position of $total" else "کارت $position از $total",
                tone = PillTone.Accent,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        BoxLevelDots(level = card.boxLevel)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = card.word,
                style = MaterialTheme.typography.headlineMedium.copy(
                    textDirection = card.word.autoTextDirection()
                ),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }

        StatusPill(text = strings.showMeaning, tone = PillTone.Neutral)
    }
}

@Composable
private fun FlashCardBack(card: LeitnerCard, strings: AppStrings) {
    val neo = isNeobrutalismDesign()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = card.word,
            style = MaterialTheme.typography.titleMedium.copy(
                textDirection = card.word.autoTextDirection()
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(if (neo) 4.dp else 3.dp)
                .clip(if (neo) RoundedCornerShape(0.dp) else CircleShape)
                .background(if (neo) NeoBrutalismAccent else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Definitions can span many lines/senses, so this area must scroll
        // independently instead of being clipped to whatever fits in the
        // card's remaining height.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .fadingEdges(topFade = 10.dp, bottomFade = 18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = card.definition,
                // Auto RTL/LTR from the definition itself (Persian definition
                // → right, English → left), independent of the menu language.
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 27.sp,
                    textDirection = card.definition.autoTextDirection()
                ),
                textAlign = TextAlign.Right,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/** Five dots showing how far a card has climbed through the boxes. */
@Composable
private fun BoxLevelDots(level: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val neo = isNeobrutalismDesign()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 1..MAX_BOX_LEVEL) {
            val filled = index <= level
            Box(
                modifier = Modifier
                    .size(if (filled) 8.dp else 6.dp)
                    .clip(if (neo) RoundedCornerShape(0.dp) else CircleShape)
                    .background(
                        if (filled) {
                            if (neo) NeoBrutalismAccent else scheme.primary
                        } else {
                            scheme.onSurfaceVariant.copy(alpha = 0.28f)
                        }
                    )
            )
        }
    }
}

/** Secondary action button in a single tone — the counterpart to GradientButton. */
@Composable
private fun SoftActionButton(
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isNeobrutalismDesign()) {
        // Neobrutalism: a flat loud color block ("didn't know" = red) with an
        // ink border, ink-black glyph/text, and a hard offset shadow.
        Row(
            modifier = modifier
                .neoHardShadow(MaterialTheme.colorScheme.outline, offset = 4.dp)
                .background(color)
                .border(2.dp, MaterialTheme.colorScheme.outline)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                maxLines = 1,
            )
        }
        return
    }
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.34f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = color)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun LeitnerCardRow(card: LeitnerCard, onDelete: () -> Unit, strings: AppStrings) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(14.dp),
    ) {
        // Keep word left / definition right absolute even when app is RTL (FA).
        // The outer Row would otherwise mirror in RTL and put the word on the right.
        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = card.word,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall.copy(
                                textAlign = TextAlign.Left,
                                textDirection = card.word.autoTextDirection()
                            ),
                            fontWeight = FontWeight.Bold,
                        )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusPill(
                        text = strings.boxLabel(card.boxLevel),
                        tone = if (card.boxLevel >= MAX_BOX_LEVEL) PillTone.Positive else PillTone.Accent,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                BoxLevelDots(level = card.boxLevel)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = card.definition,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        textAlign = TextAlign.Right,
                        textDirection = card.definition.autoTextDirection()
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            SoftIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = strings.deleteCd,
                onClick = onDelete,
                tint = AccentRed,
                size = 34.dp,
            )
            }
        }
    }
}

// The Leitner system in this app has five boxes; a card in box 5 is mastered.
private const val MAX_BOX_LEVEL = 5

@Suppress("unused")
private val leitnerMasteredTone = AccentGreen