package com.example.logic

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.model.DictionaryEntry

/**
 * Imports a plain SQLite (.db/.sqlite/.sqlite3) dictionary file into the
 * app's own dictionary_entries table (see DictionaryDatabaseHelper), the
 * same shared table used by imported .txt and .mdx/.mdd dictionaries.
 *
 * Many offline dictionary apps (StarDict-style exports, GoldenDict, etc.)
 * ship a single SQLite file with one table containing a "word"-like column
 * and a "definition"-like column. Since exact table/column names vary
 * between dictionary sources, this scans every table in the file and picks
 * the best match using common column-name heuristics, with a fallback to
 * "first two text-ish columns" when no named match is found.
 *
 * Usage examples (an original-language sentence plus its Persian meaning)
 * can live anywhere in the file: a dedicated example table referencing the
 * headword by a "word"-like column, extra columns right on the main
 * word/definition table itself, or simply as plain text buried inside some
 * other table's sentence/definition column with no direct link to the
 * headword at all. To cover all of these cases this importer:
 *  1. Looks for example/translation columns on the main word/definition
 *     table itself, first by common column names, then — if a name isn't
 *     recognized — by sampling each remaining text column's character set
 *     to guess whether it holds English or Persian/Arabic script
 *     (`findBestTable`). This mirrors the same content-based fallback
 *     already used for other tables, so a same-row example/translation pair
 *     is found even when its columns are named unexpectedly.
 *  2. Scans every OTHER table with a recognizable word-reference column for
 *     a precise (word -> example, translation) pairing (`collectExamplesFromOtherTables`).
 *  3. Builds a full word-occurrence index by tokenizing every text column of
 *     every table (including the main one) exactly once, so a headword that
 *     simply *appears* inside a longer sentence anywhere in the database
 *     — across potentially many tables with thousands of rows each — is
 *     still found and paired with any English AND any Persian sentence
 *     present in that same row, independently of each other, when available
 *     (`buildTokenOccurrenceIndex`). The full matched sentence/row value is
 *     always kept in its entirety — examples are never truncated, however
 *     long the sentence is.
 *
 * Every table and every row is read from start to finish with no row caps
 * or sampling shortcuts — for large database files (tens to hundreds of
 * megabytes, with many tables and thousands of words each) this import can
 * take noticeably longer than a plain word-list import, but nothing in the
 * file is skipped. Android's Cursor streams rows from disk rather than
 * loading a whole table into memory at once, so this stays memory-safe
 * regardless of file size.
 *
 * IMPORTANT LIMITATION: all of the above can only pair an English sentence
 * with a Persian one when BOTH already exist somewhere in the source
 * database (either the same row, or reachable via a word-reference column).
 * If a particular dictionary file only ever stores English example
 * sentences with no Persian counterpart anywhere in the file, there is
 * nothing for this importer to find or attach — the missing translation
 * does not exist in the source data, and only the English sentence is shown
 * (by design: no automatic machine translation is applied as a fallback).
 *
 * Every example found (from embedded HTML markup, this row's own example
 * columns, the precise cross-table map, and the broad token index) is
 * merged and capped per word so the dictionary popup stays readable, and
 * both an English/original sentence and its Persian counterpart are kept
 * whenever either language was found for that occurrence — they are never
 * required to come from the same row to be shown. Fully-paired (both
 * languages present) examples are always shown before English-only ones.
 */
object SqliteDictParser {

    private const val TAG = "SqliteDictParser"
    private const val BATCH_SIZE = 1000
    // Cap on how many usage examples get attached per word, combining every
    // source (embedded markup, row columns, ref-table map, token index).
    // "Find every occurrence" is honored per-source across the WHOLE
    // database (no row/table caps below); this cap only exists so one very
    // common word doesn't dump dozens of near-duplicate sentences into the
    // popup.
    private const val MAX_EXAMPLES_PER_WORD = 8
    // A value needs at least this many letters of one script to be treated
    // as an English or Persian sentence candidate in the broad token index.
    // Filters out short IDs/codes/slugs (e.g. "A12", "x9f3") that would
    // otherwise get misclassified and wrongly paired with a real sentence
    // from the same row.
    private const val MIN_LETTERS_FOR_LANGUAGE_MATCH = 3

