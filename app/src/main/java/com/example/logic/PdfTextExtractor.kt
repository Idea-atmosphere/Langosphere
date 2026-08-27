package com.example.logic

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

object PdfTextExtractor {

    private var isInitialized = false

    private fun ensureInit(context: Context) {
        if (!isInitialized) {
            PDFBoxResourceLoader.init(context)
            isInitialized = true
        }
    }

    /**
     * Extracts the text of every page of a PDF as a separate list entry (one
     * string per page, in the original page order), so the reader screen can
     * show/navigate the document page by page instead of one long merged
     * block of text. Always returns at least one entry (an empty/error
     * string) so callers can rely on a non-empty page list.
     */
    fun extractPages(context: Context, uri: Uri): List<String> {
        ensureInit(context)
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val document = PDDocument.load(inputStream)
                try {
                    val pageCount = document.numberOfPages
                    if (pageCount <= 0) {
                        listOf("")
                    } else {
                        (0 until pageCount).map { pageIndex ->
                            val stripper = PDFTextStripper()
                            stripper.startPage = pageIndex + 1
                            stripper.endPage = pageIndex + 1
                            stripper.getText(document)
                        }
                    }
                } finally {
                    document.close()
                }
            } ?: listOf("")
        } catch (e: Exception) {
            e.printStackTrace()
            listOf("Error reading PDF: ${e.message}")
        }
    }

    /** Convenience wrapper returning the whole document as one merged string (every page joined together with blank lines). */
    fun extractText(context: Context, uri: Uri): String = extractPages(context, uri).joinToString("\n\n")
}
