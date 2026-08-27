package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logic.AiMemoryManager
import com.example.logic.AiService
import com.example.logic.ChatHistoryManager
import com.example.logic.ChatMessage
import com.example.logic.ChatSession
import com.example.model.SubtitleEntry
import com.example.ui.theme.AppStrings
import com.example.ui.theme.MessageColorState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

/**
 * Agent — AI chat tab using 9Router API.
 * Translates and edits subtitles, reads/modifies imported files.
 * Supports: runtime prompt editing, AI memory, chat history, file content access,
 *           target language selection, copy text, SRT timestamp context,
 *           learning from subtitle pairs and dictionary, stop/cancel AI operations.
 */
@Composable
fun AgentScreen(
    subEnList: List<SubtitleEntry>,
    subFaList: List<SubtitleEntry>,
    subEnFileName: String = "",
    subFaFileName: String = "",
    readerText: String = "",
    readerFileName: String = "",
    onUpdateSubFa: (List<SubtitleEntry>) -> Unit = {},
    onUpdateReaderText: (String) -> Unit = {},
    onLearnFromSubtitles: () -> Unit = {},
    onLearnFromDictionary: () -> Unit = {},
    isLearning: Boolean = false,
    learnResult: String? = null,
    learnProgress: Pair<Int, Int> = 0 to 0,
    onStopLearning: () -> Unit = {},
    viewModel: AppViewModel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val appLanguage by (viewModel?.appLanguage?.collectAsState() ?: remember { mutableStateOf(com.example.ui.theme.AppLanguage.FA) })
    val strings = remember(appLanguage) { AppStrings(appLanguage) }

    val sharedPrefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
    var apiKey by remember { mutableStateOf(sharedPrefs.getString("api_key", "") ?: "") }
    var baseUrl by remember { mutableStateOf(sharedPrefs.getString("base_url", "http://localhost:20128/v1") ?: "http://localhost:20128/v1") }
    var model by remember { mutableStateOf(sharedPrefs.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini") }
    var targetLang by remember { mutableStateOf(sharedPrefs.getString("target_lang", "فارسی") ?: "فارسی") }
    var showSettings by remember { mutableStateOf(false) }

    var showMemory by remember { mutableStateOf(false) }
    var memorySummary by remember { mutableStateOf("") }
    var refreshMemory by remember { mutableStateOf(0) }

    var workingSubFa by remember { mutableStateOf(subFaList) }
    LaunchedEffect(subFaList) { workingSubFa = subFaList }

    var chatInput by remember { mutableStateOf("") }
    var chatMessages by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isThinking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var aiJob by remember { mutableStateOf<Job?>(null) }
    val chatListState = rememberLazyListState()

    var currentSession by remember { mutableStateOf(ChatHistoryManager.createNewSession()) }
    var showChatHistory by remember { mutableStateOf(false) }
    var chatHistoryList by remember { mutableStateOf<List<ChatSession>>(emptyList()) }

    LaunchedEffect(Unit) {
        val latest = ChatHistoryManager.getLatestSession(context)
        if (latest != null) {
            currentSession = latest
            chatMessages = latest.messages.map { Pair(it.role, it.content) }
        }
    }

    val onMessagesWithSave: (List<Pair<String, String>>) -> Unit = { newMessages ->
        chatMessages = newMessages
        currentSession.messages.clear()
        currentSession.messages.addAll(newMessages.map { ChatMessage(it.first, it.second, System.currentTimeMillis()) })
        if (currentSession.title == "چت جدید" && newMessages.isNotEmpty()) {
            currentSession.title = ChatHistoryManager.autoGenerateTitle(currentSession)
        }
        ChatHistoryManager.saveSession(context, currentSession)
    }

    LaunchedEffect(refreshMemory) {
        memorySummary = AiMemoryManager.getMemorySummary(context)
    }

    fun stopAi() {
        aiJob?.cancel()
        aiJob = null
        isThinking = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Language, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Agent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { currentSession = ChatHistoryManager.createNewSession(); chatMessages = emptyList() }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Add, strings.agentNewChatCd, modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = { chatHistoryList = ChatHistoryManager.listSessions(context); showChatHistory = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.History, strings.agentHistoryCd, modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = { showMemory = !showMemory; refreshMemory++ }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Psychology, strings.agentMemoryCd, modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = { showSettings = !showSettings }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Settings, strings.agentSettingsCd, modifier = Modifier.size(18.dp)) }
                }
                if (chatMessages.isNotEmpty()) { Spacer(modifier = Modifier.height(2.dp)); Text(currentSession.title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (subEnList.isNotEmpty()) AssistChip(onClick = {}, label = { Text("EN: ${subEnList.size}", fontSize = 10.sp) }, modifier = Modifier.height(24.dp))
                    if (workingSubFa.isNotEmpty()) AssistChip(onClick = {}, label = { Text("FA: ${workingSubFa.size}", fontSize = 10.sp) }, modifier = Modifier.height(24.dp))
                    if (readerText.isNotEmpty()) AssistChip(onClick = {}, label = { Text("📄 ${readerFileName.take(15)}", fontSize = 10.sp) }, modifier = Modifier.height(24.dp))
                    if (subEnList.isEmpty() && workingSubFa.isEmpty() && readerText.isEmpty()) Text(strings.agentNoFileLoaded, fontSize = 10.sp, color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
                if (AiMemoryManager.hasMemoryData(context)) { Spacer(modifier = Modifier.height(2.dp)); AssistChip(onClick = { showMemory = true; refreshMemory++ }, label = { Text(strings.agentMemoryActive, fontSize = 9.sp) }, modifier = Modifier.height(20.dp)) }
            }
        }

        if (showChatHistory) {
            ChatHistoryDialog(sessions = chatHistoryList, strings = strings, onOpenSession = { sessionId -> val session = ChatHistoryManager.loadSession(context, sessionId); if (session != null) { currentSession = session; chatMessages = session.messages.map { Pair(it.role, it.content) } }; showChatHistory = false }, onDeleteSession = { sessionId -> ChatHistoryManager.deleteSession(context, sessionId); chatHistoryList = ChatHistoryManager.listSessions(context) }, onNewSession = { currentSession = ChatHistoryManager.createNewSession(); chatMessages = emptyList(); showChatHistory = false }, onDismiss = { showChatHistory = false })
        }

        AnimatedVisibility(visible = showMemory) { MemoryPanel(context = context, strings = strings, onRefresh = { refreshMemory++ }) }

        AnimatedVisibility(visible = showSettings) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it; sharedPrefs.edit().putString("api_key", it).apply() }, label = { Text(strings.apiKeyLabel) }, modifier = Modifier.fillMaxWidth().height(56.dp), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it; sharedPrefs.edit().putString("base_url", it).apply() }, label = { Text(strings.baseUrlLabel) }, modifier = Modifier.fillMaxWidth().height(56.dp), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = model, onValueChange = { model = it; sharedPrefs.edit().putString("model", it).apply() }, label = { Text(strings.modelLabel) }, modifier = Modifier.fillMaxWidth().height(56.dp), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = targetLang, onValueChange = { targetLang = it; sharedPrefs.edit().putString("target_lang", it).apply() }, label = { Text(strings.targetLangLabel) }, modifier = Modifier.fillMaxWidth().height(56.dp), singleLine = true, textStyle = MaterialTheme.typography.bodySmall, placeholder = { Text(strings.targetLangPlaceholder, fontSize = 12.sp) })
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(strings.bubbleColorTitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(strings.bubbleColorHint, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    val chatColorOptions = remember { listOf<Color?>(null, Color(0xFF3D6E63), Color(0xFFFFB300), Color(0xFF26A69A), Color(0xFFEF5350), Color(0xFF5C6BC0), Color(0xFFE91E8C), Color(0xFF8D6E63), Color(0xFF42A5F5)) }
                    Text(strings.sentMessagesLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        chatColorOptions.forEach { swatch ->
                            val isSelected = MessageColorState.sentColor == swatch
                            if (swatch == null) {
                                Box(modifier = Modifier.size(26.dp).clip(RoundedCornerShape(100.dp)).background(MaterialTheme.colorScheme.primaryContainer).border(width = if (isSelected) 2.dp else 1.dp, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), shape = RoundedCornerShape(100.dp)).clickable { MessageColorState.sentColor = null; sharedPrefs.edit().remove("chat_sent_color").apply() }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Close, contentDescription = strings.defaultCd, modifier = Modifier.size(13.dp)) }
                            } else {
                                Box(modifier = Modifier.size(26.dp).clip(RoundedCornerShape(100.dp)).background(swatch).border(width = if (isSelected) 2.dp else 1.dp, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), shape = RoundedCornerShape(100.dp)).clickable { MessageColorState.sentColor = swatch; sharedPrefs.edit().putInt("chat_sent_color", swatch.toArgb()).apply() })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(strings.receivedMessagesLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        chatColorOptions.forEach { swatch ->
                            val isSelected = MessageColorState.receivedColor == swatch
                            if (swatch == null) {
                                Box(modifier = Modifier.size(26.dp).clip(RoundedCornerShape(100.dp)).background(MaterialTheme.colorScheme.secondaryContainer).border(width = if (isSelected) 2.dp else 1.dp, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), shape = RoundedCornerShape(100.dp)).clickable { MessageColorState.receivedColor = null; sharedPrefs.edit().remove("chat_received_color").apply() }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Close, contentDescription = strings.defaultCd, modifier = Modifier.size(13.dp)) }
                            } else {
                                Box(modifier = Modifier.size(26.dp).clip(RoundedCornerShape(100.dp)).background(swatch).border(width = if (isSelected) 2.dp else 1.dp, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), shape = RoundedCornerShape(100.dp)).clickable { MessageColorState.receivedColor = swatch; sharedPrefs.edit().putInt("chat_received_color", swatch.toArgb()).apply() })
                            }
                        }
                    }
                }
            }
        }

        if (subEnList.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { onLearnFromSubtitles() }, enabled = !isLearning, modifier = Modifier.weight(1f).height(36.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(strings.learnFromSubtitlesBtn, fontSize = 10.sp)
                }
                Button(onClick = { onLearnFromDictionary() }, enabled = !isLearning, modifier = Modifier.weight(1f).height(36.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(strings.learnFromDictionaryBtn, fontSize = 10.sp)
                }
                if (isLearning) {
                    IconButton(onClick = { onStopLearning() }, modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f), RoundedCornerShape(8.dp))) {
                        Icon(Icons.Filled.Close, strings.stopCd, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (isLearning && learnProgress.second > 0) {
                val percent = (learnProgress.first * 100 / learnProgress.second)
                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                    LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp))
                    Text("$percent% (${learnProgress.first}/${learnProgress.second})", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            learnResult?.let { Text(it, color = if (it.startsWith("خطا") || it.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 2.dp)) }
        }

        LazyColumn(state = chatListState, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(count = chatMessages.size) { index ->
                val (role, content) = chatMessages[index]
                val isUser = role == "user"
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
                    Surface(shape = RoundedCornerShape(16.dp), color = if (isUser) (MessageColorState.sentColor ?: MaterialTheme.colorScheme.primaryContainer) else (MessageColorState.receivedColor ?: MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.widthIn(max = 320.dp)) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (!isUser) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Language, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(4.dp)); Text("Agent", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }; Spacer(modifier = Modifier.height(2.dp)) }
                            SelectionContainer { Text(content, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Content), fontSize = 13.sp) }
                        }
                    }
                }
            }
            if (isThinking) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.agentThinking, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 2.dp)) }

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = chatInput, onValueChange = { chatInput = it }, modifier = Modifier.weight(1f), placeholder = { Text(strings.askAgentPlaceholder, fontSize = 13.sp) }, singleLine = false, maxLines = 3, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (chatInput.isNotBlank() && !isThinking) { val msg = chatInput; chatInput = ""; focusManager.clearFocus(); aiJob = scope.launch { sendChat(context, msg, apiKey, baseUrl, model, targetLang, subEnList, workingSubFa, readerText, readerFileName, chatMessages, onMessagesWithSave, { isThinking = it }, { errorMessage = it }, chatListState, { workingSubFa = it; onUpdateSubFa(it) }, onUpdateReaderText, strings) } } }), shape = RoundedCornerShape(24.dp), textStyle = MaterialTheme.typography.bodyMedium)
            if (isThinking) {
                IconButton(onClick = { stopAi() }, modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f), RoundedCornerShape(12.dp))) {
                    Icon(Icons.Filled.Close, strings.stopCd, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                }
            } else {
                FilledIconButton(onClick = { if (chatInput.isNotBlank()) { val msg = chatInput; chatInput = ""; focusManager.clearFocus(); aiJob = scope.launch { sendChat(context, msg, apiKey, baseUrl, model, targetLang, subEnList, workingSubFa, readerText, readerFileName, chatMessages, onMessagesWithSave, { isThinking = it }, { errorMessage = it }, chatListState, { workingSubFa = it; onUpdateSubFa(it) }, onUpdateReaderText, strings) } } }, enabled = chatInput.isNotBlank()) { Icon(Icons.Filled.Send, strings.sendCd) }
            }
        }
    }
}

