package com.example.logic

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.model.DictionaryEntry

class DictionaryDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "DictionaryDatabaseHelper"

        private const val DATABASE_NAME = "dictionary.db"
        private const val DATABASE_VERSION = 2
        private const val TABLE_NAME = "dictionary_entries"

        private const val COLUMN_WORD_KEY = "word_key"
        private const val COLUMN_ORIGINAL_WORD = "original_word"
        private const val COLUMN_HTML = "html"
        private const val COLUMN_PHONETIC = "phonetic"
        private const val COLUMN_POS = "pos"
        private const val COLUMN_DEF = "def"
        // Added in DATABASE_VERSION 2: which imported file an entry came from
        // (used by the dictionary popup's per-file filter buttons), and an
        // optional original-language usage example + its Persian meaning.
        private const val COLUMN_SOURCE = "source"
        private const val COLUMN_EXAMPLE_ORIGINAL = "example_original"
        private const val COLUMN_EXAMPLE_TRANSLATION = "example_translation"

        /** Every column this build expects. All of them are TEXT, so any that
         *  is missing can be added in place — an imported dictionary can be
         *  hundreds of megabytes and must never be dropped to fix a schema. */
        private val REQUIRED_COLUMNS: List<String> = listOf(
            COLUMN_WORD_KEY,
            COLUMN_ORIGINAL_WORD,
            COLUMN_HTML,
            COLUMN_PHONETIC,
            COLUMN_POS,
            COLUMN_DEF,
            COLUMN_SOURCE,
            COLUMN_EXAMPLE_ORIGINAL,
            COLUMN_EXAMPLE_TRANSLATION
        )
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_WORD_KEY TEXT,
                $COLUMN_ORIGINAL_WORD TEXT,
                $COLUMN_HTML TEXT,
                $COLUMN_PHONETIC TEXT,
                $COLUMN_POS TEXT,
                $COLUMN_DEF TEXT,
                $COLUMN_SOURCE TEXT,
                $COLUMN_EXAMPLE_ORIGINAL TEXT,
                $COLUMN_EXAMPLE_TRANSLATION TEXT
            )
        """.trimIndent()
        db.execSQL(createTableQuery)

        // Index is extremely important for instant matching
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_word_key ON $TABLE_NAME($COLUMN_WORD_KEY)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Preserve already-imported words: add whatever columns are missing
        // instead of dropping and recreating the table.
        reconcileSchema(db)
    }

    /**
     * A database written by a newer build must not kill the app on launch:
     * the default SQLiteOpenHelper.onDowngrade() throws, which is never the
     * right outcome for a local cache the user can rebuild by re-importing.
     * Extra columns from a newer build are harmless here, since every query
     * and insert in this class names its columns explicitly.
     */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w(TAG, "Opening a newer $DATABASE_NAME (v$oldVersion) with v$newVersion; reconciling instead of failing")
        reconcileSchema(db)
    }

    private fun reconcileSchema(db: SQLiteDatabase) {
        val existing = readColumnNames(db)
        if (existing.isEmpty()) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            onCreate(db)
            return
        }
        for (column in REQUIRED_COLUMNS) {
            if (existing.contains(column)) continue
            try {
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $column TEXT")
            } catch (e: Exception) {
                Log.w(TAG, "Could not add column '$column': ${e.message}")
            }
        }
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_word_key ON $TABLE_NAME($COLUMN_WORD_KEY)")
        } catch (e: Exception) {
            Log.w(TAG, "Could not ensure word index: ${e.message}")
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

    fun clearAllEntries() {
        val db = writableDatabase
        db.execSQL("DELETE FROM $TABLE_NAME")
    }

    fun insertEntriesInBatch(entries: List<DictionaryEntry>) {
        if (entries.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val stmt = db.compileStatement(
                "INSERT INTO $TABLE_NAME ($COLUMN_WORD_KEY, $COLUMN_ORIGINAL_WORD, $COLUMN_HTML, $COLUMN_PHONETIC, $COLUMN_POS, $COLUMN_DEF, $COLUMN_SOURCE, $COLUMN_EXAMPLE_ORIGINAL, $COLUMN_EXAMPLE_TRANSLATION) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
            )
            for (entry in entries) {
                stmt.clearBindings()
                stmt.bindString(1, entry.originalWord.lowercase().trim())
                stmt.bindString(2, entry.originalWord)
                stmt.bindString(3, entry.html)
                stmt.bindString(4, entry.phonetic)
                stmt.bindString(5, entry.pos)
                stmt.bindString(6, entry.def)
                stmt.bindString(7, entry.source)
                stmt.bindString(8, entry.exampleOriginal)
                stmt.bindString(9, entry.exampleTranslation)
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getEntriesForWord(word: String): List<DictionaryEntry> {
        val wordKey = word.lowercase().trim()
        val db = readableDatabase
        val results = mutableListOf<DictionaryEntry>()

        val cursor = db.query(
            TABLE_NAME,
            null,
            "$COLUMN_WORD_KEY = ?",
            arrayOf(wordKey),
            null,
            null,
            null
        )

        cursor.use { c ->
            val idxOriginal = c.getColumnIndexOrThrow(COLUMN_ORIGINAL_WORD)
            val idxHtml = c.getColumnIndexOrThrow(COLUMN_HTML)
            val idxPhonetic = c.getColumnIndexOrThrow(COLUMN_PHONETIC)
            val idxPos = c.getColumnIndexOrThrow(COLUMN_POS)
            val idxDef = c.getColumnIndexOrThrow(COLUMN_DEF)
            val idxSource = c.getColumnIndex(COLUMN_SOURCE)
            val idxExampleOriginal = c.getColumnIndex(COLUMN_EXAMPLE_ORIGINAL)
            val idxExampleTranslation = c.getColumnIndex(COLUMN_EXAMPLE_TRANSLATION)

            while (c.moveToNext()) {
                val originalWord = c.getString(idxOriginal) ?: ""
                val html = c.getString(idxHtml) ?: ""
                val phonetic = c.getString(idxPhonetic) ?: ""
                val pos = c.getString(idxPos) ?: ""
                val def = c.getString(idxDef) ?: ""
                val source = if (idxSource >= 0) (c.getString(idxSource) ?: "") else ""
                val exampleOriginal = if (idxExampleOriginal >= 0) (c.getString(idxExampleOriginal) ?: "") else ""
                val exampleTranslation = if (idxExampleTranslation >= 0) (c.getString(idxExampleTranslation) ?: "") else ""

                results.add(
                    DictionaryEntry(
                        originalWord = originalWord,
                        html = html,
                        phonetic = phonetic,
                        pos = pos,
                        def = def,
                        source = source,
                        exampleOriginal = exampleOriginal,
                        exampleTranslation = exampleTranslation
                    )
                )
            }
        }
        return results
    }

    fun hasEntries(): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT 1 FROM $TABLE_NAME LIMIT 1", null)
        val hasData = cursor.use { it.moveToFirst() }
        return hasData
    }

    /**
     * Get all distinct words from the dictionary database.
     * Used for learning from dictionary without subtitles.
     */
    fun getAllWords(): List<String> {
        val db = readableDatabase
        val words = mutableListOf<String>()
        val cursor = db.rawQuery("SELECT DISTINCT $COLUMN_WORD_KEY FROM $TABLE_NAME", null)
        cursor.use { c ->
            val idx = c.getColumnIndexOrThrow(COLUMN_WORD_KEY)
            while (c.moveToNext()) {
                val word = c.getString(idx)
                if (word != null && word.isNotBlank()) {
                    words.add(word)
                }
            }
        }
        return words
    }
}
