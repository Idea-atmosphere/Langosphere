package com.example.logic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    /**
     * Default number of subtitle lines per request. The old value was 1,
     * which meant a 900-line film cost 900 round trips (and 900 copies of
     * the system prompt). Batching is the single biggest speed and cost win
     * available here.
     */
    const val DEFAULT_LINES_PER_BATCH = 20

    /** How many preceding lines are sent as untranslated context. */
    private const val CONTEXT_LINES = 2

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

    /** Errors that will never succeed on a retry (bad key, bad request, ...). */
    class NonRetryableApiException(message: String) : Exception(message)

    private data class BatchItem(val index: Int, val text: String)

    suspend fun translateSubtitles(
        config: TranslationConfig,
        sourceTexts: List<String>,
        sourceTimes: List<Pair<Double, Double>?>? = null,
        targetLang: String = "fa",
        systemPrompt: String? = null,
        linesPerBatch: Int = DEFAULT_LINES_PER_BATCH,
        context: Context? = null,
        useCache: Boolean = true,
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): Result<TranslationResult> = withContext(Dispatchers.IO) {
        try {
            if (sourceTexts.isEmpty()) {
                return@withContext Result.failure(Exception("متنی برای ترجمه وجود ندارد"))
            }

            // Callers that still ask for one line per request get the sane
            // default instead; a single-line translation is unaffected
            // because the list only has one line to begin with.
            val batchSize = if (linesPerBatch <= 1) DEFAULT_LINES_PER_BATCH else linesPerBatch

            fun timeAt(index: Int): Pair<Double, Double>? = sourceTimes?.getOrNull(index)

            val results = arrayOfNulls<TranslatedLine>(sourceTexts.size)

            // 1) Serve everything already known from the cache for free.
            val pending = mutableListOf<BatchItem>()
            val cache = if (useCache && context != null) {
                AiMemoryManager.loadTranslationCache(context)
            } else {
                emptyMap()
            }
            var cacheHits = 0
            for (i in sourceTexts.indices) {
                val text = sourceTexts[i]
                if (text.isBlank()) {
                    results[i] = TranslatedLine(i, text, "", timeAt(i)?.first, timeAt(i)?.second)
                    continue
                }
                val cached = cache[AiMemoryManager.cacheKey(text)]
                if (cached != null) {
                    cacheHits++
                    results[i] = TranslatedLine(i, text, cached, timeAt(i)?.first, timeAt(i)?.second)
                } else {
                    pending.add(BatchItem(i, text))
                }
            }
            if (cacheHits > 0) Log.d(TAG, "Cache served $cacheHits/${sourceTexts.size} lines")

            // 2) Only the lines that are actually unknown reach the model,
            //    and only the memory relevant to THEM is injected.
            val sysPrompt = resolvePrompt(
                context = context,
                explicitPrompt = systemPrompt,
                promptKey = AiMemoryManager.PROMPT_TRANSLATE,
                targetLang = targetLang,
                memorySourceTexts = pending.map { it.text }
            )

            val rawResponses = mutableListOf<String>()
            val batches = pending.chunked(batchSize)
            var done = 0
            for (batch in batches) {
                val contextLines = buildContextLines(sourceTexts, batch.first().index)
                val userMessage = buildUserMessage(batch, sourceTimes, contextLines)
                val response = callChatApi(config, sysPrompt, userMessage)
                rawResponses.add(response)
                for (line in parseBatchResponse(response, batch, sourceTimes)) {
                    results[line.originalIndex] = line
                }
                done += batch.size
                onProgress?.invoke(done, pending.size)
            }

            // 3) Anything the model skipped still needs a slot.
            for (i in sourceTexts.indices) {
                if (results[i] == null) {
                    results[i] = TranslatedLine(
                        i,
                        sourceTexts[i],
                        "",
                        timeAt(i)?.first,
                        timeAt(i)?.second
                    )
                }
            }

            val all = results.filterNotNull()

            // 4) Remember the fresh work so a re-run is instant.
            if (useCache && context != null) {
                val fresh = all
                    .filter { line -> pending.any { it.index == line.originalIndex } }
                    .filter { it.translatedText.isNotBlank() }
                    .map { it.originalText to it.translatedText }
                if (fresh.isNotEmpty()) {
                    try {
                        AiMemoryManager.saveTranslationPairs(context, fresh)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to update cache: ${e.message}")
                    }
                }
            }

            Result.success(TranslationResult(all, rawResponses.joinToString("\n---\n")))
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun chat(
        config: TranslationConfig,
        messages: List<Pair<String, String>>,
        systemPrompt: String? = null,
        context: Context? = null,
        targetLang: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (context != null) {
                val lastUserMsg = messages.lastOrNull { it.first == "user" }?.second
                if (lastUserMsg != null && AiMemoryManager.parseUserNote(context, lastUserMsg)) {
                    return@withContext Result.success("✓ یادداشت/قانون ذخیره شد.")
                }
            }
            val sysPrompt = resolvePrompt(
                context = context,
                explicitPrompt = systemPrompt,
                promptKey = AiMemoryManager.PROMPT_CHAT,
                targetLang = targetLang,
                memorySourceTexts = null
            )
            val response = callChatApiConversation(config, sysPrompt, messages)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Runs a prompt against a handful of sample lines and returns the raw
     * model output, so a prompt can be checked in the editor instead of
     * being discovered as broken halfway through a film.
     */
    suspend fun testPrompt(
        config: TranslationConfig,
        promptText: String,
        sampleLines: List<String>,
        targetLang: String = "fa"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (sampleLines.isEmpty()) {
                return@withContext Result.failure(Exception("خطی برای تست وجود ندارد"))
            }
            val resolved = promptText.replace("{LANG}", AiMemoryManager.resolveLangName(targetLang))
            val items = sampleLines.mapIndexed { i, text -> BatchItem(i, text) }
            val response = callChatApi(config, resolved, buildUserMessage(items, null, emptyList()))
            Result.success(response.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolvePrompt(
        context: Context?,
        explicitPrompt: String?,
        promptKey: String,
        targetLang: String?,
        memorySourceTexts: List<String>?
    ): String {
        val base = when {
            explicitPrompt != null -> explicitPrompt.replace(
                "{LANG}",
                AiMemoryManager.resolveLangName(targetLang ?: "fa")
            )
            context != null -> AiMemoryManager.getPrompt(context, promptKey, targetLang)
            else -> when (promptKey) {
                AiMemoryManager.PROMPT_TRANSLATE -> buildDefaultSystemPrompt(targetLang ?: "fa")
                else -> (AiMemoryManager.DEFAULT_PROMPTS[promptKey] ?: "")
                    .replace("{LANG}", AiMemoryManager.resolveLangName(targetLang ?: "fa"))
            }
        }
        return injectMemory(context, base, memorySourceTexts)
    }

    /**
     * Memory injection is now targeted. It used to append the entire memory
     * (corrections + skills + notes + examples) to EVERY request, which
     * could easily be more tokens than the subtitles themselves.
     */
    private fun injectMemory(
        context: Context?,
        basePrompt: String,
        memorySourceTexts: List<String>?
    ): String {
        if (context == null) return basePrompt
        val memoryContext = try {
            if (memorySourceTexts != null) {
                AiMemoryManager.buildRelevantContext(context, memorySourceTexts)
            } else {
                AiMemoryManager.buildFullContext(context)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Memory injection skipped: ${e.message}")
            ""
        }
        return if (memoryContext.isNotBlank()) "$basePrompt\n\n$memoryContext" else basePrompt
    }

    private fun buildDefaultSystemPrompt(targetLang: String): String {
        val langName = AiMemoryManager.resolveLangName(targetLang)
        return "You are a professional subtitle translator. Translate each line to $langName. " +
            "Return JSON array: [{\"idx\": N, \"translated\": \"translation\"}] where idx is the " +
            "number shown in brackets. Return ONLY the JSON array."
    }

    /** The lines just before a batch, as background for pronouns and tone. */
    private fun buildContextLines(sourceTexts: List<String>, firstIndex: Int): List<String> {
        if (firstIndex <= 0) return emptyList()
        val from = (firstIndex - CONTEXT_LINES).coerceAtLeast(0)
        return sourceTexts.subList(from, firstIndex).filter { it.isNotBlank() }
    }

    private fun buildUserMessage(
        items: List<BatchItem>,
        times: List<Pair<Double, Double>?>?,
        contextLines: List<String>
    ): String {
        val sb = StringBuilder()
        if (contextLines.isNotEmpty()) {
            sb.appendLine("CONTEXT (previous lines, do NOT translate):")
            for (line in contextLines) sb.appendLine("- $line")
            sb.appendLine()
        }
        sb.appendLine("LINES TO TRANSLATE:")
        for (item in items) {
            val timeInfo = times?.getOrNull(item.index)
            if (timeInfo != null) {
                sb.appendLine("[${item.index}] (${formatTime(timeInfo.first)} -> ${formatTime(timeInfo.second)}): ${item.text}")
            } else {
                sb.appendLine("[${item.index}]: ${item.text}")
            }
        }
        return sb.toString()
    }

    private fun formatTime(seconds: Double): String {
        val mins = (seconds / 60).toInt()
        val secs = (seconds % 60)
        return "%d:%05.2f".format(mins, secs)
    }

    private suspend fun callChatApi(
        config: TranslationConfig,
        systemPrompt: String,
        userMessage: String
    ): String {
        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
            put(JSONObject().apply { put("role", "user"); put("content", userMessage) })
        }
        val body = JSONObject().apply {
            put("model", config.model)
            put("messages", messages)
            put("temperature", 0.3)
        }
        return callWithRetry { doCallApi(config, body.toString()) }
    }

    private suspend fun callChatApiConversation(
        config: TranslationConfig,
        systemPrompt: String,
        messages: List<Pair<String, String>>
    ): String {
        val jsonMessages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
            for ((role, content) in messages) {
                put(JSONObject().apply { put("role", role); put("content", content) })
            }
        }
        val body = JSONObject().apply {
            put("model", config.model)
            put("messages", jsonMessages)
            put("temperature", 0.5)
        }
        return callWithRetry { doCallApi(config, body.toString()) }
    }

    /**
     * Retries transient failures only, and waits with a suspending delay.
     * The old version slept on the calling thread (so cancelling a
     * translation could not take effect for seconds) and happily retried a
     * wrong API key three times.
     */
    private suspend fun callWithRetry(block: () -> String): String {
        var lastErr: Exception? = null
        for (attempt in 1..3) {
            try {
                return block()
            } catch (e: NonRetryableApiException) {
                throw e
            } catch (e: Exception) {
                lastErr = e
                Log.w(TAG, "API attempt $attempt failed: ${e.message}")
                if (attempt < 3) delay(1000L * (1L shl (attempt - 1)))
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
        val responseBody = response.body?.string()
        if (!response.isSuccessful) {
            val detail = responseBody?.take(400) ?: ""
            val message = "خطای سرور ${response.code}: $detail"
            // 401/403 = key problem, 400/404/422 = request problem: retrying
            // only wastes the user's time.
            if (response.code in intArrayOf(400, 401, 403, 404, 422)) {
                throw NonRetryableApiException(message)
            }
            throw Exception(message)
        }
        if (responseBody == null) throw Exception("پاسخ خالی از سرور")
        return JSONObject(responseBody)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    /**
     * Maps a batch response back onto absolute line indices. The previous
     * implementation coerced the returned idx into the batch range, which
     * only worked because batches were a single line — with real batches it
     * would have mixed translations up.
     */
    private fun parseBatchResponse(
        response: String,
        batch: List<BatchItem>,
        times: List<Pair<Double, Double>?>?
    ): List<TranslatedLine> {
        val byIndex = LinkedHashMap<Int, TranslatedLine>()
        val firstIndex = batch.first().index
        val lastIndex = batch.last().index

        fun put(item: BatchItem, translated: String) {
            if (translated.isBlank()) return
            val time = times?.getOrNull(item.index)
            if (!byIndex.containsKey(item.index)) {
                byIndex[item.index] = TranslatedLine(
                    item.index,
                    item.text,
                    translated.trim(),
                    time?.first,
                    time?.second
                )
            }
        }

        try {
            val arr = JSONArray(extractJson(response))
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val translated = obj.optString(
                    "translated",
                    obj.optString("translation", obj.optString("text", ""))
                )
                val rawIdx = if (obj.has("idx")) obj.optInt("idx", -1) else obj.optInt("index", -1)
                val item = when {
                    rawIdx in firstIndex..lastIndex -> batch.firstOrNull { it.index == rawIdx }
                    rawIdx in batch.indices -> batch[rawIdx]
                    else -> batch.getOrNull(i)
                } ?: batch.getOrNull(i)
                if (item != null) put(item, translated)
            }
        } catch (e: Exception) {
            Log.w(TAG, "JSON parse failed, falling back to line matching: ${e.message}")
            val lines = response.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("```") }
            for ((i, line) in lines.withIndex()) {
                val item = batch.getOrNull(i) ?: break
                val clean = line.replace(Regex("^\\[?\\d+\\]?\\s*[.:)\\-]?\\s*"), "").trim()
                put(item, clean)
            }
        }

        return byIndex.values.toList()
    }

    private fun extractJson(text: String): String {
        val raw = text.trim()
        val codeBlockRegex = Regex("```(?:json)?\\s*\\[([\\s\\S]*?)\\]\\s*```", RegexOption.IGNORE_CASE)
        codeBlockRegex.find(raw)?.let { return "[" + it.groupValues[1] + "]" }
        val startIdx = raw.indexOf('[')
        if (startIdx >= 0) {
            var depth = 0
            var inString = false
            var escaped = false
            var endIdx = -1
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
