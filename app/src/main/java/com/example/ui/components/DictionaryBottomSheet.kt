package com.example.ui.components

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.AndroidView

import com.example.model.DictionaryEntry
import com.example.model.JsonWord
import com.example.logic.TranslationDetector
import com.example.logic.autoTextAlign
import com.example.logic.autoTextDirection
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.AppStrings
import com.example.ui.theme.NeoBrutalismAccent
import com.example.ui.theme.isNeobrutalismDesign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryBottomSheet(
    searchedWord: String,
    results: List<DictionaryEntry>?,
    contextEnglish: String? = null,
    contextPersian: String? = null,
    isAddedToLeitner: Boolean = false,
    onAddToLeitner: () -> Unit = {},
    importedFileNames: List<String> = emptyList(),
    onSearchQueryChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    appLanguage: AppLanguage = AppLanguage.FA,
    // JSON learning data for the searched word (when a JSON learning file
    // exists). "The dictionary system follows the JSON learning data": this
    // card is rendered FIRST, above the normal dictionary results.
    jsonWord: JsonWord? = null
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isDark = isSystemInDarkTheme()
    val strings = remember(appLanguage) { AppStrings(appLanguage) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var queryInput by remember(searchedWord) { mutableStateOf(searchedWord) }

    // When 2+ dictionary files are imported, let the user narrow the shown
    // entries down to just one file (better access when several dictionaries
    // overlap on the same word). Resets whenever a new word is searched.
    var selectedSourceFilter by remember(searchedWord) { mutableStateOf<String?>(null) }
    val filteredResults = remember(results, selectedSourceFilter) {
        if (selectedSourceFilter == null) results
        else results?.filter { it.source.equals(selectedSourceFilter, ignoreCase = true) }
    }

    val matchResult = remember(results, contextPersian) {
        if (contextPersian != null && !results.isNullOrEmpty()) {
            TranslationDetector.detectTranslation(results.map { it.html }, contextPersian)
        } else null
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = if (isNeobrutalismDesign()) {
            RoundedCornerShape(0.dp)
        } else {
            RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
        },
        containerColor = MaterialTheme.colorScheme.surface,
        // The sheet draws its own gradient handle below; without this the
        // Material default handle was rendered on top of it.
        dragHandle = null,
        modifier = Modifier.fillMaxHeight(if (isLandscape) 0.95f else 0.88f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SheetHandle()
            WordHeadline(word = searchedWord)
            Spacer(modifier = Modifier.height(10.dp))

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                    ) {
                        SearchField(queryInput, strings, onSearchQueryChange)
                        Spacer(modifier = Modifier.height(12.dp))
                        ContextSection(contextEnglish, searchedWord, queryInput, contextPersian, matchResult, strings) { clickedWord ->
                            queryInput = clickedWord; onSearchQueryChange(clickedWord)
                        }
                        jsonWord?.let { jw ->
                            Spacer(modifier = Modifier.height(12.dp))
                            JsonWordInfoCard(jsonWord = jw, strings = strings)
                        }
                        if (!results.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            AddToLeitnerButton(isAdded = isAddedToLeitner, strings = strings, onClick = onAddToLeitner)
                        }
                    }
                    Column(modifier = Modifier.weight(1.3f).fillMaxHeight()) {
                        if (importedFileNames.size >= 2) {
                            DictionarySourceFilterRow(importedFileNames, selectedSourceFilter, strings) { selectedSourceFilter = it }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        when {
                            results == null -> LookupInProgressCard()
                            filteredResults.isNullOrEmpty() -> NoResultsCard(strings)
                            else -> WebViewPart(results = filteredResults, isDark = isDark, modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                // The whole sheet content is one scrollable list: the top
                // sections scroll together with the results and the WebView
                // keeps its own inner scrolling for long definitions. This
                // fixes the popup where the content could not scroll at all
                // and the sheet collapsed / disappeared while scrolling.
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { SearchField(queryInput, strings, onSearchQueryChange) }
                    item {
                        ContextSection(contextEnglish, searchedWord, queryInput, contextPersian, matchResult, strings) { clickedWord ->
                            queryInput = clickedWord; onSearchQueryChange(clickedWord)
                        }
                    }
                    jsonWord?.let { jw ->
                        item { JsonWordInfoCard(jsonWord = jw, strings = strings) }
                    }
                    if (!results.isNullOrEmpty()) {
                        item { AddToLeitnerButton(isAdded = isAddedToLeitner, strings = strings, onClick = onAddToLeitner) }
                    }
                    if (importedFileNames.size >= 2) {
                        item { DictionarySourceFilterRow(importedFileNames, selectedSourceFilter, strings) { selectedSourceFilter = it } }
                    }
                    // `null` means the lookup is still running, an empty list
                    // means it finished with nothing — the sheet used to show
                    // "no results" for both, which looked like a failure
                    // every single time a word was tapped.
                    if (results == null) {
                        item { LookupInProgressCard() }
                    } else if (filteredResults.isNullOrEmpty()) {
                        item { NoResultsCard(strings) }
                    } else {
                        item {
                            WebViewPart(
                                results = filteredResults,
                                isDark = isDark,
                                modifier = Modifier.fillMaxWidth().fillParentMaxHeight(0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Gradient grab handle, replacing the default Material one. */
@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val neo = isNeobrutalismDesign()
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
}

/** The looked-up word as the sheet's headline, with a brand underline. */
@Composable
private fun WordHeadline(word: String) {
    if (word.isBlank()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = word,
            // User-provided word: render RTL if it's Persian/Arabic.
            style = MaterialTheme.typography.headlineSmall.copy(
                textAlign = word.autoTextAlign(),
                textDirection = word.autoTextDirection()
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(6.dp))
        val neo = isNeobrutalismDesign()
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
}

@Composable
private fun DictionarySourceFilterRow(
    fileNames: List<String>,
    selected: String?,
    strings: AppStrings,
    onSelect: (String?) -> Unit
) {
    // Lets the user narrow the dictionary popup down to results from just one
    // imported file, useful once several dictionaries are loaded and start
    // overlapping on the same word (better access to a specific source).
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SourceChip(
            label = strings.allFilterChip,
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        fileNames.forEach { name ->
            SourceChip(
                label = name,
                selected = selected == name,
                onClick = { onSelect(if (selected == name) null else name) },
            )
        }
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val neo = isNeobrutalismDesign()
    Box(
        modifier = Modifier
            .clip(if (neo) RoundedCornerShape(0.dp) else CircleShape)
            .background(
                when {
                    neo && selected -> NeoBrutalismAccent
                    selected -> scheme.primary.copy(alpha = 0.16f)
                    neo -> scheme.surfaceContainerLowest
                    else -> scheme.surfaceVariant.copy(alpha = 0.45f)
                }
            )
            .then(
                if (neo) {
                    Modifier.border(2.dp, scheme.outline)
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = if (selected) scheme.primary.copy(alpha = 0.45f)
                        else scheme.onSurface.copy(alpha = 0.07f),
                        shape = CircleShape,
                    )
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = when {
                neo && selected -> Color.Black
                selected -> scheme.primary
                neo -> scheme.onSurface
                else -> scheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

/**
 * JSON learning data for the searched word, shown FIRST inside the
 * dictionary sheet whenever a JSON learning file contains an entry for the
 * word — "the dictionary system follows the JSON learning data". Kept
 * compact (bounded lines) so the normal dictionary results below always
 * keep enough room.
 */
@Composable
fun JsonWordInfoCard(jsonWord: JsonWord, strings: AppStrings) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = MaterialTheme.colorScheme.primary,
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(14.dp),
    ) {
        Text(
            text = strings.jsonLearningDataLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = jsonWord.word,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textAlign = jsonWord.word.autoTextAlign(),
                        textDirection = jsonWord.word.autoTextDirection()
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                jsonWord.pronunciation?.takeIf { it.isNotBlank() }?.let { ipa ->
                    Text(
                        text = ipa,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                jsonWord.partOfSpeech?.let { pos ->
                    StatusPill(
                        text = strings.partOfSpeechName(pos),
                        tone = PillTone.Accent,
                    )
                }
                jsonWord.translation?.takeIf { it.isNotBlank() }?.let { tr ->
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = tr,
                        // Auto RTL/LTR: Persian translation right-aligned,
                        // any other script left-aligned.
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
        jsonWord.meaningInContext?.takeIf { it.isNotBlank() }?.let { meaning ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = meaning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3
            )
        }
        jsonWord.extraExplanation?.takeIf { it.isNotBlank() }?.let { explanation ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )
        }
        jsonWord.examples.take(2).forEach { example ->
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "• $example",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun AddToLeitnerButton(isAdded: Boolean, strings: AppStrings, onClick: () -> Unit) {
    // Lets the user save the currently displayed word + its dictionary
    // definition into the app's built-in Leitner box (see LeitnerScreen.kt
    // and AppViewModel.addActiveWordToLeitner) for later spaced-repetition
    // review and Anki export.
    if (isAdded) {
        val neo = isNeobrutalismDesign()
        val color = if (neo) NeoBrutalismAccent else MaterialTheme.colorScheme.primary
        val shape = if (neo) RoundedCornerShape(0.dp) else RoundedCornerShape(18.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(color.copy(alpha = if (neo) 1f else 0.12f))
                .border(
                    width = if (neo) 2.dp else 1.dp,
                    color = if (neo) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                    shape = shape,
                )
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (neo) Color.Black else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = strings.addedToLeitnerLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (neo) Color.Black else MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        GradientButton(
            text = strings.addToLeitnerBtn,
            icon = Icons.Outlined.StarBorder,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SearchField(initialQuery: String, strings: AppStrings, onSearch: (String) -> Unit) {
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    val neo = isNeobrutalismDesign()
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        shape = if (neo) RoundedCornerShape(0.dp) else RoundedCornerShape(20.dp),
        placeholder = { Text(strings.searchWordPlaceholder) },
        label = { Text(strings.wordLabel) },
        // Input stays auto-detected: Persian RTL, English LTR per content.
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            textAlign = query.autoTextAlign(),
            textDirection = query.autoTextDirection()
        ),
        trailingIcon = {
            SoftIconButton(
                icon = Icons.Default.Search,
                contentDescription = strings.searchCd,
                onClick = { onSearch(query.trim()) },
                size = 34.dp,
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (neo) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.primary
            },
            unfocusedBorderColor = if (neo) {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            },
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(query.trim()) }),
        singleLine = true
    )
}

@Composable
fun ClickableContextSubText(
    text: String,
    activeWord: String,
    queryInput: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    onWordClick: (String) -> Unit
) {
    val regex = "\\b[a-zA-Z][a-zA-Z0-9'-]*\\b".toRegex()
    val matches = regex.findAll(text)
    val cleanActive1 = activeWord.lowercase().trim()
    val cleanActive2 = queryInput.lowercase().trim()

    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        for (match in matches) {
            append(text.substring(lastIndex, match.range.first))
            val startIndex = this.length
            append(match.value)
            val endIndex = this.length
            val wordLower = match.value.lowercase().trim()
            val isCurrentActive = wordLower == cleanActive1 || wordLower == cleanActive2
            addStyle(
                style = SpanStyle(
                    color = if (isCurrentActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isCurrentActive) FontWeight.Bold else FontWeight.Normal,
                    textDecoration = if (isCurrentActive) TextDecoration.None else TextDecoration.Underline
                ),
                start = startIndex, end = endIndex
            )
            addStringAnnotation(tag = "word", annotation = match.value, start = startIndex, end = endIndex)
            lastIndex = match.range.last + 1
        }
        append(text.substring(lastIndex))
    }

    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotatedString,
        modifier = modifier.pointerInput(text) {
            detectTapGestures { pos ->
                layoutResult.value?.let { result ->
                    val offset = result.getOffsetForPosition(pos)
                    annotatedString.getStringAnnotations(tag = "word", start = offset, end = offset)
                        .firstOrNull()?.let { onWordClick(it.item) }
                }
            }
        },
        // Auto RTL/LTR so the English context line renders in the correct
        // direction regardless of the app menu language.
        style = style.copy(
            textAlign = text.autoTextAlign(),
            textDirection = text.autoTextDirection()
        ),
        onTextLayout = { layoutResult.value = it }
    )
}

@Composable
fun ContextSection(
    contextEnglish: String?,
    searchedWord: String,
    queryInput: String,
    contextPersian: String?,
    matchResult: Pair<String, String>?,
    strings: AppStrings,
    onWordClick: (String) -> Unit
) {
    if (contextEnglish.isNullOrEmpty()) return
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = MaterialTheme.colorScheme.secondary,
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(14.dp),
    ) {
        Text(
            text = strings.subtitleAndTranslationLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        ClickableContextSubText(
            text = contextEnglish,
            activeWord = searchedWord,
            queryInput = queryInput,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = TextUnit.Unspecified),
            onWordClick = onWordClick,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        )
        if (!contextPersian.isNullOrEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
            Text(
                text = contextPersian,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDirection = contextPersian.autoTextDirection()
                ),
                textAlign = contextPersian.autoTextAlign(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            )
        }
        if (matchResult != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        if (isNeobrutalismDesign()) {
                            RoundedCornerShape(0.dp)
                        } else {
                            RoundedCornerShape(14.dp)
                        }
                    )
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .then(
                        if (isNeobrutalismDesign()) {
                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline)
                        } else {
                            Modifier
                        }
                    )
                    .padding(10.dp)
            ) {
                // Direction comes from the whole rendered line (label +
                // matched translation) via its first strong character, so a
                // Persian match renders RTL and an English one LTR.
                val matchedFullText = strings.matchedTranslationLabel("") + matchResult.second
                Text(
                    text = buildAnnotatedString {
                        append(strings.matchedTranslationLabel(""))
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                            append(matchResult.second)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDirection = matchedFullText.autoTextDirection()
                    ),
                    textAlign = matchedFullText.autoTextAlign(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strings.autoDetectWarning,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isNeobrutalismDesign()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Shown while a lookup is still running, instead of a premature "no results". */
@Composable
private fun LookupInProgressCard() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(34.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun NoResultsCard(strings: AppStrings) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        EmptyState(
            icon = Icons.Default.Search,
            title = strings.noResultsFound,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** `#RRGGBB` form of a Compose color, for use inside the WebView stylesheet. */
private fun Color.toCssHex(): String =
    String.format(java.util.Locale.US, "#%06X", 0xFFFFFF and this.toArgb())

/** `rgba(...)` form of a Compose color with an explicit alpha. */
private fun Color.toCssRgba(alpha: Float): String {
    val r = (this.red * 255).toInt()
    val g = (this.green * 255).toInt()
    val b = (this.blue * 255).toInt()
    return "rgba($r, $g, $b, $alpha)"
}

@Composable
fun WebViewPart(results: List<DictionaryEntry>, isDark: Boolean, modifier: Modifier = Modifier) {
    // The definition renderer used to hardcode a sepia/teal palette that had
    // nothing to do with the app's theme. Every color below is now derived
    // from the live Material color scheme, so the dictionary matches the rest
    // of Langosphere and follows Material You dynamic colors as well.
    val scheme = MaterialTheme.colorScheme
    val bgColor = scheme.surface.toCssHex()
    val textColor = scheme.onSurface.toCssHex()
    val cardBg = if (isDark) scheme.surfaceVariant.toCssRgba(0.45f) else scheme.surfaceVariant.toCssRgba(0.55f)
    val cardBorder = scheme.onSurface.toCssRgba(0.08f)
    val accentPrimary = scheme.primary.toCssHex()
    val accentGreen = scheme.secondary.toCssHex()
    val accentPink = scheme.tertiary.toCssHex()
    val accentCyan = scheme.primary.toCssHex()
    val accentTeal = scheme.secondary.toCssHex()
    val subtitleText = scheme.onSurfaceVariant.toCssHex()
    val posTextColor = scheme.onTertiary.toCssHex()
    val phoneticBg = scheme.secondary.toCssRgba(0.12f)
    val scrollTrack = scheme.onSurface.toCssRgba(0.06f)
    val scrollThumb = scheme.primary.toCssRgba(0.55f)

    // Holder (not state) for the last HTML actually loaded. The player keeps
    // recomposing this screen while the video plays, and `update` used to
    // call loadDataWithBaseURL every single time — so the definition
    // flickered and jumped back to the top a few times per second, which
    // made long entries impossible to read.
    val lastLoadedHtml = remember { arrayOfNulls<String>(1) }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.defaultTextEncodingName = "utf-8"
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                var lastTouchY = 0f
                setOnTouchListener { view, event ->
                    // Only claim the gesture for the WebView while it can
                    // actually scroll in the drag direction; otherwise hand
                    // the gesture back to the parent (the bottom sheet /
                    // list) so the sheet scrolls and dismisses naturally and
                    // no longer disappears mid-scroll.
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            lastTouchY = event.y
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            val dy = event.y - lastTouchY
                            lastTouchY = event.y
                            if ((dy > 0 && view.canScrollVertically(-1)) || (dy < 0 && view.canScrollVertically(1))) {
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                            } else {
                                view.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    false
                }
            }
        },
        onRelease = { webView ->
            // Without this the WebView (and its rendering process) stayed
            // alive after the sheet closed — a real leak when many words are
            // looked up during one film.
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        },
        update = { webView ->
            val entriesHtml = results.joinToString("\n") { entry ->
                """<div class="entry-group">${entry.html}</div>"""
            }
            val htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        html, body {
                            background: transparent;
                            color: $textColor;
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                            line-height: 1.85;
                            margin: 0; padding: 4px;
                            scrollbar-width: thin;
                            scrollbar-color: $scrollThumb $scrollTrack;
                        }
                        ::-webkit-scrollbar { width: 6px; }
                        ::-webkit-scrollbar-track { background: $scrollTrack; border-radius: 3px; }
                        ::-webkit-scrollbar-thumb { background: $scrollThumb; border-radius: 3px; }
                        .dict-content { padding-bottom: 24px; }
                        .entry-group {
                            background: $cardBg;
                            border: 1px solid $cardBorder;
                            border-radius: 22px;
                            padding: 18px;
                            margin-bottom: 14px;
                        }
                        .entry {
                            color: $accentPrimary !important;
                            font-size: 24px !important;
                            font-weight: 800 !important;
                            display: block !important;
                            border-bottom: 2px solid $cardBorder;
                            padding-bottom: 8px;
                            margin-bottom: 12px;
                            text-transform: capitalize;
                        }
                        .phonetic {
                            color: $accentGreen !important;
                            font-size: 14px !important;
                            display: inline-block !important;
                            background: $phoneticBg;
                            padding: 3px 10px;
                            border-radius: 999px;
                            margin-left: 8px;
                            font-weight: 500;
                            direction: ltr;
                        }
                        .phonetic:before, .phonetic:after { content: "/" !important; color: $subtitleText !important; }
                        .pos {
                            color: $posTextColor !important;
                            font-size: 10px !important;
                            font-weight: 700 !important;
                            background-color: $accentPink !important;
                            padding: 3px 9px !important;
                            border-radius: 999px !important;
                            text-transform: uppercase;
                            display: inline-block !important;
                            margin-bottom: 10px;
                        }
                        .def {
                            color: $textColor !important;
                            font-size: 15px !important;
                            font-weight: 500 !important;
                            direction: rtl !important;
                            text-align: right !important;
                            margin: 10px 0 !important;
                            line-height: 1.9 !important;
                            padding-right: 16px;
                            position: relative;
                        }
                        .def::before {
                            content: "◆" !important;
                            color: $accentPink !important;
                            font-size: 9px;
                            position: absolute;
                            right: 0; top: 5px;
                        }
                        .phrase {
                            color: $accentCyan !important;
                            font-size: 15px !important;
                            font-weight: 700 !important;
                            margin-top: 16px !important;
                            display: block !important;
                        }
                        .phrase::before { content: "✦ " !important; color: $accentCyan !important; }
                        .phrasedef {
                            color: $accentTeal !important;
                            font-size: 13px !important;
                            direction: rtl !important;
                            text-align: right !important;
                            padding-right: 16px;
                            margin-bottom: 12px;
                        }
                        .enex {
                            color: $accentPrimary !important;
                            font-size: 14px !important;
                            display: block !important;
                            text-align: left !important;
                            direction: ltr !important;
                            margin-top: 10px !important;
                            border-left: 3px solid $accentPrimary;
                            padding-left: 10px;
                            font-style: italic;
                        }
                        .faex {
                            color: $accentGreen !important;
                            font-size: 13px !important;
                            direction: rtl !important;
                            text-align: right !important;
                            display: block !important;
                            margin-bottom: 14px !important;
                            padding-right: 12px;
                        }
                    </style>
                </head>
                <body>
                    <div class="dict-content">$entriesHtml</div>
                </body>
                </html>
            """.trimIndent()
            if (lastLoadedHtml[0] != htmlContent) {
                lastLoadedHtml[0] = htmlContent
                webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
            }
        }
    )
}