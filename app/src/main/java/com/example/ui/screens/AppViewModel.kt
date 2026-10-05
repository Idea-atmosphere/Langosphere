package com.example.ui.screens

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.logic.AiMemoryManager
import com.example.logic.BookQuizMaterial
import com.example.logic.BookSessionStore
import com.example.logic.QuizMaterial
import com.example.logic.SentenceCardDraft
import com.example.logic.AnkiExporter
import com.example.logic.DictionaryDatabaseHelper
import com.example.logic.DictionaryParser
import com.example.logic.BinaryMdictParser
import com.example.logic.HtmlTextUtils
import com.example.logic.LeitnerBoxManager
import com.example.logic.MdictReader
import com.example.logic.PdfTextExtractor
import com.example.logic.SqliteDictParser
import com.example.logic.SubtitleJsonParser
import com.example.logic.SubtitleParser
import com.example.logic.declaredLanguageRtl
import com.example.logic.TranslationDetector
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.LanguagePairState
import com.example.ui.theme.LanguageWeightState
import com.example.ui.theme.AppStrings
import com.example.model.DictionaryEntry
import com.example.model.JsonChunkInfo
import com.example.model.BookSentence
import com.example.model.JsonSubtitlePackage
import com.example.model.JsonWord
import com.example.model.LeitnerCard
import com.example.model.SubtitleEntry
import com.example.model.SubtitleLearningState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Kind of document most recently opened in the book reader. */
enum class ReaderDocumentType(val preferenceValue: String) {
    NONE("none"),
    PDF("pdf"),
    EPUB("epub"),
    TEXT("text");

    companion object {
        fun fromPreference(value: String?): ReaderDocumentType =
            values().firstOrNull { it.preferenceValue == value } ?: NONE
    }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** Persisted copy of the imported JSON subtitle-learning file (filesDir). */
        const val JSON_SUBTITLE_FILE = "saved_sub_json.json"
        /** Per-clip JSON lesson packages of the Online tab: online_json/<videoId>.json. */
        const val ONLINE_JSON_DIR = "online_json"