@Composable
private fun ChatHistoryDialog(sessions: List<ChatSession>, strings: AppStrings, onOpenSession: (String) -> Unit, onDeleteSession: (String) -> Unit, onNewSession: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { Text(strings.chatHistoryTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold); TextButton(onClick = onNewSession) { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text(strings.agentNewChatCd, fontSize = 12.sp) } } }, text = { if (sessions.isEmpty()) { Text(strings.noChatsSaved, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } else { LazyColumn(modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { items(sessions) { session -> Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Column(modifier = Modifier.weight(1f)) { Text(session.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1); Text(ChatHistoryManager.formatTimestamp(session.updatedAt), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Row { IconButton(onClick = { onOpenSession(session.id) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.FolderOpen, strings.openCd, Modifier.size(18.dp)) }; IconButton(onClick = { onDeleteSession(session.id) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Delete, strings.deleteCd, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) } } } } } } } }, confirmButton = { TextButton(onClick = onDismiss) { Text(strings.close) } })
}

@Composable
private fun MemoryPanel(context: Context, strings: AppStrings, onRefresh: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(strings.memoryTabPrompts, strings.memoryTabCorrections, strings.memoryTabSkills, strings.memoryTabExportImport)
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { tabs.forEachIndexed { idx, label -> FilterChip(selected = selectedTab == idx, onClick = { selectedTab = idx }, label = { Text(label, fontSize = 10.sp) }, modifier = Modifier.weight(1f)) } }
            Spacer(modifier = Modifier.height(8.dp))
            when (selectedTab) { 0 -> PromptsTab(context, strings, onRefresh); 1 -> CorrectionsTab(context, strings, onRefresh); 2 -> SkillsTab(context, strings, onRefresh); 3 -> ExportImportTab(context, strings) }
        }
    }
}

