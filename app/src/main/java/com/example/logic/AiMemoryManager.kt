package com.example.logic

import android.content.Context
import android.util.Log
import com.example.model.SubtitleEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

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

    /**
     * The JSON learning package prompt. This used to be missing entirely:
     * the app consumed JsonSubtitlePackage files but there was no editable
     * prompt to PRODUCE them, so the most valuable output of the whole app
     * could not be tuned by the user.
     */
    const val PROMPT_JSON_LESSON = "json_lesson"

    /**
     * Every prompt key the settings UI should expose, in a sensible order.
     * Previously only three of the keys were reachable from the UI while the
     * rest silently used their defaults forever.
     */
    val PROMPT_KEYS: List<String> = listOf(
        PROMPT_TRANSLATE,
        PROMPT_JSON_LESSON,
        PROMPT_CHAT,
        PROMPT_AGENT,
        PROMPT_TRANSLATE_EMPTY,
        PROMPT_SYNC_TIMINGS,
        PROMPT_SYNC
    )

    /**
     * Placeholders each prompt supports, so the editor can show them as
     * insertable chips instead of leaving the user to guess.
     */
    val PROMPT_VARIABLES: Map<String, List<String>> = mapOf(
        PROMPT_TRANSLATE to listOf("{LANG}"),
        PROMPT_JSON_LESSON to listOf("{LANG}"),
        PROMPT_CHAT to listOf("{LANG}"),
        PROMPT_AGENT to listOf("{LANG}"),
        PROMPT_TRANSLATE_EMPTY to listOf("{LANG}"),
        PROMPT_SYNC_TIMINGS to listOf("{LANG}"),
        PROMPT_SYNC to emptyList()
    )

    val DEFAULT_PROMPTS = mapOf(
        PROMPT_TRANSLATE to """You are a professional subtitle translator. Translate each line to {LANG}.
Rules:
- Return a JSON array. Each element: {"idx": N, "translated": "translation"}
- "idx" MUST be the exact number shown in brackets for that line
- Return one element for EVERY line you were given, in the same order
- Lines marked as CONTEXT are only background: never translate them
- Preserve the meaning and tone accurately; keep it natural and spoken
- Keep proper nouns and brand names as-is or transliterate appropriately
- Return ONLY the JSON array, no markdown, no explanation""".trimIndent(),

        PROMPT_JSON_LESSON to """You are a language tutor building a study package from subtitles.
For each subtitle line you receive, produce a lesson object in {LANG}.

Return a JSON array. Each element:
{
  "id": "line number as text",
  "start": start time in seconds (number or null),
  "end": end time in seconds (number or null),
  "english": "the original english line, unchanged",
  "translation": "natural {LANG} translation",
  "level": "A1 | A2 | B1 | B2 | C1 | C2",
  "difficulty": "easy | medium | hard",
  "pronunciation": "IPA or simple phonetic reading of the whole line",
  "notes": "one short {LANG} note about tone, slang or culture (optional)",
  "lesson": {
    "explanation": "short {LANG} explanation of what the sentence means",
    "grammar": "the grammar point in english, e.g. present perfect",
    "grammarTranslation": "the same grammar point explained in {LANG}",
    "structure": "the sentence pattern, e.g. subject + have + past participle"
  },
  "words": [
    {
      "word": "the word as it appears",
      "pronunciation": "IPA or simple phonetic reading",
      "partOfSpeech": "noun | verb | adjective | adverb | phrase | idiom | other",
      "translation": "{LANG} meaning",
      "meaningInContext": "what it means in THIS sentence, in {LANG}",
      "extraExplanation": "collocations, register or a common mistake, in {LANG}",
      "examples": ["one short english example sentence"]
    }
  ]
}

Rules:
- Only include words worth studying (skip a, the, is, and, ...)
- 1 to 5 words per line, hardest first
- Never invent a translation you are unsure about; keep it short and honest
- Copy "english", "start" and "end" exactly as given
- Return ONLY the JSON array, no markdown, no explanation""".trimIndent(),

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

        PROMPT_CHAT to "You are a helpful translation and language learning assistant. Answer concisely, in {LANG} unless the user writes in another language.",

        // The language is a placeholder now: this prompt used to hardcode
        // "زبان: فارسی", so picking any other target language in settings
        // silently kept producing Persian.
        PROMPT_AGENT to """You are Agent, a subtitle editor. You can only see the subtitles given to you.
Rules:
- To change a subtitle, answer with JSON: [{"index": 1, "text": "new text"}]
- Only include the lines you changed
- Subtitle language: {LANG}
- For anything that is not an edit, answer normally in {LANG}""".trimIndent(),

        PROMPT_TRANSLATE_EMPTY to """Fill in the missing subtitle translations in {LANG}.
Return ONLY JSON: [{"index": 1, "text": "translation"}]""".trimIndent()
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

    const val CATEGORY_LEARNED_WORD = "learned_word"

    private val idCounter = AtomicLong(0L)

    private fun newId(suffix: String = ""): String {
        // System.currentTimeMillis() alone collided inside tight loops, which
        // made the import de-duplication drop distinct items.
        val id = System.currentTimeMillis().toString() + "_" + idCounter.incrementAndGet()
        return if (suffix.isBlank()) id else id + "_" + suffix
    }

    private fun getMemoryDir(context: Context): File {
        val dir = File(context.filesDir, MEMORY_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getMemoryFile(context: Context, fileName: String): File {
        return File(getMemoryDir(context), fileName)
    }

    /**
     * Maps whatever the settings screen stored into a language name the model
     * understands. The stored value is a DISPLAY name ("فارسی"), not a code,
     * so the old code-only map fell through and injected "فارسی" into an
     * otherwise English prompt.
     */
    fun resolveLangName(targetLang: String): String {
        val key = targetLang.trim()
        return when (key.lowercase()) {
            "fa", "fas", "per", "persian", "farsi" -> "Persian (Farsi)"
            "en", "eng", "english" -> "English"
            "ar", "ara", "arabic" -> "Arabic"
            "tr", "tur", "turkish" -> "Turkish"
            "fr", "fra", "french" -> "French"
            "de", "deu", "ger", "german" -> "German"
            "es", "spa", "spanish" -> "Spanish"
            "it", "ita", "italian" -> "Italian"
            "ru", "rus", "russian" -> "Russian"
            "ja", "jpn", "japanese" -> "Japanese"
            "ko", "kor", "korean" -> "Korean"
            "zh", "zho", "chi", "chinese" -> "Chinese"
            "hi", "hin", "hindi" -> "Hindi"
            else -> when (key) {
                "فارسی", "پارسی" -> "Persian (Farsi)"
                "انگلیسی" -> "English"
                "عربی" -> "Arabic"
                "ترکی", "استانبولی" -> "Turkish"
                "فرانسوی", "فرانسه" -> "French"
                "آلمانی" -> "German"
                "اسپانیایی" -> "Spanish"
                "ایتالیایی" -> "Italian"
                "روسی" -> "Russian"
                "ژاپنی" -> "Japanese"
                "کره‌ای", "کره ای" -> "Korean"
                "چینی" -> "Chinese"
                "هندی" -> "Hindi"
                else -> key.ifBlank { "Persian (Farsi)" }
            }
        }
    }

    fun getPrompt(context: Context, promptKey: String, targetLang: String? = null): String {
        val customPrompts = loadPrompts(context)
        var prompt = customPrompts[promptKey] ?: DEFAULT_PROMPTS[promptKey] ?: ""
        // Always resolve {LANG}: leaving the raw placeholder in the prompt
        // confused the model far more than a sane default would.
        val langName = resolveLangName(targetLang ?: "fa")
        prompt = prompt.replace("{LANG}", langName)
        return prompt
    }

    /** The prompt exactly as stored (placeholders intact) — for the editor. */
    fun getRawPrompt(context: Context, promptKey: String): String {
        return loadPrompts(context)[promptKey] ?: DEFAULT_PROMPTS[promptKey] ?: ""
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
            id = newId(),
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
                id = newId(),
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
        for (s in skills.filter { it.category != CATEGORY_LEARNED_WORD }.take(50)) {
            sb.appendLine("- [${s.category}] ${s.content}")
        }
        sb.appendLine("=== END SKILLS ===\n")
        return sb.toString()
    }

    /**
     * Deletes the auto-generated word pairs. The old co-occurrence learner
     * produced a lot of nonsense ("because → دیگه") and every single entry
     * was then injected into every request, so this is both a cleanup and a
     * cost saving.
     */
    fun purgeNoisyLearnedWords(context: Context): Int {
        val skills = loadSkills(context)
        val kept = skills.filter { it.category != CATEGORY_LEARNED_WORD }
        val removed = skills.size - kept.size
        if (removed > 0) saveSkills(context, kept)
        return removed
    }

    fun addDictNote(context: Context, word: String, note: String) {
        val notes = loadDictNotes(context).toMutableList()
        notes.removeAll { it.word.equals(word, ignoreCase = true) }
        notes.add(DictNote(
            id = newId(),
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

    // ───── Relevance-filtered memory ─────
    // Every request used to carry the ENTIRE memory (50 corrections + 50
    // skills + 30 notes + 30 examples), which is thousands of tokens of
    // mostly irrelevant text per line translated. Now only the parts that
    // share vocabulary with the lines being translated are injected, within
    // a hard character budget.

    private val EN_STOP_WORDS: Set<String> = setOf(
        "the", "and", "you", "that", "this", "for", "are", "but", "with", "have", "was", "were",
        "not", "all", "just", "what", "your", "from", "they", "them", "his", "her", "she", "him",
        "don", "get", "got", "out", "one", "can", "will", "would", "could", "should", "its",
        "too", "then", "there", "here", "when", "who", "why", "how", "been", "has", "had", "did",
        "does", "doing", "about", "some", "any", "more", "most", "very", "our", "their", "were",
        "into", "than", "only", "also", "such", "over", "under", "yeah", "okay", "oh", "uh", "um",
        "gonna", "wanna", "hey", "yes", "let", "now", "well", "like", "know"
    )

    private val FA_STOP_WORDS: Set<String> = setOf(
        "که", "این", "اون", "آن", "برای", "های", "هست", "بود", "شد", "می", "رو", "را", "از", "به",
        "با", "در", "تو", "من", "ما", "شما", "یه", "یک", "نه", "بله", "آره", "خب", "ولی", "اما",
        "چون", "اگر", "هم", "همه", "باید", "کرد", "کنم", "کنی", "کند", "کنیم", "دارم", "داری",
        "دارد", "دیگه", "الان", "وقتی", "چی", "چه", "کی", "کجا", "چرا", "خیلی", "فقط", "بعد",
        "قبل", "مثل", "تا", "یا", "پس", "شده", "نیست", "است", "اون", "اینکه", "باشه", "کنه"
    )

    private fun tokenizeEn(text: String): List<String> {
        return text.lowercase()
            .split(Regex("[^a-zA-Z']+"))
            .filter { it.length > 2 && it !in EN_STOP_WORDS }
    }

    private fun overlapScore(vocab: Set<String>, text: String): Int {
        if (vocab.isEmpty()) return 0
        return tokenizeEn(text).distinct().count { vocab.contains(it) }
    }

    /**
     * Memory selected for THIS request only: user rules always, plus the
     * corrections / glossary entries / notes / examples that actually share
     * words with the lines being translated.
     */
    fun buildRelevantContext(
        context: Context,
        sourceTexts: List<String>,
        maxChars: Int = 3500
    ): String {
        val vocab = sourceTexts.flatMap { tokenizeEn(it) }.toHashSet()
        val sb = StringBuilder()
        fun room(extra: Int): Boolean = sb.length + extra < maxChars

        // Explicit user rules are global instructions, never filtered out.
        val rules = loadSkills(context).filter { it.category != CATEGORY_LEARNED_WORD }
        if (rules.isNotEmpty()) {
            sb.appendLine("=== USER RULES & NOTES ===")
            for (s in rules.take(25)) {
                val line = "- [${s.category}] ${s.content.take(200)}"
                if (!room(line.length)) break
                sb.appendLine(line)
            }
            sb.appendLine()
        }

        if (vocab.isNotEmpty()) {
            val corrections = loadCorrections(context)
                .map { it to overlapScore(vocab, it.sourceText) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(12)
            if (corrections.isNotEmpty()) {
                sb.appendLine("=== RELEVANT CORRECTIONS (do not repeat these mistakes) ===")
                for ((c, _) in corrections) {
                    val line = "- EN: ${c.sourceText.take(120)}\n  WRONG: ${c.wrongTranslation.take(120)}\n  RIGHT: ${c.correctTranslation.take(120)}"
                    if (!room(line.length)) break
                    sb.appendLine(line)
                }
                sb.appendLine()
            }

            val glossary = loadSkills(context)
                .filter { it.category == CATEGORY_LEARNED_WORD }
                .map { it to overlapScore(vocab, it.content) }
                .filter { it.second > 0 }
                .take(20)
            if (glossary.isNotEmpty()) {
                sb.appendLine("=== GLOSSARY ===")
                for ((s, _) in glossary) {
                    val line = "- ${s.content.take(120)}"
                    if (!room(line.length)) break
                    sb.appendLine(line)
                }
                sb.appendLine()
            }

            val notes = loadDictNotes(context)
                .filter { vocab.contains(it.word.lowercase()) }
                .take(15)
            if (notes.isNotEmpty()) {
                sb.appendLine("=== WORD NOTES ===")
                for (n in notes) {
                    val line = "- ${n.word}: ${n.note.take(140)}"
                    if (!room(line.length)) break
                    sb.appendLine(line)
                }
                sb.appendLine()
            }

            val examples = loadLearnedTranslations(context)
                .map { it to overlapScore(vocab, it.sourceText) }
                .filter { it.second >= 2 }
                .sortedByDescending { it.second }
                .take(8)
            if (examples.isNotEmpty()) {
                sb.appendLine("=== STYLE EXAMPLES (match this tone) ===")
                for ((t, _) in examples) {
                    val line = "- EN: ${t.sourceText.take(100)}\n  OUT: ${t.translatedText.take(100)}"
                    if (!room(line.length)) break
                    sb.appendLine(line)
                }
            }
        }

        return sb.toString().trim()
    }

    // ───── Translation cache ─────
    // learned_translations was written but never read back, so the same line
    // was paid for again on every re-run. It is a cache now.

    fun cacheKey(text: String): String =
        text.trim().lowercase().replace(Regex("\\s+"), " ")

    fun loadTranslationCache(context: Context): Map<String, String> {
        val result = HashMap<String, String>()
        for (t in loadLearnedTranslations(context)) {
            if (t.sourceText.isNotBlank() && t.translatedText.isNotBlank()) {
                result[cacheKey(t.sourceText)] = t.translatedText
            }
        }
        return result
    }

    fun findCachedTranslation(context: Context, sourceText: String): String? {
        val key = cacheKey(sourceText)
        return loadLearnedTranslations(context)
            .firstOrNull { cacheKey(it.sourceText) == key && it.translatedText.isNotBlank() }
            ?.translatedText
    }

    /** Stores fresh source→translation pairs so they are free next time. */
    fun saveTranslationPairs(context: Context, pairs: List<Pair<String, String>>): Int {
        if (pairs.isEmpty()) return 0
        val existing = loadLearnedTranslations(context).toMutableList()
        val known = existing.map { cacheKey(it.sourceText) }.toHashSet()
        var added = 0
        for ((source, translated) in pairs) {
            if (source.isBlank() || translated.isBlank()) continue
            val key = cacheKey(source)
            if (known.contains(key)) continue
            known.add(key)
            existing.add(
                LearnedTranslation(
                    id = newId(),
                    sourceText = source,
                    translatedText = translated,
                    createdAt = nowTimestamp()
                )
            )
            added++
        }
        // Keep the cache bounded so the file cannot grow without limit.
        val trimmed = if (existing.size > 4000) existing.takeLast(4000) else existing
        if (added > 0) saveLearnedTranslations(context, trimmed)
        return added
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
        val knownKeys = learned.map { cacheKey(it.sourceText) }.toHashSet()
        var addedCount = 0

        for ((index, pair) in aligned.withIndex()) {
            onProgress?.invoke(index + 1, total)
            val (en, fa) = pair
            if (fa.text.isBlank() || fa.text.contains("ترجمه نشده")) continue
            if (en.text.isBlank()) continue

            val key = cacheKey(en.text)
            if (!knownKeys.contains(key)) {
                knownKeys.add(key)
                learned.add(
                    LearnedTranslation(
                        id = newId(),
                        sourceText = en.text,
                        translatedText = fa.text,
                        createdAt = nowTimestamp()
                    )
                )
                addedCount++
            }
        }

        // Word-pair mining, now much stricter. Before, ANY Persian word that
        // appeared 3 times next to an English word became a permanent
        // "translation", which filled the memory with noise like
        // "because → دیگه". Now function words are dropped on both sides and
        // a candidate must also DOMINATE the alternatives.
        val pairCounts = mutableMapOf<String, MutableMap<String, Int>>()
        val enTotals = mutableMapOf<String, Int>()
        for ((en, fa) in aligned) {
            val enWords = en.text.lowercase()
                .split(Regex("[^a-zA-Z']+"))
                .filter { it.length > 3 && it !in EN_STOP_WORDS }
                .distinct()
            val faWords = fa.text
                .split(Regex("[\\s،.,؛!?\u061F]+"))
                .map { it.trim() }
                .filter { it.length > 2 && it !in FA_STOP_WORDS }
                .distinct()
            if (enWords.isEmpty() || faWords.isEmpty()) continue
            // Very long lines pair almost everything with everything, so they
            // are pure noise for this kind of mining.
            if (enWords.size > 8 || faWords.size > 10) continue

            for (enWord in enWords) {
                enTotals[enWord] = (enTotals[enWord] ?: 0) + 1
                val bucket = pairCounts.getOrPut(enWord) { mutableMapOf() }
                for (faWord in faWords) {
                    bucket[faWord] = (bucket[faWord] ?: 0) + 1
                }
            }
        }

        val skills = loadSkills(context).toMutableList()
        val knownSkills = skills.filter { it.category == CATEGORY_LEARNED_WORD }
            .map { it.content }
            .toHashSet()
        for ((enWord, candidates) in pairCounts) {
            val best = candidates.entries.maxByOrNull { it.value } ?: continue
            val occurrences = enTotals[enWord] ?: 0
            if (occurrences < 4) continue
            if (best.value < 4) continue
            // The candidate must show up with this word in at least 70% of
            // its lines, and be clearly ahead of the runner-up.
            if (best.value.toDouble() / occurrences < 0.7) continue
            val runnerUp = candidates.entries
                .filter { it.key != best.key }
                .maxByOrNull { it.value }?.value ?: 0
            if (best.value < runnerUp * 2) continue

            val skillContent = "$enWord → ${best.key}"
            if (knownSkills.add(skillContent)) {
                skills.add(
                    Skill(
                        id = newId(enWord),
                        category = CATEGORY_LEARNED_WORD,
                        content = skillContent,
                        createdAt = nowTimestamp()
                    )
                )
            }
        }

        // Dictionary notes: the old loop re-read the whole notes file for
        // every word of every line (thousands of file reads). Load once,
        // collect, save once.
        if (dbHelper != null) {
            val notes = loadDictNotes(context).toMutableList()
            val knownWords = notes.map { it.word.lowercase() }.toHashSet()
            val candidateWords = LinkedHashSet<String>()
            for ((en, _) in aligned) {
                en.text.lowercase()
                    .split(Regex("[^a-zA-Z']+"))
                    .filter { it.length > 3 && it !in EN_STOP_WORDS }
                    .forEach { candidateWords.add(it) }
            }
            var noteAdded = false
            for (word in candidateWords) {
                if (knownWords.contains(word)) continue
                val dictEntries = try {
                    dbHelper.getEntriesForWord(word)
                } catch (e: Exception) {
                    emptyList()
                }
                if (dictEntries.isNotEmpty()) {
                    knownWords.add(word)
                    notes.add(
                        DictNote(
                            id = newId(),
                            word = word,
                            note = dictEntries[0].def.take(140),
                            createdAt = nowTimestamp()
                        )
                    )
                    noteAdded = true
                }
            }
            if (noteAdded) saveDictNotes(context, notes)
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

        val words = LinkedHashSet<String>()
        for (en in enList) {
            en.text.lowercase()
                .split(Regex("[^a-zA-Z']+"))
                .filter { it.length > 2 && it !in EN_STOP_WORDS }
                .forEach { words.add(it) }
        }

        val total = words.size
        if (total == 0) return 0

        val notes = loadDictNotes(context).toMutableList()
        val knownWords = notes.map { it.word.lowercase() }.toHashSet()
        var count = 0
        for ((index, word) in words.withIndex()) {
            onProgress?.invoke(index + 1, total)
            if (knownWords.contains(word)) continue
            val dictEntries = try {
                dbHelper.getEntriesForWord(word)
            } catch (e: Exception) {
                emptyList()
            }
            if (dictEntries.isNotEmpty()) {
                knownWords.add(word)
                notes.add(
                    DictNote(
                        id = newId(),
                        word = word,
                        note = dictEntries[0].def.take(200),
                        createdAt = nowTimestamp()
                    )
                )
                count++
            }
        }
        if (count > 0) saveDictNotes(context, notes)

        Log.d(TAG, "Learned $count dictionary notes from $total words")
        return count
    }

    private fun alignSubtitlePairs(
        enList: List<SubtitleEntry>,
        faList: List<SubtitleEntry>
    ): List<Pair<SubtitleEntry, SubtitleEntry>> {
        val result = mutableListOf<Pair<SubtitleEntry, SubtitleEntry>>()
        for (enSub in enList) {
            var bestFa: SubtitleEntry? = null
            var bestDiff = Double.MAX_VALUE
            for (faSub in faList) {
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
            val matched = bestFa
            if (matched != null && matched.text.isNotBlank() && !matched.text.contains("ترجمه نشده")) {
                result.add(Pair(enSub, matched))
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
        for (t in translations.take(20)) {
            sb.appendLine("- EN: ${t.sourceText.take(80)}")
            sb.appendLine("  FA: ${t.translatedText.take(80)}")
        }
        sb.appendLine("=== END LEARNED TRANSLATIONS ===\n")
        return sb.toString()
    }

    fun buildFullContext(context: Context, maxChars: Int = 6000): String {
        val sb = StringBuilder()
        sb.append(buildCorrectionsContext(context))
        sb.append(buildSkillsContext(context))
        sb.append(buildDictNotesContext(context))
        sb.append(buildLearnedTranslationsContext(context))
        val text = sb.toString().trim()
        return if (text.length <= maxChars) text else text.take(maxChars)
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
                        id = obj.optString("id", newId()),
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
                        id = obj.optString("id", newId()),
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
                        id = obj.optString("id", newId()),
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
                        id = obj.optString("id", newId()),
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
        val allSkills = loadSkills(context)
        val rules = allSkills.count { it.category != CATEGORY_LEARNED_WORD }
        val glossary = allSkills.size - rules
        val dictNotes = loadDictNotes(context).size
        val learnedTranslations = loadLearnedTranslations(context).size
        return buildString {
            appendLine("حافظه هوش مصنوعی:")
            appendLine("  پرامپت‌های سفارشی: $prompts")
            appendLine("  اصلاحات: $corrections")
            appendLine("  قوانین و یادداشت‌ها: $rules")
            appendLine("  واژه‌نامه: $glossary")
            appendLine("  یادداشت‌های دیکشنری: $dictNotes")
            appendLine("  ترجمه‌های ذخیره‌شده (کش): $learnedTranslations")
        }
    }

    private fun nowTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }
}
