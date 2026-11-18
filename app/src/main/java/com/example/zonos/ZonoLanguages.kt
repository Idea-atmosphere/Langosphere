package com.example.zonos

/**
 * The languages the bundled Zonos3-Mini web engine can speak, as the
 * engine names them (the `tts` ids of its language registry — the same
 * table the Voice Studio's project settings offer).
 *
 * The app's learning pair stores the SOURCE language as free text typed
 * by the learner ("English", "German", "آلمانی", ...). [guess] maps the
 * common English and Persian spellings onto an engine id so the TTS
 * follows the pair automatically; the learner can always override it in
 * the TTS section (Learning tools ▸ TTS ▸ Language).
 */
data class ZonoLanguage(val id: String, val nameEn: String, val nameFa: String)

object ZonoLanguages {
    const val DEFAULT_ID = "en_us"

    val ALL: List<ZonoLanguage> = listOf(
        ZonoLanguage("en_us", "English", "انگلیسی"),
        ZonoLanguage("fr_fr", "French", "فرانسوی"),
        ZonoLanguage("de", "German", "آلمانی"),
        ZonoLanguage("es", "Spanish", "اسپانیایی"),
        ZonoLanguage("it", "Italian", "ایتالیایی"),
        ZonoLanguage("pt_br", "Portuguese", "پرتغالی"),
        ZonoLanguage("ja", "Japanese", "ژاپنی"),
        ZonoLanguage("cmn", "Chinese (Mandarin)", "چینی (ماندارین)"),
        ZonoLanguage("ko", "Korean", "کره‌ای"),
        ZonoLanguage("ru", "Russian", "روسی"),
        ZonoLanguage("ar", "Arabic", "عربی"),
        ZonoLanguage("tr", "Turkish", "ترکی"),
        ZonoLanguage("nl", "Dutch", "هلندی"),
        ZonoLanguage("sv", "Swedish", "سوئدی"),
        ZonoLanguage("no", "Norwegian", "نروژی"),
        ZonoLanguage("da", "Danish", "دانمارکی"),
        ZonoLanguage("fi", "Finnish", "فنلاندی"),
        ZonoLanguage("pl", "Polish", "لهستانی"),
        ZonoLanguage("cs", "Czech", "چکی"),
        ZonoLanguage("sk", "Slovak", "اسلواکی"),
        ZonoLanguage("hu", "Hungarian", "مجاری"),
        ZonoLanguage("ro", "Romanian", "رومانیایی"),
        ZonoLanguage("bg", "Bulgarian", "بلغاری"),
        ZonoLanguage("el", "Greek", "یونانی"),
        ZonoLanguage("uk", "Ukrainian", "اوکراینی"),
        ZonoLanguage("he", "Hebrew", "عبری"),
        ZonoLanguage("hi", "Hindi", "هندی"),
        ZonoLanguage("ur", "Urdu", "اردو"),
        ZonoLanguage("bn", "Bengali", "بنگالی"),
        ZonoLanguage("pa", "Punjabi", "پنجابی"),
        ZonoLanguage("gu", "Gujarati", "گجراتی"),
        ZonoLanguage("mr", "Marathi", "مراتی"),
        ZonoLanguage("ta", "Tamil", "تامیلی"),
        ZonoLanguage("te", "Telugu", "تلوگو"),
        ZonoLanguage("kn", "Kannada", "کانادا"),
        ZonoLanguage("ml", "Malayalam", "مالایالام"),
        ZonoLanguage("or", "Odia", "اودیا"),
        ZonoLanguage("id", "Indonesian", "اندونزیایی"),
        ZonoLanguage("ms", "Malay", "مالایی"),
        ZonoLanguage("tl", "Filipino", "فیلیپینی"),
        ZonoLanguage("th", "Thai", "تایلندی"),
        ZonoLanguage("vi", "Vietnamese", "ویتنامی"),
        ZonoLanguage("ka", "Georgian", "گرجی"),
    )

    fun byId(id: String?): ZonoLanguage? = ALL.firstOrNull { it.id == id }