@Composable
private fun PromptsTab(context: Context, strings: AppStrings, onRefresh: () -> Unit) {
    val promptKeys = listOf(AiMemoryManager.PROMPT_TRANSLATE to strings.promptTranslate, AiMemoryManager.PROMPT_CHAT to strings.promptChat, AiMemoryManager.PROMPT_AGENT to strings.promptAgent)
    var editingKey by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    if (editingKey == null) {
        LazyColumn(modifier = Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(promptKeys) { (key, label) ->
                val isCustom = AiMemoryManager.hasCustomPrompt(context, key)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium); Text(if (isCustom) strings.customBadge else strings.presetDefault, fontSize = 9.sp, color = if (isCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { editingKey = key; editText = AiMemoryManager.getPrompt(context, key) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(strings.editBtn, fontSize = 10.sp) }
                        if (isCustom) { TextButton(onClick = { AiMemoryManager.resetPrompt(context, key); onRefresh() }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(strings.resetBtn, fontSize = 10.sp, color = MaterialTheme.colorScheme.error) } }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    } else {
        Column {
            Text(strings.editingPromptTitle(promptKeys.find { it.first == editingKey }?.second ?: ""), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(value = editText, onValueChange = { editText = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 250.dp), textStyle = MaterialTheme.typography.bodySmall, placeholder = { Text(strings.promptTextPlaceholder, fontSize = 11.sp) })
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { AiMemoryManager.savePrompt(context, editingKey!!, editText); editingKey = null; onRefresh() }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text(strings.save, fontSize = 11.sp) }
                OutlinedButton(onClick = { editingKey = null }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text(strings.cancel, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun CorrectionsTab(context: Context, strings: AppStrings, onRefresh: () -> Unit) {
    val corrections = remember { AiMemoryManager.loadCorrections(context) }
    var showAddDialog by remember { mutableStateOf(false) }
    if (corrections.isEmpty()) { Text(strings.noCorrectionsSaved, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(modifier = Modifier.height(8.dp)) }
    else { Text(strings.correctionsSavedCount(corrections.size), fontSize = 11.sp, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(4.dp)); LazyColumn(modifier = Modifier.heightIn(max = 250.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { items(corrections) { corr -> Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(modifier = Modifier.padding(8.dp)) { Text(strings.sourceLabel(corr.sourceText.take(60)), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("❌ ${corr.wrongTranslation.take(60)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.error); Text("✅ ${corr.correctTranslation.take(60)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary); Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { AiMemoryManager.removeCorrection(context, corr.id); onRefresh() }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(strings.deleteCd, fontSize = 10.sp, color = MaterialTheme.colorScheme.error) } } } } } } }
    Spacer(modifier = Modifier.height(4.dp))
    TextButton(onClick = { showAddDialog = true }) { Text(strings.addManualCorrection, fontSize = 11.sp) }
    if (showAddDialog) {
        var srcText by remember { mutableStateOf("") }; var wrongText by remember { mutableStateOf("") }; var correctText by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showAddDialog = false }, title = { Text(strings.addCorrectionTitle, fontSize = 14.sp) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = srcText, onValueChange = { srcText = it }, label = { Text(strings.sourceTextLabel, fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall); OutlinedTextField(value = wrongText, onValueChange = { wrongText = it }, label = { Text(strings.wrongTranslationLabel, fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall); OutlinedTextField(value = correctText, onValueChange = { correctText = it }, label = { Text(strings.correctTranslationLabel, fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall) } }, confirmButton = { TextButton(onClick = { if (srcText.isNotBlank() && correctText.isNotBlank()) { AiMemoryManager.addCorrection(context, srcText, wrongText, correctText); showAddDialog = false; onRefresh() } }) { Text(strings.save, fontSize = 12.sp) } }, dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text(strings.cancel, fontSize = 12.sp) } })
    }
}

@Composable
private fun SkillsTab(context: Context, strings: AppStrings, onRefresh: () -> Unit) {
    val skills = remember { AiMemoryManager.loadSkills(context) }
    var showAddDialog by remember { mutableStateOf(false) }
    if (skills.isEmpty()) { Text(strings.noSkillsSaved, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(modifier = Modifier.height(8.dp)) }
    else { Text(strings.skillsSavedCount(skills.size), fontSize = 11.sp, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(4.dp)); LazyColumn(modifier = Modifier.heightIn(max = 250.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { items(skills) { skill -> Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) { Column(modifier = Modifier.weight(1f)) { Text("[${skill.category}]", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text(skill.content, fontSize = 11.sp) }; TextButton(onClick = { AiMemoryManager.removeSkill(context, skill.id); onRefresh() }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(strings.deleteCd, fontSize = 10.sp, color = MaterialTheme.colorScheme.error) } } } } } }
    Spacer(modifier = Modifier.height(4.dp))
    TextButton(onClick = { showAddDialog = true }) { Text(strings.addSkillNote, fontSize = 11.sp) }
    if (showAddDialog) {
        var content by remember { mutableStateOf("") }; var category by remember { mutableStateOf("user_note") }
        AlertDialog(onDismissRequest = { showAddDialog = false }, title = { Text(strings.addSkillTitle, fontSize = 14.sp) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text(strings.textLabel, fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("user_note" to strings.categoryUserNote, "translation_rule" to strings.categoryTranslationRule, "skill" to strings.categorySkill, "dictionary_tip" to strings.categoryDictionaryTip).forEach { (cat, label) -> FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(label, fontSize = 10.sp) }) } } } }, confirmButton = { TextButton(onClick = { if (content.isNotBlank()) { AiMemoryManager.addSkill(context, category, content); showAddDialog = false; onRefresh() } }) { Text(strings.save, fontSize = 12.sp) } }, dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text(strings.cancel, fontSize = 12.sp) } })
    }
}

@Composable
private fun ExportImportTab(context: Context, strings: AppStrings) {
    var importMode by remember { mutableStateOf("merge") }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                val tempFile = File(context.cacheDir, "ai_memory_import.json")
                context.contentResolver.openInputStream(it)?.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
                val count = AiMemoryManager.importFromFile(context, tempFile, importMode)
                Toast.makeText(context, "$count ($importMode)", Toast.LENGTH_LONG).show()
                tempFile.delete()
            } catch (e: Exception) { Toast.makeText(context, strings.errorWithMessage(e.message), Toast.LENGTH_LONG).show() }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(strings.exportImportTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(strings.exportImportDesc, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = {
            try {
                val tempFile = File(context.cacheDir, "ai_memory_export.json")
                AiMemoryManager.exportToFile(context, tempFile)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "ai_memory_export.json")
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out -> tempFile.inputStream().use { inp -> inp.copyTo(out) } }
                        Toast.makeText(context, strings.savedAtPath("Downloads/ai_memory_export.json"), Toast.LENGTH_LONG).show()
                    } else { Toast.makeText(context, strings.downloadsCreateError, Toast.LENGTH_LONG).show() }
                } else {
                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    val destFile = File(downloadsDir, "ai_memory_export.json")
                    tempFile.copyTo(destFile, overwrite = true)
                    Toast.makeText(context, strings.savedAtPath(destFile.absolutePath), Toast.LENGTH_LONG).show()
                }
                tempFile.delete()
            } catch (e: Exception) { Toast.makeText(context, strings.errorWithMessage(e.message), Toast.LENGTH_LONG).show() }
        }, modifier = Modifier.fillMaxWidth().height(36.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Icon(Icons.Filled.Download, null, Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text(strings.exportToDownloadsBtn, fontSize = 11.sp) }
        HorizontalDivider()
        Text(strings.importFromFileLabel, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = importMode == "merge", onClick = { importMode = "merge" }, label = { Text("merge", fontSize = 10.sp) })
            FilterChip(selected = importMode == "replace", onClick = { importMode = "replace" }, label = { Text("replace", fontSize = 10.sp) })
        }
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.fillMaxWidth().height(36.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Icon(Icons.Filled.Upload, null, Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text(strings.selectFileImportBtn, fontSize = 11.sp) }
        HorizontalDivider()
        TextButton(onClick = { AiMemoryManager.clearAll(context); Toast.makeText(context, strings.close, Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(strings.clearAllMemoryBtn, fontSize = 11.sp) }
        Spacer(modifier = Modifier.height(4.dp))
        Text(AiMemoryManager.getMemorySummary(context), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
    }
}

private suspend fun sendChat(context: Context, input: String, apiKey: String, baseUrl: String, model: String, targetLang: String, subEnList: List<SubtitleEntry>, workingSubFa: List<SubtitleEntry>, readerText: String, readerFileName: String, currentMessages: List<Pair<String, String>>, onMessages: (List<Pair<String, String>>) -> Unit, onThinking: (Boolean) -> Unit, onError: (String?) -> Unit, listState: androidx.compose.foundation.lazy.LazyListState, onUpdateSubFa: (List<SubtitleEntry>) -> Unit, onUpdateReaderText: (String) -> Unit, strings: AppStrings) {
    if (apiKey.isBlank()) { onError(strings.apiKeySetError); return }
    val userMessage = input.trim()
    onMessages(currentMessages + Pair("user", userMessage))
    onThinking(true); onError(null)
    try {
        val systemPrompt = buildSystemPrompt(context, subEnList, workingSubFa, readerText, readerFileName, targetLang)
        val result = AiService.chat(AiService.TranslationConfig(apiKey, baseUrl, model), currentMessages + Pair("user", userMessage), systemPrompt, context)
        result.fold(onSuccess = { response ->
            val changes = parseSubtitleChanges(response)
            val readerUpdate = parseReaderTextUpdate(response)
            var displayResponse = response
            if (changes != null) { val updated = workingSubFa.toMutableList(); changes.forEach { (idx, text) -> if (idx - 1 in updated.indices) updated[idx - 1] = updated[idx - 1].copy(text = text) }; onUpdateSubFa(updated); displayResponse = "✓ ${changes.size}\n$response" }
            if (readerUpdate != null) { onUpdateReaderText(readerUpdate); displayResponse = "✓\n$response" }
            onMessages(currentMessages + Pair("user", userMessage) + Pair("assistant", displayResponse))
        }, onFailure = { e -> onError(strings.errorWithMessage(e.message)); onMessages(currentMessages + Pair("user", userMessage)) })
    } catch (e: Exception) { onError(strings.errorWithMessage(e.message)); onMessages(currentMessages + Pair("user", userMessage)) }
    finally { onThinking(false); kotlinx.coroutines.delay(100); try { listState.animateScrollToItem(Int.MAX_VALUE) } catch (_: Exception) {} }
}

private fun formatSrtTime(seconds: Double): String {
    val totalMs = (seconds * 1000).toLong()
    val ms = totalMs % 1000
    val totalSec = totalMs / 1000
    val sec = totalSec % 60
    val totalMin = totalSec / 60
    val min = totalMin % 60
    val hour = totalMin / 60
    return String.format("%02d:%02d:%02d,%03d", hour, min, sec, ms)
}

private fun buildSystemPrompt(context: Context, enList: List<SubtitleEntry>, faList: List<SubtitleEntry>, readerText: String = "", readerFileName: String = "", targetLang: String = "فارسی"): String {
    val basePrompt = AiMemoryManager.getPrompt(context, AiMemoryManager.PROMPT_AGENT)
    val memoryContext = AiMemoryManager.buildFullContext(context)
    val sb = StringBuilder()
    sb.appendLine(basePrompt)
    sb.appendLine()
    sb.appendLine("Target language for translation: $targetLang")
    if (memoryContext.isNotBlank()) { sb.appendLine(); sb.append(memoryContext); sb.appendLine() }
    sb.appendLine()
    if (readerText.isNotBlank()) { sb.appendLine("=== Text file ($readerFileName) ==="); sb.appendLine(readerText); sb.appendLine(); sb.appendLine("To change the file's text, use this tag:"); sb.appendLine("[READER_UPDATE]new full text[/READER_UPDATE]"); sb.appendLine() }
    sb.appendLine("=== EN subtitle (${enList.size} lines) ===")
    enList.forEachIndexed { i, s -> sb.appendLine("[${i+1}] ${formatSrtTime(s.start)} → ${formatSrtTime(s.end)} | ${s.text}") }
    sb.appendLine()
    sb.appendLine("=== FA subtitle (${faList.size} lines) ===")
    faList.forEachIndexed { i, s -> sb.appendLine("[${i+1}] ${formatSrtTime(s.start)} → ${formatSrtTime(s.end)} | ${s.text}") }
    sb.appendLine()
    sb.appendLine("To change the subtitle, return a JSON array: [{\"index\": 1, \"text\": \"new text\"}]")
    return sb.toString()
}

private fun parseSubtitleChanges(response: String): List<Pair<Int, String>>? {
    val match = Regex("""\[[\s\S]*?\]""").find(response) ?: return null
    return try {
        val arr = JSONArray(match.value)
        (0 until arr.length()).mapNotNull { i -> val obj = arr.getJSONObject(i); if (obj.has("index") && obj.has("text")) Pair(obj.getInt("index"), obj.getString("text")) else null }.ifEmpty { null }
    } catch (e: Exception) { null }
}

private fun parseReaderTextUpdate(response: String): String? {
    val regex = Regex("""\[READER_UPDATE\]([\s\S]*?)\[/READER_UPDATE\]""")
    val match = regex.find(response) ?: return null
    return match.groupValues[1].trim().ifEmpty { null }
}
