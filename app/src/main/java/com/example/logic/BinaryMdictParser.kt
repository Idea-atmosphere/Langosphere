package com.example.logic

import android.util.Log
import com.example.model.DictionaryEntry
import java.io.File
import java.io.RandomAccessFile

object BinaryMdictParser {

    private const val TAG = "BinaryMdictParser"
    // Larger batches mean fewer SQLite transactions during bulk import, which
    // is one of the biggest wins for import speed on large .mdx files.
    private const val BATCH_SIZE = 3000

    /**
     * Parse MDX using MdictReader (ported from Ciyue).
     * Reads all entries via record blocks and stores in SQLite in batches.
     *
     * @param maxKeys Maximum number of words to import (0 = unlimited)
     * @param sourceName Display name of the imported .mdx file, stored on
     * every entry (see DictionaryEntry.source) so the dictionary popup can
     * later offer "only show results from this file" filter buttons.
     *
     * Performance notes:
     * - This DB-stored copy of a .mdx file's entries is only ever read back
     *   as a fallback (AppViewModel.lookupWord uses the live MdictReader
     *   first, and only falls back to this table if that reader is
     *   unavailable), so per-entry usage-example extraction (Jsoup-based,
     *   see HtmlTextUtils.extractHtmlExamples) is skipped here and computed
     *   lazily at lookup time instead — it would otherwise run on every
     *   single word during import for no benefit in the common case.
     * - `System.gc()` is intentionally NOT called between batches anymore:
     *   forcing a full GC every ~1000 entries was adding significant,
     *   unnecessary pause time to large imports without a corresponding
     *   memory-safety benefit (each batch is already flushed and cleared).
     */
    fun parseMdx(
        filePath: String,
        dbHelper: DictionaryDatabaseHelper,
        clearFirst: Boolean = false,
        maxKeys: Int = 0,
        sourceName: String = "",
        progressCallback: (Int) -> Unit = {}
    ): Int {
        if (clearFirst) {
            dbHelper.clearAllEntries()
        }

        var totalCount = 0
        val batchList = mutableListOf<DictionaryEntry>()

        Log.d(TAG, "parseMdx: starting with filePath=$filePath, maxKeys=$maxKeys")

        val reader = MdictReader(filePath)
        try {
            reader.open()
            Log.d(TAG, "parseMdx: file opened, calling initDict(maxKeys=$maxKeys)")

            reader.initDict(maxKeys = maxKeys)

            Log.d(TAG, "parseMdx: initDict done, ${reader.numEntries} entries, encoding=${reader.encoding}")

            // Get all unique keys sorted by word
            val allKeys = reader.getAllKeysSorted()
            Log.d(TAG, "Total keys from reader: ${allKeys.size}")

            var processed = 0

            // Process keys in batches of BATCH_SIZE
            var keyIdx = 0
            while (keyIdx < allKeys.size) {
                val endIdx = minOf(keyIdx + BATCH_SIZE, allKeys.size)

                for (i in keyIdx until endIdx) {
                    val key = allKeys[i]
                    try {
                        // locateAll returns all entries for this word
                        val infos = reader.locateAll(key.word)
                        if (infos.isEmpty()) continue

                        // For duplicate words, combine all entries
                        val sb = StringBuilder()
                        for (info in infos) {
                            val html = reader.readOneMdx(info)
                            val finalHtml = handleLink(html, reader) ?: html
                            if (finalHtml.isNotBlank()) {
                                if (sb.isNotEmpty()) sb.append("\n<hr>\n")
                                sb.append(finalHtml)
                            }
                        }

                        if (sb.isNotEmpty()) {
                            val combinedHtml = sb.toString()
                            batchList.add(DictionaryEntry(
                                originalWord = key.word,
                                html = combinedHtml,
                                phonetic = "",
                                pos = "",
                                def = HtmlTextUtils.htmlToReadableText(combinedHtml),
                                source = sourceName
                                // exampleOriginal/exampleTranslation intentionally left blank here —
                                // see performance note above. AppViewModel.lookupWord extracts
                                // them on the fly from `html` (which still carries any .enex/.faex
                                // markup) whenever this dictionary is looked up live.
                            ))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error reading record for '${key.word}': ${e.message}")
                    }

                    processed++
                }

                // Flush batch
                if (batchList.isNotEmpty()) {
                    dbHelper.insertEntriesInBatch(batchList)
                    totalCount += batchList.size
                    progressCallback(totalCount)
                    batchList.clear()
                }

                if (processed % 5000 == 0) {
                    Log.d(TAG, "Progress: $processed/${allKeys.size} (${processed * 100 / allKeys.size}%)")
                }

                keyIdx = endIdx
            }

            // Flush remaining
            if (batchList.isNotEmpty()) {
                dbHelper.insertEntriesInBatch(batchList)
                totalCount += batchList.size
                progressCallback(totalCount)
                batchList.clear()
            }

            Log.d(TAG, "Parse complete: $totalCount entries stored in DB")
        } catch (e: Exception) {
            Log.e(TAG, "Parse MDX failed: ${e.message}", e)
            throw e
        } finally {
            reader.close()
        }

        return totalCount
    }

    /**
     * Parse MDD using MdictReader.
     */
    fun parseMdd(
        filePath: String,
        dbHelper: DictionaryDatabaseHelper,
        clearFirst: Boolean = false,
        sourceName: String = "",
        progressCallback: (Int) -> Unit = {}
    ): Int {
        if (clearFirst) {
            dbHelper.clearAllEntries()
        }

        var totalCount = 0
        val batchList = mutableListOf<DictionaryEntry>()

        val reader = MdictReader(filePath)
        try {
            reader.open()
            reader.initDict()

            Log.d(TAG, "MDD loaded: ${reader.numEntries} entries")

            val allKeys = reader.getAllKeysSorted()
            Log.d(TAG, "Total MDD keys: ${allKeys.size}")

            var processed = 0
            var keyIdx = 0

            while (keyIdx < allKeys.size) {
                val endIdx = minOf(keyIdx + BATCH_SIZE, allKeys.size)

                for (i in keyIdx until endIdx) {
                    val key = allKeys[i]
                    try {
                        batchList.add(DictionaryEntry(
                            originalWord = key.word,
                            html = key.word,
                            phonetic = "",
                            pos = "",
                            def = key.word,
                            source = sourceName
                        ))
                    } catch (e: Exception) {
                        Log.w(TAG, "Error reading MDD record for '${key.word}': ${e.message}")
                    }

                    if (batchList.size >= BATCH_SIZE) {
                        dbHelper.insertEntriesInBatch(batchList)
                        totalCount += batchList.size
                        progressCallback(totalCount)
                        batchList.clear()
                    }

                    processed++
                }

                keyIdx = endIdx
            }

            if (batchList.isNotEmpty()) {
                dbHelper.insertEntriesInBatch(batchList)
                totalCount += batchList.size
                progressCallback(totalCount)
            }

            Log.d(TAG, "MDD parse complete: $totalCount entries")
        } catch (e: Exception) {
            Log.e(TAG, "Parse MDD failed: ${e.message}", e)
            throw e
        } finally {
            reader.close()
        }

        return totalCount
    }

    /**
     * Handle @@@LINK= redirects by following the link to the target word.
     */
    private fun handleLink(html: String, reader: MdictReader): String? {
        if (!html.startsWith("@@@LINK=")) return html

        val targetWord = html
            .removePrefix("@@@LINK=")
            .replace(Regex("[\\n\\r\\x00]"), "")
            .trim()

        if (targetWord.isEmpty()) return null

        return try {
            val infos = reader.locateAll(targetWord)
            if (infos.isEmpty()) return null

            val sb = StringBuilder()
            for (info in infos) {
                sb.append(reader.readOneMdx(info))
            }
            sb.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to follow link to '$targetWord': ${e.message}")
            null
        }
    }
}
