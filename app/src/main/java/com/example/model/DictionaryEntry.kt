package com.example.model

/**
 * @param source Display name of the imported dictionary file this entry
 * came from (e.g. "MyDict.mdx" or "words.db"), used by the dictionary popup
 * to offer per-file filter buttons when multiple dictionaries are imported.
 * Empty for the built-in default dictionary.
 * @param exampleOriginal Optional original-language (e.g. English) usage
 * example sentence for this word, when the source dictionary provides one.
 * @param exampleTranslation Optional Persian meaning/translation of
 * [exampleOriginal], when the source dictionary provides one.
 */
data class DictionaryEntry(
    val originalWord: String,
    val html: String,
    val phonetic: String,
    val pos: String,
    val def: String,
    val source: String = "",
    val exampleOriginal: String = "",
    val exampleTranslation: String = ""
)