    private val WORD_COLUMN_CANDIDATES = listOf("word", "entry", "headword", "key", "term", "title", "name")
    private val DEF_COLUMN_CANDIDATES = listOf("definition", "trans", "translation", "explanation", "content", "meaning", "paraphrase", "description", "html", "value", "detail")
    private val PHONETIC_COLUMN_CANDIDATES = listOf("phonetic", "pronunciation", "phon")
    private val POS_COLUMN_CANDIDATES = listOf("pos", "type", "wordtype", "part_of_speech")
    private val EXAMPLE_WORD_REF_CANDIDATES = listOf("word", "entry", "headword", "key", "term", "related_word", "ref_word", "for_word", "base_word", "word_key")
    private val EXAMPLE_ORIGINAL_CANDIDATES = listOf("example", "example_en", "example_original", "sentence", "usage", "instance", "example_source", "sample", "sentence_en")
    private val EXAMPLE_TRANSLATION_CANDIDATES = listOf("example_fa", "example_meaning", "translation_example", "sentence_fa", "meaning_example", "persian_example", "example_translation", "example_trans", "misal", "example_persian", "farsi", "persian")

    // Matches a "word" in either Latin or Persian/Arabic script, used to spot
    // every place a headword shows up inside a longer sentence anywhere in
    // the source database (not just in a dedicated example/reference table).
    private val WORD_TOKEN_REGEX = Regex("[A-Za-z\u0600-\u06FF][A-Za-z\u0600-\u06FF'-]{1,}")

    // Column type affinities that never hold sentence-like text, used to
    // EXCLUDE columns from language scanning. Everything else (including
    // unusual/custom type names like CLOB, STRING, NVARCHAR, or no declared
    // type at all) is treated as potentially textish, since SQLite column
    // types are only hints and vary a lot between dictionary export tools.
    private val NON_TEXT_TYPE_MARKERS = listOf("INT", "REAL", "FLOA", "DOUB", "NUMERIC", "BLOB", "BOOL")

    private data class TableSchema(
        val tableName: String,
        val wordColumn: String,
        val defColumn: String,
        val phoneticColumn: String?,
        val posColumn: String?,
        val exampleOriginalColumn: String?,
        val exampleTranslationColumn: String?,
        val rowCount: Long
    )