        private const val PREF_READER_DOCUMENT_URI = "reader_document_uri"
        private const val PREF_READER_DOCUMENT_TYPE = "reader_document_type"
    }

    private val sharedPrefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val dbHelper = DictionaryDatabaseHelper(application)
    private val leitnerHelper = LeitnerBoxManager(application)

    // App-wide UI language (Persian/English), changeable from the settings
    // menu in MainScreen and persisted across launches. See ui/theme/Strings.kt
    // for AppLanguage/AppStrings.
    //
    // On first launch (no saved "app_language" preference yet) the language is
    // auto-detected from the device's system language: Persian devices default
    // to Persian, English or any other system language defaults to English.
    // After that first choice, the saved preference always takes precedence.
    private val _appLanguage = MutableStateFlow(
        AppLanguage.fromCode(
            sharedPrefs.getString("app_language", null) ?: run {
                val systemLanguage = application.resources.configuration.locales.get(0).language
                if (systemLanguage == "fa") "fa" else "en"
            }
        )
    )
    val appLanguage: StateFlow<AppLanguage> = _appLanguage

    /** Switches the app's UI language (Persian/English) and persists the choice. */
    fun setAppLanguage(language: AppLanguage) {
        _appLanguage.value = language
        sharedPrefs.edit().putString("app_language", language.code).apply()
    }

    /** Builds an [AppStrings] snapshot for the current UI language, used for status/toast messages emitted from ViewModel logic (not composables). */
    private fun strings() = AppStrings(_appLanguage.value, getApplication())

    private val _isDictionaryLoaded = MutableStateFlow(false)
    val isDictionaryLoaded: StateFlow<Boolean> = _isDictionaryLoaded

    private val _isImportingDict = MutableStateFlow(false)
    val isImportingDict: StateFlow<Boolean> = _isImportingDict

    data class DictFileInfo(val name: String, val type: String)

    private val _importedDictFiles = MutableStateFlow<List<DictFileInfo>>(emptyList())
    val importedDictFiles: StateFlow<List<DictFileInfo>> = _importedDictFiles

    init { loadImportedDictFiles() }

    private fun loadImportedDictFiles() {
        val raw = sharedPrefs.getString("imported_dict_files", null)
        if (raw != null) {
            _importedDictFiles.value = raw.split("||").filter { it.isNotEmpty() }.mapNotNull {
                val parts = it.split("|")
                if (parts.size == 2) DictFileInfo(parts[0], parts[1]) else null
            }
        }
    }

    private fun saveImportedDictFiles() {
        sharedPrefs.edit().putString("imported_dict_files", _importedDictFiles.value.joinToString("||") { "${it.name}|${it.type}" }).apply()
    }

    private fun addDictFile(name: String, type: String) {
        val current = _importedDictFiles.value.toMutableList()
        current.removeAll { it.name.equals(name, ignoreCase = true) }
        current.add(DictFileInfo(name, type))
        _importedDictFiles.value = current
        saveImportedDictFiles()
    }

    fun removeDictionary(name: String, type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            when (type) { "mdd" -> context.cacheDir.listFiles()?.forEach { f -> if (f.name != "AD.css") f.delete() }; "mdx", "txt", "db" -> dbHelper.clearAllEntries() }
            val current = _importedDictFiles.value.toMutableList()
            current.removeAll { it.name.equals(name, ignoreCase = true) }
            _importedDictFiles.value = current
            saveImportedDictFiles()
            _isDictionaryLoaded.value = dbHelper.hasEntries()
            sharedPrefs.edit().putBoolean("is_dict_loaded", dbHelper.hasEntries()).apply()
        }
    }

    fun clearAllDictionaries() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            dbHelper.clearAllEntries()
            context.cacheDir.listFiles()?.forEach { f -> if (f.name != "AD.css") f.delete() }
            _isDictionaryLoaded.value = false
            _importedDictFiles.value = emptyList()
            sharedPrefs.edit().putBoolean("is_dict_loaded", false).putString("imported_dict_files", "").apply()
        }
    }

    private val _importCount = MutableStateFlow(0)
    val importCount: StateFlow<Int> = _importCount
    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError
    private val _readerText = MutableStateFlow("")
    val readerText: StateFlow<String> = _readerText
    private val _readerFileName = MutableStateFlow("")
    val readerFileName: StateFlow<String> = _readerFileName
    private val _readerDocumentUri = MutableStateFlow<Uri?>(null)
    val readerDocumentUri: StateFlow<Uri?> = _readerDocumentUri
    private val _readerDocumentType = MutableStateFlow(ReaderDocumentType.NONE)
    val readerDocumentType: StateFlow<ReaderDocumentType> = _readerDocumentType
    // PDF page-by-page reading support: when the loaded document is a PDF,
    // _readerText holds only the CURRENT page's text (see goToReaderPage),
    // while the other pages are kept in separate per-page files on disk
    // (see readerPagesDir) so switching pages doesn't need to re-extract
    // anything. For plain text documents these stay at their default values
    // and _readerText simply holds the whole document, same as before.
    private val _readerIsPdf = MutableStateFlow(false)
    val readerIsPdf: StateFlow<Boolean> = _readerIsPdf
    private val _readerPageCount = MutableStateFlow(0)
    val readerPageCount: StateFlow<Int> = _readerPageCount
    private val _readerCurrentPage = MutableStateFlow(0)
    val readerCurrentPage: StateFlow<Int> = _readerCurrentPage
    private val _isReaderFullscreen = MutableStateFlow(false)
    val isReaderFullscreen: StateFlow<Boolean> = _isReaderFullscreen
    // User-chosen custom text color for the reader section, stored as an ARGB
    // Int (see androidx.compose.ui.graphics.Color.toArgb()/Color(Int) on the
    // UI side). null means "use the theme's default text color".
    private val _readerTextColor = MutableStateFlow<Int?>(null)
    val readerTextColor: StateFlow<Int?> = _readerTextColor
    private val _activeWord = MutableStateFlow<String?>(null)
    val activeWord: StateFlow<String?> = _activeWord
    private val _activeEnglishContext = MutableStateFlow<String?>(null)
    val activeEnglishContext: StateFlow<String?> = _activeEnglishContext
    private val _activePersianContext = MutableStateFlow<String?>(null)
    val activePersianContext: StateFlow<String?> = _activePersianContext
    private val _dictionaryResults = MutableStateFlow<List<DictionaryEntry>?>(null)
    val dictionaryResults: StateFlow<List<DictionaryEntry>?> = _dictionaryResults
    private var mdxFilePath: String? = null
    private var mdictReader: MdictReader? = null
    // Display name of the currently loaded .mdx file, tagged onto entries
    // built live from mdictReader (see lookupWord) so they also carry a
    // `source` (DictionaryEntry.source) usable by the dictionary popup's
    // per-file filter buttons, same as DB-backed .txt/.mdd/.db imports.
    private var mdxSourceName: String = ""

    // Leitner box (spaced-repetition flashcards saved from the dictionary popup).
    private val _leitnerCards = MutableStateFlow<List<LeitnerCard>>(emptyList())
    val leitnerCards: StateFlow<List<LeitnerCard>> = _leitnerCards
    private val _leitnerDueCards = MutableStateFlow<List<LeitnerCard>>(emptyList())
    val leitnerDueCards: StateFlow<List<LeitnerCard>> = _leitnerDueCards
    private val _leitnerMessage = MutableStateFlow<String?>(null)
    val leitnerMessage: StateFlow<String?> = _leitnerMessage
    private val _isActiveWordInLeitner = MutableStateFlow(false)
    val isActiveWordInLeitner: StateFlow<Boolean> = _isActiveWordInLeitner

    private val _videoUri = MutableStateFlow<Uri?>(null)
    val videoUri: StateFlow<Uri?> = _videoUri
    private val _videoFileName = MutableStateFlow("")
    val videoFileName: StateFlow<String> = _videoFileName
    private val _subEnList = MutableStateFlow<List<SubtitleEntry>>(emptyList())
    val subEnList: StateFlow<List<SubtitleEntry>> = _subEnList
    private val _subFaList = MutableStateFlow<List<SubtitleEntry>>(emptyList())
    val subFaList: StateFlow<List<SubtitleEntry>> = _subFaList
    private val _subEnFileName = MutableStateFlow("")
    val subEnFileName: StateFlow<String> = _subEnFileName
    private val _subFaFileName = MutableStateFlow("")
    val subFaFileName: StateFlow<String> = _subFaFileName
    private val _subEnOffset = MutableStateFlow(0.0)
    val subEnOffset: StateFlow<Double> = _subEnOffset
    private val _subFaOffset = MutableStateFlow(0.0)
    val subFaOffset: StateFlow<Double> = _subFaOffset

    // ── JSON subtitle learning package ──
    // The AI-generated JSON learning file (see model/JsonSubtitleModels.kt
    // and logic/SubtitleJsonParser.kt). Display priority while a package is
    // loaded: JSON learning file > imported subtitle files > default
    // subtitle source (VideoPlayerScreen implements this priority). The raw
    // JSON is persisted to saved_sub_json.json so it survives restarts.
    private val _jsonSubtitles = MutableStateFlow<JsonSubtitlePackage?>(null)
    val jsonSubtitles: StateFlow<JsonSubtitlePackage?> = _jsonSubtitles

    // ── JSON slot scope: local film vs. one online clip ──
    // The JSON slot above (with its lessons, chunk merge, time shift, export
    // and word/sentence lookups) is shared UI plumbing, but its CONTENT must
    // never leak between the Video tab and the Online tab: a lesson built for
    // the film on disk has nothing to do with a YouTube clip. So the slot is
    // scoped. null = the local film (saved_sub_json.json, exactly as before);
    // a YouTube id = that clip's own package in online_json/<id>.json. The
    // Online player switches the scope while it is on screen
    // ([useOnlineJsonScope]) and every read/write below goes to the scope's
    // own file, so the local film's package is parked untouched meanwhile.
    private val _jsonScope = MutableStateFlow<String?>(null)
    /** null = the local film's JSON; otherwise the online clip id whose JSON is in the slot. */
    val jsonScope: StateFlow<String?> = _jsonScope

    private data class ParkedJson(val pkg: JsonSubtitlePackage?, val offset: Double, val fileName: String)
    /** The local film's JSON state while an online clip owns the slot. */
    private val parkedLocalJson = MutableStateFlow<ParkedJson?>(null)

    /**
     * The LOCAL film's learning JSON, whatever clip is open — the quiz's
     * "film subtitles" source. While an online clip owns the slot this reads
     * the parked package, so the quiz never mistakes a clip's JSON for the
     * film's.
     */
    val localFilmJson: StateFlow<JsonSubtitlePackage?> =
        combine(_jsonSubtitles, _jsonScope, parkedLocalJson) { pkg, scope, parked ->
            if (scope == null) pkg else parked?.pkg
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Every online clip's learning JSON, merged — the quiz's "online
     * subtitles" source. Rebuilt from the online_json/ directory on demand
     * (see [refreshOnlineQuizJson]); imports and chunk removals refresh it.
     */
    private val _onlineQuizJson = MutableStateFlow<JsonSubtitlePackage?>(null)
    val onlineQuizJson: StateFlow<JsonSubtitlePackage?> = _onlineQuizJson
    @Volatile private var jsonScopeGeneration = 0

    private fun safeVideoFileName(videoId: String): String =
        videoId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64).ifBlank { "video" }

    /** The file that persists the JSON slot of the current scope. */
    private fun currentJsonFile(): File {
        val context = getApplication<Application>()
        val scope = _jsonScope.value ?: return File(context.filesDir, JSON_SUBTITLE_FILE)
        val dir = File(context.filesDir, ONLINE_JSON_DIR).apply { mkdirs() }
        return File(dir, safeVideoFileName(scope) + ".json")
    }

    /** SharedPreferences key of the display name of the current scope's JSON. */
    private fun currentJsonNameKey(): String =
        _jsonScope.value?.let { "online_json_name_" + safeVideoFileName(it) } ?: "sub_json_file_name"

    /**
     * Gives the JSON slot to the online clip [videoId] (its own saved package
     * or nothing), or back to the local film with null. Call on the main
     * thread. Switching to a clip parks the film's package in memory; its
     * file is never touched, so the Video tab gets it back exactly as it was.
     */
    fun useOnlineJsonScope(videoId: String?) {
        val current = _jsonScope.value
        if (current == videoId) return
        if (current == null) {
            parkedLocalJson.value = ParkedJson(_jsonSubtitles.value, _jsonOffset.value, _jsonSubFileName.value)
        }
        val generation = ++jsonScopeGeneration
        _jsonScope.value = videoId
        if (videoId == null) {
            val parked = parkedLocalJson.value
            parkedLocalJson.value = null
            _jsonSubtitles.value = parked?.pkg
            _jsonOffset.value = parked?.offset ?: 0.0
            _jsonSubFileName.value = parked?.fileName ?: ""
            return
        }
        // A clip starts with an EMPTY slot at once; its own saved lesson (if
        // any) is read from disk right after.
        _jsonSubtitles.value = null
        _jsonOffset.value = 0.0
        _jsonSubFileName.value = ""
        val file = currentJsonFile()
        val nameKey = currentJsonNameKey()
        viewModelScope.launch(Dispatchers.IO) {
            val pkg = try {
                if (file.exists()) file.readText().takeIf { SubtitleJsonParser.looksLikeSubtitleJson(it) }?.let { SubtitleJsonParser.parse(it) } else null
            } catch (e: Exception) {
                e.printStackTrace(); null
            }
            if (pkg != null && generation == jsonScopeGeneration && _jsonScope.value == videoId && _jsonSubtitles.value == null) {
                _jsonSubtitles.value = pkg
                _jsonSubFileName.value = sharedPrefs.getString(nameKey, "") ?: ""
            }
        }
    }

    /**
     * The book reader's imported sentences, packaged as quiz material.
     *
     * Loaded on demand (the quiz setup screen asks for it) rather than at
     * startup: reading every stored book to build a package is wasted work for
     * a learner who opens the app to watch a film. The book store is scanned
     * once per request and the result cached, so switching the quiz's source
     * filter back and forth is free.
     */
    private val _bookQuizMaterial = MutableStateFlow<QuizMaterial?>(null)
    val bookQuizMaterial: StateFlow<QuizMaterial?> = _bookQuizMaterial

    /**
     * The normalized text of every sentence already in the Leitner box.
     *
     * The reader and the film subtitle list both mark a line as saved from
     * this, so "[+ لایتنر جمله]" turns into "در لایتنر ذخیره شد" everywhere the
     * moment any surface saves it.
     */
    private val _savedSentenceTexts = MutableStateFlow<Set<String>>(emptySet())
    val savedSentenceTexts: StateFlow<Set<String>> = _savedSentenceTexts
    private val _jsonSubFileName = MutableStateFlow("")
    val jsonSubFileName: StateFlow<String> = _jsonSubFileName

    // ── JSON subtitle time sync ──
    // Cumulative time shift applied to the JSON package's timestamps (same
    // feature as the EN/FA subtitle sync). The shifted timestamps are
    // persisted back into saved_sub_json.json, so the shift survives app
    // restarts; the offset itself is in-memory only (it restarts at 0 after
    // a relaunch, matching the EN/FA offset behavior).
    private val _jsonOffset = MutableStateFlow(0.0)
    val jsonOffset: StateFlow<Double> = _jsonOffset

    /** Shifts every timed JSON subtitle line forward/back by [seconds]. */
    fun shiftJson(seconds: Double) {
        val pkg = _jsonSubtitles.value ?: return
        _jsonOffset.value += seconds
        _jsonSubtitles.value = pkg.copy(
            subtitles = pkg.subtitles.map { s ->
                if (s.start == null && s.end == null) s
                else s.copy(
                    start = s.start?.let { (it + seconds).coerceAtLeast(0.0) },
                    end = s.end?.let { (it + seconds).coerceAtLeast(0.0) }
                )
            }
        )
        persistJsonSubtitle()
    }

    /** Undoes every JSON time shift applied this session (back to import time). */
    fun resetJson() {
        val seconds = -_jsonOffset.value
        if (seconds == 0.0) return
        _jsonOffset.value = 0.0
        _jsonSubtitles.value = _jsonSubtitles.value?.copy(
            subtitles = _jsonSubtitles.value!!.subtitles.map { s ->
                if (s.start == null && s.end == null) s
                else s.copy(
                    start = s.start?.let { (it + seconds).coerceAtLeast(0.0) },
                    end = s.end?.let { (it + seconds).coerceAtLeast(0.0) }
                )
            }
        )
        persistJsonSubtitle()
    }

    /** Writes the current JSON package (with any time shifts) back to disk. */
    private fun persistJsonSubtitle() {
        val pkg = _jsonSubtitles.value ?: return
        try {
            currentJsonFile().writeText(SubtitleJsonParser.serialize(pkg))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── Learning settings ──
    // "Use Dictionary When JSON Learning Data Exists": when ENABLED the
    // normal dictionary opens on word tap even with a JSON file loaded
    // (and shows the JSON word data first); when DISABLED word taps open
    // the JSON learning sheet instead of the dictionary.
    private val _useDictionaryWithJson = MutableStateFlow(sharedPrefs.getBoolean("use_dictionary_with_json", true))
    val useDictionaryWithJson: StateFlow<Boolean> = _useDictionaryWithJson

    /** User's CEFR learning level (A1..C2), used for level-appropriate explanations and prompts. */
    private val _learningLevel = MutableStateFlow(sharedPrefs.getString("learning_level", "B1") ?: "B1")
    val learningLevel: StateFlow<String> = _learningLevel

    // ── Subtitle learning sheet state (sentence lesson / word analysis) ──
    private val _learningSheet = MutableStateFlow<SubtitleLearningState?>(null)
    val learningSheet: StateFlow<SubtitleLearningState?> = _learningSheet

    // JSON word data for the currently looked-up word (rendered by the
    // dictionary bottom sheet on top of the normal dictionary results, so
    // "the dictionary system follows the JSON learning data").
    private val _activeJsonWord = MutableStateFlow<JsonWord?>(null)
    val activeJsonWord: StateFlow<JsonWord?> = _activeJsonWord

    private val _isTranslatingSingle = MutableStateFlow(false)
    val isTranslatingSingle: StateFlow<Boolean> = _isTranslatingSingle
    private val _singleTranslateError = MutableStateFlow<String?>(null)
    val singleTranslateError: StateFlow<String?> = _singleTranslateError
    private val _translatingIndex = MutableStateFlow(-1)
    val translatingIndex: StateFlow<Int> = _translatingIndex
    private val _isLearning = MutableStateFlow(false)
    val isLearning: StateFlow<Boolean> = _isLearning
    private val _learnResult = MutableStateFlow<String?>(null)
    val learnResult: StateFlow<String?> = _learnResult
    private val _learnProgress = MutableStateFlow<Pair<Int, Int>>(0 to 0)
    val learnProgress: StateFlow<Pair<Int, Int>> = _learnProgress

    data class ManagedFileInfo(val name: String, val size: Long, val lastModified: Long)
    private val _managedFiles = MutableStateFlow<List<ManagedFileInfo>>(emptyList())
    val managedFiles: StateFlow<List<ManagedFileInfo>> = _managedFiles
    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult
    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage

    private var translateJob: Job? = null
    private var learnSubsJob: Job? = null
    private var learnDictJob: Job? = null

    fun shiftSubEn(seconds: Double) { _subEnOffset.value += seconds; _subEnList.update { it.map { e -> e.copy(start = (e.start + seconds).coerceAtLeast(0.0), end = (e.end + seconds).coerceAtLeast(0.0)) } } }
    fun shiftSubFa(seconds: Double) { _subFaOffset.value += seconds; _subFaList.update { it.map { e -> e.copy(start = (e.start + seconds).coerceAtLeast(0.0), end = (e.end + seconds).coerceAtLeast(0.0)) } } }

    fun stopTranslation() { translateJob?.cancel(); translateJob = null; _isTranslatingSingle.value = false; _translatingIndex.value = -1 }
    fun stopLearning() { learnSubsJob?.cancel(); learnDictJob?.cancel(); learnSubsJob = null; learnDictJob = null; _isLearning.value = false; _learnProgress.value = 0 to 0 }
    fun updateSubFaList(newList: List<SubtitleEntry>) { _subFaList.value = newList; saveSubFaSrt() }
    fun saveSubFaSrt(): File? { val faList = _subFaList.value; if (faList.isEmpty()) return null; val file = File(getApplication<Application>().filesDir, "saved_sub_fa.srt"); SubtitleParser.writeSrtFile(faList, file); return file }

    fun translateSingleSubtitle(index: Int, baseUrl: String, apiKey: String, model: String, targetLang: String) {
        val enList = _subEnList.value
        if (index < 0 || index >= enList.size) { _singleTranslateError.value = strings().invalidIndexError; return }
        _isTranslatingSingle.value = true; _translatingIndex.value = index; _singleTranslateError.value = null
        translateJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val enSub = enList[index]
                val config = com.example.logic.AiService.TranslationConfig(apiKey, baseUrl, model)
                val systemPrompt = "You are a subtitle translator. Translate the given text to $targetLang. Return ONLY the translated text, nothing else."
                android.util.Log.d("AppViewModel", "=== translateSingleSubtitle START ===")
                android.util.Log.d("AppViewModel", "EN index=$index, start=${enSub.start}, text='${enSub.text.take(80)}'")
                val result = com.example.logic.AiService.chat(config, listOf(Pair("user", enSub.text)), systemPrompt, context, noteSavedMessage = strings().aiNoteSaved)
                result.fold(
                    onSuccess = { response ->
                        val translation = response.trim().removeSurrounding("\"").removeSurrounding("'").trim()
                        android.util.Log.d("AppViewModel", "Translation: '$translation', isBlank=${translation.isBlank()}")
                        if (translation.isNotBlank()) {
                            val faList = _subFaList.value.toMutableList()
                            var bestFaIdx = -1; var bestDiff = Double.MAX_VALUE
                            for (i in faList.indices) { val diff = Math.abs(faList[i].start - enSub.start); if (diff < bestDiff) { bestDiff = diff; bestFaIdx = i } }
                            if (bestFaIdx >= 0 && bestDiff < 0.5) { faList[bestFaIdx] = faList[bestFaIdx].copy(text = translation); android.util.Log.d("AppViewModel", "Updated FA idx=$bestFaIdx") }
                            else { faList.add(SubtitleEntry(enSub.start, enSub.end, translation, "fa")); faList.sortBy { it.start }; android.util.Log.d("AppViewModel", "Added NEW FA at start=${enSub.start}") }
                            _subFaList.value = faList; saveSubFaSrt()
                            android.util.Log.d("AppViewModel", "=== translateSingleSubtitle END ===")
                        } else { _singleTranslateError.value = strings().translationEmptyError }
                    },
                    onFailure = { e -> _singleTranslateError.value = strings().apiErrorMessage(e) }
                )
            } catch (e: Exception) { _singleTranslateError.value = strings().apiErrorMessage(e) } finally { _isTranslatingSingle.value = false; _translatingIndex.value = -1 }
        }
    }

    fun learnFromSubtitlesOnly() {
        val enList = _subEnList.value; val faList = _subFaList.value
        if (enList.isEmpty()) { _learnResult.value = strings().subEnNotLoaded; return }
        if (faList.isEmpty()) { _learnResult.value = strings().subFaNotLoadedBoth; return }
        _isLearning.value = true; _learnResult.value = null; _learnProgress.value = 0 to 0
        learnSubsJob = viewModelScope.launch(Dispatchers.IO) {
            try { val context = getApplication<Application>(); val count = AiMemoryManager.learnFromSubtitles(context, enList, faList, null) { cur, tot -> _learnProgress.value = cur to tot }; _learnResult.value = if (count == 0) strings().noMatchedPairsFound else strings().learnedPairsCount(count) } catch (e: Exception) { _learnResult.value = strings().errorWithMessage(e.message) } finally { _isLearning.value = false }
        }
    }

    fun learnFromDictionaryOnly() {
        _isLearning.value = true; _learnResult.value = null; _learnProgress.value = 0 to 0
        learnDictJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val words = mutableSetOf<String>()
                words.addAll(dbHelper.getAllWords())
                if (mdictReader != null && mdictReader!!.isInitialized) { words.addAll(mdictReader!!.getAllKeysSorted().map { it.word }) }
                if (words.isEmpty()) { _learnResult.value = strings().dictNotLoadedForLearning; return@launch }
                val total = words.size; var count = 0
                for ((index, word) in words.withIndex()) {
                    _learnProgress.value = (index + 1) to total
                    var def = ""
                    val entries = dbHelper.getEntriesForWord(word)
                    if (entries.isNotEmpty()) def = entries[0].def.take(200)
                    else if (mdictReader != null && mdictReader!!.isInitialized) { try { val infos = mdictReader!!.locateAll(word); if (infos.isNotEmpty()) { var html = mdictReader!!.readOneMdx(infos[0]); if (html.startsWith("@@@LINK=")) { val target = html.removePrefix("@@@LINK=").replace(Regex("[\\n\\r\\x00]"), "").trim(); if (target.isNotEmpty()) { val li = mdictReader!!.locateAll(target); if (li.isNotEmpty()) html = mdictReader!!.readOneMdx(li[0]) } }; def = HtmlTextUtils.htmlToReadableText(html).take(200) } } catch (e: Exception) {} }
                    if (def.isNotBlank()) { val notes = AiMemoryManager.loadDictNotes(context); if (notes.none { it.word.equals(word, ignoreCase = true) }) { AiMemoryManager.addDictNote(context, word, def); count++ } }
                }
                _learnResult.value = strings().learnedWordsCount(count, total)
            } catch (e: Exception) { _learnResult.value = strings().errorWithMessage(e.message) } finally { _isLearning.value = false }
        }
    }

    fun clearSingleTranslateError() { _singleTranslateError.value = null }
    fun clearLearnResult() { _learnResult.value = null; _learnProgress.value = 0 to 0 }

    fun refreshManagedFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>(); val filesDir = context.filesDir; val files = mutableListOf<ManagedFileInfo>()
            listOf("saved_sub_en.srt", "saved_sub_fa.srt", "saved_sub_json.json", "saved_reader_text.txt", "current_dict.mdx", "current_dict.mdd").forEach { name -> val f = File(filesDir, name); if (f.exists()) files.add(ManagedFileInfo(name, f.length(), f.lastModified())) }
            val aiMemoryDir = File(filesDir, "ai_memory")
            if (aiMemoryDir.exists() && aiMemoryDir.isDirectory) aiMemoryDir.listFiles()?.forEach { f -> if (f.isFile) files.add(ManagedFileInfo("ai_memory/${f.name}", f.length(), f.lastModified())) }
            _managedFiles.value = files
        }
    }
    fun deleteManagedFile(fileName: String) { viewModelScope.launch(Dispatchers.IO) { val f = File(getApplication<Application>().filesDir, fileName); if (f.exists()) f.delete(); refreshManagedFiles() } }
    fun exportToDownloads(fileName: String) { viewModelScope.launch(Dispatchers.IO) { try { val context = getApplication<Application>(); val sf = File(context.filesDir, fileName); if (!sf.exists()) { _exportResult.value = strings().fileNotFoundError; return@launch }; val dn = fileName.substringAfterLast("/"); val path = saveToDownloads(context, dn, sf); _exportResult.value = strings().savedAtPath(path) } catch (e: Exception) { _exportResult.value = strings().errorWithMessage(e.message) } } }
    fun exportAllToDownloads() { viewModelScope.launch(Dispatchers.IO) { try { val context = getApplication<Application>(); val filesDir = context.filesDir; val names = mutableListOf<String>(); listOf("saved_sub_en.srt", "saved_sub_fa.srt", "saved_sub_json.json", "saved_reader_text.txt", "current_dict.mdx", "current_dict.mdd").forEach { n -> val f = File(filesDir, n); if (f.exists()) names.add(n) }; val amDir = File(filesDir, "ai_memory"); if (amDir.exists() && amDir.isDirectory) amDir.listFiles()?.forEach { f -> if (f.isFile) names.add("ai_memory/${f.name}") }; if (names.isEmpty()) { _exportResult.value = strings().noFilesToExport; return@launch }; var ok = 0; val savedNames = mutableListOf<String>(); for (n in names) { try { val sf = File(filesDir, n); val dn = n.substringAfterLast("/"); saveToDownloads(context, dn, sf); ok++; savedNames.add(dn) } catch (e: Exception) {} }; _exportResult.value = strings().exportedFilesSummary(ok, names.size, savedNames.joinToString(", ")) } catch (e: Exception) { _exportResult.value = strings().errorWithMessage(e.message) } } }
    fun exportSrtToDownloads() { viewModelScope.launch(Dispatchers.IO) { try { val context = getApplication<Application>(); val faList = _subFaList.value; if (faList.isEmpty()) { _saveMessage.value = strings().noFaSubtitleExists; return@launch }; val srtFile = saveSubFaSrt() ?: throw Exception(strings().srtCreateError); val displayName = if (_subFaFileName.value.isNotEmpty()) _subFaFileName.value else "subtitle_fa.srt"; val path = saveToDownloads(context, displayName, srtFile); _saveMessage.value = strings().savedAtPath(path) } catch (e: Exception) { _saveMessage.value = strings().errorWithMessage(e.message) } } }
    private fun saveToDownloads(context: Context, displayName: String, srcFile: File): String { return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) { val resolver = context.contentResolver; val values = android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName); put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream"); put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS) }; val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw Exception(strings().downloadsCreateError); resolver.openOutputStream(uri)?.use { out -> srcFile.inputStream().use { inp -> inp.copyTo(out) } } ?: throw Exception(strings().fileWriteError); "Downloads/$displayName" } else { val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS); if (!downloadsDir.exists()) downloadsDir.mkdirs(); val destFile = File(downloadsDir, displayName); srcFile.copyTo(destFile, overwrite = true); destFile.absolutePath } }
    fun clearExportResult() { _exportResult.value = null }
    fun clearSaveMessage() { _saveMessage.value = null }

    // Per-page text files for an imported PDF, kept separate from the plain
    // saved_reader_text.txt used for non-PDF documents so switching pages
    // doesn't need to re-extract or re-split anything.
    private fun readerPagesDir(context: Context): File = File(context.filesDir, "reader_pages").apply { mkdirs() }
    private fun writeReaderPages(context: Context, pages: List<String>) {
        val dir = readerPagesDir(context)
        dir.listFiles()?.forEach { it.delete() }
        pages.forEachIndexed { index, pageText -> File(dir, "page_$index.txt").writeText(pageText) }
    }
    private fun readReaderPage(context: Context, index: Int): String { val f = File(readerPagesDir(context), "page_$index.txt"); return if (f.exists()) f.readText() else "" }

    fun updateReaderText(newText: String) {
        _readerText.value = newText
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            if (_readerIsPdf.value) File(readerPagesDir(context), "page_${_readerCurrentPage.value}.txt").writeText(newText)
            else File(context.filesDir, "saved_reader_text.txt").writeText(newText)
        }
    }

    /** Jumps to a specific PDF page (0-based), clamped to the valid range. No-op when the loaded document isn't a PDF. */
    fun goToReaderPage(index: Int) {
        val count = _readerPageCount.value
        if (!_readerIsPdf.value || count == 0) return
        val clamped = index.coerceIn(0, count - 1)
        if (clamped == _readerCurrentPage.value) return
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val pageText = readReaderPage(context, clamped)
            _readerCurrentPage.value = clamped
            _readerText.update { pageText }
            sharedPrefs.edit().putInt("reader_current_page", clamped).apply()
        }
    }
    fun nextReaderPage() = goToReaderPage(_readerCurrentPage.value + 1)
    fun previousReaderPage() = goToReaderPage(_readerCurrentPage.value - 1)
    fun toggleReaderFullscreen() { _isReaderFullscreen.value = !_isReaderFullscreen.value }
    fun setReaderFullscreen(value: Boolean) { _isReaderFullscreen.value = value }

    /**
     * Sets (and persists) the custom text color used to display the reader's
     * text. Pass null to reset back to the theme's default text color.
     */
    fun setReaderTextColor(colorArgb: Int?) {
        _readerTextColor.value = colorArgb
        if (colorArgb != null) sharedPrefs.edit().putInt("reader_text_color", colorArgb).apply()
        else sharedPrefs.edit().remove("reader_text_color").apply()
    }

    /**
     * The "source → target" language pair (Settings ▸ Prompts) is
     * restored before anything else here, so labels built from the ViewModel
     * (toasts, default file names) never run ahead of the saved value.
     * MainActivity restores it too, before the first composition.
     */
    init {
        LanguagePairState.restore(getApplication<Application>())
        LanguageWeightState.restore(getApplication<Application>())
    }

    init { loadPersistedData() }
    init { refreshLeitnerCards() }

    private fun loadPersistedData() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val savedType = ReaderDocumentType.fromPreference(
                sharedPrefs.getString(PREF_READER_DOCUMENT_TYPE, null),
            )
            val savedUri = sharedPrefs.getString(PREF_READER_DOCUMENT_URI, null)
                ?.takeIf { it.isNotBlank() }
                ?.let { value -> runCatching { Uri.parse(value) }.getOrNull() }
            val savedName = sharedPrefs.getString("reader_file_name", "") ?: ""
            val legacyIsPdf = sharedPrefs.getBoolean("reader_is_pdf", false)
            val pageCount = sharedPrefs.getInt("reader_page_count", 0)

            // Restore the source handle as well as the extracted text cache.
            // The BookReaderState is screen-scoped and gets rebuilt on process
            // death, while this URI has a persisted SAF read grant.
            _readerFileName.value = savedName
            _readerDocumentUri.value = savedUri
            _readerDocumentType.value = savedType

            when {
                savedType == ReaderDocumentType.EPUB -> {
                    _readerIsPdf.value = false
                    _readerPageCount.value = 0
                    _readerCurrentPage.value = 0
                    _readerText.value = ""
                }
                savedType == ReaderDocumentType.PDF ||
                    (savedType == ReaderDocumentType.NONE && legacyIsPdf && pageCount > 0) -> {
                    if (pageCount > 0) {
                        val currentPage = sharedPrefs.getInt("reader_current_page", 0).coerceIn(0, pageCount - 1)
                        _readerIsPdf.value = true
                        _readerPageCount.value = pageCount
                        _readerCurrentPage.value = currentPage
                        _readerText.value = readReaderPage(context, currentPage)
                    } else if (savedType == ReaderDocumentType.PDF && savedUri != null) {
                        // Recover a PDF that was selected just before the app
                        // closed, before its page cache had finished writing.
                        _readerIsPdf.value = true
                        _readerPageCount.value = 0
                        _readerCurrentPage.value = 0
                        loadTextFile(savedUri, "application/pdf")
                    }
                }
                savedType == ReaderDocumentType.TEXT -> {
                    _readerIsPdf.value = false
                    _readerPageCount.value = 0
                    _readerCurrentPage.value = 0
                    val readerFile = File(context.filesDir, "saved_reader_text.txt")
                    if (readerFile.exists()) _readerText.value = readerFile.readText()
                }
                else -> {
                    // Backward-compatible restore for installs that predate
                    // reader_document_uri/type, including their PDF page cache.
                    if (legacyIsPdf && pageCount > 0) {
                        val currentPage = sharedPrefs.getInt("reader_current_page", 0).coerceIn(0, pageCount - 1)
                        _readerIsPdf.value = true
                        _readerPageCount.value = pageCount
                        _readerCurrentPage.value = currentPage
                        _readerText.value = readReaderPage(context, currentPage)
                    } else {
                        val readerFile = File(context.filesDir, "saved_reader_text.txt")
                        if (readerFile.exists()) _readerText.value = readerFile.readText()
                    }
                }
            }
            if (sharedPrefs.contains("reader_text_color")) { _readerTextColor.value = sharedPrefs.getInt("reader_text_color", 0) }
            val videoUriStr = sharedPrefs.getString("video_uri_str", null)
            if (!videoUriStr.isNullOrEmpty()) { try { _videoUri.value = Uri.parse(videoUriStr); _videoFileName.value = sharedPrefs.getString("video_file_name", "") ?: "" } catch (e: Exception) { e.printStackTrace() } }
            val subEnFile = File(context.filesDir, "saved_sub_en.srt")
            if (subEnFile.exists()) { try { subEnFile.inputStream().use { s -> _subEnList.value = SubtitleParser.parseSubtitle(s, "en"); _subEnFileName.value = sharedPrefs.getString("sub_en_file_name", "") ?: "" } } catch (e: Exception) { e.printStackTrace() } }
            val subFaFile = File(context.filesDir, "saved_sub_fa.srt")
            if (subFaFile.exists()) { try { subFaFile.inputStream().use { s -> _subFaList.value = SubtitleParser.parseSubtitle(s, "fa"); _subFaFileName.value = sharedPrefs.getString("sub_fa_file_name", "") ?: "" } } catch (e: Exception) { e.printStackTrace() } }
            val jsonSubFile = File(context.filesDir, JSON_SUBTITLE_FILE)
            if (jsonSubFile.exists()) { try { val jsonText = jsonSubFile.readText(); if (SubtitleJsonParser.looksLikeSubtitleJson(jsonText)) { val restoredPkg = SubtitleJsonParser.parse(jsonText); val restoredName = sharedPrefs.getString("sub_json_file_name", "") ?: ""; if (_jsonScope.value == null) { _jsonSubtitles.value = restoredPkg; _jsonSubFileName.value = restoredName } else { parkedLocalJson.value = ParkedJson(restoredPkg, 0.0, restoredName) } } } catch (e: Exception) { e.printStackTrace() } }
            var hasEntries = dbHelper.hasEntries()
            if (!hasEntries) { try { context.assets.open("default_dict.txt").use { s -> com.example.logic.DictionaryParser.parseAndSaveToDb(s, dbHelper, true) }; hasEntries = dbHelper.hasEntries() } catch (e: Exception) { e.printStackTrace() } }
            _isDictionaryLoaded.value = hasEntries
            val mdxFile = File(context.filesDir, "current_dict.mdx")
            if (mdxFile.exists()) { try { android.util.Log.d("AppViewModel", "Restoring MdictReader: ${mdxFile.absolutePath}, size=${mdxFile.length()}"); val reader = MdictReader(mdxFile.absolutePath); reader.open(); reader.initDict(maxKeys = 0); mdictReader = reader; mdxFilePath = mdxFile.absolutePath; mdxSourceName = _importedDictFiles.value.firstOrNull { it.type == "mdx" }?.name ?: ""; android.util.Log.d("AppViewModel", "MdictReader restored OK, numEntries=${reader.numEntries}") } catch (e: Exception) { android.util.Log.e("AppViewModel", "Failed to restore MdictReader: ${e.message}") } } else { android.util.Log.w("AppViewModel", "No current_dict.mdx found") }
        }
    }

    fun loadDictionary(uri: Uri, clearFirst: Boolean = false) {
        _isImportingDict.value = true; _importCount.value = 0; _importError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                val originalDisplayName = getUriDisplayName(context, uri)
                val displayName = originalDisplayName.lowercase()
                val isSqliteDb = displayName.endsWith(".db") || displayName.endsWith(".sqlite") || displayName.endsWith(".sqlite3")
                if (displayName.endsWith(".mdx")) {
                    val destFile = java.io.File(context.filesDir, "current_dict.mdx")
                    context.contentResolver.openInputStream(uri)?.use { input -> java.io.FileOutputStream(destFile).use { output -> input.copyTo(output) } }
                    val maxWords = sharedPrefs.getInt("max_words", 0)
                    BinaryMdictParser.parseMdx(destFile.absolutePath, dbHelper, clearFirst, maxWords, originalDisplayName) { p -> _importCount.value = p }
                    mdictReader?.close(); val reader = MdictReader(destFile.absolutePath); reader.open(); reader.initDict(maxKeys = 0); mdictReader = reader; mdxFilePath = destFile.absolutePath; mdxSourceName = originalDisplayName
                } else if (displayName.endsWith(".mdd")) {
                    val destFile = java.io.File(context.filesDir, "current_dict.mdd")
                    context.contentResolver.openInputStream(uri)?.use { input -> java.io.FileOutputStream(destFile).use { output -> input.copyTo(output) } }
                    BinaryMdictParser.parseMdd(destFile.absolutePath, dbHelper, clearFirst, originalDisplayName) { p -> _importCount.value = p }
                } else if (isSqliteDb) {
                    // SQLiteDatabase needs a real file path (not a content:// stream), so
                    // copy the picked .db/.sqlite/.sqlite3 file into cacheDir first, import
                    // every word/definition pair straight into dictionary_entries (the same
                    // shared table used by .txt and .mdx imports), then discard the copy —
                    // unlike .mdx there is no separate live reader to keep around.
                    val tempFile = java.io.File(context.cacheDir, "import_dict_${System.currentTimeMillis()}.db")
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input -> java.io.FileOutputStream(tempFile).use { output -> input.copyTo(output) } }
                        val maxWords = sharedPrefs.getInt("max_words", 0)
                        SqliteDictParser.parseSqliteDb(tempFile.absolutePath, dbHelper, clearFirst, maxWords, originalDisplayName) { p -> _importCount.value = p }
                    } finally {
                        tempFile.delete()
                    }
                } else {
                    context.contentResolver.openInputStream(uri)?.use { s -> DictionaryParser.parseAndSaveToDb(s, dbHelper, clearFirst, originalDisplayName) { p -> _importCount.value = p } }
                }
                val isLoaded = dbHelper.hasEntries(); _isDictionaryLoaded.value = isLoaded; sharedPrefs.edit().putBoolean("is_dict_loaded", isLoaded).apply()
                addDictFile(originalDisplayName, when { displayName.endsWith(".mdx") -> "mdx"; displayName.endsWith(".mdd") -> "mdd"; isSqliteDb -> "db"; else -> "txt" })
            } catch (e: Exception) { e.printStackTrace(); _importError.value = strings().apiErrorMessage(e) } finally { _isImportingDict.value = false }
        }
    }

    fun clearImportError() { _importError.value = null }

    /**
     * Saves the SAF document handle as well as its display name. The extracted
     * PDF text is cached separately, so without this URI the reader can still
     * show the book after a restart while actions that need to re-read the PDF
     * (such as range extraction) incorrectly think no document is open.
     */
    fun rememberReaderDocument(uri: Uri, name: String, type: ReaderDocumentType) {
        val context = getApplication<Application>()
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { error ->
            android.util.Log.w("AppViewModel", "Could not persist the reader document permission", error)
        }

        _readerDocumentUri.value = uri
        _readerDocumentType.value = type
        _readerFileName.value = name
        _readerText.value = ""
        _readerIsPdf.value = type == ReaderDocumentType.PDF
        _readerPageCount.value = 0
        _readerCurrentPage.value = 0

        sharedPrefs.edit()
            .putString(PREF_READER_DOCUMENT_URI, uri.toString())
            .putString(PREF_READER_DOCUMENT_TYPE, type.preferenceValue)
            .putString("reader_file_name", name)
            .putBoolean("reader_is_pdf", type == ReaderDocumentType.PDF)
            .putInt("reader_page_count", 0)
            .putInt("reader_current_page", 0)
            .apply()
    }

    fun loadTextFile(uri: Uri, mimeType: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                val originalName = getUriDisplayName(context, uri)
                val isPdf = mimeType?.contains("pdf", ignoreCase = true) == true ||
                    originalName.endsWith(".pdf", ignoreCase = true) ||
                    uri.toString().endsWith(".pdf", true)
                val documentType = if (isPdf) ReaderDocumentType.PDF else ReaderDocumentType.TEXT
                _readerDocumentUri.value = uri
                _readerDocumentType.value = documentType
                if (isPdf) {
                    // Split into per-page text files so the reader screen can show
                    // and navigate the PDF page by page (see readerPagesDir /
                    // goToReaderPage) instead of one long merged block of text.
                    val pages = PdfTextExtractor.extractPages(context, uri).ifEmpty { listOf("") }
                    writeReaderPages(context, pages)
                    File(context.filesDir, "saved_reader_text.txt").delete()
                    _readerIsPdf.value = true; _readerPageCount.value = pages.size; _readerCurrentPage.value = 0
                    _readerText.update { pages.first() }
                    sharedPrefs.edit().putBoolean("reader_is_pdf", true).putInt("reader_page_count", pages.size).putInt("reader_current_page", 0).apply()
                } else {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                    readerPagesDir(context).listFiles()?.forEach { it.delete() }
                    File(context.filesDir, "saved_reader_text.txt").writeText(text)
                    _readerIsPdf.value = false; _readerPageCount.value = 0; _readerCurrentPage.value = 0
                    _readerText.update { text }
                    sharedPrefs.edit().putBoolean("reader_is_pdf", false).putInt("reader_page_count", 0).putInt("reader_current_page", 0).apply()
                }
                _readerFileName.value = originalName
                sharedPrefs.edit()
                    .putString(PREF_READER_DOCUMENT_URI, uri.toString())
                    .putString(PREF_READER_DOCUMENT_TYPE, documentType.preferenceValue)
                    .putString("reader_file_name", originalName)
                    .apply()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun setVideo(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>(); val originalName = getUriDisplayName(context, uri)
            _videoUri.value = uri; _videoFileName.value = originalName
            sharedPrefs.edit().putString("video_uri_str", uri.toString()).putString("video_file_name", originalName).apply()
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun loadSubEn(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                _subEnOffset.value = 0.0; val originalName = getUriDisplayName(context, uri)
                context.contentResolver.openInputStream(uri)?.use { s -> val f = File(context.filesDir, "saved_sub_en.srt"); f.outputStream().use { o -> s.copyTo(o) } }
                val f = File(context.filesDir, "saved_sub_en.srt")
                if (f.exists()) { f.inputStream().use { s -> _subEnList.value = SubtitleParser.parseSubtitle(s, "en"); _subEnFileName.value = originalName; sharedPrefs.edit().putString("sub_en_file_name", originalName).apply() } }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun loadSubFa(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                _subFaOffset.value = 0.0; val originalName = getUriDisplayName(context, uri)
                context.contentResolver.openInputStream(uri)?.use { s -> val f = File(context.filesDir, "saved_sub_fa.srt"); f.outputStream().use { o -> s.copyTo(o) } }
                val f = File(context.filesDir, "saved_sub_fa.srt")
                if (f.exists()) { f.inputStream().use { s -> _subFaList.value = SubtitleParser.parseSubtitle(s, "fa"); _subFaFileName.value = originalName; sharedPrefs.edit().putString("sub_fa_file_name", originalName).apply() } }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    /**
     * Loads an English subtitle directly from the clipboard. The clipboard may
     * hold either the copied contents of a subtitle file (plain text — SRT/VTT
     * with `-->` cues or LRC-style `[mm:ss.xx]` lines) or a content:// URI of a
     * subtitle file copied from a file manager. See [loadSubtitleFromClipboard].
     */
    fun loadSubEnFromClipboard() {
        loadSubtitleFromClipboard(isEnglish = true)
    }

    /** Loads a Persian subtitle directly from the clipboard. See [loadSubEnFromClipboard]. */
    fun loadSubFaFromClipboard() {
        loadSubtitleFromClipboard(isEnglish = false)
    }

    /**
     * "Send to the video tab" from the Online tab: the captions of an online
     * clip (and its optional translation track) become the EN/FA subtitle
     * slots, persisted like a picked file, so they can be studied, edited by
     * the AI tab and used with a local copy of the clip.
     */
    fun loadSubtitlesFromOnline(enList: List<SubtitleEntry>, faList: List<SubtitleEntry>, sourceTitle: String) {
        if (enList.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val s = strings()
            try {
                val safeTitle = sourceTitle.trim().replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").take(60).ifBlank { "online" }
                val enName = "$safeTitle.en.srt"
                SubtitleParser.writeSrtFile(enList, File(context.filesDir, "saved_sub_en.srt"))
                _subEnOffset.value = 0.0
                _subEnList.value = enList
                _subEnFileName.value = enName
                sharedPrefs.edit().putString("sub_en_file_name", enName).apply()
                if (faList.isNotEmpty()) {
                    val faName = "$safeTitle.translation.srt"
                    SubtitleParser.writeSrtFile(faList, File(context.filesDir, "saved_sub_fa.srt"))
                    _subFaOffset.value = 0.0
                    _subFaList.value = faList
                    _subFaFileName.value = faName
                    sharedPrefs.edit().putString("sub_fa_file_name", faName).apply()
                }
                _saveMessage.value = s.subtitleLoadedFromClipboard(enName)
            } catch (e: Exception) {
                e.printStackTrace()
                _saveMessage.value = s.errorWithMessage(e.message)
            }
        }
    }

    /**
     * Loads a learning JSON straight from the clipboard — a copied file (the
     * clipboard carries its content URI) or copied JSON text. The JSON attach
     * chooser's paste option, mirroring [loadSubEnFromClipboard].
     */
    fun loadJsonFromClipboard() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val s = strings()
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = clipboard.primaryClip
                if (clip == null || clip.itemCount == 0) { _saveMessage.value = s.clipboardEmptyError; return@launch }
                val item = clip.getItemAt(0)

                var content: String? = null
                var displayName: String? = null

                // A file copied in a file manager arrives as a content URI.
                val uri = item.uri
                if (uri != null) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            content = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        }
                        displayName = getUriDisplayName(context, uri)
                    } catch (e: Exception) {
                        content = null
                    }
                }
                if (content.isNullOrBlank()) {
                    val text = try { item.coerceToText(context)?.toString() } catch (e: Exception) { null }
                    if (!text.isNullOrBlank()) content = text
                }

                val raw = content
                if (raw.isNullOrBlank()) { _saveMessage.value = s.clipboardEmptyError; return@launch }
                importJsonSubtitleText(
                    raw,
                    displayName?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _saveMessage.value = s.errorWithMessage(e.message)
            }
        }
    }

    private fun loadSubtitleFromClipboard(isEnglish: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val s = strings()
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = clipboard.primaryClip
                if (clip == null || clip.itemCount == 0) { _saveMessage.value = s.clipboardEmptyError; return@launch }
                val item = clip.getItemAt(0)

                var content: String? = null
                var displayName: String? = null

                // Case 1: a file (e.g. a .srt copied from a file manager) is on
                // the clipboard as a content URI — read it like a picked file.
                val uri = item.uri
                if (uri != null) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            content = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        }
                        displayName = getUriDisplayName(context, uri)
                    } catch (e: Exception) {
                        content = null
                    }
                }

                // Case 2: the subtitle text itself was copied.
                if (content.isNullOrBlank()) {
                    val text = try { item.coerceToText(context)?.toString() } catch (e: Exception) { null }
                    if (!text.isNullOrBlank()) content = text
                }

                val raw = content
                if (raw.isNullOrBlank()) { _saveMessage.value = s.clipboardEmptyError; return@launch }

                val parsed = SubtitleParser.parseSubtitleContent(raw, if (isEnglish) "en" else "fa")
                if (parsed.isEmpty()) { _saveMessage.value = s.clipboardNoSubtitleError; return@launch }

                val fileName = displayName
                    ?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
                    ?: if (isEnglish) s.clipboardSubtitleDefaultNameEn else s.clipboardSubtitleDefaultNameFa
                val savedName = File(context.filesDir, if (isEnglish) "saved_sub_en.srt" else "saved_sub_fa.srt")
                savedName.writeText(raw)
                if (isEnglish) {
                    _subEnOffset.value = 0.0
                    _subEnList.value = parsed
                    _subEnFileName.value = fileName
                    sharedPrefs.edit().putString("sub_en_file_name", fileName).apply()
                } else {
                    _subFaOffset.value = 0.0
                    _subFaList.value = parsed
                    _subFaFileName.value = fileName
                    sharedPrefs.edit().putString("sub_fa_file_name", fileName).apply()
                }
                _saveMessage.value = s.subtitleLoadedFromClipboard(fileName)
            } catch (e: Exception) {
                e.printStackTrace()
                _saveMessage.value = s.errorWithMessage(e.message)
            }
        }
    }

    // ── Learning settings ──

    /** Toggles "Use Dictionary When JSON Learning Data Exists" and persists the choice. */
    fun setUseDictionaryWithJson(enabled: Boolean) {
        _useDictionaryWithJson.value = enabled
        sharedPrefs.edit().putBoolean("use_dictionary_with_json", enabled).apply()
    }

    /** Sets the user's CEFR learning level (A1..C2) and persists it. */
    fun setLearningLevel(level: String) {
        val normalized = level.trim().uppercase()
        if (normalized.isEmpty()) return
        _learningLevel.value = normalized
        sharedPrefs.edit().putString("learning_level", normalized).apply()
    }

    // ── JSON subtitle import ──

    /**
     * Imports a JSON subtitle-learning file picked from storage. The content
     * is auto-detected and validated by [SubtitleJsonParser]; invalid input
     * produces a user-friendly toast message via [saveMessage].
     */
    fun loadJsonSubtitleFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw Exception(strings().jsonEmptyFileError)
                importJsonSubtitleText(text, getUriDisplayName(context, uri))
            } catch (e: SubtitleJsonParser.SubtitleJsonParseException) {
                _saveMessage.value = strings().jsonParseError(e.message)
            } catch (e: Exception) {
                e.printStackTrace()
                _saveMessage.value = strings().errorWithMessage(e.message)
            }
        }
    }

    /**
     * Imports JSON subtitle-learning content from raw text — a picked file, a
     * pasted AI answer, or a whole chat reply.
     *
     * The text does not have to be pure JSON: the AI prompt templates ask the
     * model to start each answer with a `CHUNK 1-50` line, and models add
     * markdown fences or a sentence of commentary anyway. All of that is
     * detected and stripped by [SubtitleJsonParser], which also merges several
     * chunk documents found in one paste.
     *
     * Chunked imports are merged with the JSON that is already loaded, so
     * "CHUNK 1-50" followed by "CHUNK 51-100" builds the whole film; an import
     * without a chunk marker replaces the loaded file instead. Merged chunks
     * stay individually removable ([removeJsonChunk]) and exportable
     * ([exportJsonSubtitleToDownloads]). Safe to call from any thread.
     */
    fun importJsonSubtitleText(text: String, sourceName: String? = null) {
        val s = strings()
        if (text.trim().isEmpty()) { _saveMessage.value = s.jsonEmptyFileError; return }
        if (!SubtitleJsonParser.looksLikeSubtitleJson(text)) { _saveMessage.value = s.jsonNotSubtitleJson; return }
        try {
            val outcome = SubtitleJsonParser.parseWithReport(text)
            val incoming = outcome.pkg
            val existing = _jsonSubtitles.value
            val chunk = incoming.chunks.firstOrNull()
            val isChunked = incoming.chunks.isNotEmpty()
            val mergeWith = if (isChunked) existing else null
            val reimportedChunk = chunk != null &&
                mergeWith?.chunks?.any { it.label == chunk.label && it.start == chunk.start && it.end == chunk.end } == true

            // Remember where each new chunk came from, so the chunk list can
            // show the file / chat name next to its range.
            val stamped = if (incoming.chunks.isEmpty() || sourceName.isNullOrBlank()) {
                incoming
            } else {
                incoming.copy(
                    chunks = incoming.chunks.map {
                        if (it.sourceName.isBlank()) it.copy(sourceName = sourceName) else it
                    }
                )
            }
            val pkg = if (mergeWith == null) stamped
            else SubtitleJsonParser.mergePackages(mergeWith, stamped)

            val explicitName = sourceName?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
            val name = explicitName
                ?: _jsonSubFileName.value.takeIf { mergeWith != null && it.isNotBlank() }
                ?: s.jsonDefaultName

            // Always store the normalized package rather than the raw paste: it
            // is free of CHUNK lines / fences / commentary, it keeps the chunk
            // bookkeeping so merged chunks survive a restart, and it is exactly
            // what the time-shift code writes back anyway. The file is the one
            // of the current scope (local film or the open online clip).
            currentJsonFile().writeText(SubtitleJsonParser.serialize(pkg))

            _jsonSubtitles.value = pkg
            // A brand new file starts unsynchronised; merging another chunk of
            // the SAME film keeps the shift the user already dialled in.
            if (mergeWith == null) _jsonOffset.value = 0.0
            _jsonSubFileName.value = name
            sharedPrefs.edit().putString(currentJsonNameKey(), name).apply()

            val importedMessage = when {
                chunk != null && reimportedChunk ->
                    s.jsonChunkReimported(chunk.shortLabel, pkg.subtitles.size)
                chunk != null && mergeWith != null ->
                    s.jsonChunkMerged(chunk.shortLabel, pkg.chunks.size, pkg.subtitles.size)
                chunk != null ->
                    s.jsonChunkImported(chunk.shortLabel, name, pkg.subtitles.size)
                else -> s.jsonImportedSuccess(name, pkg.subtitles.size)
            }
            // An import into an online clip feeds the quiz's online source.
            if (_jsonScope.value != null) refreshOnlineQuizJson()

            // A paste can hold one complete chunk plus one truncated chunk.
            // The complete half imports; the broken half must not vanish
            // silently, so the success toast carries a warning line.
            _saveMessage.value =
                if (outcome.skippedDocuments > 0) {
                    importedMessage + "\n" + s.jsonPartialImportWarning(outcome.skippedDocuments)
                } else {
                    importedMessage
                }
        } catch (e: SubtitleJsonParser.SubtitleJsonParseException) {
            _saveMessage.value = s.jsonParseError(e.message)
        } catch (e: Exception) {
            e.printStackTrace()
            _saveMessage.value = s.errorWithMessage(e.message)
        }
    }

    /**
     * Removes one merged AI chunk again ("that answer was wrong") and keeps the
     * rest of the imported JSON. Clears the JSON slot when nothing is left.
     */
    fun removeJsonChunk(chunk: JsonChunkInfo) {
        val s = strings()
        val current = _jsonSubtitles.value
        if (current == null) return
        val remaining = SubtitleJsonParser.removeChunk(current, chunk)
        if (remaining == null) {
            removeJsonSubtitle(silent = true)
            _saveMessage.value = s.jsonChunkRemovedLast(chunk.shortLabel)
            return
        }
        val targetFile = currentJsonFile()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                targetFile.writeText(SubtitleJsonParser.serialize(remaining))
                _jsonSubtitles.value = remaining
                if (_jsonScope.value != null) refreshOnlineQuizJson()
                _saveMessage.value = s.jsonChunkRemoved(chunk.shortLabel, remaining.subtitles.size)
            } catch (e: Exception) {
                e.printStackTrace()
                _saveMessage.value = s.errorWithMessage(e.message)
            }
        }
    }

    /**
     * Clears ONLY the imported JSON package (English/Persian subtitle files are
     * left alone) — the escape hatch after a wrong merge.
     */
    fun removeJsonSubtitle(silent: Boolean = false) {
        val targetFile = currentJsonFile()
        val nameKey = currentJsonNameKey()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                targetFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _jsonSubtitles.value = null
            _jsonOffset.value = 0.0
            _jsonSubFileName.value = ""
            if (_jsonScope.value != null) refreshOnlineQuizJson()
            sharedPrefs.edit().remove(nameKey).apply()
            if (!silent) _saveMessage.value = strings().jsonRemovedAll
        }
    }

    /**
     * Exports the loaded JSON package (including every merged chunk) to
     * Downloads as a standard Langosphere JSON file, so a film built chunk by
     * chunk can be kept, shared or re-imported later.
     */
    fun exportJsonSubtitleToDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = strings()
                val pkg = _jsonSubtitles.value
                if (pkg == null) { _saveMessage.value = s.noJsonToExport; return@launch }
                val context = getApplication<Application>()
                val temp = File(context.filesDir, "json_subtitle_export.json")
                temp.writeText(SubtitleJsonParser.serialize(pkg))
                val path = saveToDownloads(context, jsonExportFileName(pkg), temp)
                temp.delete()
                _saveMessage.value = s.jsonExportSaved(path, pkg.subtitles.size, pkg.chunks.size)
            } catch (e: Exception) {
                e.printStackTrace()
                _saveMessage.value = strings().errorWithMessage(e.message)
            }
        }
    }

    /** Builds a safe Downloads file name for the exported JSON package. */
    private fun jsonExportFileName(pkg: JsonSubtitlePackage): String {
        val base = _jsonSubFileName.value
            .substringBeforeLast('.')
            .trim()
            .replace(Regex("[^A-Za-z0-9._ \u0600-\u06FF-]"), "")
            .ifBlank { "langosphere_learning" }
        val suffix = if (pkg.chunks.size > 1) "_merged_${pkg.chunks.size}chunks" else ""
        return "$base$suffix.json"
    }

    /**
     * "Remove Imported Subtitles": clears ALL imported subtitle data —
     * English, Persian, and JSON — from memory, disk, and preferences.
     */
    fun removeAllSubtitles() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            File(context.filesDir, "saved_sub_en.srt").delete()
            File(context.filesDir, "saved_sub_fa.srt").delete()
            File(context.filesDir, JSON_SUBTITLE_FILE).delete()
            _subEnList.value = emptyList()
            _subFaList.value = emptyList()
            _subEnOffset.value = 0.0
            _subFaOffset.value = 0.0
            _subEnFileName.value = ""
            _subFaFileName.value = ""
            // The film's JSON lives in the slot only while no online clip owns it.
            if (_jsonScope.value == null) {
                _jsonSubtitles.value = null
                _jsonOffset.value = 0.0
                _jsonSubFileName.value = ""
            } else {
                parkedLocalJson.value = null
            }
            sharedPrefs.edit()
                .remove("sub_en_file_name")
                .remove("sub_fa_file_name")
                .remove("sub_json_file_name")
                .apply()
            _saveMessage.value = strings().subtitleRemovedAll
        }
    }

    // ── Subtitle learning interactions (sentence lesson / word analysis) ──

    /**
     * Opens the learning sheet for a clicked English sentence. JSON learning
     * data is used first (matched by sentence text); when the JSON package
     * has no entry, a fallback view is built from the aligned translation
     * plus per-word dictionary definitions.
     */
    fun openSentenceLesson(sentence: String, translation: String?) {
        val s = sentence.trim()
        if (s.isEmpty()) return
        val jsonSub = findJsonSubtitleForSentence(s)
        viewModelScope.launch(Dispatchers.IO) {
            val fallback = if (jsonSub == null) buildFallbackVocabulary(s) else emptyMap()
            // The JSON's declared languages steer the sheet's direction:
            // an RTL target language (Persian, Arabic, ...) renders every
            // translation right-to-left, an LTR one left-to-right — with no
            // declaration each text decides by its own script.
            val declared = declaredLanguageRtl(_jsonSubtitles.value?.metadata?.language) to
                declaredLanguageRtl(_jsonSubtitles.value?.metadata?.targetLanguage)
            _learningSheet.value = SubtitleLearningState(
                jsonSubtitle = jsonSub,
                sentenceEnglish = s,
                translation = jsonSub?.translation ?: translation,
                fallbackVocab = fallback,
                sourceLanguageRtl = declared.first,
                targetLanguageRtl = declared.second
            )
        }
    }

    /**
     * Opens the learning sheet for a clicked English word (word-analysis
     * mode). Called when the dictionary toggle is disabled and a JSON
     * package exists; [jsonWord] is null when the JSON has no entry for
     * the word and the sheet shows a friendly fallback instead.
     */
    fun openWordLesson(word: String, sentence: String, translation: String?) {
        val w = word.trim()
        if (w.isEmpty()) return
        val jsonSub = findJsonSubtitleForSentence(sentence.trim())
        val jsonWord = jsonSub?.words?.firstOrNull { it.word.equals(w, ignoreCase = true) }
        _learningSheet.value = SubtitleLearningState(
            jsonSubtitle = jsonSub,
            sentenceEnglish = sentence.trim(),
            translation = jsonSub?.translation ?: translation,
            targetWord = w,
            jsonWord = jsonWord,
            sourceLanguageRtl = declaredLanguageRtl(_jsonSubtitles.value?.metadata?.language),
            targetLanguageRtl = declaredLanguageRtl(_jsonSubtitles.value?.metadata?.targetLanguage)
        )
    }

    fun clearLearningSheet() { _learningSheet.value = null }

    /** Finds the JSON subtitle whose English text matches the given sentence (trimmed, case-insensitive). */
    private fun findJsonSubtitleForSentence(sentence: String): com.example.model.JsonSubtitle? {
        val pkg = _jsonSubtitles.value ?: return null
        val s = sentence.trim()
        if (s.isEmpty()) return null
        return pkg.subtitles.firstOrNull { it.english.trim().equals(s, ignoreCase = true) }
            ?: pkg.subtitles.firstOrNull { it.english.trim().contains(s, ignoreCase = true) }
    }

    /** Looks up the matching JSON word entry for a word, preferring the entry inside the given sentence's JSON subtitle. */
    private fun findJsonWord(word: String, sentence: String?): JsonWord? {
        val pkg = _jsonSubtitles.value ?: return null
        val w = word.trim().lowercase()
        if (w.isEmpty()) return null
        val sub = sentence?.let { findJsonSubtitleForSentence(it) }
        val candidates = if (sub != null) listOf(sub) else pkg.subtitles
        return candidates.asSequence()
            .flatMap { it.words.asSequence() }
            .firstOrNull { it.word.trim().lowercase() == w }
    }

    /** Builds a small word -> dictionary-definition map used as the fallback vocabulary section when no JSON lesson exists. */
    private fun buildFallbackVocabulary(sentence: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val wordRegex = Regex("\\b[a-zA-Z][a-zA-Z0-9'-]*\\b")
        for (word in wordRegex.findAll(sentence).map { it.value }.distinct().take(15)) {
            val def = dbHelper.getEntriesForWord(word.lowercase()).firstOrNull()?.def?.take(180)
            if (!def.isNullOrBlank()) result[word] = def
        }
        return result
    }

    fun lookupWord(word: String, englishContext: String? = null, persianContext: String? = null) {
        val cleanWord = word.trim().lowercase()
        _activeWord.value = cleanWord; _activeEnglishContext.value = englishContext; _activePersianContext.value = persianContext
        // JSON learning data follows the dictionary system: attach the
        // matching JSON word entry (if any) so the dictionary bottom sheet
        // can show it on top of the normal dictionary results.
        _activeJsonWord.value = findJsonWord(cleanWord, englishContext)
        viewModelScope.launch(Dispatchers.IO) {
            _isActiveWordInLeitner.value = leitnerHelper.containsWord(cleanWord)
            val reader = mdictReader
            if (reader != null) {
                try {
                    val infos = reader.locateAll(cleanWord)
                    if (infos.isNotEmpty()) {
                        val entries = mutableListOf<DictionaryEntry>()
                        for (info in infos) {
                            var html = reader.readOneMdx(info)
                            if (html.startsWith("@@@LINK=")) {
                                val target = html.removePrefix("@@@LINK=").replace(Regex("[\\n\\r\\x00]"), "").trim()
                                if (target.isNotEmpty()) {
                                    val li = reader.locateAll(target)
                                    for (l in li) {
                                        html = reader.readOneMdx(l)
                                        val (exOrig, exTrans) = HtmlTextUtils.extractHtmlExamples(html)
                                        entries.add(DictionaryEntry(originalWord = cleanWord, html = html, phonetic = "", pos = "", def = HtmlTextUtils.htmlToReadableText(html), source = mdxSourceName, exampleOriginal = exOrig, exampleTranslation = exTrans))
                                    }
                                }
                            } else {
                                val (exOrig, exTrans) = HtmlTextUtils.extractHtmlExamples(html)
                                entries.add(DictionaryEntry(originalWord = cleanWord, html = html, phonetic = "", pos = "", def = HtmlTextUtils.htmlToReadableText(html), source = mdxSourceName, exampleOriginal = exOrig, exampleTranslation = exTrans))
                            }
                        }
                        if (entries.isNotEmpty()) { _dictionaryResults.value = entries; return@launch }
                    }
                } catch (e: Exception) { android.util.Log.w("AppViewModel", "MdictReader lookup failed: ${e.message}") }
            }
            _dictionaryResults.value = dbHelper.getEntriesForWord(cleanWord)
        }
    }

    override fun onCleared() { super.onCleared(); mdictReader?.close(); mdictReader = null; translateJob?.cancel(); learnSubsJob?.cancel(); learnDictJob?.cancel() }
    fun clearActiveWord() { _activeWord.value = null; _dictionaryResults.value = null; _activeEnglishContext.value = null; _activePersianContext.value = null; _isActiveWordInLeitner.value = false; _activeJsonWord.value = null }

    fun refreshLeitnerCards() {
        viewModelScope.launch(Dispatchers.IO) {
            val cards = leitnerHelper.getAllCards()
            _leitnerCards.value = cards
            _leitnerDueCards.value = leitnerHelper.getDueCards()
            // The reader's "already saved" state, kept in step with the box.
            _savedSentenceTexts.value = cards
                .filter { it.isSentence }
                .mapTo(mutableSetOf()) { LeitnerCard.normalizeFront(it.word) }
        }
    }

    /**
     * Adds the currently open dictionary word to the Leitner box, using the
     * same definition text shown in the dictionary bottom sheet (the offline
     * dictionary's plain `def` field, or its HTML entry converted to readable
     * text via HtmlTextUtils). Safe to call again for an already-added word:
     * it just refreshes the stored definition without resetting review progress.
     */
    fun addActiveWordToLeitner() {
        val word = _activeWord.value ?: return
        val entries = _dictionaryResults.value
        if (entries.isNullOrEmpty()) { _leitnerMessage.value = strings().noWordMeaningFound; return }
        val definition = entries.joinToString("\n\n") { entry ->
            entry.def.ifBlank { HtmlTextUtils.htmlToReadableText(entry.html) }
        }.trim()
        if (definition.isBlank()) { _leitnerMessage.value = strings().noMeaningToSave; return }
        viewModelScope.launch(Dispatchers.IO) {
            val isNew = leitnerHelper.addCard(word, definition)
            _isActiveWordInLeitner.value = true
            refreshLeitnerCards()
            _leitnerMessage.value = if (isNew) strings().wordAddedToLeitner(word) else strings().wordUpdatedInLeitner(word)
        }
    }

    /**
     * Adds any word/definition pair straight to the Leitner box. Used by the
     * beta "quiz from JSON" section, where the meaning comes from the imported
     * AI learning package instead of the offline dictionary.
     */
    fun addWordToLeitner(word: String, definition: String) {
        val cleanWord = word.trim()
        val cleanDefinition = definition.trim()
        if (cleanWord.isEmpty() || cleanDefinition.isEmpty()) {
            _leitnerMessage.value = strings().noMeaningToSave
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isNew = leitnerHelper.addCard(cleanWord, cleanDefinition)
                refreshLeitnerCards()
                _leitnerMessage.value = if (isNew) {
                    strings().wordAddedToLeitner(cleanWord)
                } else {
                    strings().wordUpdatedInLeitner(cleanWord)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _leitnerMessage.value = strings().errorWithMessage(e.message)
            }
        }
    }

    /**
     * Adds a whole sentence to the Leitner box - the shared action behind
     * "[+ لایتنر جمله]" in the book reader and in the film subtitle list.
     *
     * The draft is built by [com.example.logic.SentenceCardFactory], so a
     * sentence card carries the same teaching payload whichever surface it
     * came from: front is the original line, back is the translation, the
     * grammar lesson, the sentence structure, IPA and the key words.
     */
    fun addSentenceToLeitner(draft: SentenceCardDraft) {
        if (!draft.isValid) {
            _leitnerMessage.value = strings().noMeaningToSave
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val added = leitnerHelper.addSentenceCard(draft)
                refreshLeitnerCards()
                _leitnerMessage.value = if (added) {
                    strings().sentenceAddedToLeitner
                } else {
                    strings().sentenceUpdatedInLeitner
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _leitnerMessage.value = strings().errorWithMessage(e.message)
            }
        }
    }

    /**
     * Re-reads every stored book's sentences and rebuilds the quiz material.
     * Called when the quiz setup screen opens, so a freshly imported chapter is
     * quizzed without restarting the app.
     */
    fun refreshBookQuizMaterial() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val store = BookSessionStore(context)
                val documents = store.documentKeys().mapNotNull { key ->
                    val sentences: List<BookSentence> = store.loadSentences(key)
                    if (sentences.isEmpty()) null else BookQuizMaterial.Document(key, sentences)
                }
                _bookQuizMaterial.value = BookQuizMaterial.build(documents)
            } catch (e: Exception) {
                e.printStackTrace()
                _bookQuizMaterial.value = null
            }
        }
    }

    /**
     * Rebuilds the quiz's "online subtitles" material: every per-clip
     * learning JSON saved under online_json/ is parsed and merged into one
     * package, so the quiz can draw from all of the Online tab's clips —
     * whichever one is (or is not) currently open.
     */
    fun refreshOnlineQuizJson() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val dir = File(context.filesDir, ONLINE_JSON_DIR)
                val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: emptyArray()
                var merged: JsonSubtitlePackage? = null
                for (file in files) {
                    val pkg = try {
                        file.readText()
                            .takeIf { SubtitleJsonParser.looksLikeSubtitleJson(it) }
                            ?.let { SubtitleJsonParser.parse(it) }
                    } catch (e: Exception) {
                        null
                    }
                    if (pkg != null) {
                        merged = merged?.let { SubtitleJsonParser.mergePackages(it, pkg) } ?: pkg
                    }
                }
                _onlineQuizJson.value = merged
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun markLeitnerKnown(id: Long) { viewModelScope.launch(Dispatchers.IO) { leitnerHelper.markKnown(id); refreshLeitnerCards() } }
    fun markLeitnerUnknown(id: Long) { viewModelScope.launch(Dispatchers.IO) { leitnerHelper.markUnknown(id); refreshLeitnerCards() } }
    fun deleteLeitnerCard(id: Long) { viewModelScope.launch(Dispatchers.IO) { leitnerHelper.deleteCard(id); refreshLeitnerCards() } }
    fun clearLeitnerMessage() { _leitnerMessage.value = null }

    /**
     * Exports every Leitner card as an Anki-compatible plain text file
     * (see logic/AnkiExporter.kt for the exact format) and saves it to
     * Downloads, reusing the same saveToDownloads() helper as other exports.
     */
    fun exportLeitnerToAnki() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cards = leitnerHelper.getAllCards()
                if (cards.isEmpty()) { _leitnerMessage.value = strings().leitnerBoxEmpty; return@launch }
                val text = AnkiExporter.buildExportText(cards)
                val (wordCount, sentenceCount) = AnkiExporter.countByKind(cards)
                val context = getApplication<Application>()
                val file = File(context.filesDir, "leitner_anki_export.txt")
                file.writeText(text)
                val path = saveToDownloads(context, "leitner_anki_export.txt", file)
                _leitnerMessage.value = strings().ankiExportSaved(path, wordCount, sentenceCount)
            } catch (e: Exception) {
                _leitnerMessage.value = strings().errorWithMessage(e.message)
            }
        }
    }

    private fun getUriDisplayName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") { val cursor = context.contentResolver.query(uri, null, null, null, null); try { if (cursor != null && cursor.moveToFirst()) { val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (idx != -1) result = cursor.getString(idx) } } catch (e: Exception) { e.printStackTrace() } finally { cursor?.close() } }
        if (result == null) { result = uri.path; val cut = result?.lastIndexOf('/') ?: -1; if (cut != -1) result = result?.substring(cut + 1) }
        return result ?: "Unknown"
    }
}
