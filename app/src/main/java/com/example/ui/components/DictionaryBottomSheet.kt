package com.example.ui.components

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.content.res.Configuration
import androidx.compose.foundation.gestures.detectTapGestures
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
import com.example.logic.TranslationDetector
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.AppStrings

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
    appLanguage: AppLanguage = AppLanguage.FA
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isDark = isSystemInDarkTheme()
    val strings = remember(appLanguage) { AppStrings(appLanguage) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
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
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxHeight(if (isLandscape) 0.95f else 0.85f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.width(40.dp).height(4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ) {}
            }

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
                        if (filteredResults.isNullOrEmpty()) NoResultsCard(strings)
                        else WebViewPart(results = filteredResults, isDark = isDark, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                SearchField(queryInput, strings, onSearchQueryChange)
                Spacer(modifier = Modifier.height(12.dp))
                ContextSection(contextEnglish, searchedWord, queryInput, contextPersian, matchResult, strings) { clickedWord ->
                    queryInput = clickedWord; onSearchQueryChange(clickedWord)
                }
                if (!results.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AddToLeitnerButton(isAdded = isAddedToLeitner, strings = strings, onClick = onAddToLeitner)
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (importedFileNames.size >= 2) {
                    DictionarySourceFilterRow(importedFileNames, selectedSourceFilter, strings) { selectedSourceFilter = it }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (filteredResults.isNullOrEmpty()) NoResultsCard(strings)
                else WebViewPart(results = filteredResults, isDark = isDark, modifier = Modifier.weight(1f))
            }
        }
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
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(strings.allFilterChip) }
        )
        fileNames.forEach { name ->
            FilterChip(
                selected = selected == name,
                onClick = { onSelect(if (selected == name) null else name) },
                label = { Text(name, maxLines = 1) }
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
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = if (isAdded) {
            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        } else {
            ButtonDefaults.outlinedButtonColors()
        }
    ) {
        Icon(
            imageVector = if (isAdded) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(if (isAdded) strings.addedToLeitnerLabel else strings.addToLeitnerBtn)
    }
}

@Composable
private fun SearchField(initialQuery: String, strings: AppStrings, onSearch: (String) -> Unit) {
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        placeholder = { Text(strings.searchWordPlaceholder) },
        label = { Text(strings.wordLabel) },
        trailingIcon = {
            IconButton(onClick = { onSearch(query.trim()) }) {
                Icon(Icons.Default.Search, contentDescription = strings.searchCd)
            }
        },
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
        style = style,
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
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = strings.subtitleAndTranslationLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            ClickableContextSubText(
                text = contextEnglish,
                activeWord = searchedWord,
                queryInput = queryInput,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = TextUnit.Unspecified),
                onWordClick = onWordClick,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )
            if (!contextPersian.isNullOrEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Text(
                    text = contextPersian,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )
            }
            if (matchResult != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = buildAnnotatedString {
                                append(strings.matchedTranslationLabel(""))
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                                    append(matchResult.second)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = strings.autoDetectWarning,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Right
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NoResultsCard(strings: AppStrings) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = strings.noResultsFound,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        )
    }
}

@Composable
fun WebViewPart(results: List<DictionaryEntry>, isDark: Boolean, modifier: Modifier = Modifier) {
    // Palette mirrors the app's hand-tuned, low-glare theme (see ui/theme/Color.kt)
    // so the offline dictionary reader is just as comfortable to read as the rest of the app.
    val bgColor = if (isDark) "#1B1D1E" else "#F7F4EF"
    val textColor = if (isDark) "#E5E1D8" else "#2F2B26"
    val cardBg = if (isDark) "#282B2D" else "#F1ECE2"
    val cardBorder = if (isDark) "rgba(143, 207, 192, 0.25)" else "rgba(61, 110, 99, 0.18)"
    val accentPrimary = if (isDark) "#8FCFC0" else "#3D6E63"
    val accentGreen = if (isDark) "#7FC79A" else "#3F7D5C"
    val accentPink = if (isDark) "#E3A0AC" else "#B4677A"
    val accentCyan = if (isDark) "#7FD1DB" else "#2A8C99"
    val accentTeal = if (isDark) "#8AD6C4" else "#0B7A6E"
    val subtitleText = if (isDark) "#C8C2B4" else "#6B6459"
    val scrollTrack = if (isDark) "#34383A" else "#ECE6DC"
    val scrollThumb = if (isDark) "#8FCFC0" else "#3D6E63"

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.defaultTextEncodingName = "utf-8"
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnTouchListener { view, event ->
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    false
                }
            }
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
                            background: $bgColor;
                            color: $textColor;
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                            line-height: 1.8;
                            margin: 0; padding: 8px;
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
                            border-radius: 14px;
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
                            background: ${if (isDark) "rgba(127,199,154,0.12)" else "rgba(63,125,92,0.08)"};
                            padding: 3px 8px;
                            border-radius: 6px;
                            margin-left: 8px;
                            font-weight: 500;
                            direction: ltr;
                        }
                        .phonetic:before, .phonetic:after { content: "/" !important; color: $subtitleText !important; }
                        .pos {
                            color: $bgColor !important;
                            font-size: 10px !important;
                            font-weight: 700 !important;
                            background-color: $accentPink !important;
                            padding: 2px 7px !important;
                            border-radius: 4px !important;
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
                            line-height: 1.8 !important;
                            padding-right: 16px;
                            position: relative;
                        }
                        .def::before {
                            content: "◆" !important;
                            color: $accentPink !important;
                            font-size: 9px;
                            position: absolute;
                            right: 0; top: 3px;
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
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
        }
    )
}
