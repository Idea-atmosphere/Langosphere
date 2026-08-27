package com.example.logic

import android.content.Context
import android.util.Log
import com.example.model.SubtitleEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiService {

    private const val TAG = "AiService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class TranslationConfig(
        val apiKey: String,
        val baseUrl: String = "http://localhost:20128/v1",
        val model: String = "gpt-4o-mini"
    )

    data class TranslationResult(
        val translatedLines: List<TranslatedLine>,
        val rawResponse: String
    )

    data class TranslatedLine(
        val originalIndex: Int,
        val originalText: String,
        val translatedText: String,
        val start: Double?,
        val end: Double?
    )

    suspend fun translateSubtitles(
        config: TranslationConfig,
        sourceTexts: List<String>,
        sourceTimes: List<Pair<Double, Double>?>? = null,
        targetLang: String = "fa",
        systemPrompt: String? = null,
        linesPerBatch: Int = 1,
        context: Context? = null
    ): Result<TranslationResult> = withContext(Dispatchers.IO) {
        try {
            if (sourceTexts.isEmpty()) return@withContext Result.failure(Exception("متنی برای ترجمه وجود ندارد"))
            val sysPrompt = resolvePrompt(context, systemPrompt, AiMemoryManager.PROMPT_TRANSLATE, targetLang)
            val allResults = mutableListOf<TranslatedLine>()
            val rawResponses = mutableListOf<String>()
            val batches = sourceTexts.chunked(linesPerBatch.coerceAtLeast(1))
            for ((batchIdx, batch) in batches.withIndex()) {
                val startIdx = batchIdx * linesPerBatch
                val userMessage = buildUserMessage(batch, startIdx, sourceTimes)
                val response = callChatApi(config, sysPrompt, userMessage)
                val parsed = parseTranslationResponse(response, batch, startIdx, sourceTimes)
                allResults.addAll(parsed)
                rawResponses.add(response)
            }
            Result.success(TranslationResult(allResults, rawResponses.joinToString("\n---\n")))
        } catch (e: Exception) { Log.e(TAG, "Translation failed: ${e.message}", e); Result.failure(e) }
    }

    suspend fun chat(
        config: TranslationConfig,
        messages: List<Pair<String, String>>,
        systemPrompt: String? = null,
        context: Context? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (context != null) {
                val lastUserMsg = messages.lastOrNull { it.first == "user" }?.second
                if (lastUserMsg != null && AiMemoryManager.parseUserNote(context, lastUserMsg)) {
                    return@withContext Result.success("✓ یادداشت/قانون ذخیره شد.")
                }
            }
            val sysPrompt = resolvePrompt(context, systemPrompt, AiMemoryManager.PROMPT_CHAT, null)
            val response = callChatApiConversation(config, sysPrompt, messages)
            Result.success(response)
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun resolvePrompt(context: Context?, explicitPrompt: String?, promptKey: String, targetLang: String?): String {
        if (explicitPrompt != null) return injectMemory(context, explicitPrompt)
        if (context != null) { val prompt = AiMemoryManager.getPrompt(context, promptKey, targetLang); return injectMemory(context, prompt) }
        val fallback = when (promptKey) {
            AiMemoryManager.PROMPT_TRANSLATE -> buildDefaultSystemPrompt(targetLang ?: "fa")
            AiMemoryManager.PROMPT_CHAT -> AiMemoryManager.DEFAULT_PROMPTS[AiMemoryManager.PROMPT_CHAT] ?: "You are a helpful assistant. Answer concisely."
            else -> ""
        }
        return fallback
    }

    private fun injectMemory(context: Context?, basePrompt: String): String {
        if (context == null) return basePrompt
        val memoryContext = AiMemoryManager.buildFullContext(context)
        return if (memoryContext.isNotBlank()) "$basePrompt\n\n$memoryContext" else basePrompt
    }

    private fun buildDefaultSystemPrompt(targetLang: String): String {
        val langName = when (targetLang) { "fa" -> "Persian (Farsi)"; "ar" -> "Arabic"; "tr" -> "Turkish"; "fr" -> "French"; "de" -> "German"; "es" -> "Spanish"; "ja" -> "Japanese"; "ko" -> "Korean"; "zh" -> "Chinese"; else -> targetLang }
        return "You are a professional subtitle translator. Translate each line to $langName. Return JSON array: [{\"idx\": N, \"translated\": \"translation\"}]. Return ONLY the JSON array."
    }

    private fun buildUserMessage(texts: List<String>, startIdx: Int, times: List<Pair<Double, Double>?>?): String {
        val sb = StringBuilder()
        for ((i, text) in texts.withIndex()) {
            val idx = startIdx + i
            val timeInfo = times?.getOrNull(idx)
            if (timeInfo != null) sb.appendLine("[$idx] (${formatTime(timeInfo.first)} -> ${formatTime(timeInfo.second)}): $text")
            else sb.appendLine("[$idx]: $text")
        }
        return sb.toString()
    }

    private fun formatTime(seconds: Double): String { val mins = (seconds / 60).toInt(); val secs = (seconds % 60); return "%d:%05.2f".format(mins, secs) }

    private fun callChatApi(config: TranslationConfig, systemPrompt: String, userMessage: String): String {
        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
            put(JSONObject().apply { put("role", "user"); put("content", userMessage) })
        }
        val body = JSONObject().apply { put("model", config.model); put("messages", messages); put("temperature", 0.3) }
        return callWithRetry(config) { doCallApi(config, body.toString()) }
    }

    private fun callChatApiConversation(config: TranslationConfig, systemPrompt: String, messages: List<Pair<String, String>>): String {
        val jsonMessages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
            for ((role, content) in messages) put(JSONObject().apply { put("role", role); put("content", content) })
        }
        val body = JSONObject().apply { put("model", config.model); put("messages", jsonMessages); put("temperature", 0.5) }
        return callWithRetry(config) { doCallApi(config, body.toString()) }
    }

    private inline fun callWithRetry(config: TranslationConfig, block: () -> String): String {
        var lastErr: Exception? = null
        for (attempt in 1..3) {
            try { return block() } catch (e: Exception) {
                lastErr = e; Log.w(TAG, "API attempt $attempt failed: ${e.message}")
                if (attempt < 3) Thread.sleep(1000L * (1L shl (attempt - 1)))
            }
        }
        throw lastErr ?: Exception("Unknown error after 3 attempts")
    }

    private fun doCallApi(config: TranslationConfig, bodyStr: String): String {
        val request = Request.Builder()
            .url("${config.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(bodyStr.toRequestBody("application/json".toMediaType()))
            .build()
        Log.d(TAG, "API call to ${config.baseUrl}/chat/completions, model=${config.model}, bodySize=${bodyStr.length}")
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("پاسخ خالی از سرور")
        if (!response.isSuccessful) throw Exception("خطای سرور ${response.code}: $responseBody")
        return JSONObject(responseBody).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }

    private fun parseTranslationResponse(response: String, batch: List<String>, startIdx: Int, times: List<Pair<Double, Double>?>?): List<TranslatedLine> {
        val results = mutableListOf<TranslatedLine>()
        val jsonStr = extractJson(response)
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val idx = obj.optInt("idx", startIdx + i)
                val translated = obj.optString("translated", obj.optString("translation", ""))
                val origIdx = idx.coerceIn(0, batch.size - 1)
                val time = times?.getOrNull(startIdx + origIdx)
                results.add(TranslatedLine(startIdx + origIdx, batch.getOrElse(origIdx) { "" }, translated, time?.first, time?.second))
            }
        } catch (e: Exception) {
            Log.w(TAG, "JSON parse failed, fallback: ${e.message}")
            val lines = response.lines().filter { it.isNotBlank() }
            for ((i, line) in lines.withIndex()) {
                if (i >= batch.size) break
                val clean = line.replace(Regex("^\\d+[.\\)]]\\s*"), "").trim()
                val time = times?.getOrNull(startIdx + i)
                results.add(TranslatedLine(startIdx + i, batch[i], clean, time?.first, time?.second))
            }
        }
        for (i in batch.indices) {
            if (results.none { it.originalIndex == startIdx + i }) {
                val time = times?.getOrNull(startIdx + i)
                results.add(TranslatedLine(startIdx + i, batch[i], "", time?.first, time?.second))
            }
        }
        return results
    }

    private fun extractJson(text: String): String {
        val raw = text.trim()
        val codeBlockRegex = Regex("```(?:json)?\\s*\\[([\\s\\S]*?)\\]\\s*```", RegexOption.IGNORE_CASE)
        codeBlockRegex.find(raw)?.let { return "[" + it.groupValues[1] + "]" }
        val startIdx = raw.indexOf('[')
        if (startIdx >= 0) {
            var depth = 0; var inString = false; var escaped = false; var endIdx = -1
            for (i in startIdx until raw.length) {
                val c = raw[i]
                when {
                    escaped -> escaped = false
                    c == '\\' && inString -> escaped = true
                    c == '"' -> inString = !inString
                    !inString && c == '[' -> depth++
                    !inString && c == ']' && --depth == 0 -> { endIdx = i; break }
                }
            }
            if (endIdx >= 0) return raw.substring(startIdx, endIdx + 1)
        }
        Regex("\\[\\s*\\{.*?\\}\\s*]", RegexOption.DOT_MATCHES_ALL).find(raw)?.let { return it.value }
        return raw
    }
}