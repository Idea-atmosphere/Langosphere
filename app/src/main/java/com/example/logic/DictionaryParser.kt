package com.example.logic

import com.example.model.DictionaryEntry
import org.jsoup.Jsoup
import java.io.InputStream

object DictionaryParser {
    
    fun parseAndSaveToDb(
        inputStream: InputStream, 
        dbHelper: DictionaryDatabaseHelper, 
        clearFirst: Boolean = false,
        sourceName: String = "",
        progressCallback: (Int) -> Unit = {}
    ) {
        val reader = inputStream.bufferedReader()
        if (clearFirst) {
            dbHelper.clearAllEntries() // Clear existing database first
        }
        
        val batchList = mutableListOf<DictionaryEntry>()
        var totalCount = 0

        // Auto-detect format by sampling the first 1000 lines
        val sampleLines = mutableListOf<String>()
        var hasMdictDelimiter = false
        
        for (i in 0 until 1000) {
            val line = reader.readLine() ?: break
            sampleLines.add(line)
            if (line.trim() == "</>") {
                hasMdictDelimiter = true
            }
        }
        
        val iterator = sampleLines.iterator()
        
        fun parseHtmlAndCreateEntry(word: String, rawHtml: String): DictionaryEntry {
            var htmlContent = rawHtml.trim()
            
            // Clean HTML stylesheets links
            htmlContent = htmlContent.replace(Regex("<link[^>]*>", RegexOption.IGNORE_CASE), "")
            
            // Do lightweight extraction using Jsoup
            val tempDoc = Jsoup.parseBodyFragment(htmlContent)
            val phonetic = tempDoc.select(".phonetic").text().trim()
            val pos = tempDoc.select(".pos").text().trim()
            
            // Extract definitions. Convert each definition's inner HTML (not
            // .text(), which collapses <br> line breaks into plain spaces)
            // through the shared HTML->text helper so multiple senses stay on
            // separate, readable lines instead of being jammed together.
            val defElements = tempDoc.select(".def")
            val defText = if (defElements.isNotEmpty()) {
                defElements.map { HtmlTextUtils.htmlToReadableText(it.html()).replace("■", "").trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")
            } else {
                HtmlTextUtils.htmlToReadableText(tempDoc.html())
            }

            // Some dictionary sources mark an original-language usage example
            // and its Persian meaning with .enex/.faex classes (already styled
            // in the offline reader's WebView CSS); pick those up as plain
            // text too so they're usable outside the WebView (Leitner box, AI
            // "learn from dictionary" notes).
            val (exampleOriginal, exampleTranslation) = HtmlTextUtils.extractHtmlExamples(htmlContent)
            
            return DictionaryEntry(
                originalWord = word.trim(),
                html = htmlContent,
                phonetic = phonetic,
                pos = pos,
                def = defText,
                source = sourceName,
                exampleOriginal = exampleOriginal,
                exampleTranslation = exampleTranslation
            )
        }

        if (hasMdictDelimiter) {
            // Delimited Format: 
            // Word
            // HTML content (multiline)
            // </>
            var currentWord: String? = null
            val currentHtmlBuilder = StringBuilder()
            
            fun processLine(line: String) {
                val trimmed = line.trim()
                if (trimmed == "</>" || trimmed.startsWith("</>")) {
                    if (currentWord != null) {
                        try {
                            batchList.add(parseHtmlAndCreateEntry(currentWord!!, currentHtmlBuilder.toString()))
                            if (batchList.size >= 1000) {
                                dbHelper.insertEntriesInBatch(batchList)
                                totalCount += batchList.size
                                progressCallback(totalCount)
                                batchList.clear()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    currentWord = null
                    currentHtmlBuilder.setLength(0)
                } else {
                    if (currentWord == null) {
                        if (trimmed.isNotEmpty()) {
                            currentWord = trimmed
                        }
                    } else {
                        // Shield against OOM: limit individual definition sizing to 64KB
                        if (currentHtmlBuilder.length < 65536) {
                            currentHtmlBuilder.append(line).append("\n")
                        }
                    }
                }
            }
            
            while (iterator.hasNext()) {
                processLine(iterator.next())
            }
            
            while (true) {
                val line = reader.readLine() ?: break
                processLine(line)
            }
            
        } else {
            // Alternate Format (Two line alternate - no empty lines):
            // Word
            // HTML definition
            var currentWord: String? = null
            
            fun processLine(line: String) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return
                
                if (currentWord == null) {
                    currentWord = trimmed
                } else {
                    try {
                        batchList.add(parseHtmlAndCreateEntry(currentWord!!, trimmed))
                        if (batchList.size >= 1000) {
                            dbHelper.insertEntriesInBatch(batchList)
                            totalCount += batchList.size
                            progressCallback(totalCount)
                            batchList.clear()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    currentWord = null
                }
            }
            
            while (iterator.hasNext()) {
                processLine(iterator.next())
            }
            
            while (true) {
                val line = reader.readLine() ?: break
                processLine(line)
            }
        }
        
        // Final batch insertion
        if (batchList.isNotEmpty()) {
            dbHelper.insertEntriesInBatch(batchList)
            totalCount += batchList.size
            progressCallback(totalCount)
        }
    }
}
