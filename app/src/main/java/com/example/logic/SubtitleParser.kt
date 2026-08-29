package com.example.logic

import com.example.model.SubtitleEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

object SubtitleParser {
    
    suspend fun parseSubtitle(inputStream: InputStream, lang: String): List<SubtitleEntry> = withContext(Dispatchers.IO) {
        val text = inputStream.bufferedReader().use { it.readText() }
        parseSubtitleText(text, lang)
    }

    /**
     * Parses subtitle content already held as a string — used for subtitles
     * pasted straight from the clipboard (copied file contents or raw SRT/LRC
     * text). Supports the same SRT/VTT and LRC styles as [parseSubtitle].
     */
    fun parseSubtitleContent(text: String, lang: String): List<SubtitleEntry> =
        parseSubtitleText(text, lang)

    private fun parseSubtitleText(text: String, lang: String): List<SubtitleEntry> {
        val lines = text.replace("\r", "").split("\n")
        val subs = mutableListOf<SubtitleEntry>()

        if (text.contains("-->")) {
            var currentSubStart = 0.0
            var currentSubEnd = 0.0
            var currentText = ""
            var isActive = false

            fun flushCurrentBlock() {
                if (isActive && currentText.isNotBlank()) {
                    subs.add(SubtitleEntry(currentSubStart, currentSubEnd, currentText.trim()))
                }
                currentText = ""
                isActive = false
            }

            var i = 0
            while (i < lines.size) {
                val trimmed = lines[i].trim()

                if (trimmed.isEmpty()) {
                    flushCurrentBlock()
                    i++
                    continue
                }

                if (trimmed.contains("-->")) {
                    flushCurrentBlock()
                    val times = trimmed.split("-->")
                    if (times.size >= 2) {
                        currentSubStart = timeToSeconds(times[0].trim())
                        currentSubEnd = timeToSeconds(times[1].trim())
                        isActive = true
                    }
                } else if (isActive) {
                    val isCueIndex = trimmed.toIntOrNull() != null &&
                        i + 1 < lines.size && lines[i + 1].trim().contains("-->")
                    if (!isCueIndex) {
                        val cleanedLine = sanitizeSubtitleText(trimmed)
                        if (cleanedLine.isNotBlank()) {
                            currentText += if (currentText.isEmpty()) cleanedLine else " $cleanedLine"
                        }
                    }
                }
                i++
            }
            flushCurrentBlock()
        } else {
            val tempSubs = mutableListOf<SubtitleEntry>()
            
            val timeCodeRegex = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?")
            
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                
                val lower = trimmed.lowercase()
                if (lower.startsWith("[ar:") || lower.startsWith("[ti:") || lower.startsWith("[al:") || lower.startsWith("[by:") || lower.startsWith("[re:") || lower.startsWith("[ve:")) {
                    continue
                }
                
                val matches = timeCodeRegex.findAll(trimmed).toList()
                if (matches.isEmpty()) continue
                
                var content = trimmed
                for (match in matches) {
                    content = content.replace(match.value, "")
                }
                content = sanitizeSubtitleText(content).trim()
                
                if (content.isNotBlank()) {
                    for (match in matches) {
                        try {
                            val mins = match.groupValues[1].toDoubleOrNull() ?: 0.0
                            val secs = match.groupValues[2].toDoubleOrNull() ?: 0.0
                            val msStr = match.groupValues[3]
                            val ms = if (msStr.isNotEmpty()) {
                                val padded = msStr.padEnd(3, '0').substring(0, 3)
                                (padded.toDoubleOrNull() ?: 0.0) / 1000.0
                            } else {
                                0.0
                            }
                            val start = mins * 60.0 + secs + ms
                            tempSubs.add(SubtitleEntry(start, start, content))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            tempSubs.sortBy { it.start }
            for (i in tempSubs.indices) {
                val end = if (i < tempSubs.size - 1) tempSubs[i+1].start else tempSubs[i].start + 8.0
                subs.add(SubtitleEntry(tempSubs[i].start, end, tempSubs[i].text))
            }
        }
        return subs.filter { it.text.isNotBlank() }
    }

    /**
     * Write a list of SubtitleEntry to an SRT file.
     * Used to save modified subtitles back to disk.
     */
    fun writeSrtFile(entries: List<SubtitleEntry>, outputFile: File) {
        val sb = StringBuilder()
        for ((index, entry) in entries.withIndex()) {
            sb.appendLine(index + 1)
            sb.appendLine("${formatSrtTime(entry.start)} --> ${formatSrtTime(entry.end)}")
            sb.appendLine(entry.text)
            sb.appendLine()
        }
        outputFile.writeText(sb.toString())
    }

    private fun sanitizeSubtitleText(text: String): String {
        var clean = text.replace(Regex("</?[^>]+>"), "")
        clean = clean.replace(Regex("(font|color|b|i|u|span)>\\s*$", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("\\b(font|color|b|i|u|span)>", RegexOption.IGNORE_CASE), "")
        return clean.trim()
    }

    private fun timeToSeconds(timeStr: String): Double {
        val parts = timeStr.replace(',', '.').split(':')
        var seconds = 0.0
        if (parts.size == 3) {
            seconds += (parts[0].toDoubleOrNull() ?: 0.0) * 3600
            seconds += (parts[1].toDoubleOrNull() ?: 0.0) * 60
            seconds += (parts[2].toDoubleOrNull() ?: 0.0)
        } else if (parts.size == 2) {
            seconds += (parts[0].toDoubleOrNull() ?: 0.0) * 60
            seconds += (parts[1].toDoubleOrNull() ?: 0.0)
        }
        return seconds
    }

    private fun formatSrtTime(seconds: Double): String {
        val totalMs = (seconds * 1000).toLong()
        val ms = totalMs % 1000
        val totalSec = totalMs / 1000
        val sec = totalSec % 60
        val totalMin = totalSec / 60
        val min = totalMin % 60
        val hour = totalMin / 60
        return String.format("%02d:%02d:%02d,%03d", hour, min, sec, ms)
    }
}
