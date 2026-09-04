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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logic.AiMemoryManager
import com.example.logic.AiService
import com.example.logic.ChatHistoryManager
import com.example.logic.ChatMessage
import com.example.logic.ChatSession
import com.example.logic.autoTextDirection
import com.example.model.SubtitleEntry
import com.example.ui.components.EmptyState
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.PillTone
import com.example.ui.components.SectionHeader
import com.example.ui.components.SegmentedPills
import com.example.ui.components.SoftIconButton
import com.example.ui.components.StatusPill
import com.example.ui.components.brandBrush
import com.example.ui.components.fadingEdges
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
 *
 * Layout note: the expandable panels (settings, memory) live in the same
 * column as the conversation, so they must carry their own bounded scroll.
 * Without it they were simply squeezed into whatever height was left and the
 * bottom half of the panel became unreachable.
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

    // Keep the newest message in view. The old code tried to scroll from
    // inside sendChat with animateScrollToItem(Int.MAX_VALUE), which always
    // threw and was swallowed, so the list simply never followed the chat.
    LaunchedEffect(chatMessages.size, isThinking) {
        if (chatMessages.isNotEmpty()) {
            try {
                chatListState.animateScrollToItem(chatMessages.size - 1)
            } catch (_: Exception) {
            }
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

    val sendCurrentInput = {
        if (chatInput.isNotBlank() && !isThinking) {
            val msg = chatInput
            chatInput = ""
            focusManager.clearFocus()
            aiJob = scope.launch {
                sendChat(
                    context, msg, apiKey, baseUrl, model, targetLang, subEnList, workingSubFa,
                    readerText, readerFileName, chatMessages, onMessagesWithSave,
                    { isThinking = it }, { errorMessage = it }, chatListState,
                    { workingSubFa = it; onUpdateSubFa(it) }, onUpdateReaderText, strings
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {

        // ── Agent header ──
        GlassCard(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            cornerRadius = 24.dp,
            contentPadding = PaddingValues(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(brandBrush()),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Agent",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (chatMessages.isNotEmpty()) {
                        Text(
                            text = currentSession.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                SoftIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = strings.agentNewChatCd,
                    onClick = {
                        currentSession = ChatHistoryManager.createNewSession()
                        chatMessages = emptyList()
                    },
                    size = 34.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
                SoftIconButton(
                    icon = Icons.Filled.History,
                    contentDescription = strings.agentHistoryCd,
                    onClick = {
                        chatHistoryList = ChatHistoryManager.listSessions(context)
                        showChatHistory = true
                    },
                    size = 34.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
                SoftIconButton(
                    icon = Icons.Filled.Psychology,
                    contentDescription = strings.agentMemoryCd,
                    onClick = { showMemory = !showMemory; refreshMemory++ },
                    tint = if (showMemory) MaterialTheme.colorScheme.primary else null,
                    size = 34.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
                SoftIconButton(
                    icon = Icons.Filled.Settings,
                    contentDescription = strings.agentSettingsCd,
                    onClick = { showSettings = !showSettings },
                    tint = if (showSettings) MaterialTheme.colorScheme.primary else null,
                    size = 34.dp
                )
            }

            // Which material the agent can actually see right now.
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                if (subEnList.isNotEmpty()) {
                    StatusPill(text = "EN · ${subEnList.size}", tone = PillTone.Accent)
                }
                if (workingSubFa.isNotEmpty()) {
                    StatusPill(text = "FA · ${workingSubFa.size}", tone = PillTone.Positive)
                }
                if (readerText.isNotEmpty()) {
                    StatusPill(text = readerFileName.take(18), tone = PillTone.Neutral)
                }
                if (AiMemoryManager.hasMemoryData(context)) {
                    StatusPill(text = strings.agentMemoryActive, tone = PillTone.Warning)
                }
                if (subEnList.isEmpty() && workingSubFa.isEmpty() && readerText.isEmpty()) {
                    StatusPill(text = strings.agentNoFileLoaded, tone = PillTone.Negative)
                }
            }
        }

        if (showChatHistory) {
            ChatHistoryDialog(sessions = chatHistoryList, strings = strings, onOpenSession = { sessionId -> val session = ChatHistoryManager.loadSession(context, sessionId); if (session != null) { currentSession = session; chatMessages = session.messages.map { Pair(it.role, it.content) } }; showChatHistory = false }, onDeleteSession = { sessionId -> ChatHistoryManager.deleteSession(context, sessionId); chatHistoryList = ChatHistoryManager.listSessions(context) }, onNewSession = { currentSession = ChatHistoryManager.createNewSession(); chatMessages = emptyList(); showChatHistory = false }, onDismiss = { showChatHistory = false })
        }

        AnimatedVisibility(visible = showMemory) {
            MemoryPanel(
                context = context,
                strings = strings,
                config = AiService.TranslationConfig(apiKey, baseUrl, model),
                targetLang = targetLang,
                sampleLines = subEnList.map { it.text }.filter { it.isNotBlank() }.take(3),
                onRefresh = { refreshMemory++ }
            )
        }

        // ── Connection & appearance settings ──
        AnimatedVisibility(visible = showSettings) {
            GlassCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                cornerRadius = 22.dp,
                contentPadding = PaddingValues(14.dp)
            ) {
                // Bounded + scrollable: the panel is taller than the space the
                // chat column can spare, so it needs its own scroll.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; sharedPrefs.edit().putString("api_key", it).apply() },
                        label = { Text(strings.apiKeyLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it; sharedPrefs.edit().putString("base_url", it).apply() },
                        label = { Text(strings.baseUrlLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it; sharedPrefs.edit().putString("model", it).apply() },
                            label = { Text(strings.modelLabel) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = targetLang,
                            onValueChange = { targetLang = it; sharedPrefs.edit().putString("target_lang", it).apply() },
                            label = { Text(strings.targetLangLabel) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            textStyle = MaterialTheme.typography.bodySmall,
                            placeholder = { Text(strings.targetLangPlaceholder, fontSize = 12.sp) }
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    SectionHeader(title = strings.bubbleColorTitle, subtitle = strings.bubbleColorHint)
                    Spacer(modifier = Modifier.height(10.dp))
                    val chatColorOptions = remember { listOf<Color?>(null, Color(0xFF3D6E63), Color(0xFFFFB300), Color(0xFF26A69A), Color(0xFFEF5350), Color(0xFF5C6BC0), Color(0xFFE91E8C), Color(0xFF8D6E63), Color(0xFF42A5F5)) }
                    Text(
                        text = strings.sentMessagesLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        chatColorOptions.forEach { swatch ->
                            BubbleSwatch(
                                color = swatch,
                                fallback = MaterialTheme.colorScheme.primaryContainer,
                                selected = MessageColorState.sentColor == swatch,
                                emptyLabel = strings.defaultCd,
                                onClick = {
                                    MessageColorState.sentColor = swatch
                                    if (swatch == null) sharedPrefs.edit().remove("chat_sent_color").apply()
                                    else sharedPrefs.edit().putInt("chat_sent_color", swatch.toArgb()).apply()
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = strings.receivedMessagesLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        chatColorOptions.forEach { swatch ->
                            BubbleSwatch(
                                color = swatch,
                                fallback = MaterialTheme.colorScheme.secondaryContainer,
                                selected = MessageColorState.receivedColor == swatch,
                                emptyLabel = strings.defaultCd,
                                onClick = {
                                    MessageColorState.receivedColor = swatch
                                    if (swatch == null) sharedPrefs.edit().remove("chat_received_color").apply()
                                    else sharedPrefs.edit().putInt("chat_received_color", swatch.toArgb()).apply()
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Batch learning actions ──
        if (subEnList.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GradientButton(
                    text = strings.learnFromSubtitlesBtn,
                    onClick = { onLearnFromSubtitles() },
                    modifier = Modifier.weight(1f),
                    enabled = !isLearning
                )
                GradientButton(
                    text = strings.learnFromDictionaryBtn,
                    onClick = { onLearnFromDictionary() },
                    modifier = Modifier.weight(1f),
                    enabled = !isLearning
                )
                if (isLearning) {
                    SoftIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = strings.stopCd,
                        onClick = { onStopLearning() },
                        tint = MaterialTheme.colorScheme.error,
                        size = 40.dp
                    )
                }
            }
            if (isLearning && learnProgress.second > 0) {
                val percent = (learnProgress.first * 100 / learnProgress.second)
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$percent%  (${learnProgress.first}/${learnProgress.second})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            learnResult?.let {
                Text(
                    text = it,
                    color = if (it.startsWith("خطا") || it.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall.copy(
                        textAlign = TextAlign.Start,
                        textDirection = it.autoTextDirection()
                    ),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        // ── Conversation ──
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (chatMessages.isEmpty() && !isThinking) {
                EmptyState(
                    icon = Icons.Filled.Language,
                    title = "Agent",
                    description = strings.askAgentPlaceholder,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = chatListState,
                    modifier = Modifier.fillMaxSize().fadingEdges(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    items(count = chatMessages.size) { index ->
                        val (role, content) = chatMessages[index]
                        ChatBubble(isUser = role == "user", content = content)
                    }
                    if (isThinking) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                Surface(
                                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp),
                                    color = MessageColorState.receivedColor ?: MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(strings.agentThinking, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        errorMessage?.let {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                contentColor = MaterialTheme.colorScheme.error,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        // ── Composer ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = chatInput,
                onValueChange = { chatInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(strings.askAgentPlaceholder, fontSize = 13.sp) },
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendCurrentInput() }),
                shape = RoundedCornerShape(24.dp),
                // Typed text follows its own direction: Persian input
                // right-aligns, English input left-aligns.
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    textAlign = TextAlign.Start,
                    textDirection = chatInput.autoTextDirection()
                )
            )
            if (isThinking) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.error,
                    shape = CircleShape,
                    modifier = Modifier.size(50.dp),
                    onClick = { stopAi() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, strings.stopCd, modifier = Modifier.size(22.dp))
                    }
                }
            } else {
                val canSend = chatInput.isNotBlank()
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .then(
                            if (canSend) Modifier.background(brandBrush())
                            else Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        )
                        .clickable(enabled = canSend) { sendCurrentInput() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = strings.sendCd,
                        tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}

/** One chat bubble, with the agent badge on assistant messages. */
@Composable
private fun ChatBubble(isUser: Boolean, content: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomEnd = if (isUser) 6.dp else 20.dp,
                bottomStart = if (isUser) 20.dp else 6.dp
            ),
            color = if (isUser) (MessageColorState.sentColor ?: MaterialTheme.colorScheme.primaryContainer)
            else (MessageColorState.receivedColor ?: MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!isUser) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(brandBrush()),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Language,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Agent",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                // A monospace family used to be forced here, which broke the
                // shaping of Persian text; the normal family renders both
                // scripts correctly.
                SelectionContainer {
                    Text(
                        text = content,
                        fontSize = 13.5.sp,
                        lineHeight = 21.sp,
                        // Auto RTL/LTR: Persian chat messages align right,
                        // English ones left — regardless of the menu language.
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textAlign = TextAlign.Start,
                            textDirection = content.autoTextDirection()
                        )
                    )
                }
            }
        }
    }
}

/** Colour swatch used by the bubble-colour pickers. */
@Composable
private fun BubbleSwatch(
    color: Color?,
    fallback: Color,
    selected: Boolean,
    emptyLabel: String,
    onClick: () -> Unit
) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Box(
        modifier = Modifier
            .size(if (selected) 30.dp else 26.dp)
            .clip(CircleShape)
            .background(color ?: fallback)
            .border(width = if (selected) 2.dp else 1.dp, color = ring, shape = CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (color == null) {
            Icon(Icons.Filled.Close, contentDescription = emptyLabel, modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
private fun ChatHistoryDialog(sessions: List<ChatSession>, strings: AppStrings, onOpenSession: (String) -> Unit, onDeleteSession: (String) -> Unit, onNewSession: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(strings.chatHistoryTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onNewSession) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.agentNewChatCd, style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        text = {
            if (sessions.isEmpty()) {
                Text(strings.noChatsSaved, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sessions) { session ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            onClick = { onOpenSession(session.id) }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(session.title, style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Start, textDirection = session.title.autoTextDirection()), fontWeight = FontWeight.Medium, maxLines = 1)
                                    Text(
                                        text = ChatHistoryManager.formatTimestamp(session.updatedAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                SoftIconButton(
                                    icon = Icons.Filled.FolderOpen,
                                    contentDescription = strings.openCd,
                                    onClick = { onOpenSession(session.id) },
                                    size = 32.dp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                SoftIconButton(
                                    icon = Icons.Filled.Delete,
                                    contentDescription = strings.deleteCd,
                                    onClick = { onDeleteSession(session.id) },
                                    tint = MaterialTheme.colorScheme.error,
                                    size = 32.dp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.close) } }
    )
}

@Composable
private fun MemoryPanel(
    context: Context,
    strings: AppStrings,
    config: AiService.TranslationConfig,
    targetLang: String,
    sampleLines: List<String>,
    onRefresh: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(strings.memoryTabPrompts, strings.memoryTabCorrections, strings.memoryTabSkills, strings.memoryTabExportImport)
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
        SegmentedPills(
            items = tabs,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        // The tabs are all taller than the leftover height, so the body gets a
        // bounded scroll of its own. The inner lists are plain columns now, so
        // there is no scroll-inside-scroll ambiguity.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
        ) {
            when (selectedTab) {
                0 -> PromptsTab(context, strings, config, targetLang, sampleLines, onRefresh)
                1 -> CorrectionsTab(context, strings, onRefresh)
                2 -> SkillsTab(context, strings, onRefresh)
                3 -> ExportImportTab(context, strings)
            }
        }
    }
}

/**
 * The prompt studio.
 *
 * Every prompt the app actually uses is editable here — including the JSON
 * lesson package, which had no prompt at all before. The editor works on the
 * RAW prompt so placeholders survive a save, shows which variables the prompt
 * supports, and can run the prompt on a few real subtitle lines before it is
 * trusted with a whole film.
 */
@Composable
private fun PromptsTab(
    context: Context,
    strings: AppStrings,
    config: AiService.TranslationConfig,
    targetLang: String,
    sampleLines: List<String>,
    onRefresh: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isEn = strings.isEn

    fun labelFor(key: String): String = when (key) {
        AiMemoryManager.PROMPT_TRANSLATE -> strings.promptTranslate
        AiMemoryManager.PROMPT_CHAT -> strings.promptChat
        AiMemoryManager.PROMPT_AGENT -> strings.promptAgent
        AiMemoryManager.PROMPT_JSON_LESSON -> if (isEn) "JSON lesson package" else "بستهٔ آموزشی JSON"
        AiMemoryManager.PROMPT_TRANSLATE_EMPTY -> if (isEn) "Empty lines" else "خطوط ترجمه‌نشده"
        AiMemoryManager.PROMPT_SYNC_TIMINGS -> if (isEn) "Sync + translate" else "هم‌زمان‌سازی + ترجمه"
        AiMemoryManager.PROMPT_SYNC -> if (isEn) "Subtitle matching" else "تطبیق زیرنویس‌ها"
        else -> key
    }

    fun descriptionFor(key: String): String = when (key) {
        AiMemoryManager.PROMPT_TRANSLATE -> if (isEn) "Used for translating subtitle lines" else "برای ترجمهٔ خطوط زیرنویس"
        AiMemoryManager.PROMPT_JSON_LESSON -> if (isEn) "Builds the word/grammar study package" else "ساخت بستهٔ واژه و گرامر برای هر جمله"
        AiMemoryManager.PROMPT_CHAT -> if (isEn) "Free chat with the assistant" else "گفتگوی آزاد با دستیار"
        AiMemoryManager.PROMPT_AGENT -> if (isEn) "Agent that edits your subtitle files" else "عاملی که فایل زیرنویس را ویرایش می‌کند"
        AiMemoryManager.PROMPT_TRANSLATE_EMPTY -> if (isEn) "Fills lines left untranslated" else "پر کردن خطوطی که ترجمه نشده‌اند"
        AiMemoryManager.PROMPT_SYNC_TIMINGS -> if (isEn) "Matches two tracks and fixes timings" else "تطبیق دو زیرنویس و اصلاح زمان‌بندی"
        AiMemoryManager.PROMPT_SYNC -> if (isEn) "Meaning-based subtitle matching" else "تطبیق زیرنویس‌ها بر اساس معنا"
        else -> ""
    }

    var editingKey by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var localRefresh by remember { mutableIntStateOf(0) }
    var testing by remember { mutableStateOf(false) }
    var testOutput by remember { mutableStateOf<String?>(null) }
    var testError by remember { mutableStateOf<String?>(null) }

    val fallbackSamples = remember {
        listOf(
            "I've been waiting for this my whole life.",
            "Don't take it personally, kid.",
            "We should probably get going."
        )
    }
    val effectiveSamples = if (sampleLines.isNotEmpty()) sampleLines else fallbackSamples

    val currentKey = editingKey
    if (currentKey == null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiMemoryManager.PROMPT_KEYS.forEach { key ->
                val isCustom = remember(localRefresh, key) { AiMemoryManager.hasCustomPrompt(context, key) }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(labelFor(key), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        val desc = descriptionFor(key)
                        if (desc.isNotBlank()) {
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        StatusPill(
                            text = if (isCustom) strings.customBadge else strings.presetDefault,
                            tone = if (isCustom) PillTone.Accent else PillTone.Neutral
                        )
                    }
                    TextButton(
                        onClick = {
                            // getRawPrompt, not getPrompt: opening the editor on
                            // a resolved prompt then saving it baked the current
                            // language into the template permanently.
                            editText = AiMemoryManager.getRawPrompt(context, key)
                            testOutput = null
                            testError = null
                            editingKey = key
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Text(strings.editBtn, style = MaterialTheme.typography.labelMedium)
                    }
                    if (isCustom) {
                        TextButton(
                            onClick = { AiMemoryManager.resetPrompt(context, key); localRefresh++; onRefresh() },
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Text(strings.resetBtn, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            }
        }
    } else {
        Column {
            Text(
                text = strings.editingPromptTitle(labelFor(currentKey)),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Variables are insertable now instead of being folklore.
            val variables = AiMemoryManager.PROMPT_VARIABLES[currentKey] ?: emptyList()
            if (variables.isNotEmpty()) {
                Text(
                    text = if (isEn) "Tap to insert a variable:" else "برای درج متغیر بزن:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
                    variables.forEach { variable ->
                        FilterChip(
                            selected = editText.contains(variable),
                            onClick = { editText = editText.trimEnd() + " " + variable },
                            label = { Text(variable, fontSize = 10.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isEn) "{LANG} becomes \"${AiMemoryManager.resolveLangName(targetLang)}\""
                    else "{LANG} به «${AiMemoryManager.resolveLangName(targetLang)}» تبدیل می‌شود",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp),
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    textAlign = TextAlign.Start,
                    textDirection = editText.autoTextDirection()
                ),
                placeholder = { Text(strings.promptTextPlaceholder, fontSize = 11.sp) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton(
                    text = strings.save,
                    onClick = {
                        AiMemoryManager.savePrompt(context, currentKey, editText)
                        editingKey = null
                        localRefresh++
                        onRefresh()
                    },
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { editingKey = null },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(strings.cancel, style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            Spacer(modifier = Modifier.height(10.dp))

            // Try before you buy: run the prompt on three real lines.
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        testing = true
                        testOutput = null
                        testError = null
                        scope.launch {
                            val result = AiService.testPrompt(
                                config = config,
                                promptText = editText,
                                sampleLines = effectiveSamples,
                                targetLang = targetLang
                            )
                            result.fold(
                                onSuccess = { testOutput = it },
                                onFailure = { testError = it.message ?: "?" }
                            )
                            testing = false
                        }
                    },
                    enabled = !testing && config.apiKey.isNotBlank() && editText.isNotBlank(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (testing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (isEn) "Test on ${effectiveSamples.size} lines" else "تست روی ${effectiveSamples.size} خط",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (config.apiKey.isBlank()) {
                    Text(
                        text = strings.apiKeySetError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (sampleLines.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isEn) "No subtitle loaded, so built-in sample lines are used."
                    else "زیرنویسی بار نشده؛ از خطوط نمونهٔ داخلی استفاده می‌شود.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            testError?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = strings.errorWithMessage(message),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            testOutput?.let { output ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        // No inner scroll: the panel body scrolls now, and two
                        // nested vertical scrolls fight each other.
                        Text(
                            text = output,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CorrectionsTab(context: Context, strings: AppStrings, onRefresh: () -> Unit) {
    // Keyed on a local counter: without it the list stayed stale after an
    // entry was added or deleted.
    var localRefresh by remember { mutableIntStateOf(0) }
    val corrections = remember(localRefresh) { AiMemoryManager.loadCorrections(context) }
    var showAddDialog by remember { mutableStateOf(false) }
    if (corrections.isEmpty()) {
        Text(strings.noCorrectionsSaved, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
    } else {
        Text(strings.correctionsSavedCount(corrections.size), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        // Plain column: this list lives inside the panel's own scroll.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            corrections.forEach { corr ->
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(strings.sourceLabel(corr.sourceText.take(60)), style = MaterialTheme.typography.labelSmall.copy(textAlign = TextAlign.Start, textDirection = strings.sourceLabel(corr.sourceText.take(60)).autoTextDirection()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text("✕ ${corr.wrongTranslation.take(60)}", style = MaterialTheme.typography.labelSmall.copy(textAlign = TextAlign.Start, textDirection = corr.wrongTranslation.take(60).autoTextDirection()), color = MaterialTheme.colorScheme.error)
                        Text("✓ ${corr.correctTranslation.take(60)}", style = MaterialTheme.typography.labelSmall.copy(textAlign = TextAlign.Start, textDirection = corr.correctTranslation.take(60).autoTextDirection()), color = MaterialTheme.colorScheme.primary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { AiMemoryManager.removeCorrection(context, corr.id); localRefresh++; onRefresh() }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text(strings.deleteCd, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    TextButton(onClick = { showAddDialog = true }) { Text(strings.addManualCorrection, style = MaterialTheme.typography.labelMedium) }
    if (showAddDialog) {
        var srcText by remember { mutableStateOf("") }; var wrongText by remember { mutableStateOf("") }; var correctText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text(strings.addCorrectionTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = srcText, onValueChange = { srcText = it }, label = { Text(strings.sourceTextLabel, fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), textStyle = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Start, textDirection = srcText.autoTextDirection()))
                    OutlinedTextField(value = wrongText, onValueChange = { wrongText = it }, label = { Text(strings.wrongTranslationLabel, fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), textStyle = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Start, textDirection = wrongText.autoTextDirection()))
                    OutlinedTextField(value = correctText, onValueChange = { correctText = it }, label = { Text(strings.correctTranslationLabel, fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), textStyle = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Start, textDirection = correctText.autoTextDirection()))
                }
            },
            confirmButton = { TextButton(onClick = { if (srcText.isNotBlank() && correctText.isNotBlank()) { AiMemoryManager.addCorrection(context, srcText, wrongText, correctText); showAddDialog = false; localRefresh++; onRefresh() } }) { Text(strings.save) } },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text(strings.cancel) } }
        )
    }
}

@Composable
private fun SkillsTab(context: Context, strings: AppStrings, onRefresh: () -> Unit) {
    var localRefresh by remember { mutableIntStateOf(0) }
    val skills = remember(localRefresh) { AiMemoryManager.loadSkills(context) }
    var showAddDialog by remember { mutableStateOf(false) }
    val glossaryCount = skills.count { it.category == AiMemoryManager.CATEGORY_LEARNED_WORD }
    if (skills.isEmpty()) {
        Text(strings.noSkillsSaved, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
    } else {
        Text(strings.skillsSavedCount(skills.size), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            skills.forEach { skill ->
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            StatusPill(
                                text = skill.category,
                                tone = if (skill.category == AiMemoryManager.CATEGORY_LEARNED_WORD) PillTone.Neutral else PillTone.Accent
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(skill.content, style = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Start, textDirection = skill.content.autoTextDirection()))
                        }
                        TextButton(onClick = { AiMemoryManager.removeSkill(context, skill.id); localRefresh++; onRefresh() }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(strings.deleteCd, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { showAddDialog = true }) { Text(strings.addSkillNote, style = MaterialTheme.typography.labelMedium) }
        // Auto-mined word pairs are noisy by nature; make them easy to dump
        // in one go instead of deleting them one by one.
        if (glossaryCount > 0) {
            TextButton(
                onClick = { AiMemoryManager.purgeNoisyLearnedWords(context); localRefresh++; onRefresh() },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    text = if (strings.isEn) "Clear glossary ($glossaryCount)" else "پاک کردن واژه‌نامه ($glossaryCount)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    if (showAddDialog) {
        var content by remember { mutableStateOf("") }; var category by remember { mutableStateOf("user_note") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text(strings.addSkillTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text(strings.textLabel, fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), textStyle = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Start, textDirection = content.autoTextDirection()))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        listOf("user_note" to strings.categoryUserNote, "translation_rule" to strings.categoryTranslationRule, "skill" to strings.categorySkill, "dictionary_tip" to strings.categoryDictionaryTip).forEach { (cat, label) ->
                            FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(label, fontSize = 10.sp) })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { if (content.isNotBlank()) { AiMemoryManager.addSkill(context, category, content); showAddDialog = false; localRefresh++; onRefresh() } }) { Text(strings.save) } },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text(strings.cancel) } }
        )
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = strings.exportImportTitle, subtitle = strings.exportImportDesc)
        GradientButton(
            text = strings.exportToDownloadsBtn,
            onClick = {
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
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Filled.Download
        )
        HorizontalDivider()
        Text(strings.importFromFileLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = importMode == "merge", onClick = { importMode = "merge" }, label = { Text("merge", fontSize = 10.sp) })
            FilterChip(selected = importMode == "replace", onClick = { importMode = "replace" }, label = { Text("replace", fontSize = 10.sp) })
        }
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.Upload, null, Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(strings.selectFileImportBtn, style = MaterialTheme.typography.labelMedium)
        }
        HorizontalDivider()
        TextButton(onClick = { AiMemoryManager.clearAll(context); Toast.makeText(context, strings.close, Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Text(strings.clearAllMemoryBtn, style = MaterialTheme.typography.labelMedium)
        }
        Text(
            text = AiMemoryManager.getMemorySummary(context),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace
        )
    }
}

private suspend fun sendChat(context: Context, input: String, apiKey: String, baseUrl: String, model: String, targetLang: String, subEnList: List<SubtitleEntry>, workingSubFa: List<SubtitleEntry>, readerText: String, readerFileName: String, currentMessages: List<Pair<String, String>>, onMessages: (List<Pair<String, String>>) -> Unit, onThinking: (Boolean) -> Unit, onError: (String?) -> Unit, listState: androidx.compose.foundation.lazy.LazyListState, onUpdateSubFa: (List<SubtitleEntry>) -> Unit, onUpdateReaderText: (String) -> Unit, strings: AppStrings) {
    if (apiKey.isBlank()) { onError(strings.apiKeySetError); return }
    val userMessage = input.trim()
    onMessages(currentMessages + Pair("user", userMessage))
    onThinking(true); onError(null)
    try {
        val systemPrompt = buildSystemPrompt(context, subEnList, workingSubFa, readerText, readerFileName, targetLang)
        val result = AiService.chat(
            config = AiService.TranslationConfig(apiKey, baseUrl, model),
            messages = currentMessages + Pair("user", userMessage),
            systemPrompt = systemPrompt,
            context = context,
            targetLang = targetLang
        )
        result.fold(onSuccess = { response ->
            val changes = parseSubtitleChanges(response)
            val readerUpdate = parseReaderTextUpdate(response)
            var displayResponse = response
            if (changes != null) { val updated = workingSubFa.toMutableList(); changes.forEach { (idx, text) -> if (idx - 1 in updated.indices) updated[idx - 1] = updated[idx - 1].copy(text = text) }; onUpdateSubFa(updated); displayResponse = "✓ ${changes.size}\n$response" }
            if (readerUpdate != null) { onUpdateReaderText(readerUpdate); displayResponse = "✓\n$response" }
            onMessages(currentMessages + Pair("user", userMessage) + Pair("assistant", displayResponse))
        }, onFailure = { e -> onError(strings.errorWithMessage(e.message)); onMessages(currentMessages + Pair("user", userMessage)) })
    } catch (e: Exception) { onError(strings.errorWithMessage(e.message)); onMessages(currentMessages + Pair("user", userMessage)) }
    finally { onThinking(false) }
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
    // targetLang is passed through now, so {LANG} in the agent prompt
    // resolves to the language actually selected in settings.
    val basePrompt = AiMemoryManager.getPrompt(context, AiMemoryManager.PROMPT_AGENT, targetLang)
    val memoryContext = AiMemoryManager.buildFullContext(context)
    val sb = StringBuilder()
    sb.appendLine(basePrompt)
    sb.appendLine()
    sb.appendLine("Target language for translation: ${AiMemoryManager.resolveLangName(targetLang)}")
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