    /**
     * @param sourceName Display name of the imported file, stored on every
     * entry (see DictionaryEntry.source) so the dictionary popup can later
     * offer "only show results from this file" filter buttons when multiple
     * dictionaries are imported.
     * @param maxWords Maximum number of words to import (0 = unlimited), mirrors BinaryMdictParser's maxKeys.
     */
    fun parseSqliteDb(
        filePath: String,
        dbHelper: DictionaryDatabaseHelper,
        clearFirst: Boolean = false,
        maxWords: Int = 0,
        sourceName: String = "",
        progressCallback: (Int) -> Unit = {}
    ): Int {
        if (clearFirst) {
            dbHelper.clearAllEntries()
        }

        var totalCount = 0
        val batchList = mutableListOf<DictionaryEntry>()

        val sourceDb = SQLiteDatabase.openDatabase(filePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val allTables = listTables(sourceDb)
            val schema = findBestTable(sourceDb, allTables)
                ?: throw Exception("هیچ جدول واژه/تعریف قابل شناسایی در این فایل دیتابیس پیدا نشد")
            Log.d(TAG, "Using table='${schema.tableName}' word='${schema.wordColumn}' def='${schema.defColumn}' exOrig='${schema.exampleOriginalColumn}' exTrans='${schema.exampleTranslationColumn}' rows=${schema.rowCount}, total tables in DB=${allTables.size}")

            // Precise pairing: other tables with an explicit word-reference column.
            val refExampleMap = collectExamplesFromOtherTables(sourceDb, allTables, schema.tableName)
            Log.d(TAG, "Collected ref-based examples for ${refExampleMap.size} words")

            // Broad pairing: every occurrence of a headword inside any text
            // column of any table (including the main one), found via a
            // single full pass over every row of every table rather than
            // re-scanning the database per word, and with no row cap so
            // large tables are read from start to finish.
            val tokenExampleMap = buildTokenOccurrenceIndex(sourceDb, allTables)
            Log.d(TAG, "Collected token-based examples for ${tokenExampleMap.size} words")

            val columns = mutableListOf(schema.wordColumn, schema.defColumn)
            if (schema.phoneticColumn != null) columns.add(schema.phoneticColumn)
            if (schema.posColumn != null) columns.add(schema.posColumn)
            if (schema.exampleOriginalColumn != null) columns.add(schema.exampleOriginalColumn)
            if (schema.exampleTranslationColumn != null) columns.add(schema.exampleTranslationColumn)
            val quotedColumns = columns.distinct().joinToString(", ") { "\"$it\"" }

            // maxWords only applies to the main word/definition table and
            // defaults to unlimited (0); every row is read either way, since
            // this is the user-visible word list itself, not an example scan.
            val limitClause = if (maxWords > 0) " LIMIT $maxWords" else ""
            val cursor: Cursor = sourceDb.rawQuery("SELECT $quotedColumns FROM \"${schema.tableName}\"$limitClause", null)
            cursor.use { c ->
                val wordIdx = c.getColumnIndex(schema.wordColumn)
                val defIdx = c.getColumnIndex(schema.defColumn)
                val phoneticIdx = if (schema.phoneticColumn != null) c.getColumnIndex(schema.phoneticColumn) else -1
                val posIdx = if (schema.posColumn != null) c.getColumnIndex(schema.posColumn) else -1
                val exOrigIdx = if (schema.exampleOriginalColumn != null) c.getColumnIndex(schema.exampleOriginalColumn) else -1
                val exTransIdx = if (schema.exampleTranslationColumn != null) c.getColumnIndex(schema.exampleTranslationColumn) else -1

                while (c.moveToNext()) {
                    val word = (if (wordIdx >= 0) c.getString(wordIdx) else null)?.trim().orEmpty()
                    val rawDef = (if (defIdx >= 0) c.getString(defIdx) else null).orEmpty()
                    if (word.isEmpty() || rawDef.isEmpty()) continue

                    val phonetic = if (phoneticIdx >= 0) c.getString(phoneticIdx).orEmpty() else ""
                    val pos = if (posIdx >= 0) c.getString(posIdx).orEmpty() else ""
                    val wordKey = word.lowercase().trim()

                    // Gather every usage example we can find for this word, most
                    // to least precise, merged and de-duplicated. Each pair is
                    // (English/original sentence, Persian sentence) and either
                    // side may be blank if only one language was found for that
                    // particular occurrence — they are never dropped just
                    // because the other language wasn't present alongside it.
                    val examples = mutableListOf<Pair<String, String>>()

                    val (embeddedOriginal, embeddedTranslation) = HtmlTextUtils.extractHtmlExamples(rawDef)
                    val hasEmbedded = embeddedOriginal.isNotBlank() || embeddedTranslation.isNotBlank()
                    if (hasEmbedded) examples.add(embeddedOriginal to embeddedTranslation)

                    val rowOriginal = if (exOrigIdx >= 0) c.getString(exOrigIdx).orEmpty() else ""
                    val rowTranslation = if (exTransIdx >= 0) c.getString(exTransIdx).orEmpty() else ""
                    if (rowOriginal.isNotBlank() || rowTranslation.isNotBlank()) {
                        examples.add(rowOriginal to rowTranslation)
                    }

                    refExampleMap[wordKey]?.let { pair ->
                        if (examples.none { it.first == pair.first && it.second == pair.second }) examples.add(pair)
                    }

                    tokenExampleMap[wordKey]?.forEach { pair ->
                        if (examples.size < MAX_EXAMPLES_PER_WORD &&
                            examples.none { it.first == pair.first && it.second == pair.second }
                        ) {
                            examples.add(pair)
                        }
                    }

                    // If we have at least one example with BOTH languages
                    // present, prefer those first so the popup leads with a
                    // fully-translated pair rather than an English-only one.
                    val (fullyPaired, partial) = examples.partition { it.first.isNotBlank() && it.second.isNotBlank() }
                    val cappedExamples = (fullyPaired + partial).take(MAX_EXAMPLES_PER_WORD)

                    var htmlContent = rawDef
                    if (!hasEmbedded && cappedExamples.isNotEmpty()) {
                        // The definition's own HTML didn't already carry
                        // .enex/.faex markup, so append every example found
                        // using that same convention (already styled by the
                        // dictionary reader's WebView CSS — no UI changes needed).
                        // Both languages are appended whenever present, even
                        // when they come from different occurrences. Sentences
                        // are appended in full, never truncated.
                        htmlContent = buildString {
                            append(rawDef)
                            for ((orig, trans) in cappedExamples) {
                                if (orig.isNotBlank()) append("<div class=\"enex\">${escapeHtml(orig)}</div>")
                                if (trans.isNotBlank()) append("<div class=\"faex\">${escapeHtml(trans)}</div>")
                            }
                        }
                    }

                    batchList.add(
                        DictionaryEntry(
                            originalWord = word,
                            html = htmlContent,
                            phonetic = phonetic,
                            pos = pos,
                            def = HtmlTextUtils.htmlToReadableText(htmlContent),
                            source = sourceName,
                            exampleOriginal = cappedExamples.mapNotNull { it.first.takeIf { s -> s.isNotBlank() } }.joinToString("\n"),
                            exampleTranslation = cappedExamples.mapNotNull { it.second.takeIf { s -> s.isNotBlank() } }.joinToString("\n")
                        )
                    )

                    if (batchList.size >= BATCH_SIZE) {
                        dbHelper.insertEntriesInBatch(batchList)
                        totalCount += batchList.size
                        progressCallback(totalCount)
                        batchList.clear()
                    }
                }
            }

            if (batchList.isNotEmpty()) {
                dbHelper.insertEntriesInBatch(batchList)
                totalCount += batchList.size
                progressCallback(totalCount)
                batchList.clear()
            }

            Log.d(TAG, "SQLite dictionary import complete: $totalCount entries stored")
        } finally {
            sourceDb.close()
        }

        return totalCount
    }

