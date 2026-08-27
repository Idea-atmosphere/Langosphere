package com.example.logic

import org.jsoup.Jsoup
import java.util.Locale

object TranslationDetector {

    private val STOP_WORDS = setOf(
        "در", "به", "از", "با", "تا", "را", "این", "آن", "که", "و", "یا", "اما", "اگر", "ولی",
        "من", "تو", "او", "ما", "شما", "آنها", "ایشان", "بود", "است", "شد", "شدن", "بودن", "کردن", "ام", "ای", "یم", "ید", "اند"
    )

    fun extractDefinitions(entryHtml: String): List<String> {
        val candidates = mutableListOf<String>()
        try {
            val doc = Jsoup.parse(entryHtml)
            
            // Extract from .def elements (user's provided pattern)
            val defElements = doc.select(".def")
            if (defElements.isNotEmpty()) {
                for (el in defElements) {
                    val text = el.text().trim()
                    if (text.isNotEmpty()) {
                        candidates.add(text)
                    }
                }
            } else {
                // Look for standard list items or paragraphs if no .def is found
                val text = doc.text()
                // Split by newline or common separators
                val parts = text.split(Regex("[\\n;،,/]"))
                for (part in parts) {
                    val trimmed = part.trim()
                    if (trimmed.isNotEmpty() && !trimmed.contains("Encoding=") && !trimmed.contains("<Dictionary")) {
                        candidates.add(trimmed)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return candidates
    }

    /**
     * Finds which Persian word/phrase from the dictionary definitions corresponds to the Persian translation sentence.
     * Returns a pair containing the detected Persian definition/phrase and the detected Persian word(s) inside the sentence.
     */
    fun detectTranslation(entryHtmlList: List<String>, farsiSentence: String): Pair<String, String>? {
        if (farsiSentence.isBlank()) return null
        
        // 1. Clean the Farsi sentence and break into raw words
        val cleanSentence = farsiSentence.replace(Regex("[.\\p{Punct}،؛؟!?()（）<>|_]"), " ")
        val sentenceWords = cleanSentence.split("\\s+".toRegex())
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length > 1 }
        
        if (sentenceWords.isEmpty()) return null

        // 2. Extract all definitions/meanings from the HTML entries
        val allDefs = entryHtmlList.flatMap { extractDefinitions(it) }
        if (allDefs.isEmpty()) return null

        var bestPhrase: String? = null
        var bestMatchedWordsInSentence = ""
        var bestScore = 0.0

        for (def in allDefs) {
            // Instead of skipping definitions that contain English words,
            // we will clean the definition by removing parentheses, HTML tags, and English text
            // to extract pure Persian keywords!
            var cleanDef = def.replace(Regex("<[^>]*>"), " ") // Remove HTML tags
                .replace(Regex("\\([^)]*\\)"), " ") // Remove nested parentheses
                .replace(Regex("（[^）]*）"), " ") // Remove Persian nested parentheses
                .replace(Regex("[a-zA-Z0-9]"), " ") // Strip English alphanumeric characters
                .trim()
                
            if (cleanDef.isEmpty()) continue

            val defWords = cleanDef.replace(Regex("[.\\p{Punct}،؛؟!?...…]"), " ")
                .split("\\s+".toRegex())
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.length > 1 }
            
            if (defWords.isEmpty()) continue

            var score = 0.0
            val matchedSentenceIndices = mutableSetOf<Int>()

            for (defWord in defWords) {
                val isStopWord = STOP_WORDS.contains(defWord)
                val wordWeight = if (isStopWord) 0.1 else 1.0

                for ((sIdx, sWord) in sentenceWords.withIndex()) {
                    var matchFound = false
                    var matchPoints = 0.0
                    
                    if (sWord == defWord) {
                        matchFound = true
                        matchPoints = 1.0
                    } else if (sWord.contains(defWord) && defWord.length >= 3) {
                        matchFound = true
                        matchPoints = 0.9
                    } else if (defWord.contains(sWord) && sWord.length >= 3) {
                        matchFound = true
                        matchPoints = 0.9
                    } else {
                        // Advanced stem approximation for Persian agglutinative morphology
                        val stripSuffixes = { w: String ->
                            w.removeSuffix("‌ها").removeSuffix("های").removeSuffix("ها")
                             .removeSuffix("ان").removeSuffix("بان").removeSuffix("ین").removeSuffix("ون")
                             .removeSuffix("تر").removeSuffix("ترین")
                             .removeSuffix("خواه").removeSuffix("ساز")
                             .removeSuffix("ی").removeSuffix("ه")
                             .removeSuffix("م").removeSuffix("ت").removeSuffix("ش")
                             .removeSuffix("یم").removeSuffix("ید").removeSuffix("ند")
                             .removePrefix("می‌").removePrefix("می ").removePrefix("بی").removePrefix("نا")
                        }
                        
                        val stemC = stripSuffixes(defWord)
                        val stemS = stripSuffixes(sWord)
                        
                        if (stemC.length >= 2 && stemS.length >= 2 && (stemC == stemS || stemC.startsWith(stemS) || stemS.startsWith(stemC))) {
                            matchFound = true
                            matchPoints = 0.8
                        }
                    }

                    if (matchFound) {
                        score += matchPoints * wordWeight
                        matchedSentenceIndices.add(sIdx)
                        break // avoid matching same defWord with multiple sentenceWords
                    }
                }
            }

            if (score > 0) {
                // Normalize score to favor concise, accurate matches over long generic descriptions
                val normalizedScore = score / Math.sqrt(defWords.size.toDouble())
                if (normalizedScore > bestScore) {
                    bestScore = normalizedScore
                    bestPhrase = def
                    
                    // Re-construct the matched words in the original sentence to show the user where it is
                    bestMatchedWordsInSentence = matchedSentenceIndices.sorted()
                        .map { sentenceWords[it] }
                        .joinToString(" ")
                }
            }
        }

        // Return a match if the score is above a safe threshold
        return if (bestScore >= 0.25 && bestPhrase != null && bestMatchedWordsInSentence.isNotEmpty()) {
            Pair(bestPhrase, bestMatchedWordsInSentence)
        } else {
            null
        }
    }
}
