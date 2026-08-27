package com.example.logic

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Manages chat session persistence.
 * Sessions are stored as individual JSON files in filesDir/chat_history/.
 * Each session has: id, title, messages (role+content+timestamp), createdAt, updatedAt.
 */
object ChatHistoryManager {

    private const val HISTORY_DIR = "chat_history"

    private fun getHistoryDir(context: Context): File {
        val dir = File(context.filesDir, HISTORY_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Create a new empty chat session.
     */
    fun createNewSession(): ChatSession {
        val now = System.currentTimeMillis()
        return ChatSession(
            id = UUID.randomUUID().toString(),
            title = "چت جدید",
            messages = mutableListOf(),
            createdAt = now,
            updatedAt = now
        )
    }

    /**
     * Save (or update) a chat session to disk.
     */
    fun saveSession(context: Context, session: ChatSession) {
        session.updatedAt = System.currentTimeMillis()
        val file = File(getHistoryDir(context), "${session.id}.json")
        val json = JSONObject()
        json.put("id", session.id)
        json.put("title", session.title)
        json.put("createdAt", session.createdAt)
        json.put("updatedAt", session.updatedAt)
        val messagesArray = JSONArray()
        for (msg in session.messages) {
            val msgJson = JSONObject()
            msgJson.put("role", msg.role)
            msgJson.put("content", msg.content)
            msgJson.put("timestamp", msg.timestamp)
            messagesArray.put(msgJson)
        }
        json.put("messages", messagesArray)
        file.writeText(json.toString())
    }

    /**
     * Load a full chat session by ID (including all messages).
     */
    fun loadSession(context: Context, sessionId: String): ChatSession? {
        val file = File(getHistoryDir(context), "$sessionId.json")
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            val messages = mutableListOf<ChatMessage>()
            val messagesArray = json.getJSONArray("messages")
            for (i in 0 until messagesArray.length()) {
                val msgJson = messagesArray.getJSONObject(i)
                messages.add(
                    ChatMessage(
                        role = msgJson.getString("role"),
                        content = msgJson.getString("content"),
                        timestamp = msgJson.optLong("timestamp", 0)
                    )
                )
            }
            ChatSession(
                id = json.getString("id"),
                title = json.optString("title", "چت"),
                messages = messages,
                createdAt = json.getLong("createdAt"),
                updatedAt = json.optLong("updatedAt", json.getLong("createdAt"))
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * List all chat sessions (metadata only, no messages loaded).
     * Sorted by updatedAt descending (most recent first).
     */
    fun listSessions(context: Context): List<ChatSession> {
        val dir = getHistoryDir(context)
        val sessions = mutableListOf<ChatSession>()
        dir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".json")) {
                try {
                    val json = JSONObject(file.readText())
                    sessions.add(
                        ChatSession(
                            id = json.getString("id"),
                            title = json.optString("title", "چت"),
                            messages = mutableListOf(),
                            createdAt = json.getLong("createdAt"),
                            updatedAt = json.optLong("updatedAt", json.getLong("createdAt"))
                        )
                    )
                } catch (_: Exception) {}
            }
        }
        return sessions.sortedByDescending { it.updatedAt }
    }

    /**
     * Delete a chat session by ID.
     */
    fun deleteSession(context: Context, sessionId: String) {
        val file = File(getHistoryDir(context), "$sessionId.json")
        if (file.exists()) file.delete()
    }

    /**
     * Get the most recent chat session (full, with messages), or null if none.
     */
    fun getLatestSession(context: Context): ChatSession? {
        val sessions = listSessions(context)
        return if (sessions.isNotEmpty()) loadSession(context, sessions.first().id) else null
    }

    /**
     * Auto-generate a title from the first user message.
     */
    fun autoGenerateTitle(session: ChatSession): String {
        if (session.messages.isEmpty()) return "چت جدید"
        val firstUserMsg = session.messages.find { it.role == "user" }
        if (firstUserMsg != null) {
            val title = firstUserMsg.content.take(40).trim()
            return if (title.length < firstUserMsg.content.length) "$title..." else title
        }
        return "چت جدید"
    }

    /**
     * Get total number of saved sessions.
     */
    fun getSessionCount(context: Context): Int {
        val dir = getHistoryDir(context)
        return dir.listFiles()?.count { it.name.endsWith(".json") } ?: 0
    }

    /**
     * Format timestamp to a human-readable date string.
     */
    fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}

/**
 * A single chat message.
 */
data class ChatMessage(
    val role: String,       // "user" or "assistant"
    val content: String,
    val timestamp: Long
)

/**
 * A chat session containing a list of messages.
 */
data class ChatSession(
    val id: String,
    var title: String,
    val messages: MutableList<ChatMessage>,
    val createdAt: Long,
    var updatedAt: Long
)