    private fun listTables(db: SQLiteDatabase): List<String> {
        val tableNames = mutableListOf<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'",
            null
        ).use { c ->
            while (c.moveToNext()) tableNames.add(c.getString(0))
        }
        return tableNames
    }

    private fun findBestTable(db: SQLiteDatabase, tableNames: List<String>): TableSchema? {
        var best: TableSchema? = null
        var bestIsNamedMatch = false

        for (table in tableNames) {
            val columns = getColumns(db, table)
            if (columns.isEmpty()) continue

            val wordCol = findColumn(columns, WORD_COLUMN_CANDIDATES)
            val defCol = findColumn(columns, DEF_COLUMN_CANDIDATES)
            val phoneticCol = findColumn(columns, PHONETIC_COLUMN_CANDIDATES)
            val posCol = findColumn(columns, POS_COLUMN_CANDIDATES)
            // Some dictionaries keep the usage example right in the main
            // word/definition table as extra columns; pick those up too if
            // present (used before falling back to the cross-table example map).
            var exOrigCol = findColumn(columns, EXAMPLE_ORIGINAL_CANDIDATES)?.takeIf { it != wordCol && it != defCol }
            var exTransCol = findColumn(columns, EXAMPLE_TRANSLATION_CANDIDATES)?.takeIf { it != wordCol && it != defCol }

            if (wordCol != null && defCol != null && wordCol != defCol) {
                // If a named-candidate match didn't find the example and/or
                // its Persian translation column, fall back to sampling the
                // remaining text columns' character sets — the same
                // content-based heuristic already used for OTHER tables in
                // `collectExamplesFromOtherTables`. This is what lets a
                // same-row example/translation pair on the MAIN table be
                // found even when its columns have unexpected names (e.g.
                // "col1"/"col2", or a language name this importer doesn't
                // already know about).
                if (exOrigCol == null || exTransCol == null) {
                    val remaining = columns.filter {
                        it.first != wordCol && it.first != defCol && it.first != phoneticCol && it.first != posCol &&
                            it.first != exOrigCol && it.first != exTransCol && isTextish(it.second)
                    }
                    for ((name, _) in remaining) {
                        val lang = detectColumnLanguage(db, table, name)
                        if (exOrigCol == null && lang == "en") exOrigCol = name
                        else if (exTransCol == null && lang == "fa") exTransCol = name
                    }
                }

                val rowCount = countRows(db, table)
                val currentBest = best
                if (rowCount > 0 && (currentBest == null || !bestIsNamedMatch || rowCount > currentBest.rowCount)) {
                    best = TableSchema(table, wordCol, defCol, phoneticCol, posCol, exOrigCol, exTransCol, rowCount)
                    bestIsNamedMatch = true
                }
                continue
            }

            // Fallback: first two text-ish columns, only considered if no named match found yet.
            if (!bestIsNamedMatch) {
                val textColumns = columns.filter { isTextish(it.second) }
                if (textColumns.size >= 2) {
                    val rowCount = countRows(db, table)
                    val currentBest = best
                    if (rowCount > 0 && (currentBest == null || rowCount > currentBest.rowCount)) {
                        best = TableSchema(table, textColumns[0].first, textColumns[1].first, null, null, null, null, rowCount)
                    }
                }
            }
        }

        return best
    }

    /**
     * Scans EVERY row (no cap) of every table other than the main
     * word/definition table for a usage-example section: a column that
     * references the headword plus one or two text columns holding the
     * original-language sentence and its Persian meaning. Column names vary
     * a lot between dictionary sources, so besides common name candidates
     * this also falls back to guessing which of the remaining text columns
     * is English vs. Persian by sampling their character sets. Returns word
     * (lowercased) -> (English example, Persian example) for the single
     * most direct match per word.
     */
    private fun collectExamplesFromOtherTables(
        db: SQLiteDatabase,
        tableNames: List<String>,
        mainTable: String
    ): Map<String, Pair<String, String>> {
        val map = mutableMapOf<String, Pair<String, String>>()
        for (table in tableNames) {
            if (table == mainTable) continue
            val columns = getColumns(db, table)
            if (columns.isEmpty()) continue

            val wordRefCol = findColumn(columns, EXAMPLE_WORD_REF_CANDIDATES) ?: continue
            var origCol = findColumn(columns, EXAMPLE_ORIGINAL_CANDIDATES)?.takeIf { it != wordRefCol }
            var transCol = findColumn(columns, EXAMPLE_TRANSLATION_CANDIDATES)?.takeIf { it != wordRefCol }

            if (origCol == null || transCol == null) {
                val remaining = columns.filter { it.first != wordRefCol && isTextish(it.second) && it.first != origCol && it.first != transCol }
                for ((name, _) in remaining) {
                    val lang = detectColumnLanguage(db, table, name)
                    if (origCol == null && lang == "en") origCol = name
                    else if (transCol == null && lang == "fa") transCol = name
                }
            }

            if (origCol == null && transCol == null) continue

            val selectCols = mutableListOf(wordRefCol)
            origCol?.let { selectCols.add(it) }
            transCol?.let { selectCols.add(it) }
            val quoted = selectCols.distinct().joinToString(", ") { "\"$it\"" }

            try {
                // Full table scan — the user wants the entire database read
                // from start to finish, so no LIMIT is applied here.
                db.rawQuery("SELECT $quoted FROM \"$table\"", null).use { c ->
                    val refIdx = c.getColumnIndex(wordRefCol)
                    val origIdx = if (origCol != null) c.getColumnIndex(origCol) else -1
                    val transIdx = if (transCol != null) c.getColumnIndex(transCol) else -1
                    while (c.moveToNext()) {
                        val key = (if (refIdx >= 0) c.getString(refIdx) else null)?.lowercase()?.trim().orEmpty()
                        if (key.isEmpty() || map.containsKey(key)) continue
                        val orig = if (origIdx >= 0) c.getString(origIdx).orEmpty() else ""
                        val trans = if (transIdx >= 0) c.getString(transIdx).orEmpty() else ""
                        if (orig.isNotBlank() || trans.isNotBlank()) map[key] = orig to trans
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed scanning example table '$table': ${e.message}")
            }
        }
        return map
    }

    /**
     * Builds a word (lowercased) -> list of (English sentence, Persian
     * sentence) index by scanning every text column of EVERY ROW of every
     * table exactly once, with no row cap — this is what lets the importer
     * find a headword anywhere it merely appears *inside* a longer sentence,
     * not just via a dedicated reference column, across a database that may
     * have many tables with thousands of rows each. The full matched value
     * (the entire sentence/cell) is always kept, however long it is — it is
     * never trimmed or truncated.
     *
     * Language is classified per VALUE (not per column), so a table that
     * mixes English and Persian text across different rows of the same
     * column is still handled correctly. A value only counts as an English
     * or Persian candidate once it has enough letters of that script
     * (`MIN_LETTERS_FOR_LANGUAGE_MATCH`) to look like real text rather than
     * a short ID/code/slug that happens to contain a couple of Latin
     * letters. For each row, every distinct English-classified value and
     * every distinct Persian-classified value found anywhere in that row are
     * collected; every word token found anywhere in the row is then paired
     * positionally with those English and Persian values (index 0 with
     * index 0, index 1 with index 1, etc.), so a row with several example
     * columns still produces several distinct example pairs instead of only
     * the first one. When only one language was present in a row, that
     * language's sentence is still kept with the other side left blank —
     * languages are never dropped just because the other one wasn't present
     * alongside it in that particular row.
     *
     * Note: this can only pair a Persian sentence with an English one when
     * BOTH exist somewhere in the same row of the source database. If a
     * dictionary file's example rows are English-only with no Persian
     * counterpart stored anywhere, there is nothing to pair — the
     * translation simply is not present in the source data, and (by design)
     * no automatic machine translation is generated to fill that gap.
     */
    private fun buildTokenOccurrenceIndex(
        db: SQLiteDatabase,
        tableNames: List<String>
    ): Map<String, MutableList<Pair<String, String>>> {
        val index = HashMap<String, MutableList<Pair<String, String>>>()
        for (table in tableNames) {
            val columns = getColumns(db, table)
            if (columns.isEmpty()) continue
            val textCols = columns.filter { isTextish(it.second) }.map { it.first }
            if (textCols.isEmpty()) continue

            val quoted = textCols.distinct().joinToString(", ") { "\"$it\"" }
            try {
                // Full table scan, every row, no LIMIT: the user asked for the
                // entire database and every table to be read completely.
                // Cursor streams rows from disk, so this stays memory-safe.
                db.rawQuery("SELECT $quoted FROM \"$table\"", null).use { c ->
                    val colIndexes = textCols.associateWith { c.getColumnIndex(it) }
                    while (c.moveToNext()) {
                        val enValues = mutableListOf<String>()
                        val faValues = mutableListOf<String>()
                        val allValues = mutableListOf<String>()

                        for (col in textCols) {
                            val idx = colIndexes[col] ?: -1
                            val value = if (idx >= 0) c.getString(idx) else null
                            if (value.isNullOrBlank()) continue
                            allValues.add(value)
                            when (classifyLanguage(value)) {
                                "en" -> enValues.add(value)
                                "fa" -> faValues.add(value)
                                else -> {} // no letters / ambiguous / too short to be confident: still searched for tokens below, just not kept as a language bucket
                            }
                        }
                        if (allValues.isEmpty()) continue

                        val tokensInRow = allValues.asSequence()
                            .flatMap { WORD_TOKEN_REGEX.findAll(it).map { m -> m.value.lowercase() } }
                            .filter { it.length >= 2 }
                            .toSet()
                        if (tokensInRow.isEmpty()) continue

                        val pairCount = maxOf(enValues.size, faValues.size, 1)

                        for (token in tokensInRow) {
                            val list = index.getOrPut(token) { mutableListOf() }
                            if (list.size >= MAX_EXAMPLES_PER_WORD) continue
                            for (i in 0 until pairCount) {
                                if (list.size >= MAX_EXAMPLES_PER_WORD) break
                                val en = enValues.getOrNull(i) ?: ""
                                val fa = faValues.getOrNull(i) ?: ""
                                if (en.isBlank() && fa.isBlank()) continue
                                if (list.none { it.first == en && it.second == fa }) {
                                    list.add(en to fa)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed token-indexing table '$table': ${e.message}")
            }
        }
        return index
    }

    /**
     * Classifies a single string as mostly English (Latin script) or mostly
     * Persian/Arabic script, requiring at least MIN_LETTERS_FOR_LANGUAGE_MATCH
     * letters of the winning script so short IDs/codes/slugs aren't
     * misclassified as a sentence in one language or the other.
     */
    private fun classifyLanguage(text: String): String {
        var latin = 0
        var persian = 0
        for (ch in text) {
            if (ch in 'a'..'z' || ch in 'A'..'Z') latin++
            else if (ch.code in 0x0600..0x06FF) persian++
        }
        return when {
            persian > latin && persian >= MIN_LETTERS_FOR_LANGUAGE_MATCH -> "fa"
            latin > persian && latin >= MIN_LETTERS_FOR_LANGUAGE_MATCH -> "en"
            else -> "unknown"
        }
    }

    /** Samples a handful of rows to guess whether a column mostly holds English (Latin) or Persian/Arabic script text. Used to pick which named column is the example/translation column both on the main table (`findBestTable`) and other tables (`collectExamplesFromOtherTables`) when name-based matching doesn't find one. */
    private fun detectColumnLanguage(db: SQLiteDatabase, table: String, column: String): String {
        var latin = 0
        var persian = 0
        try {
            db.rawQuery("SELECT \"$column\" FROM \"$table\" WHERE \"$column\" IS NOT NULL LIMIT 15", null).use { c ->
                while (c.moveToNext()) {
                    val v = c.getString(0) ?: continue
                    latin += v.count { it in 'a'..'z' || it in 'A'..'Z' }
                    persian += v.count { it.code in 0x0600..0x06FF }
                }
            }
        } catch (e: Exception) {
            // ignore, treat as unknown
        }
        return when {
            persian > latin -> "fa"
            latin > persian -> "en"
            else -> "unknown"
        }
    }

    /**
     * Treats a SQLite column as potentially holding sentence-like text
     * unless its declared type affinity is clearly numeric/binary/boolean.
     * SQLite type declarations are only hints (and often missing entirely
     * on columns exported by third-party dictionary tools), so this
     * deliberately blacklists only the types that could never hold a
     * sentence, rather than requiring an exact "TEXT"/"CHAR" match — custom
     * or unusual type names (CLOB, STRING, NVARCHAR, empty/untyped columns,
     * etc.) are still scanned.
     */
    private fun isTextish(type: String): Boolean {
        val t = type.uppercase()
        if (t.isEmpty()) return true
        return NON_TEXT_TYPE_MARKERS.none { marker -> t.contains(marker) }
    }

    private fun getColumns(db: SQLiteDatabase, table: String): List<Pair<String, String>> {
        val columns = mutableListOf<Pair<String, String>>()
        db.rawQuery("PRAGMA table_info(\"$table\")", null).use { c ->
            val nameIdx = c.getColumnIndex("name")
            val typeIdx = c.getColumnIndex("type")
            while (c.moveToNext()) {
                columns.add(c.getString(nameIdx) to (c.getString(typeIdx) ?: ""))
            }
        }
        return columns
    }

    private fun findColumn(columns: List<Pair<String, String>>, candidates: List<String>): String? {
        for (candidate in candidates) {
            columns.firstOrNull { it.first.equals(candidate, ignoreCase = true) }?.let { return it.first }
        }
        for (candidate in candidates) {
            columns.firstOrNull { it.first.contains(candidate, ignoreCase = true) }?.let { return it.first }
        }
        return null
    }

    private fun countRows(db: SQLiteDatabase, table: String): Long {
        return try {
            db.rawQuery("SELECT COUNT(*) FROM \"$table\"", null).use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}
