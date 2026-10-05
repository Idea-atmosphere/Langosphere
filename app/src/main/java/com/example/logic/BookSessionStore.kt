package com.example.logic

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.model.BookCheckpoint
import com.example.model.BookSentence
import com.example.model.JsonLesson
import com.example.model.JsonWord
import com.example.model.SentenceHighlight

/**
 * On-device storage for book sessions: the enriched sentences, the reading
 * checkpoint and the highlights, one row per sentence per document.
 *
 * Why a real database rather than preferences: a single book easily reaches
 * several thousand enriched sentences with lessons and word lists attached.
 * Keeping that in `SharedPreferences` would mean serialising and rewriting the
 * entire document on every ingest and every highlight, which is both slow and
 * a reliable way to lose data when the process dies mid-write. A table gives
 * incremental writes, indexed lookups and transactional batches instead.
 *
 * This mirrors [LeitnerBoxManager]'s hand-written [SQLiteOpenHelper] approach
 * rather than introducing a second persistence style into the project.
 *
 * All calls are blocking and must be made off the main thread.
 */
class BookSessionStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_DOCUMENTS (
                doc_key TEXT PRIMARY KEY,
                doc_name TEXT NOT NULL,
                source_language TEXT NOT NULL DEFAULT '',
                target_language TEXT NOT NULL DEFAULT '',
                level TEXT NOT NULL DEFAULT '',
                last_id INTEGER NOT NULL DEFAULT 0,
                last_start INTEGER NOT NULL DEFAULT -1,
                last_location TEXT NOT NULL DEFAULT '',
                last_unit INTEGER NOT NULL DEFAULT 0,
                last_sentence_in_unit INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_SENTENCES (
                doc_key TEXT NOT NULL,
                sentence_id INTEGER NOT NULL,
                anchor_start INTEGER NOT NULL,
                anchor_end INTEGER NOT NULL,
                location TEXT NOT NULL DEFAULT '',
                english TEXT NOT NULL DEFAULT '',
                translation TEXT NOT NULL DEFAULT '',
                level TEXT NOT NULL DEFAULT '',
                difficulty TEXT NOT NULL DEFAULT '',
                pronunciation TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                simplified TEXT NOT NULL DEFAULT '',
                lesson_explanation TEXT NOT NULL DEFAULT '',
                lesson_grammar TEXT NOT NULL DEFAULT '',
                lesson_grammar_translation TEXT NOT NULL DEFAULT '',
                lesson_structure TEXT NOT NULL DEFAULT '',
                words_json TEXT NOT NULL DEFAULT '[]',
                PRIMARY KEY (doc_key, sentence_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_HIGHLIGHTS (
                doc_key TEXT NOT NULL,
                sentence_id INTEGER NOT NULL,
                char_start INTEGER NOT NULL,
                char_end INTEGER NOT NULL,
                color_argb INTEGER NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (doc_key, sentence_id, char_start, char_end)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_sentences_doc ON $TABLE_SENTENCES (doc_key, sentence_id)")
        db.execSQL("CREATE INDEX idx_highlights_doc ON $TABLE_HIGHLIGHTS (doc_key, sentence_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first release of this store. Future migrations are
        // added as explicit steps here; the tables are a derived cache of
        // ingested JSON, so a rebuild is always a safe last resort.
        if (oldVersion < 1) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_HIGHLIGHTS")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_SENTENCES")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_DOCUMENTS")
            onCreate(db)
        }
    }

    // ──────── documents ────────

    /** Registers or refreshes a document row. Safe to call on every open. */
    fun upsertDocument(
        docKey: String,
        docName: String,
        sourceLanguage: String,
        targetLanguage: String,
        level: String,
    ) {
        if (docKey.isBlank()) return
        val values = ContentValues().apply {
            put("doc_key", docKey)
            put("doc_name", docName)
            put("source_language", sourceLanguage)
            put("target_language", targetLanguage)
            put("level", level)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(
            TABLE_DOCUMENTS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun documentKeys(): List<String> {
        val keys = ArrayList<String>()
        readableDatabase.query(
            TABLE_DOCUMENTS,
            arrayOf("doc_key"),
            null,
            null,
            null,
            null,
            "updated_at DESC",
        ).use { cursor ->
            while (cursor.moveToNext()) keys += cursor.getString(0)
        }
        return keys
    }

    fun deleteDocument(docKey: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_HIGHLIGHTS, "doc_key = ?", arrayOf(docKey))
            db.delete(TABLE_SENTENCES, "doc_key = ?", arrayOf(docKey))
            db.delete(TABLE_DOCUMENTS, "doc_key = ?", arrayOf(docKey))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ──────── checkpoint ────────

    fun saveCheckpoint(docKey: String, checkpoint: BookCheckpoint) {
        if (docKey.isBlank()) return
        val values = ContentValues().apply {
            put("last_id", checkpoint.lastId)
            put("last_start", checkpoint.lastStart)
            put("last_location", checkpoint.lastLocation)
            put("last_unit", checkpoint.lastUnitNumber ?: 0)
            put("last_sentence_in_unit", checkpoint.lastSentenceInUnit ?: 0)
            put("updated_at", System.currentTimeMillis())
        }
        val updated = writableDatabase.update(
            TABLE_DOCUMENTS,
            values,
            "doc_key = ?",
            arrayOf(docKey),
        )
        if (updated == 0) {
            // The checkpoint arrived before the document row (an import with no
            // open document); keep it rather than dropping it.
            values.put("doc_key", docKey)
            values.put("doc_name", docKey.substringAfter(':'))
            writableDatabase.insertWithOnConflict(
                TABLE_DOCUMENTS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    fun loadCheckpointWithFallback(docKey: String, legacyKey: String): BookCheckpoint {
        val primary = loadCheckpoint(docKey)
        if (!primary.isEmpty) return primary
        if (legacyKey.isNotBlank() && legacyKey != docKey) {
            return loadCheckpoint(legacyKey)
        }
        return BookCheckpoint.EMPTY
    }

    fun loadCheckpoint(docKey: String): BookCheckpoint {
        readableDatabase.query(
            TABLE_DOCUMENTS,
            arrayOf("last_id", "last_start", "last_location", "last_unit", "last_sentence_in_unit"),
            "doc_key = ?",
            arrayOf(docKey),
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return BookCheckpoint.EMPTY
            return BookCheckpoint(
                lastId = cursor.getInt(0),
                lastStart = cursor.getInt(1),
                lastLocation = cursor.getString(2) ?: "",
                lastUnitNumber = cursor.getInt(3).takeIf { it > 0 },
                lastSentenceInUnit = cursor.getInt(4).takeIf { it > 0 },
            )
        }
    }

    // ──────── sentences ────────

    /**
     * Writes a batch of sentences in one transaction, replacing rows with the
     * same id, and updates checkpoint atomically in same transaction.
     * Mission: parse & validate before write, then transactional write, success only after commit.
     * Callers merge first (see [BookJsonIngest.merge]) so what lands here is already the winning version.
     */
    fun saveSentences(docKey: String, sentences: List<BookSentence>) {
        if (docKey.isBlank() || sentences.isEmpty()) return
        val checkpoint = BookJsonIngest.checkpointOf(sentences)
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Ensure document row exists
            val docValues = ContentValues().apply {
                put("doc_key", docKey)
                put("doc_name", docKey.substringAfter(':').substringBefore(':'))
                put("last_id", checkpoint.lastId)
                put("last_start", checkpoint.lastStart)
                put("last_location", checkpoint.lastLocation)
                put("last_unit", checkpoint.lastUnitNumber ?: 0)
                put("last_sentence_in_unit", checkpoint.lastSentenceInUnit ?: 0)
                put("updated_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict(TABLE_DOCUMENTS, null, docValues, SQLiteDatabase.CONFLICT_IGNORE)
            // Update checkpoint fields
            val cpValues = ContentValues().apply {
                put("last_id", checkpoint.lastId)
                put("last_start", checkpoint.lastStart)
                put("last_location", checkpoint.lastLocation)
                put("last_unit", checkpoint.lastUnitNumber ?: 0)
                put("last_sentence_in_unit", checkpoint.lastSentenceInUnit ?: 0)
                put("updated_at", System.currentTimeMillis())
            }
            db.update(TABLE_DOCUMENTS, cpValues, "doc_key = ?", arrayOf(docKey))

            for (sentence in sentences) {
                val values = ContentValues().apply {
                    put("doc_key", docKey)
                    put("sentence_id", sentence.id)
                    put("anchor_start", sentence.start)
                    put("anchor_end", sentence.end)
                    put("location", sentence.location)
                    put("english", sentence.english)
                    put("translation", sentence.translation.orEmpty())
                    put("level", sentence.level.orEmpty())
                    put("difficulty", sentence.difficulty.orEmpty())
                    put("pronunciation", sentence.pronunciation.orEmpty())
                    put("notes", sentence.notes.orEmpty())
                    put("simplified", sentence.simplified.orEmpty())
                    put("lesson_explanation", sentence.lesson?.explanation.orEmpty())
                    put("lesson_grammar", sentence.lesson?.grammar.orEmpty())
                    put("lesson_grammar_translation", sentence.lesson?.grammarTranslation.orEmpty())
                    put("lesson_structure", sentence.lesson?.structure.orEmpty())
                    put("words_json", encodeWords(sentence.words))
                }
                db.insertWithOnConflict(
                    TABLE_SENTENCES,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Legacy method kept for callers that only need checkpoint update */
    fun saveSentencesLegacy(docKey: String, sentences: List<BookSentence>) {
        saveSentences(docKey, sentences)
    }

    /** Loads sentences, trying legacy key without URI hash as fallback for migration. */
    fun loadSentencesWithFallback(docKey: String, legacyKey: String): List<BookSentence> {
        if (docKey.isBlank()) return emptyList()
        val primary = loadSentences(docKey)
        if (primary.isNotEmpty()) return primary
        if (legacyKey.isNotBlank() && legacyKey != docKey) {
            val legacy = loadSentences(legacyKey)
            if (legacy.isNotEmpty()) {
                // Migrate legacy to new key
                saveSentences(docKey, legacy)
                val cp = loadCheckpoint(legacyKey)
                if (!cp.isEmpty) saveCheckpoint(docKey, cp)
                return legacy
            }
        }
        return emptyList()
    }

    fun loadSentences(docKey: String): List<BookSentence> {
        val result = ArrayList<BookSentence>()
        readableDatabase.query(
            TABLE_SENTENCES,
            null,
            "doc_key = ?",
            arrayOf(docKey),
            null,
            null,
            "sentence_id ASC",
        ).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("sentence_id")
            val startIndex = cursor.getColumnIndexOrThrow("anchor_start")
            val endIndex = cursor.getColumnIndexOrThrow("anchor_end")
            val locationIndex = cursor.getColumnIndexOrThrow("location")
            val englishIndex = cursor.getColumnIndexOrThrow("english")
            val translationIndex = cursor.getColumnIndexOrThrow("translation")
            val levelIndex = cursor.getColumnIndexOrThrow("level")
            val difficultyIndex = cursor.getColumnIndexOrThrow("difficulty")
            val pronunciationIndex = cursor.getColumnIndexOrThrow("pronunciation")
            val notesIndex = cursor.getColumnIndexOrThrow("notes")
            val simplifiedIndex = cursor.getColumnIndexOrThrow("simplified")
            val explanationIndex = cursor.getColumnIndexOrThrow("lesson_explanation")
            val grammarIndex = cursor.getColumnIndexOrThrow("lesson_grammar")
            val grammarTranslationIndex = cursor.getColumnIndexOrThrow("lesson_grammar_translation")
            val structureIndex = cursor.getColumnIndexOrThrow("lesson_structure")
            val wordsIndex = cursor.getColumnIndexOrThrow("words_json")

            while (cursor.moveToNext()) {
                val explanation = cursor.getString(explanationIndex).orEmpty()
                val grammar = cursor.getString(grammarIndex).orEmpty()
                val grammarTranslation = cursor.getString(grammarTranslationIndex).orEmpty()
                val structure = cursor.getString(structureIndex).orEmpty()
                val hasLesson = explanation.isNotBlank() || grammar.isNotBlank() ||
                    grammarTranslation.isNotBlank() || structure.isNotBlank()
                result += BookSentence(
                    id = cursor.getInt(idIndex),
                    start = cursor.getInt(startIndex),
                    end = cursor.getInt(endIndex),
                    location = cursor.getString(locationIndex).orEmpty(),
                    english = cursor.getString(englishIndex).orEmpty(),
                    translation = cursor.getString(translationIndex)?.takeIf { it.isNotBlank() },
                    level = cursor.getString(levelIndex)?.takeIf { it.isNotBlank() },
                    difficulty = cursor.getString(difficultyIndex)?.takeIf { it.isNotBlank() },
                    pronunciation = cursor.getString(pronunciationIndex)?.takeIf { it.isNotBlank() },
                    notes = cursor.getString(notesIndex)?.takeIf { it.isNotBlank() },
                    lesson = if (hasLesson) {
                        JsonLesson(
                            explanation = explanation.takeIf { it.isNotBlank() },
                            grammar = grammar.takeIf { it.isNotBlank() },
                            grammarTranslation = grammarTranslation.takeIf { it.isNotBlank() },
                            structure = structure.takeIf { it.isNotBlank() },
                        )
                    } else {
                        null
                    },
                    words = decodeWords(cursor.getString(wordsIndex).orEmpty()),
                    simplified = cursor.getString(simplifiedIndex)?.takeIf { it.isNotBlank() },
                )
            }
        }
        return result
    }

    fun sentenceCount(docKey: String): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_SENTENCES WHERE doc_key = ?",
            arrayOf(docKey),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    // ──────── highlights ────────

    /**
     * Stores a highlight by sentence id and character offsets — never by
     * anything layout-dependent — which is what lets it survive rotation, a
     * font-size change or a window resize.
     */
    fun saveHighlight(docKey: String, highlight: SentenceHighlight) {
        if (docKey.isBlank() || !highlight.isValid) return
        val values = ContentValues().apply {
            put("doc_key", docKey)
            put("sentence_id", highlight.sentenceId)
            put("char_start", highlight.charStart)
            put("char_end", highlight.charEnd)
            put("color_argb", highlight.colorArgb)
            put("note", highlight.note)
            put("created_at", highlight.createdAt)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_HIGHLIGHTS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun deleteHighlight(docKey: String, highlight: SentenceHighlight) {
        writableDatabase.delete(
            TABLE_HIGHLIGHTS,
            "doc_key = ? AND sentence_id = ? AND char_start = ? AND char_end = ?",
            arrayOf(
                docKey,
                highlight.sentenceId.toString(),
                highlight.charStart.toString(),
                highlight.charEnd.toString(),
            ),
        )
    }

    fun clearHighlights(docKey: String, sentenceId: Int) {
        writableDatabase.delete(
            TABLE_HIGHLIGHTS,
            "doc_key = ? AND sentence_id = ?",
            arrayOf(docKey, sentenceId.toString()),
        )
    }

    fun loadHighlights(docKey: String): List<SentenceHighlight> {
        val result = ArrayList<SentenceHighlight>()
        readableDatabase.query(
            TABLE_HIGHLIGHTS,
            arrayOf("sentence_id", "char_start", "char_end", "color_argb", "note", "created_at"),
            "doc_key = ?",
            arrayOf(docKey),
            null,
            null,
            "sentence_id ASC, char_start ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += SentenceHighlight(
                    sentenceId = cursor.getInt(0),
                    charStart = cursor.getInt(1),
                    charEnd = cursor.getInt(2),
                    colorArgb = cursor.getInt(3),
                    note = cursor.getString(4).orEmpty(),
                    createdAt = cursor.getLong(5),
                )
            }
        }
        return result
    }

    // ──────── word list encoding ────────

    /**
     * Word lists are stored as the same JSON the import format uses. A child
     * table would be more normalised, but word lists are only ever read and
     * written as a whole with their sentence, and keeping the import shape
     * means export is lossless.
     */
    private fun encodeWords(words: List<JsonWord>): String {
        if (words.isEmpty()) return "[]"
        val builder = StringBuilder("[")
        words.forEachIndexed { index, word ->
            if (index > 0) builder.append(',')
            builder.append('{')
            builder.append("\"word\":\"").append(JsonRepairEngine.escape(word.word.orEmpty())).append("\",")
            builder.append("\"translation\":\"").append(JsonRepairEngine.escape(word.translation.orEmpty())).append("\",")
            builder.append("\"partOfSpeech\":\"").append(JsonRepairEngine.escape(word.partOfSpeech.orEmpty())).append("\",")
            builder.append("\"meaningInContext\":\"").append(JsonRepairEngine.escape(word.meaningInContext.orEmpty())).append("\",")
            builder.append("\"extraExplanation\":\"").append(JsonRepairEngine.escape(word.extraExplanation.orEmpty())).append("\",")
            builder.append("\"pronunciation\":\"").append(JsonRepairEngine.escape(word.pronunciation.orEmpty())).append("\",")
            builder.append("\"examples\":[")
            word.examples.forEachIndexed { exampleIndex, example ->
                if (exampleIndex > 0) builder.append(',')
                builder.append('"').append(JsonRepairEngine.escape(example)).append('"')
            }
            builder.append("]}")
        }
        builder.append(']')
        return builder.toString()
    }

    private fun decodeWords(json: String): List<JsonWord> {
        if (json.isBlank() || json == "[]") return emptyList()
        val parsed = JsonRepairEngine.parse(json) ?: return emptyList()
        return parsed.asList().mapNotNull { item ->
            val word = item.stringOrNull("word") ?: return@mapNotNull null
            JsonWord(
                word = word,
                translation = item.stringOrNull("translation"),
                partOfSpeech = item.stringOrNull("partOfSpeech"),
                meaningInContext = item.stringOrNull("meaningInContext"),
                extraExplanation = item.stringOrNull("extraExplanation"),
                examples = item["examples"]?.asList()?.mapNotNull { it.asString() }.orEmpty(),
                pronunciation = item.stringOrNull("pronunciation"),
            )
        }
    }

    companion object {
        private const val DATABASE_NAME = "book_sessions.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_DOCUMENTS = "book_documents"
        private const val TABLE_SENTENCES = "book_sentences"
        private const val TABLE_HIGHLIGHTS = "book_highlights"
    }
}