    /** Extra spellings per engine id (lower-case, both scripts). The table's own names match too. */
    private val ALIASES: Map<String, List<String>> = mapOf(
        "en_us" to listOf("en", "eng", "english", "انگلیسی", "امریکایی", "بریتانیایی", "british", "american"),
        "fr_fr" to listOf("fr", "fra", "french", "français", "francais", "فرانسه", "فرانسوی"),
        "de" to listOf("ger", "deu", "german", "deutsch", "آلمانی", "المانی"),
        "es" to listOf("spa", "spanish", "español", "espanol", "castellano", "اسپانیایی", "اسپانیولی"),
        "it" to listOf("ita", "italian", "italiano", "ایتالیایی"),
        "pt_br" to listOf("pt", "por", "portuguese", "português", "portugues", "brazilian", "پرتغالی", "برزیلی"),
        "ja" to listOf("jp", "jpn", "japanese", "日本語", "ژاپنی", "ژاپونی"),
        "cmn" to listOf("zh", "zho", "chinese", "mandarin", "中文", "چینی", "ماندارین"),
        "ko" to listOf("kr", "kor", "korean", "한국어", "کره‌ای", "کره ای", "کره‌یی"),
        "ru" to listOf("rus", "russian", "русский", "روسی"),
        "ar" to listOf("ara", "arabic", "العربية", "عربی"),
        "tr" to listOf("tur", "turkish", "türkçe", "turkce", "ترکی", "ترکی استانبولی"),
        "nl" to listOf("nld", "dut", "dutch", "nederlands", "هلندی"),
        "sv" to listOf("swe", "swedish", "svenska", "سوئدی"),
        "no" to listOf("nor", "norwegian", "norsk", "نروژی"),
        "da" to listOf("dan", "danish", "dansk", "دانمارکی"),
        "fi" to listOf("fin", "finnish", "suomi", "فنلاندی"),
        "pl" to listOf("pol", "polish", "polski", "لهستانی"),
        "cs" to listOf("ces", "cze", "czech", "čeština", "چکی", "چک"),
        "sk" to listOf("slk", "slovak", "اسلواکی"),
        "hu" to listOf("hun", "hungarian", "magyar", "مجاری", "مجارستانی"),
        "ro" to listOf("ron", "rum", "romanian", "română", "رومانیایی"),
        "bg" to listOf("bul", "bulgarian", "بلغاری"),
        "el" to listOf("ell", "gre", "greek", "ελληνικά", "یونانی"),
        "uk" to listOf("ukr", "ukrainian", "اوکراینی"),
        "he" to listOf("heb", "hebrew", "עברית", "عبری"),
        "hi" to listOf("hin", "hindi", "हिन्दी", "هندی"),
        "ur" to listOf("urd", "urdu", "اردو"),
        "bn" to listOf("ben", "bengali", "bangla", "بنگالی"),
        "pa" to listOf("pan", "punjabi", "پنجابی"),
        "gu" to listOf("guj", "gujarati", "گجراتی"),
        "mr" to listOf("mar", "marathi", "مراتی"),
        "ta" to listOf("tam", "tamil", "تامیلی", "تامیل"),
        "te" to listOf("tel", "telugu", "تلوگو"),
        "kn" to listOf("kan", "kannada", "کانادا"),
        "ml" to listOf("mal", "malayalam", "مالایالام"),
        "or" to listOf("ori", "odia", "oriya", "اودیا"),
        "id" to listOf("ind", "indonesian", "bahasa indonesia", "اندونزیایی"),
        "ms" to listOf("msa", "may", "malay", "bahasa melayu", "مالایی"),
        "tl" to listOf("fil", "tgl", "filipino", "tagalog", "فیلیپینی", "تاگالوگ"),
        "th" to listOf("tha", "thai", "ไทย", "تایلندی"),
        "vi" to listOf("vie", "vietnamese", "tiếng việt", "ویتنامی"),
        "ka" to listOf("kat", "geo", "georgian", "ქართული", "گرجی"),
    )

    /**
     * Best engine id for a language name typed by the learner, or null
     * when nothing matches. Matching is case-insensitive, ignores
     * surrounding punctuation and also accepts the engine id itself.
     */
    fun guess(typed: String?): String? {
        val raw = typed?.trim()?.lowercase()?.trim('(', ')', '.', ',', '«', '»', '"', '\'')
        if (raw.isNullOrEmpty()) return null
        val key = raw.replace('\u200c', ' ').replace(Regex("\\s+"), " ")
        ALL.firstOrNull { it.id == key }?.let { return it.id }
        for (lang in ALL) {
            if (lang.nameEn.lowercase() == key || lang.nameFa == key) return lang.id
        }
        for ((id, names) in ALIASES) {
            if (names.any { it == key || it.replace('\u200c', ' ') == key }) return id
        }
        // "Chinese (Mandarin)" / "English (US)" style: match on the first word.
        val head = key.substringBefore(' ').substringBefore('(').trim()
        if (head.isNotEmpty() && head != key) return guess(head)
        return null
    }

    /** [guess] with the engine default as fallback. */
    fun resolve(typed: String?): String = guess(typed) ?: DEFAULT_ID
}
