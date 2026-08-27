package com.example.logic

import android.content.Context
import android.util.Log
import com.example.model.DictionaryEntry
import com.example.model.SubtitleEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AiMemoryManager {

    private const val TAG = "AiMemoryManager"
    private const val MEMORY_DIR = "ai_memory"

    private const val PROMPTS_FILE = "prompts.json"
    private const val CORRECTIONS_FILE = "corrections.json"
    private const val SKILLS_FILE = "skills.json"
    private const val DICT_NOTES_FILE = "dictionary_notes.json"
    private const val LEARNED_TRANSLATIONS_FILE = "learned_translations.json"

    const val PROMPT_TRANSLATE = "translate"
    const val PROMPT_SYNC = "sync"
    const val PROMPT_SYNC_TIMINGS = "sync_timings"
    const val PROMPT_CHAT = "chat"
    const val PROMPT_AGENT = "agent"
    const val PROMPT_TRANSLATE_EMPTY = "translate_empty"

    val DEFAULT_PROMPTS = mapOf(
        PROMPT_TRANSLATE to """You are a professional subtitle translator. Translate each line to {LANG}.
Rules:
- Return a JSON array. Each element: {"idx": N, "translated": "translation"}
- Preserve the meaning and tone accurately
- Keep proper nouns and brand names as-is or transliterate appropriately
- Return ONLY the JSON array, no markdown, no explanation
- Index numbers (idx) must match the source line numbers starting from 0""".trimIndent(),

        PROMPT_SYNC to """You are a subtitle synchronization expert. You receive two subtitle tracks with different timings.
Match each source subtitle with the best matching target subtitle based on meaning.
Return a JSON array where each element has:
- "source_idx": index in source
- "target_idx": index in best matching target (or -1 if no match)
- "source_text": the source subtitle text
- "matched_text": the matched target subtitle text
- "start": the source start time
- "end": the source end time
Return ONLY the JSON array, no other text.""".trimIndent(),

        PROMPT_SYNC_TIMINGS to """You are a subtitle synchronization and translation expert.
You receive English (source) subtitles and {LANG} (target) subtitles with their timestamps.

Your job:
1. For each English subtitle, find the matching {LANG} subtitle by MEANING (not just time).
2. If a match exists but timing differs, use the ENGLISH timing.
3. If NO {LANG} translation exists for an English line, TRANSLATE it to {LANG}.
4. If {LANG} subtitles exist that don't match any English line, DISCARD them.

Return a JSON array. Each element:
{
  "en_idx": index in English list,
  "text": "the {LANG} text (matched or translated)",
  "start": English start time in seconds,
  "end": English end time in seconds
}

Rules:
- Return ONE entry PER English subtitle line (no more, no less)
- Preserve the order of English subtitles
- For translation, keep natural conversational tone
- Keep proper nouns as-is or transliterate
- Return ONLY the JSON array, no other text""".trimIndent(),

        PROMPT_CHAT to "You are a helpful translation and language learning assistant. Answer concisely.",

        PROMPT_AGENT to """تو Agent هستی، مترجم زیرنویس. فقط به زیرنویس‌های زیر دسترسی داری.
قوانین:
- برای تغییر زیرنویس خروجی JSON بده: [{"index": 1, "text": "متن جدید"}]
- فقط خطوط تغییر یافته
- زبان: فارسی""".trimIndent(),

        PROMPT_TRANSLATE_EMPTY to """ترجمه خطوط خالی زیرنویس فارسی. خروجی JSON: [{"index": 1, "text": "ترجمه فارسی"}]""".trimIndent()
    )

    data class Correction(
        val id: String,
        val sourceText: String,
        val wrongTranslation: String,
        val correctTranslation: String,
        val note: String = "",
        val createdAt: String = ""
    )

    data class Skill(
        val id: String,
        val category: String,
        val content: String,
        val createdAt: String = ""
    )

    data class DictNote(
        val id: String,
        val word: String,
        val note: String,
        val createdAt: String = ""
    )

    data class LearnedTranslation(
        val id: String,
        val sourceText: String,
        val translatedText: String,
        val createdAt: String = ""
    )

    private fun getMemoryDir(context: Context): File {
        val dir = File(context.filesDir, MEMORY_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getMemoryFile(context: Context, fileName: String): File {
        return File(getMemoryDir(context), fileName)
    }

    fun getPrompt(context: Context, promptKey: String, targetLang: String? = null): String {
        val customPrompts = loadPrompts(context)
        var prompt = customPrompts[promptKey] ?: DEFAULT_PROMPTS[promptKey] ?: ""

        if (targetLang != null) {
            val langName = when (targetLang) {
                "fa" -> "Persian (Farsi)"
                "ar" -> "Arabic"
                "tr" -> "Turkish"
                "fr" -> "French"
                "de" -> "German"
                "es" -> "Spanish"
                "ja" -> "Japanese"
                "ko" -> "Korean"
                "zh" -> "Chinese"
                else -> targetLang
            }
            prompt = prompt.replace("{LANG}", langName)
        }

        return prompt
    }

    fun savePrompt(context: Context, promptKey: String, prompt: String) {
        val prompts = loadPrompts(context).toMutableMap()
        prompts[promptKey] = prompt
        savePrompts(context, prompts)
    }

    fun resetPrompt(context: Context, promptKey: String) {
        val prompts = loadPrompts(context).toMutableMap()
        prompts.remove(promptKey)
        savePrompts(context, prompts)
    }

    fun getCustomPrompts(context: Context): Map<String, String> {
        return loadPrompts(context)
    }

    fun hasCustomPrompt(context: Context, promptKey: String): Boolean {
        return loadPrompts(context).containsKey(promptKey)
    }

    private fun loadPrompts(context: Context): Map<String, String> {
        val file = getMemoryFile(context, PROMPTS_FILE)
        if (!file.exists()) return emptyMap()
        return try {
            val json = JSONObject(file.readText())
            val result = mutableMapOf<String, String>()
            for (key in json.keys()) {
                result[key] = json.getString(key)
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load prompts: ${e.message}")
            emptyMap()
        }
    }

    private fun savePrompts(context: Context, prompts: Map<String, String>) {
        val file = getMemoryFile(context, PROMPTS_FILE)
        val json = JSONObject()
        for ((key, value) in prompts) {
            json.put(key, value)
        }
        file.writeText(json.toString(2))
    }

    fun addCorrection(context: Context, sourceText: String, wrongTranslation: String, correctTranslation: String, note: String = "") {
        val corrections = loadCorrections(context).toMutableList()
        corrections.removeAll { it.sourceText == sourceText && it.wrongTranslation == wrongTranslation }
        corrections.add(Correction(
            id = System.currentTimeMillis().toString(),
            sourceText = sourceText,
            wrongTranslation = wrongTranslation,
            correctTranslation = correctTranslation,
            note = note,
            createdAt = nowTimestamp()
        ))
        saveCorrections(context, corrections)
        Log.d(TAG, "Correction added: '$sourceText' → '$correctTranslation'")
    }

    fun removeCorrection(context: Context, correctionId: String) {
        val corrections = loadCorrections(context).toMutableList()
        corrections.removeAll { it.id == correctionId }
        saveCorrections(context, corrections)
    }

    fun loadCorrections(context: Context): List<Correction> {
        val file = getMemoryFile(context, CORRECTIONS_FILE)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Correction(
                    id = obj.getString("id"),
                    sourceText = obj.getString("sourceText"),
                    wrongTranslation = obj.getString("wrongTranslation"),
                    correctTranslation = obj.getString("correctTranslation"),
                    note = obj.optString("note", ""),
                    createdAt = obj.optString("createdAt", "")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load corrections: ${e.message}")
            emptyList()
        }
    }

    private fun saveCorrections(context: Context, corrections: List<Correction>) {
        val file = getMemoryFile(context, CORRECTIONS_FILE)
        val arr = JSONArray()
        for (c in corrections) {
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("sourceText", c.sourceText)
                put("wrongTranslation", c.wrongTranslation)
                put("correctTranslation", c.correctTranslation)
                put("note", c.note)
                put("createdAt", c.createdAt)
            })
        }
        file.writeText(arr.toString(2))
    }

    fun buildCorrectionsContext(context: Context): String {
        val corrections = loadCorrections(context)
        if (corrections.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("\n=== CORRECTIONS (avoid these mistakes) ===")
        for (c in corrections.take(50)) {
            sb.appendLine("- Source: ${c.sourceText}")
            sb.appendLine("  WRONG: ${c.wrongTranslation}")
            sb.appendLine("  RIGHT: ${c.correctTranslation}")
            if (c.note.isNotBlank()) sb.appendLine("  Note: ${c.note}")
        }
        if (corrections.size > 50) sb.appendLine("... +${corrections.size - 50} more corrections")
        sb.appendLine("=== END CORRECTIONS ===\n")
        return sb.toString()
    }

    fun addSkill(context: Context, category: String, content: String) {
        val skills = loadSkills(context).toMutableList()
        if (skills.none { it.category == category && it.content == content }) {
            skills.add(Skill(
                id = System.currentTimeMillis().toString(),
                category = category,
                content = content,
                createdAt = nowTimestamp()
            ))
            saveSkills(context, skills)
            Log.d(TAG, "Skill added: [$category] $content")
        }
    }

    fun removeSkill(context: Context, skillId: String) {
        val skills = loadSkills(context).toMutableList()
        skills.removeAll { it.id == skillId }
        saveSkills(context, skills)
    }

    fun loadSkills(context: Context): List<Skill> {
        val file = getMemoryFile(context, SKILLS_FILE)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Skill(
                    id = obj.getString("id"),
                    category = obj.optString("category", ""),
                    content = obj.getString("content"),
                    createdAt = obj.optString("createdAt", "")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load skills: ${e.message}")
            emptyList()
        }
    }

    private fun saveSkills(context: Context, skills: List<Skill>) {
        val file = getMemoryFile(context, SKILLS_FILE)
        val arr = JSONArray()
        for (s in skills) {
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("category", s.category)
                put("content", s.content)
                put("createdAt", s.createdAt)
            })
        }
        file.writeText(arr.toString(2))
    }

    fun buildSkillsContext(context: Context): String {
        val skills = loadSkills(context)
        if (skills.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("\n=== LEARNED SKILLS & NOTES ===")
        for (s in skills.take(50)) {
            sb.appendLine("- [${s.category}] ${s.content}")
        }
        if (skills.size > 50) sb.appendLine("... +${skills.size - 50} more skills")
        sb.appendLine("=== END SKILLS ===\n")
        return sb.toString()
    }

    fun addDictNote(context: Context, word: String, note: String) {
        val notes = loadDictNotes(context).toMutableList()
        notes.removeAll { it.word.equals(word, ignoreCase = true) }
        notes.add(DictNote(
            id = System.currentTimeMillis().toString(),
            word = word,
            note = note,
            createdAt = nowTimestamp()
        ))
        saveDictNotes(context, notes)
        Log.d(TAG, "Dict note added for '$word'")
    }

    fun removeDictNote(context: Context, noteId: String) {
        val notes = loadDictNotes(context).toMutableList()
        notes.removeAll { it.id == noteId }
        saveDictNotes(context, notes)
    }

    fun loadDictNotes(context: Context): List<DictNote> {
        val file = getMemoryFile(context, DICT_NOTES_FILE)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                DictNote(
                    id = obj.getString("id"),
                    word = obj.getString("word"),
                    note = obj.getString("note"),
                    createdAt = obj.optString("createdAt", "")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load dict notes: ${e.message}")
            emptyList()
        }
    }

    private fun saveDictNotes(context: Context, notes: List<DictNote>) {
        val file = getMemoryFile(context, DICT_NOTES_FILE)
        val arr = JSONArray()
        for (n in notes) {
            arr.put(JSONObject().apply {
                put("id", n.id)
                put("word", n.word)
                put("note", n.note)
                put("createdAt", n.createdAt)
            })
        }
        file.writeText(arr.toString(2))
    }

    fun buildDictNotesContext(context: Context): String {
        val notes = loadDictNotes(context)
        if (notes.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("\n=== DICTIONARY NOTES ===")
        for (n in notes.take(30)) {
            sb.appendLine("- ${n.word}: ${n.note}")
        }
        if (notes.size > 30) sb.appendLine("... +${notes.size - 30} more notes")
        sb.appendLine("=== END DICT NOTES ===\n")
        return sb.toString()
    }

    fun learnFromSubtitles(
        context: Context,
        enList: List<SubtitleEntry>,
        faList: List<SubtitleEntry>,
        dbHelper: DictionaryDatabaseHelper? = null,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Int {
        if (enList.isEmpty() || faList.isEmpty()) return 0

        val aligned = alignSubtitlePairs(enList, faList)
        if (aligned.isEmpty()) return 0

        val total = aligned.size
        val learned = loadLearnedTranslations(context).toMutableList()
        var addedCount = 0

        for ((index, pair) in aligned.withIndex()) {
            onProgress?.invoke(index + 1, total)
            val (en, fa) = pair
            if (fa.text.isBlank() || fa.text.contains("ترجمه نشده")) continue
            if (en.text.isBlank()) continue

            if (learned.none { it.sourceText.equals(en.text, ignoreCase = true) }) {
                learned.add(LearnedTranslation(
                    id = System.currentTimeMillis().toString() + "_${addedCount}",
                    sourceText = en.text,
                    translatedText = fa.text,
                    createdAt = nowTimestamp()
                ))
                addedCount++
            }
        }

        val wordPatterns = mutableMapOf<String, MutableMap<String, Int>>()
        for ((en, fa) in aligned) {
            val enWords = en.text.lowercase().split(Regex("[^a-zA-Z']+"))
                .filter { it.length > 2 }
            val faWords = fa.text.split(Regex("[\\s،.,؛!?]+"))
                .filter { it.length > 1 }

            for (enWord in enWords.distinct()) {
                if (!wordPatterns.containsKey(enWord)) {
                    wordPatterns[enWord] = mutableMapOf()
                }
                for (faWord in faWords.distinct()) {
                    wordPatterns[enWord]!![faWord] = (wordPatterns[enWord]!![faWord] ?: 0) + 1
                }
            }
        }

        val skills = loadSkills(context).toMutableList()
        for ((enWord, faCandidates) in wordPatterns) {
            val sorted = faCandidates.entries.sortedByDescending { it.value }
            if (sorted.isNotEmpty() && sorted[0].value >= 3) {
                val bestFa = sorted[0].key
                val skillContent = "$enWord → $bestFa"
                if (skills.none { it.category == "learned_word" && it.content == skillContent }) {
                    skills.add(Skill(
                        id = System.currentTimeMillis().toString() + "_w_${enWord}",
                        category = "learned_word",
                        content = skillContent,
                        createdAt = nowTimestamp()
                    ))
                }
            }
        }

        if (dbHelper != null) {
            for ((en, fa) in aligned) {
                val enWords = en.text.lowercase().split(Regex("[^a-zA-Z']+"))
                    .filter { it.length > 2 }
                for (word in enWords.distinct()) {
                    val dictEntries = dbHelper.getEntriesForWord(word)
                    if (dictEntries.isNotEmpty()) {
                        val def = dictEntries[0].def.take(100)
                        val notes = loadDictNotes(context)
                        if (notes.none { it.word.equals(word, ignoreCase = true) }) {
                            addDictNote(context, word, def)
                        }
                    }
                }
            }
        }

        saveLearnedTranslations(context, learned)
        saveSkills(context, skills)
        Log.d(TAG, "Learned $addedCount translation pairs and extracted word patterns")
        return addedCount
    }

    fun learnFromDictionary(
        context: Context,
        enList: List<SubtitleEntry>,
        dbHelper: DictionaryDatabaseHelper,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Int {
        if (enList.isEmpty()) return 0

        val words = mutableSetOf<String>()
        for (en in enList) {
            val enWords = en.text.lowercase().split(Regex("[^a-zA-Z']+"))
                .filter { it.length > 2 }
            words.addAll(enWords)
        }

        val total = words.size
        if (total == 0) return 0

        var count = 0
        for ((index, word) in words.withIndex()) {
            onProgress?.invoke(index + 1, total)
            val dictEntries = dbHelper.getEntriesForWord(word)
            if (dictEntries.isNotEmpty()) {
                val def = dictEntries[0].def.take(200)
                val notes = loadDictNotes(context)
                if (notes.none { it.word.equals(word, ignoreCase = true) }) {
                    addDictNote(context, word, def)
                    count++
                }
            }
        }

        Log.d(TAG, "Learned $count dictionary notes from $total words")
        return count
    }

    private fun alignSubtitlePairs(
        enList: List<SubtitleEntry>,
        faList: List<SubtitleEntry>
    ): List<Pair<SubtitleEntry, SubtitleEntry>> {
        val result = mutableListOf<Pair<SubtitleEntry, SubtitleEntry>>()
        var faIdx = 0
        for (enSub in enList) {
            var bestFa: SubtitleEntry? = null
            var bestDiff = Double.MAX_VALUE
            for (i in faIdx until faList.size) {
                val faSub = faList[i]
                val diff = Math.abs(faSub.start - enSub.start)
                if (diff < 3.0) {
                    if (diff < bestDiff) {
                        bestDiff = diff
                        bestFa = faSub
                    }
                } else if (faSub.start > enSub.start + 3.0) {
                    break
                }
            }
            if (bestFa != null && bestFa.text.isNotBlank() && !bestFa.text.contains("ترجمه نشده")) {
                result.add(Pair(enSub, bestFa!!))
            }
        }
        return result
    }

    fun loadLearnedTranslations(context: Context): List<LearnedTranslation> {
        val file = getMemoryFile(context, LEARNED_TRANSLATIONS_FILE)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                LearnedTranslation(
                    id = obj.getString("id"),
                    sourceText = obj.getString("sourceText"),
                    translatedText = obj.getString("translatedText"),
                    createdAt = obj.optString("createdAt", "")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load learned translations: ${e.message}")
            emptyList()
        }
    }

    private fun saveLearnedTranslations(context: Context, translations: List<LearnedTranslation>) {
        val file = getMemoryFile(context, LEARNED_TRANSLATIONS_FILE)
        val arr = JSONArray()
        for (t in translations) {
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("sourceText", t.sourceText)
                put("translatedText", t.translatedText)
                put("createdAt", t.createdAt)
            })
        }
        file.writeText(arr.toString(2))
    }

    fun buildLearnedTranslationsContext(context: Context): String {
        val translations = loadLearnedTranslations(context)
        if (translations.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("\n=== LEARNED TRANSLATION EXAMPLES ===")
        for (t in translations.take(30)) {
            sb.appendLine("- EN: ${t.sourceText.take(80)}")
            sb.appendLine("  FA: ${t.translatedText.take(80)}")
        }
        if (translations.size > 30) sb.appendLine("... +${translations.size - 30} more examples")
        sb.appendLine("=== END LEARNED TRANSLATIONS ===\n")
        return sb.toString()
    }

    fun buildFullContext(context: Context): String {
        val sb = StringBuilder()
        sb.append(buildCorrectionsContext(context))
        sb.append(buildSkillsContext(context))
        sb.append(buildDictNotesContext(context))
        sb.append(buildLearnedTranslationsContext(context))
        return sb.toString().trim()
    }

    fun hasMemoryData(context: Context): Boolean {
        return loadCorrections(context).isNotEmpty() ||
               loadSkills(context).isNotEmpty() ||
               loadDictNotes(context).isNotEmpty() ||
               loadPrompts(context).isNotEmpty() ||
               loadLearnedTranslations(context).isNotEmpty()
    }

    fun autoLearnFromCorrection(
        context: Context,
        originalText: String,
        aiTranslation: String,
        userCorrectedTranslation: String
    ): Int {
        if (aiTranslation == userCorrectedTranslation) return 0
        if (userCorrectedTranslation.isBlank()) return 0

        addCorrection(
            context = context,
            sourceText = originalText,
            wrongTranslation = aiTranslation,
            correctTranslation = userCorrectedTranslation,
            note = "Auto-learned from user correction"
        )
        return 1
    }

    fun parseUserNote(context: Context, userMessage: String): Boolean {
        val trimmed = userMessage.trim()
        val notePrefixes = listOf("یادداشت:", "note:", "نکته:", "قانون:", "rule:", "skill:", "مهارت:")
        for (prefix in notePrefixes) {
            if (trimmed.lowercase().startsWith(prefix)) {
                val content = trimmed.substring(prefix.length).trim()
                if (content.isNotBlank()) {
                    val category = when {
                        prefix.lowercase().contains("قانون") || prefix.lowercase().contains("rule") -> "translation_rule"
                        prefix.lowercase().contains("مهارت") || prefix.lowercase().contains("skill") -> "skill"
                        else -> "user_note"
                    }
                    addSkill(context, category, content)
                    return true
                }
            }
        }
        return false
    }

    fun exportToFile(context: Context, destFile: File): File {
        val exportData = JSONObject().apply {
            put("version", 1)
            put("exportedAt", nowTimestamp())
            put("prompts", JSONObject(loadPrompts(context)))
            put("corrections", JSONArray().also { arr ->
                loadCorrections(context).forEach { c ->
                    arr.put(JSONObject().apply {
                        put("id", c.id)
                        put("sourceText", c.sourceText)
                        put("wrongTranslation", c.wrongTranslation)
                        put("correctTranslation", c.correctTranslation)
                        put("note", c.note)
                        put("createdAt", c.createdAt)
                    })
                }
            })
            put("skills", JSONArray().also { arr ->
                loadSkills(context).forEach { s ->
                    arr.put(JSONObject().apply {
                        put("id", s.id)
                        put("category", s.category)
                        put("content", s.content)
                        put("createdAt", s.createdAt)
                    })
                }
            })
            put("dictNotes", JSONArray().also { arr ->
                loadDictNotes(context).forEach { n ->
                    arr.put(JSONObject().apply {
                        put("id", n.id)
                        put("word", n.word)
                        put("note", n.note)
                        put("createdAt", n.createdAt)
                    })
                }
            })
            put("learnedTranslations", JSONArray().also { arr ->
                loadLearnedTranslations(context).forEach { t ->
                    arr.put(JSONObject().apply {
                        put("id", t.id)
                        put("sourceText", t.sourceText)
                        put("translatedText", t.translatedText)
                        put("createdAt", t.createdAt)
                    })
                }
            })
        }

        destFile.writeText(exportData.toString(2))
        Log.d(TAG, "Memory exported to ${destFile.absolutePath}")
        return destFile
    }

    fun exportToString(context: Context): String {
        val tempFile = File(context.cacheDir, "ai_memory_export.json")
        exportToFile(context, tempFile)
        return tempFile.readText()
    }

    fun importFromFile(context: Context, sourceFile: File, mergeMode: String = "merge"): Int {
        if (!sourceFile.exists()) {
            Log.w(TAG, "Import file not found: ${sourceFile.absolutePath}")
            return 0
        }
        return importFromString(context, sourceFile.readText(), mergeMode)
    }

    fun importFromString(context: Context, jsonStr: String, mergeMode: String = "merge"): Int {
        var count = 0
        try {
            val root = JSONObject(jsonStr)

            if (root.has("prompts")) {
                val promptsObj = root.getJSONObject("prompts")
                if (mergeMode == "replace") {
                    savePrompts(context, emptyMap())
                }
                val existing = if (mergeMode == "merge") loadPrompts(context).toMutableMap() else mutableMapOf()
                for (key in promptsObj.keys()) {
                    existing[key] = promptsObj.getString(key)
                    count++
                }
                savePrompts(context, existing)
            }

            if (root.has("corrections")) {
                val corrArr = root.getJSONArray("corrections")
                val existing = if (mergeMode == "merge") loadCorrections(context).toMutableList() else mutableListOf()
                if (mergeMode == "replace") existing.clear()
                for (i in 0 until corrArr.length()) {
                    val obj = corrArr.getJSONObject(i)
                    val correction = Correction(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        sourceText = obj.getString("sourceText"),
                        wrongTranslation = obj.getString("wrongTranslation"),
                        correctTranslation = obj.getString("correctTranslation"),
                        note = obj.optString("note", ""),
                        createdAt = obj.optString("createdAt", "")
                    )
                    if (mergeMode == "merge" && existing.none { it.id == correction.id }) {
                        existing.add(correction)
                    } else if (mergeMode == "replace") {
                        existing.add(correction)
                    }
                    count++
                }
                saveCorrections(context, existing)
            }

            if (root.has("skills")) {
                val skillsArr = root.getJSONArray("skills")
                val existing = if (mergeMode == "merge") loadSkills(context).toMutableList() else mutableListOf()
                if (mergeMode == "replace") existing.clear()
                for (i in 0 until skillsArr.length()) {
                    val obj = skillsArr.getJSONObject(i)
                    val skill = Skill(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        category = obj.optString("category", ""),
                        content = obj.getString("content"),
                        createdAt = obj.optString("createdAt", "")
                    )
                    if (mergeMode == "merge" && existing.none { it.id == skill.id }) {
                        existing.add(skill)
                    } else if (mergeMode == "replace") {
                        existing.add(skill)
                    }
                    count++
                }
                saveSkills(context, existing)
            }

            if (root.has("dictNotes")) {
                val notesArr = root.getJSONArray("dictNotes")
                val existing = if (mergeMode == "merge") loadDictNotes(context).toMutableList() else mutableListOf()
                if (mergeMode == "replace") existing.clear()
                for (i in 0 until notesArr.length()) {
                    val obj = notesArr.getJSONObject(i)
                    val note = DictNote(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        word = obj.getString("word"),
                        note = obj.getString("note"),
                        createdAt = obj.optString("createdAt", "")
                    )
                    if (mergeMode == "merge" && existing.none { it.id == note.id }) {
                        existing.add(note)
                    } else if (mergeMode == "replace") {
                        existing.add(note)
                    }
                    count++
                }
                saveDictNotes(context, existing)
            }

            if (root.has("learnedTranslations")) {
                val ltArr = root.getJSONArray("learnedTranslations")
                val existing = if (mergeMode == "merge") loadLearnedTranslations(context).toMutableList() else mutableListOf()
                if (mergeMode == "replace") existing.clear()
                for (i in 0 until ltArr.length()) {
                    val obj = ltArr.getJSONObject(i)
                    val lt = LearnedTranslation(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        sourceText = obj.getString("sourceText"),
                        translatedText = obj.getString("translatedText"),
                        createdAt = obj.optString("createdAt", "")
                    )
                    if (mergeMode == "merge" && existing.none { it.id == lt.id }) {
                        existing.add(lt)
                    } else if (mergeMode == "replace") {
                        existing.add(lt)
                    }
                    count++
                }
                saveLearnedTranslations(context, existing)
            }

            Log.d(TAG, "Imported $count items (mode=$mergeMode)")
        } catch (e: Exception) {
            Log.e(TAG, "Import failed: ${e.message}", e)
        }
        return count
    }

    fun clearAll(context: Context) {
        val dir = getMemoryDir(context)
        dir.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "All memory data cleared")
    }

    fun getMemorySummary(context: Context): String {
        val prompts = loadPrompts(context).size
        val corrections = loadCorrections(context).size
        val skills = loadSkills(context).size
        val dictNotes = loadDictNotes(context).size
        val learnedTranslations = loadLearnedTranslations(context).size
        return buildString {
            appendLine("حافظه هوش مصنوعی:")
            appendLine("  پرامپت‌های سفارشی: $prompts")
            appendLine("  اصلاحات: $corrections")
            appendLine("  مهارت‌ها/یادداشت‌ها: $skills")
            appendLine("  یادداشت‌های دیکشنری: $dictNotes")
            appendLine("  ترجمه‌های یادگرفته: $learnedTranslations")
        }
    }

    private fun nowTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }
}