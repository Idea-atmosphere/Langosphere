package com.example.logic

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.model.LeitnerCard
import java.util.concurrent.TimeUnit

/**
 * Stores the user's "Leitner box" flashcards in their own local SQLite
 * database (separate from the dictionary database), and implements the
 * classic 5-box Leitner spaced-repetition schedule:
 *   box 1 -> due again after 1 day
 *   box 2 -> due again after 2 days
 *   box 3 -> due again after 4 days
 *   box 4 -> due again after 8 days
 *   box 5 -> due again after 16 days (mastered box)
 * A correct answer promotes a card to the next box (capped at 5); a wrong
 * answer resets it to box 1 and makes it due again soon.
 */
class LeitnerBoxManager(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "LeitnerBoxManager"

        private const val DATABASE_NAME = "leitner_box.db"

        /**
         * Bumped from 1 to 2 because databases written by earlier builds are
         * already at version 2 on real devices. Opening one of those with a
         * lower number made SQLiteOpenHelper's default onDowngrade() throw
         * ("Can't downgrade database from version 2 to 1"), which crashed the
         * app during startup before any screen was drawn.
         *
         * Never lower this number again — and note that the crash is no longer
         * possible either way, because onDowngrade below repairs the schema
         * instead of throwing.
         *
         * v3 adds the sentence-card columns (kind, source, translation,
         * pronunciation, lesson_text, words_text, location). They are added by
         * the reconciliation below, so an existing box keeps every card and
         * every review date it had; a card written by v2 simply reads as a
         * WORD card from the dictionary, which is exactly what it is.
         */
        private const val DATABASE_VERSION = 3

        private const val TABLE_NAME = "leitner_cards"

        private const val COLUMN_ID = "id"
        private const val COLUMN_WORD = "word"
        private const val COLUMN_WORD_KEY = "word_key"
        private const val COLUMN_DEFINITION = "definition"
        private const val COLUMN_BOX_LEVEL = "box_level"
        private const val COLUMN_NEXT_REVIEW = "next_review"
        private const val COLUMN_CREATED_AT = "created_at"
        private const val COLUMN_KIND = "kind"
        private const val COLUMN_SOURCE = "source"
        private const val COLUMN_TRANSLATION = "translation"
        private const val COLUMN_PRONUNCIATION = "pronunciation"
        private const val COLUMN_LESSON_TEXT = "lesson_text"
        private const val COLUMN_WORDS_TEXT = "words_text"
        private const val COLUMN_LOCATION = "location"

        /**
         * Columns this build reads and writes, with the type used when one has
         * to be added to an older table. COLUMN_ID is deliberately absent: it
         * is the primary key and cannot be added by ALTER TABLE, so a table
         * without it has to be rebuilt.
         */
        private val COLUMN_TYPES: Map<String, String> = linkedMapOf(
            COLUMN_WORD to "TEXT",
            COLUMN_WORD_KEY to "TEXT",
            COLUMN_DEFINITION to "TEXT",
            COLUMN_BOX_LEVEL to "INTEGER",
            COLUMN_NEXT_REVIEW to "INTEGER",
            COLUMN_CREATED_AT to "INTEGER",
            COLUMN_KIND to "TEXT",
            COLUMN_SOURCE to "TEXT",
            COLUMN_TRANSLATION to "TEXT",
            COLUMN_PRONUNCIATION to "TEXT",
            COLUMN_LESSON_TEXT to "TEXT",
            COLUMN_WORDS_TEXT to "TEXT",
            COLUMN_LOCATION to "TEXT"
        )

        /** Key prefix that keeps sentence cards from colliding with word cards. */
        private const val SENTENCE_KEY_PREFIX = "s:"

        /**
         * The lookup key of a card.
         *
         * Word cards keep the exact key format earlier builds wrote
         * (`word.lowercase().trim()`), so their uniqueness and the
         * "already in the box" checks keep working against an existing
         * database. Sentence cards live under `s:<source>:<text>`: a whole
         * sentence is not a word, and the same line can legitimately appear in
         * a book and in a film with different lessons.
         */
        internal fun keyFor(kind: String, source: String, text: String): String {
            val normalized = LeitnerCard.normalizeFront(text)
            return if (kind == LeitnerCard.KIND_SENTENCE) {
                "$SENTENCE_KEY_PREFIX$source:$normalized"
            } else {
                normalized
            }
        }

        private val BOX_INTERVAL_DAYS = longArrayOf(1, 2, 4, 8, 16)
        const val MAX_BOX_LEVEL = 5
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_WORD TEXT,
                $COLUMN_WORD_KEY TEXT UNIQUE,
                $COLUMN_DEFINITION TEXT,
                $COLUMN_BOX_LEVEL INTEGER,
                $COLUMN_NEXT_REVIEW INTEGER,
                $COLUMN_CREATED_AT INTEGER,
                $COLUMN_KIND TEXT,
                $COLUMN_SOURCE TEXT,
                $COLUMN_TRANSLATION TEXT,
                $COLUMN_PRONUNCIATION TEXT,
                $COLUMN_LESSON_TEXT TEXT,
                $COLUMN_WORDS_TEXT TEXT,
                $COLUMN_LOCATION TEXT
            )
            """.trimIndent()
        )
    }

    /**
     * Upgrades used to DROP the table and recreate it, which silently deleted
     * every flashcard the user had built up. Now the stored table is inspected
     * and only what is actually missing gets added.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        reconcileSchema(db)
    }

    /**
     * Opening a database written by a NEWER build must never be fatal: users
     * install release APKs over debug builds, roll back to an older release, or
     * restore a backup, and none of that justifies killing the app on launch.
     * The stored table is repaired if needed and otherwise left alone —
     * columns a newer build added are simply ignored here.
     */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w(TAG, "Opening a newer $DATABASE_NAME (v$oldVersion) with v$newVersion; reconciling instead of failing")
        reconcileSchema(db)
    }

    private fun reconcileSchema(db: SQLiteDatabase) {
        val existing = readColumnNames(db)

        if (existing.isEmpty()) {
            // No table at all (or it was unreadable): create a fresh one.
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            onCreate(db)
            return
        }

        if (!existing.contains(COLUMN_ID)) {
            // Without the primary key the table cannot be repaired in place.
            Log.w(TAG, "$TABLE_NAME has no '$COLUMN_ID' column; rebuilding it")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            onCreate(db)
            return
        }

        for ((column, type) in COLUMN_TYPES) {
            if (existing.contains(column)) continue
            try {
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $column $type")
            } catch (e: Exception) {
                Log.w(TAG, "Could not add column '$column': ${e.message}")
            }
        }
    }

    private fun readColumnNames(db: SQLiteDatabase): Set<String> {
        val names = mutableSetOf<String>()
        try {
            db.rawQuery("PRAGMA table_info(\"$TABLE_NAME\")", null).use { c ->
                val nameIdx = c.getColumnIndex("name")
                if (nameIdx < 0) return emptySet()
                while (c.moveToNext()) {
                    c.getString(nameIdx)?.let { names.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read $TABLE_NAME schema: ${e.message}")
            return emptySet()
        }
        return names
    }

    /** Reads a text column that may be NULL, or may not exist at all (-1). */
    private fun Int.readText(cursor: Cursor): String? =
        if (this < 0 || cursor.isNull(this)) null else cursor.getString(this)

    fun containsWord(word: String): Boolean {
        val db = readableDatabase
        val cursor = db.query(TABLE_NAME, arrayOf(COLUMN_ID), "$COLUMN_WORD_KEY = ?", arrayOf(word.lowercase().trim()), null, null, null)
        return cursor.use { it.moveToFirst() }
    }

    /**
     * Adds a new card for [word], or (if it already exists) just refreshes its
     * stored [definition] without touching its box/review progress.
     * Returns true only when a brand-new card was inserted.
     */
    fun addCard(word: String, definition: String): Boolean {
        val db = writableDatabase
        val wordKey = word.lowercase().trim()
        val now = System.currentTimeMillis()
        val existing = db.query(TABLE_NAME, arrayOf(COLUMN_ID), "$COLUMN_WORD_KEY = ?", arrayOf(wordKey), null, null, null)
        val exists = existing.use { it.moveToFirst() }
        if (exists) {
            val values = ContentValues().apply {
                put(COLUMN_DEFINITION, definition)
                put(COLUMN_KIND, LeitnerCard.KIND_WORD)
            }
            db.update(TABLE_NAME, values, "$COLUMN_WORD_KEY = ?", arrayOf(wordKey))
            return false
        }
        val values = ContentValues().apply {
            put(COLUMN_WORD, word.trim())
            put(COLUMN_WORD_KEY, wordKey)
            put(COLUMN_DEFINITION, definition)
            put(COLUMN_BOX_LEVEL, 1)
            put(COLUMN_NEXT_REVIEW, now)
            put(COLUMN_CREATED_AT, now)
            put(COLUMN_KIND, LeitnerCard.KIND_WORD)
            put(COLUMN_SOURCE, LeitnerCard.SOURCE_DICTIONARY)
        }
        db.insert(TABLE_NAME, null, values)
        return true
    }

    /**
     * Adds a whole sentence as a card, or refreshes the stored back of it when
     * the same line from the same source is already in the box.
     *
     * Refreshing (rather than ignoring) matters here: re-importing a book with
     * a better AI answer should update the lesson on the card, exactly like a
     * re-added dictionary word updates its definition — and it must never
     * reset the card's box or its review date.
     *
     * @return true only when a new card was inserted.
     */
    fun addSentenceCard(draft: SentenceCardDraft): Boolean {
        if (!draft.isValid) return false
        val db = writableDatabase
        val front = draft.front.trim()
        val key = keyFor(LeitnerCard.KIND_SENTENCE, draft.source, front)
        val now = System.currentTimeMillis()
        val existing = db.query(TABLE_NAME, arrayOf(COLUMN_ID), "$COLUMN_WORD_KEY = ?", arrayOf(key), null, null, null)
        val exists = existing.use { it.moveToFirst() }
        val values = ContentValues().apply {
            put(COLUMN_WORD, front)
            put(COLUMN_DEFINITION, draft.definition)
            put(COLUMN_KIND, LeitnerCard.KIND_SENTENCE)
            put(COLUMN_SOURCE, draft.source)
            put(COLUMN_TRANSLATION, draft.translation)
            put(COLUMN_PRONUNCIATION, draft.pronunciation)
            put(COLUMN_LESSON_TEXT, draft.lessonText)
            put(COLUMN_WORDS_TEXT, draft.wordsText)
            put(COLUMN_LOCATION, draft.location)
        }
        if (exists) {
            db.update(TABLE_NAME, values, "$COLUMN_WORD_KEY = ?", arrayOf(key))
            return false
        }
        values.put(COLUMN_WORD_KEY, key)
        values.put(COLUMN_BOX_LEVEL, 1)
        values.put(COLUMN_NEXT_REVIEW, now)
        values.put(COLUMN_CREATED_AT, now)
        db.insert(TABLE_NAME, null, values)
        return true
    }

    /** True when this sentence (from this source) is already in the box. */
    fun containsSentence(front: String, source: String): Boolean {
        val key = keyFor(LeitnerCard.KIND_SENTENCE, source, front)
        val cursor = readableDatabase.query(
            TABLE_NAME, arrayOf(COLUMN_ID), "$COLUMN_WORD_KEY = ?", arrayOf(key), null, null, null
        )
        return cursor.use { it.moveToFirst() }
    }

    fun deleteCard(id: Long) {
        writableDatabase.delete(TABLE_NAME, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun getAllCards(): List<LeitnerCard> {
        val db = readableDatabase
        val cursor = db.query(TABLE_NAME, null, null, null, null, null, "$COLUMN_NEXT_REVIEW ASC")
        return readCards(cursor)
    }

    fun getDueCards(now: Long = System.currentTimeMillis()): List<LeitnerCard> {
        val db = readableDatabase
        val cursor = db.query(TABLE_NAME, null, "$COLUMN_NEXT_REVIEW <= ?", arrayOf(now.toString()), null, null, "$COLUMN_NEXT_REVIEW ASC")
        return readCards(cursor)
    }

    private fun readCards(cursor: Cursor): List<LeitnerCard> {
        val result = mutableListOf<LeitnerCard>()
        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(COLUMN_ID)
            val wordIdx = c.getColumnIndexOrThrow(COLUMN_WORD)
            val defIdx = c.getColumnIndexOrThrow(COLUMN_DEFINITION)
            val boxIdx = c.getColumnIndexOrThrow(COLUMN_BOX_LEVEL)
            val nextIdx = c.getColumnIndexOrThrow(COLUMN_NEXT_REVIEW)
            val createdIdx = c.getColumnIndexOrThrow(COLUMN_CREATED_AT)
            // Columns a v3 database has and a v2 one does not: read them when
            // they are there and fall back to the word-card defaults when they
            // are not, so the box opens on either schema.
            val kindIdx = c.getColumnIndex(COLUMN_KIND)
            val sourceIdx = c.getColumnIndex(COLUMN_SOURCE)
            val translationIdx = c.getColumnIndex(COLUMN_TRANSLATION)
            val pronunciationIdx = c.getColumnIndex(COLUMN_PRONUNCIATION)
            val lessonIdx = c.getColumnIndex(COLUMN_LESSON_TEXT)
            val wordsIdx = c.getColumnIndex(COLUMN_WORDS_TEXT)
            val locationIdx = c.getColumnIndex(COLUMN_LOCATION)
            while (c.moveToNext()) {
                result.add(
                    LeitnerCard(
                        id = c.getLong(idIdx),
                        word = c.getString(wordIdx) ?: "",
                        definition = c.getString(defIdx) ?: "",
                        boxLevel = c.getInt(boxIdx),
                        nextReviewAt = c.getLong(nextIdx),
                        createdAt = c.getLong(createdIdx),
                        kind = kindIdx.readText(c) ?: LeitnerCard.KIND_WORD,
                        source = sourceIdx.readText(c) ?: LeitnerCard.SOURCE_DICTIONARY,
                        translation = translationIdx.readText(c).orEmpty(),
                        pronunciation = pronunciationIdx.readText(c).orEmpty(),
                        lessonText = lessonIdx.readText(c).orEmpty(),
                        wordsText = wordsIdx.readText(c).orEmpty(),
                        location = locationIdx.readText(c).orEmpty()
                    )
                )
            }
        }
        return result
    }

    /** The user knew the word: promote it to the next box (capped at 5) and push its review date out. */
    fun markKnown(id: Long) {
        val db = writableDatabase
        val cursor = db.query(TABLE_NAME, arrayOf(COLUMN_BOX_LEVEL), "$COLUMN_ID = ?", arrayOf(id.toString()), null, null, null)
        val currentBox = cursor.use { if (it.moveToFirst()) it.getInt(0) else 1 }
        val newBox = (currentBox + 1).coerceAtMost(MAX_BOX_LEVEL)
        val intervalDays = BOX_INTERVAL_DAYS[newBox - 1]
        val nextReview = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(intervalDays)
        val values = ContentValues().apply { put(COLUMN_BOX_LEVEL, newBox); put(COLUMN_NEXT_REVIEW, nextReview) }
        db.update(TABLE_NAME, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    /** The user didn't know the word: reset it to box 1, due again soon. */
    fun markUnknown(id: Long) {
        val db = writableDatabase
        val nextReview = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)
        val values = ContentValues().apply { put(COLUMN_BOX_LEVEL, 1); put(COLUMN_NEXT_REVIEW, nextReview) }
        db.update(TABLE_NAME, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun cardCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_NAME", null)
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }
}
