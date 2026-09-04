package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logic.AiService
import com.example.logic.AiMemoryManager
import com.example.logic.autoTextDirection
import com.example.model.SubtitleEntry
import com.example.ui.theme.AppStrings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationScreen(
    subEnList: List<SubtitleEntry>,
    subFaList: List<SubtitleEntry>,
    dictionaryHtml: String? = null,
    viewModel: AppViewModel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val appLanguage by (viewModel?.appLanguage?.collectAsState() ?: remember { mutableStateOf(com.example.ui.theme.AppLanguage.FA) })
    val strings = remember(appLanguage) { AppStrings(appLanguage) }

    // Settings state
    val sharedPrefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
    var apiKey by remember { mutableStateOf(sharedPrefs.getString("api_key", "") ?: "") }
    var baseUrl by remember { mutableStateOf(sharedPrefs.getString("base_url", "http://localhost:20128/v1") ?: "http://localhost:20128/v1") }
    var model by remember { mutableStateOf(sharedPrefs.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini") }
    var targetLang by remember { mutableStateOf(sharedPrefs.getString("target_lang", "fa") ?: "fa") }

    // UI state
    var showSettings by remember { mutableStateOf(false) }
    var linesPerBatch by remember { mutableIntStateOf(sharedPrefs.getInt("lines_per_batch", 1)) }
    var isDefaultMode by remember { mutableStateOf(linesPerBatch == 0) }
    var batchOffset by remember { mutableIntStateOf(0) }
    var isTranslating by remember { mutableStateOf(false) }
    var translationProgress by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Translation results
    var translatedLines by remember { mutableStateOf<List<AiService.TranslatedLine>>(emptyList()) }

    // Selected source lines
    var selectedSource by remember { mutableStateOf("en") } // "en" or "fa"
    val sourceList = if (selectedSource == "en") subEnList else subFaList

    // Chat state
    var chatInput by remember { mutableStateOf("") }
    var chatMessages by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isChatLoading by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }

    // Navigation index
    var currentLineIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        // ── Source Selection + Controls ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Source language toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedSource == "en",
                        onClick = { selectedSource = "en" },
                        label = { Text(strings.subEnChip) },
                        leadingIcon = if (selectedSource == "en") {
                            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp)) }
                        } else null,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedSource == "fa",
                        onClick = { selectedSource = "fa" },
                        label = { Text(strings.subFaChip) },
                        leadingIcon = if (selectedSource == "fa") {
                            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp)) }
                        } else null,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Lines per batch control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        strings.linesPerRequestLabel(isDefaultMode, linesPerBatch),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = isDefaultMode,
                            onClick = {
                                isDefaultMode = true
                                linesPerBatch = 0
                                sharedPrefs.edit().putInt("lines_per_batch", 0).apply()
                            },
                            label = { Text(strings.defaultChip, fontSize = 11.sp) }
                        )
                        listOf(1, 10, 50, 100).forEach { n ->
                            FilterChip(
                                selected = !isDefaultMode && linesPerBatch == n,
                                onClick = {
                                    isDefaultMode = false
                                    linesPerBatch = n
                                    batchOffset = 0
                                    sharedPrefs.edit().putInt("lines_per_batch", n).apply()
                                },
                                label = { Text("$n", fontSize = 12.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Translate from current position (with batch pagination)
                    Button(
                        onClick = {
                            if (apiKey.isBlank()) {
                                errorMessage = strings.apiKeySetError
                                showSettings = true
                                return@Button
                            }
                            scope.launch {
                                isTranslating = true
                                errorMessage = null
                                try {
                                    val config = AiService.TranslationConfig(apiKey, baseUrl, model)

                                    val effectiveBatchSize = if (isDefaultMode) sourceList.size else linesPerBatch
                                    val startIdx = if (isDefaultMode) 0 else batchOffset
                                    val endIdx = (startIdx + effectiveBatchSize).coerceAtMost(sourceList.size)

                                    if (startIdx >= sourceList.size) {
                                        errorMessage = strings.noLinesLeftError
                                        isTranslating = false
                                        return@launch
                                    }

                                    val texts = sourceList.subList(startIdx, endIdx).map { it.text }
                                    val times = sourceList.subList(startIdx, endIdx).map { Pair(it.start, it.end) }

                                    translationProgress = strings.translatingLinesProgress(startIdx + 1, endIdx, sourceList.size)

                                    val effectiveLinesPerBatch = if (isDefaultMode) sourceList.size else linesPerBatch
                                    val result = AiService.translateSubtitles(
                                        config = config,
                                        sourceTexts = texts,
                                        sourceTimes = times,
                                        targetLang = targetLang,
                                        linesPerBatch = effectiveLinesPerBatch,
                                        context = context
                                    )

                                    result.fold(
                                        onSuccess = { translationResult ->
                                            translatedLines = translationResult.translatedLines
                                            currentLineIndex = startIdx
                                            if (!isDefaultMode) {
                                                batchOffset = endIdx
                                            }
                                        },
                                        onFailure = { e ->
                                            errorMessage = strings.errorWithMessage(e.message)
                                        }
                                    )
                                } catch (e: Exception) {
                                    errorMessage = strings.errorWithMessage(e.message)
                                } finally {
                                    isTranslating = false
                                    translationProgress = ""
                                }
                            }
                        },
                        enabled = !isTranslating && sourceList.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (isTranslating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.translateBtn)
                    }

                    // Translate all (same as default + reset)
                    OutlinedButton(
                        onClick = {
                            if (apiKey.isBlank()) {
                                errorMessage = strings.apiKeySetError
                                showSettings = true
                                return@OutlinedButton
                            }
                            scope.launch {
                                isTranslating = true
                                errorMessage = null
                                currentLineIndex = 0
                                batchOffset = 0
                                try {
                                    val config = AiService.TranslationConfig(apiKey, baseUrl, model)
                                    val texts = sourceList.map { it.text }
                                    val times = sourceList.map { Pair(it.start, it.end) }

                                    translationProgress = strings.translatingAllProgress(sourceList.size)

                                    val result = AiService.translateSubtitles(
                                        config = config,
                                        sourceTexts = texts,
                                        sourceTimes = times,
                                        targetLang = targetLang,
                                        linesPerBatch = sourceList.size,
                                        context = context
                                    )

                                    result.fold(
                                        onSuccess = { translatedLines = it.translatedLines },
                                        onFailure = { e -> errorMessage = strings.errorWithMessage(e.message) }
                                    )
                                } catch (e: Exception) {
                                    errorMessage = strings.errorWithMessage(e.message)
                                } finally {
                                    isTranslating = false
                                    translationProgress = ""
                                }
                            }
                        },
                        enabled = !isTranslating && sourceList.isNotEmpty(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.allBtn)
                    }
                }

                // Progress text
                if (translationProgress.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        translationProgress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Error message
                errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // ── Navigation bar + Batch pagination ──
        if (translatedLines.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                currentLineIndex = (currentLineIndex - linesPerBatch).coerceAtLeast(0)
                            },
                            enabled = currentLineIndex > 0
                        ) {
                            Icon(Icons.Filled.SkipPrevious, strings.prevLineCd)
                        }

                        Text(
                            "${currentLineIndex + 1} / ${sourceList.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = {
                                currentLineIndex = (currentLineIndex + linesPerBatch).coerceAtMost(
                                    (sourceList.size - 1).coerceAtLeast(0)
                                )
                            },
                            enabled = currentLineIndex < sourceList.size - 1
                        ) {
                            Icon(Icons.Filled.SkipNext, strings.nextLineCd)
                        }
                    }

                    // Batch info + next batch button (only in non-default mode)
                    if (!isDefaultMode && linesPerBatch > 0 && !isTranslating) {
                        val currentBatch = (batchOffset / linesPerBatch) + 1
                        val totalBatches = (sourceList.size + linesPerBatch - 1) / linesPerBatch
                        val hasMoreBatches = batchOffset < sourceList.size

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                strings.batchInfo(currentBatch, totalBatches, batchOffset + 1, (batchOffset + linesPerBatch).coerceAtMost(sourceList.size)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )

                            if (hasMoreBatches) {
                                TextButton(
                                    onClick = {
                                        // Trigger next batch translation
                                        scope.launch {
                                            isTranslating = true
                                            errorMessage = null
                                            try {
                                                val config = AiService.TranslationConfig(apiKey, baseUrl, model)
                                                val startIdx = batchOffset
                                                val endIdx = (startIdx + linesPerBatch).coerceAtMost(sourceList.size)
                                                val texts = sourceList.subList(startIdx, endIdx).map { it.text }
                                                val times = sourceList.subList(startIdx, endIdx).map { Pair(it.start, it.end) }

                                                translationProgress = strings.translatingLinesProgress(startIdx + 1, endIdx, sourceList.size)

                                                val result = AiService.translateSubtitles(
                                                    config = config,
                                                    sourceTexts = texts,
                                                    sourceTimes = times,
                                                    targetLang = targetLang,
                                                    linesPerBatch = linesPerBatch,
                                                    context = context
                                                )

                                                result.fold(
                                                    onSuccess = { translationResult ->
                                                        translatedLines = translationResult.translatedLines
                                                        currentLineIndex = startIdx
                                                        batchOffset = endIdx
                                                    },
                                                    onFailure = { e -> errorMessage = strings.errorWithMessage(e.message) }
                                                )
                                            } catch (e: Exception) {
                                                errorMessage = strings.errorWithMessage(e.message)
                                            } finally {
                                                isTranslating = false
                                                translationProgress = ""
                                            }
                                        }
                                    }
                                ) {
                                    Text(strings.nextBatchBtn, style = MaterialTheme.typography.labelMedium)
                                    Icon(Icons.Filled.SkipNext, null, Modifier.size(16.dp))
                                }
                            } else {
                                Text(
                                    strings.allDoneLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Results List ──
        if (translatedLines.isNotEmpty()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(translatedLines) { index, line ->
                    TranslationLineCard(
                        line = line,
                        isHighlighted = line.originalIndex == currentLineIndex,
                        onClick = {
                            currentLineIndex = line.originalIndex
                        }
                    )
                }
            }
        } else if (sourceList.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Language,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        strings.noSubtitleLoadedTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        strings.noSubtitleLoadedHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            // Source list preview when no translation yet
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(sourceList.take(20)) { index, sub ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (index == currentLineIndex)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        ),
                        onClick = { currentLineIndex = index }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    sub.text,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textAlign = TextAlign.Start,
                                        textDirection = sub.text.autoTextDirection()
                                    ),
                                    fontSize = 13.sp
                                )
                                Text(
                                    "${formatSeconds(sub.start)} - ${formatSeconds(sub.end)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
                if (sourceList.size > 20) {
                    item {
                        Text(
                            strings.moreLinesLabel(sourceList.size - 20),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }

        // ── Bottom: Chat toggle + Settings ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Chat input
            OutlinedTextField(
                value = chatInput,
                onValueChange = { chatInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(strings.askAboutSubtitlePlaceholder, fontSize = 13.sp) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                // Typed text follows its own direction: Persian input
                // right-aligns, English input left-aligns.
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    textAlign = TextAlign.Start,
                    textDirection = chatInput.autoTextDirection()
                )
            )

            IconButton(
                onClick = {
                    if (chatInput.isBlank() || apiKey.isBlank()) return@IconButton
                    scope.launch {
                        isChatLoading = true
                        val config = AiService.TranslationConfig(apiKey, baseUrl, model)

                        // Build context from current subtitles
                        val contextText = buildString {
                            appendLine("Current source subtitles (first 20 lines):")
                            sourceList.take(20).forEachIndexed { i, s ->
                                appendLine("[$i] ${s.text}")
                            }
                            if (translatedLines.isNotEmpty()) {
                                appendLine("\nTranslated lines:")
                                translatedLines.take(20).forEach { t ->
                                    appendLine("[${t.originalIndex}] ${t.originalText} → ${t.translatedText}")
                                }
                            }
                            if (!dictionaryHtml.isNullOrBlank()) {
                                appendLine("\nDictionary context: ${dictionaryHtml.take(500)}")
                            }
                        }

                        val userMsg = "$contextText\n\nQuestion: $chatInput"
                        val newMessages = chatMessages + listOf("user" to userMsg)

                        // Build system prompt with memory context
                        val systemPrompt = buildString {
                            appendLine(AiMemoryManager.getPrompt(context, AiMemoryManager.PROMPT_CHAT))
                            val memCtx = AiMemoryManager.buildFullContext(context)
                            if (memCtx.isNotBlank()) {
                                appendLine()
                                append(memCtx)
                            }
                        }

                        val result = AiService.chat(config, newMessages, systemPrompt, context)
                        result.fold(
                            onSuccess = { response ->
                                chatMessages = newMessages + ("assistant" to response)
                                chatInput = ""
                            },
                            onFailure = { e ->
                                chatMessages = chatMessages + ("assistant" to strings.errorWithMessage(e.message))
                            }
                        )
                        isChatLoading = false
                    }
                },
                enabled = chatInput.isNotBlank() && !isChatLoading,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = strings.sendCd,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            // Settings button
            IconButton(onClick = { showSettings = !showSettings }) {
                Icon(Icons.Filled.Settings, contentDescription = strings.agentSettingsCd)
            }
        }

        // ── Chat messages ──
        AnimatedVisibility(visible = chatMessages.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .padding(bottom = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                LazyColumn(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(count = chatMessages.size) { idx ->
                        val (role, content) = chatMessages[idx]
                        val isUser = role == "user"
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isUser)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = content,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    textAlign = TextAlign.Start,
                                    textDirection = content.autoTextDirection()
                                ),
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (isChatLoading) {
                        item {
                            Row(modifier = Modifier.padding(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.translationProcessing, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        // ── Settings Dialog ──
        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text(strings.translationSettingsTitle) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text(strings.apiKeyLabel) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = { Text(strings.baseUrlLabel) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = { Text(strings.modelLabel) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Text("${strings.targetLangLabel}:", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("fa" to strings.langFa, "ar" to strings.langAr, "tr" to strings.langTr, "fr" to strings.langFr).forEach { (code, name) ->
                                FilterChip(
                                    selected = targetLang == code,
                                    onClick = { targetLang = code },
                                    label = { Text(name, fontSize = 12.sp) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("de" to strings.langDe, "es" to strings.langEs, "ja" to strings.langJa, "ko" to strings.langKo).forEach { (code, name) ->
                                FilterChip(
                                    selected = targetLang == code,
                                    onClick = { targetLang = code },
                                    label = { Text(name, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        sharedPrefs.edit()
                            .putString("api_key", apiKey)
                            .putString("base_url", baseUrl)
                            .putString("model", model)
                            .putString("target_lang", targetLang)
                            .apply()
                        showSettings = false
                    }) {
                        Text(strings.save)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSettings = false }) {
                        Text(strings.cancel)
                    }
                }
            )
        }
    }
}

@Composable
fun TranslationLineCard(
    line: AiService.TranslatedLine,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isHighlighted) 4.dp else 1.dp
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Timestamp + index
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (line.start != null && line.end != null) {
                    Text(
                        "${formatSeconds(line.start)} → ${formatSeconds(line.end)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
                Text(
                    "#${line.originalIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Original text (auto RTL/LTR: source subtitle may be FA or EN)
            Text(
                text = line.originalText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textAlign = TextAlign.Start,
                    textDirection = line.originalText.autoTextDirection()
                ),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Translated text (auto RTL/LTR based on the AI output)
            Text(
                text = line.translatedText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textAlign = TextAlign.Start,
                    textDirection = line.translatedText.autoTextDirection()
                ),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                lineHeight = 20.sp,
                fontFamily = FontFamily.Default
            )
        }
    }
}

private fun formatSeconds(seconds: Double): String {
    val mins = (seconds / 60).toInt()
    val secs = (seconds % 60).toInt()
    return "%d:%02d".format(mins, secs)
}
