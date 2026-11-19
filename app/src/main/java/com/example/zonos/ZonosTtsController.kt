package com.example.zonos

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * App-level owner of the on-device TTS (Zonos3-Mini web engine):
 *
 *  - the hidden engine WebView ([engine]) that speaks ONE sentence at a
 *    time for the subtitle cards and the book reader;
 *  - the full Voice Studio page ([studio]) shown in Learning tools ▸ TTS,
 *    whose voice designs are read back before every generate;
 *  - the CORE model pack in Download/Langosphere/models/ ([packs]):
 *    download / complete / verify / delete / import / locate.
 *
 * No Compose here: the UI observes the flows and maps [TtsNotice] kinds to
 * localized strings. Lives in the AppViewModel (application context); the
 * WebViews themselves are built lazily with an Activity context when the
 * Compose layer attaches them.
 */
class ZonosTtsController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val KEY_VOICE = "tts_voice"
        private const val KEY_SPEED = "tts_speed"
        private const val KEY_LANGUAGE = "tts_language"
        private const val KEY_LAST_LOAD_MS = "tts_last_load_ms"
        private const val KEY_PLAYBACK_RATE = "tts_playback_rate"

        const val RATE_MIN = 0.5f
        const val RATE_MAX = 2.0f
        /** Step of the player's skip buttons. */
        const val SKIP_MS = 5_000L

        /** How long [speak] waits for the engine page's functions after a (re)load. */
        private const val PAGE_READY_TIMEOUT_MS = 20_000L

        /** [language] value meaning "follow the learning pair's source language". */
        const val LANGUAGE_AUTO = "auto"
        const val SPEED_MIN = 0.5f
        const val SPEED_MAX = 2.0f
    }

    val engine = ZonoEngine(context)
    val studio = StudioEngine(context)
    val packs = ModelPackManager(context)

    private val prefs get() = context.getSharedPreferences(ModelsLocation.PREFS_NAME, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- state

    data class DownloadState(
        val file: String,
        val done: Long,
        val total: Long,
        val bytesPerSec: Long = 0
    )

    /** What the UI should tell the learner; mapped to localized text by the UI. */
    sealed class TtsNotice {
        object CoreMissing : TtsNotice()
        data class CoreIncomplete(val missingFiles: Int, val missingBytes: Long, val folders: List<FolderStatus>) : TtsNotice()
        object EngineNotReady : TtsNotice()
        data class GenerateFailed(val detail: String) : TtsNotice()
        object DownloadBusy : TtsNotice()
        data class DownloadDone(val completedOnly: Boolean, val cacheCleared: Int) : TtsNotice()
        object DownloadStopped : TtsNotice()
        data class DownloadFailed(val detail: String) : TtsNotice()
        object NameClash : TtsNotice()
        object NeedsFolderPick : TtsNotice()
        object VerifyOk : TtsNotice()
        data class VerifyBad(val detail: String) : TtsNotice()
        data class Deleted(val freedBytes: Long) : TtsNotice()
        data class Imported(val files: Int, val skipped: Int, val stillIncomplete: Boolean) : TtsNotice()
        object ImportNothing : TtsNotice()
        data class ImportFailed(val detail: String) : TtsNotice()
        data class Located(val files: Int) : TtsNotice()
        object LocateNothing : TtsNotice()
        data class LocateFailed(val detail: String) : TtsNotice()
        data class ExportSaved(val destination: String) : TtsNotice()
        data class ExportFailed(val detail: String) : TtsNotice()
    }

    private val _notice = MutableStateFlow<TtsNotice?>(null)
    /** One-shot notice; the UI shows it and calls [dismissNotice]. */
    val notice: StateFlow<TtsNotice?> = _notice.asStateFlow()
    fun dismissNotice() { _notice.value = null }

    /** Starts as "nothing installed" and is refreshed on IO right away (the first MediaStore scan must not run on the main thread). */
    private val _coreStatus = MutableStateFlow(
        ModelPackManager.PackStatus(CORE_PACK, installed = false, installedBytes = 0L, presentFiles = 0, missing = CORE_PACK.files)
    )
    val coreStatus: StateFlow<ModelPackManager.PackStatus> = _coreStatus.asStateFlow()

    private val _folderStatuses = MutableStateFlow(folderStatuses(CORE_PACK, emptyMap()))
    val folderStatuses: StateFlow<List<FolderStatus>> = _folderStatuses.asStateFlow()

    val coreInstalled: Boolean get() = _coreStatus.value.installed

    private val _download = MutableStateFlow<DownloadState?>(null)
    val download: StateFlow<DownloadState?> = _download.asStateFlow()

    private val _busy = MutableStateFlow(false)
    /** An import / verify / delete is running. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _voices = MutableStateFlow<List<ZonoVoice>>(emptyList())
    val voices: StateFlow<List<ZonoVoice>> = _voices.asStateFlow()

    private val _selectedVoice = MutableStateFlow(prefs.getString(KEY_VOICE, DEFAULT_VOICE_ID) ?: DEFAULT_VOICE_ID)
    val selectedVoice: StateFlow<String> = _selectedVoice.asStateFlow()

    private val _speed = MutableStateFlow(prefs.getFloat(KEY_SPEED, 1.0f).coerceIn(SPEED_MIN, SPEED_MAX))
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _language = MutableStateFlow(prefs.getString(KEY_LANGUAGE, LANGUAGE_AUTO) ?: LANGUAGE_AUTO)
    /** Engine language id, or [LANGUAGE_AUTO]. */
    val language: StateFlow<String> = _language.asStateFlow()

    /** Key of the sentence currently being generated/played (null = idle). */
    private val _playingKey = MutableStateFlow<String?>(null)
    val playingKey: StateFlow<String?> = _playingKey.asStateFlow()

    /** What the current [playingKey] is going through; drives the loading banner. */
    enum class SpeechPhase { IDLE, LOADING_MODEL, GENERATING, PLAYING }

    private val _speechPhase = MutableStateFlow(SpeechPhase.IDLE)
    val speechPhase: StateFlow<SpeechPhase> = _speechPhase.asStateFlow()

    /** True while the current [playingKey] is a cached take being played again (see [replay]). */
    private val _replaying = MutableStateFlow(false)
    val replaying: StateFlow<Boolean> = _replaying.asStateFlow()

    /** Model-load progress (percent / ETA) while the engine loads the model; null otherwise. */
    private val _loadProgress = MutableStateFlow<LoadProgress?>(null)
    val loadProgress: StateFlow<LoadProgress?> = _loadProgress.asStateFlow()

    /**
     * Keys whose generated take is still cached in the engine page, i.e.
     * [replay] can play them again instantly. Empty after every page reload.
     */
    val takes: StateFlow<Set<String>> get() = engine.takes
    fun hasTake(key: String): Boolean = key in engine.takes.value

    private var loadEstimator = LoadEstimator(lastLoadMs())
    private var loadTicker: Job? = null

    // ------------------------------------------------------------- player

    /** What the player popup shows: the sentence (and its translation, if any) behind [key]. */
    data class PlayerRequest(val key: String, val text: String, val translation: String?)

    private val _player = MutableStateFlow<PlayerRequest?>(null)
    /** Non-null while the player popup is open. */
    val player: StateFlow<PlayerRequest?> = _player.asStateFlow()

    /** Transport state of the playing take (position / duration / paused). */
    val playback: StateFlow<Playback?> get() = engine.playback

    private val _playbackRate = MutableStateFlow(prefs.getFloat(KEY_PLAYBACK_RATE, 1.0f).coerceIn(RATE_MIN, RATE_MAX))
    /** Playback speed of the takes (0.5–2.0, pitch preserved); persisted. */
    val playbackRate: StateFlow<Float> = _playbackRate.asStateFlow()

    /**
     * Open the player popup for [key] and start it: the cached take when
     * there is one, otherwise a fresh generation (which loads the model
     * first on the first playback of a session). A tap on the sentence
     * that is already playing just brings the popup back.
     */
    fun openPlayer(key: String, text: String, translation: String?) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        _player.value = PlayerRequest(key, clean, translation?.trim()?.takeIf { it.isNotEmpty() })
        if (_playingKey.value != key) {
            if (hasTake(key)) replay(key, clean) else speak(key, clean)
        }
    }

    /**
     * Hide the popup. A take that is playing finishes on its own and a
     * loading model keeps loading (the banner shows its progress); only a
     * paused take is stopped, so the sentence's button drops its spinner.
     */
    fun closePlayer() {
        _player.value = null
        if (engine.playback.value?.paused == true) stop()
    }

    /** A new take for the popup's sentence (a different reading of the same text). */
    fun regenerate() {
        val req = _player.value ?: return
        speak(req.key, req.text)
    }

    /**
     * Play the popup's sentence from the start — the centre play button once
     * a take exists (a finished take replays, a playing one rewinds). The
     * transport row has no separate button for this any more.
     */
    fun restart() {
        val req = _player.value ?: return
        val pb = engine.playback.value
        if (_playingKey.value == req.key && pb != null) {
            engine.seekTo(0L)
            engine.resume()
        } else {
            replay(req.key, req.text)
        }
    }

    fun pause() = engine.pause()
    fun resume() = engine.resume()
    fun seekTo(positionMs: Long) = engine.seekTo(positionMs)

    fun seekBy(deltaMs: Long) {
        val pb = engine.playback.value ?: return
        val target = (pb.positionMs + deltaMs).coerceIn(0L, if (pb.durationMs > 0) pb.durationMs else Long.MAX_VALUE)
        engine.seekTo(target)
    }

    fun setPlaybackRate(rate: Float) {
        val r = rate.coerceIn(RATE_MIN, RATE_MAX)
        _playbackRate.value = r
        prefs.edit().putFloat(KEY_PLAYBACK_RATE, r).apply()
        engine.setRate(r)
    }

    /** Which design the last generate used (for the TTS section's status line). */
    private val _designSource = MutableStateFlow<DesignSource>(DesignSource.Default)
    val designSource: StateFlow<DesignSource> = _designSource.asStateFlow()

    /** Resolves the free-text source language of the learning pair; set by the UI layer. */
    @Volatile
    var pairSourceLanguage: () -> String? = { null }

    private var playJob: Job? = null
    private var downloadJob: Job? = null

    // ---- engine calibration, read at runtime once READY ----
    private var paceSafeMin = -2f
    private var paceSafeMax = 2f
    private var paceTimingTable: List<Pair<Float, Float>> = listOf(
        -4f to 2.881f, -2f to 2.212f, -1f to 1.451f, 0f to 1.0f,
        1f to 0.779f, 2f to 0.63f, 4f to 0.549f
    )
    private var controlsFetched = false
    private var capabilitiesFetched = false
    private var laciLanguages: List<String> = emptyList()
    private var studioDoc: StudioDoc? = null
    private val studioAtlas: StudioAtlas? by lazy {
        try {
            parseStudioAtlas(context.assets.open("studio-atlas.json").bufferedReader().readText())
        } catch (_: Exception) {
            null
        }
    }

    init {
        loadVoices()
        scope.launch {
            withContext(Dispatchers.IO) { refreshPacks() }
            engine.load()
        }
        scope.launch {
            engine.state.collect { st -> trackLoad(st) }
        }
        scope.launch {
            engine.state.collect { st ->
                if (st.status == EngineStatus.READY && !controlsFetched) {
                    controlsFetched = true
                    fetchPaceControl()
                }
                if (st.status == EngineStatus.READY && !capabilitiesFetched) {
                    capabilitiesFetched = true
                    fetchCapabilities()
                }
                if (st.status != EngineStatus.READY) {
                    controlsFetched = false
                    capabilitiesFetched = false
                }
            }
        }
        scope.launch {
            var seen: ExportOutcome? = null
            studio.lastExport.collect { exp ->
                if (exp != null && exp != seen) {
                    seen = exp
                    _notice.value = if (exp.error != null) TtsNotice.ExportFailed(exp.error)
                    else TtsNotice.ExportSaved(exp.destination)
                }
            }
        }
    }

    // ------------------------------------------------------------ settings

    fun selectVoice(id: String) {
        _selectedVoice.value = id
        prefs.edit().putString(KEY_VOICE, id).apply()
    }

    fun setSpeed(value: Float) {
        val v = value.coerceIn(SPEED_MIN, SPEED_MAX)
        _speed.value = v
        prefs.edit().putFloat(KEY_SPEED, v).apply()
    }

    /** [id] = an engine language id or [LANGUAGE_AUTO]. */
    fun setLanguage(id: String) {
        val v = if (id == LANGUAGE_AUTO || ZonoLanguages.byId(id) != null) id else LANGUAGE_AUTO
        _language.value = v
        prefs.edit().putString(KEY_LANGUAGE, v).apply()
    }

    /** The engine language the next generate will use. */
    fun effectiveLanguage(): String {
        val chosen = _language.value
        if (chosen != LANGUAGE_AUTO) return chosen
        return ZonoLanguages.resolve(pairSourceLanguage())
    }

    /** Studio UI language default follows the app language ("fa" / "en"). */
    fun setStudioDefaultLang(lang: String) {
        studio.defaultLang = normalizeStudioLang(lang)
    }

    private fun loadVoices() {
        scope.launch(Dispatchers.IO) {
            try {
                val json = context.assets.open("zonos/voices.json").bufferedReader().use { it.readText() }
                val arr = JSONArray(json)
                val list = (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    ZonoVoice(
                        id = o.optString("id"),
                        name = o.optString("name", o.optString("id")),
                        language = o.optString("language", "English"),
                        tag = o.optString("tag", ""),
                        gender = o.optString("gender", ""),
                    )
                }.filter { it.id.isNotEmpty() }
                if (list.isNotEmpty()) {
                    _voices.value = list
                    if (list.none { it.id == _selectedVoice.value }) {
                        selectVoice(list.firstOrNull { it.id == DEFAULT_VOICE_ID }?.id ?: list.first().id)
                    }
                }
            } catch (_: Exception) {
                // Keep the empty list; the engine's default voice still works.
            }
        }
    }

    private fun fetchPaceControl() {
        engine.fetchControls { json ->
            if (json == null) return@fetchControls
            try {
                val doc = JSONObject(json)
                val controls = doc.optJSONObject("ui")?.optJSONArray("controls")
                if (controls != null) {
                    for (i in 0 until controls.length()) {
                        val c = controls.optJSONObject(i) ?: continue
                        if (c.optString("id") == "pace") {
                            val safe = c.optJSONArray("safe") ?: c.optJSONArray("range")
                            if (safe != null && safe.length() >= 2) {
                                paceSafeMin = safe.optDouble(0, -2.0).toFloat()
                                paceSafeMax = safe.optDouble(1, 2.0).toFloat()
                            }
                            break
                        }
                    }
                }
                val tempo = doc.optJSONObject("timing")?.optJSONArray("delivery.tempo")
                if (tempo != null && tempo.length() >= 2) {
                    val points = ArrayList<Pair<Float, Float>>()
                    for (i in 0 until tempo.length()) {
                        val pair = tempo.optJSONArray(i) ?: continue
                        if (pair.length() >= 2) {
                            points.add(pair.optDouble(0).toFloat() to pair.optDouble(1).toFloat())
                        }
                    }
                    if (points.size >= 2) paceTimingTable = points.sortedBy { it.first }
                }
            } catch (_: Exception) {
                // Controls doc unparseable: the speed slider stays on the fallback table.
            }
        }
    }

    private fun fetchCapabilities() {
        engine.fetchCapabilities { json ->
            try {
                if (json != null) {
                    val langs = JSONObject(json).optJSONArray("laci_languages")
                    val list = ArrayList<String>()
                    if (langs != null) for (i in 0 until langs.length()) list.add(langs.optString(i))
                    laciLanguages = list
                }
            } catch (_: Exception) {
                // LACI stays off.
            }
        }
    }

    /**
     * Re-read the open Studio project document (bench + personas +
     * settings) from the engine's shared storage. 5 s timeout so a hung
     * WebView never blocks playback: on timeout the previous value is
     * kept; on a clean "no doc" answer it is cleared.
     */
    private suspend fun refreshStudioDesigns() {
        var answered = false
        val json: String? = withTimeoutOrNull(5000) {
            suspendCancellableCoroutine { cont ->
                engine.fetchStudioDoc { result ->
                    answered = true
                    if (cont.isActive) cont.resumeWith(Result.success(result))
                }
            }
        }
        if (!answered) return
        studioDoc = json?.let { parseStudioDoc(it) }
    }

    private fun optsJson(voiceId: String, language: String): String {
        val (design, source) = resolveReaderDesign(studioDoc, voiceId, studioAtlas)
        _designSource.value = source
        return readerOptsJsonWithDesign(
            _speed.value, paceTimingTable, paceSafeMin, paceSafeMax,
            design, laciLanguages, packs.fullMrAvailable(), language
        )
    }

    // ------------------------------------------------------------- speech

    val engineReady: Boolean get() = engine.state.value.status == EngineStatus.READY

    /**
     * Speak one sentence. Only ONE sentence is ever generated at a time:
     * a new call stops the previous one. [key] identifies the sentence
     * for [playingKey] (e.g. "cue-12" or "sentence-7"). Returns false
     * (with a notice) when the model pack or the engine is not ready.
     * The generated take is cached under [key] for [replay].
     */
    fun speak(key: String, text: String): Boolean = startPlayback(key, text, replayFirst = false)

    /**
     * Play the take generated earlier for [key] again, without generating
     * (same voice, same reading). Falls back to a fresh generation when
     * the engine page no longer has it. Returns false like [speak].
     */
    fun replay(key: String, text: String): Boolean = startPlayback(key, text, replayFirst = true)

    private fun startPlayback(key: String, text: String, replayFirst: Boolean): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        if (!coreInstalled) {
            refreshPacks()
            if (!coreInstalled) {
                _notice.value = coreIncompleteNotice() ?: TtsNotice.CoreMissing
                return false
            }
        }
        val st = engine.state.value.status
        if (st == EngineStatus.NO_MODEL || st == EngineStatus.NOT_LOADED) engine.load()
        if (!engine.isAttached || engine.state.value.status == EngineStatus.MISSING) {
            _notice.value = TtsNotice.EngineNotReady
            return false
        }
        stop()
        _playingKey.value = key
        _replaying.value = replayFirst && hasTake(key)
        if (engine.state.value.status == EngineStatus.READY) {
            _speechPhase.value = SpeechPhase.GENERATING
        } else {
            // The model loads on this first playback: count the wait from the
            // tap, so the banner's elapsed time / history ETA start now.
            _speechPhase.value = SpeechPhase.LOADING_MODEL
            if (!loadEstimator.running) {
                val now = System.currentTimeMillis()
                loadEstimator = LoadEstimator(lastLoadMs())
                loadEstimator.start(now)
                _loadProgress.value = loadEstimator.snapshot(now)
                startLoadTicker()
            }
        }
        val voice = _selectedVoice.value
        val language = effectiveLanguage()
        val index = key.hashCode() and 0x7fffffff
        playJob = scope.launch {
            val result: Result<String> = run {
                // Right after a page (re)load the module may still be evaluating:
                // wait for __zonoGenerate instead of failing the first tap.
                val pageReady = awaitPageFunctions()
                if (pageReady) engine.setRate(_playbackRate.value)
                if (!pageReady) {
                    val st = engine.state.value
                    val detail = st.error?.takeIf { st.status == EngineStatus.ERROR || st.status == EngineStatus.MISSING }
                    return@run Result.failure(IllegalStateException(detail ?: "engine page not ready"))
                }
                if (replayFirst && hasTake(key)) {
                    val r = engine.replay(index, key)
                    if (r.isSuccess && r.getOrNull() != "MISS") return@run r
                    if (r.isFailure && r.exceptionOrNull()?.message == "stopped") return@run r
                    // The page lost the take: generate it afresh.
                    _replaying.value = false
                    _speechPhase.value = if (engine.state.value.status == EngineStatus.READY) SpeechPhase.GENERATING else SpeechPhase.LOADING_MODEL
                }
                refreshStudioDesigns()
                val opts = optsJson(voice, language)
                engine.generate(index, voice, clean, opts, takeKey = key)
            }
            if (_playingKey.value == key) {
                _playingKey.value = null
                _speechPhase.value = SpeechPhase.IDLE
                _replaying.value = false
            }
            if (result.isFailure) {
                val msg = result.exceptionOrNull()?.message ?: "unknown error"
                if (msg != "stopped") {
                    _notice.value = coreIncompleteNotice()
                        ?: TtsNotice.GenerateFailed(msg)
                }
            }
        }
        return true
    }

    /** True when the engine page exposes its functions (or already runs the model). */
    private suspend fun awaitPageFunctions(): Boolean {
        val ready = withTimeoutOrNull(PAGE_READY_TIMEOUT_MS) {
            engine.state.first { st ->
                st.functionsReady || st.status == EngineStatus.READY || st.status == EngineStatus.IDLE ||
                    st.status == EngineStatus.ERROR || st.status == EngineStatus.MISSING || st.status == EngineStatus.NO_MODEL
            }
        } ?: return false
        return ready.status != EngineStatus.ERROR && ready.status != EngineStatus.MISSING && ready.status != EngineStatus.NO_MODEL
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        engine.stop()
        _playingKey.value = null
        _speechPhase.value = SpeechPhase.IDLE
        _replaying.value = false
    }

    fun isPlaying(key: String): Boolean = _playingKey.value == key

    // ------------------------------------------------------- load tracking

    private fun lastLoadMs(): Long? = prefs.getLong(KEY_LAST_LOAD_MS, 0L).takeIf { it > 0L }

    /**
     * Follows the engine through a model load: starts the estimator on the
     * first progress report (or when a playback is waiting on a LOADING
     * engine), ticks the ETA every second, remembers the total duration
     * once READY, and keeps [speechPhase] honest for the banner.
     */
    private fun trackLoad(st: EngineState) {
        val now = System.currentTimeMillis()
        val modelLoading = st.status == EngineStatus.LOADING && st.total > 0L
        when {
            modelLoading -> {
                if (!loadEstimator.running) loadEstimator = LoadEstimator(lastLoadMs())
                loadEstimator.sample(st.loaded, st.total, st.phase, now)
                _loadProgress.value = loadEstimator.snapshot(now)
                startLoadTicker()
            }
            st.status == EngineStatus.READY -> {
                if (loadEstimator.running) {
                    val took = loadEstimator.elapsed(now)
                    if (took > 1000L) prefs.edit().putLong(KEY_LAST_LOAD_MS, took).apply()
                }
                loadEstimator.reset()
                _loadProgress.value = null
                stopLoadTicker()
            }
            st.status == EngineStatus.ERROR || st.status == EngineStatus.MISSING || st.status == EngineStatus.NO_MODEL -> {
                loadEstimator.reset()
                _loadProgress.value = null
                stopLoadTicker()
            }
        }
        // Phase of the sentence in flight.
        if (_playingKey.value != null) {
            val index = _playingKey.value?.let { it.hashCode() and 0x7fffffff }
            _speechPhase.value = when {
                st.playingIndex != null && st.playingIndex == index -> SpeechPhase.PLAYING
                st.status == EngineStatus.READY -> SpeechPhase.GENERATING
                else -> SpeechPhase.LOADING_MODEL
            }
        }
    }

    private fun startLoadTicker() {
        if (loadTicker?.isActive == true) return
        loadTicker = scope.launch {
            while (loadEstimator.running) {
                delay(1000)
                if (loadEstimator.running) _loadProgress.value = loadEstimator.snapshot(System.currentTimeMillis())
            }
        }
    }

    private fun stopLoadTicker() {
        loadTicker?.cancel()
        loadTicker = null
    }

    // -------------------------------------------------------------- packs

    fun refreshPacks() {
        _coreStatus.value = packs.status(CORE_PACK)
        _folderStatuses.value = packs.folderStatuses(CORE_PACK)
    }

    private fun coreIncompleteNotice(): TtsNotice? {
        val core = _coreStatus.value
        if (core.installed || core.presentFiles == 0) return null
        return TtsNotice.CoreIncomplete(
            missingFiles = core.missing.size,
            missingBytes = core.missing.sumOf { it.size },
            folders = _folderStatuses.value.filter { !it.complete }
        )
    }

    /** Download the whole CORE pack, or only its missing files. */
    fun downloadCore(missingOnly: Boolean) {
        if (downloadJob?.isActive == true) {
            _notice.value = TtsNotice.DownloadBusy
            return
        }
        val def = CORE_PACK
        downloadJob = scope.launch(Dispatchers.IO) {
            val startMs = System.currentTimeMillis()
            try {
                val total = if (missingOnly) packs.missingFiles(def).sumOf { it.size } else def.totalBytes
                _download.value = DownloadState(def.files.firstOrNull()?.name ?: "", 0L, total)
                val onProgress: (Long, Long, String) -> Unit = { done, tot, file ->
                    val elapsedSec = (System.currentTimeMillis() - startMs).coerceAtLeast(1L) / 1000.0
                    _download.value = DownloadState(file, done, tot, (done / elapsedSec).toLong())
                }
                if (missingOnly) packs.downloadMissing(def, onProgress) else packs.downloadPack(def, onProgress)
                _download.value = null
                refreshPacks()
                val changed = packs.lastReplacedChangedCount
                if (changed > 0) {
                    engine.clearModelCache()
                    studio.clearModelCache()
                }
                _notice.value = TtsNotice.DownloadDone(missingOnly, changed)
                withContext(Dispatchers.Main) {
                    engine.load()
                    studio.refreshGate()
                }
            } catch (e: Exception) {
                _download.value = null
                refreshPacks()
                _notice.value = when (e) {
                    is CancellationException -> TtsNotice.DownloadStopped
                    is ModelsLocation.NameClashException -> TtsNotice.NameClash
                    is ModelPackManager.VerifyFailedException ->
                        TtsNotice.DownloadFailed(e.problems.firstOrNull() ?: "verification failed")
                    else -> {
                        val msg = e.message ?: e.javaClass.simpleName
                        if (!ModelsLocation.mediaStoreAvailable && e is java.io.IOException && msg.contains("pick the models folder")) {
                            TtsNotice.NeedsFolderPick
                        } else {
                            TtsNotice.DownloadFailed(msg)
                        }
                    }
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    fun deleteCore() {
        if (downloadJob?.isActive == true) {
            _notice.value = TtsNotice.DownloadBusy
            return
        }
        scope.launch(Dispatchers.IO) {
            _busy.value = true
            try {
                withContext(Dispatchers.Main) { stop() }
                val freed = packs.deletePack(CORE_PACK)
                engine.clearModelCache()
                studio.clearModelCache()
                refreshPacks()
                _notice.value = TtsNotice.Deleted(freed)
                withContext(Dispatchers.Main) {
                    engine.load()
                    studio.refreshGate()
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun verifyCore() {
        scope.launch(Dispatchers.IO) {
            _busy.value = true
            try {
                val result = packs.verifyPack(CORE_PACK)
                refreshPacks()
                _notice.value = when (result) {
                    is ModelPackManager.VerifyResult.Ok -> TtsNotice.VerifyOk
                    is ModelPackManager.VerifyResult.Bad ->
                        TtsNotice.VerifyBad(result.problems.firstOrNull() ?: "")
                }
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun afterImport() {
        refreshPacks()
        if (packs.coreInstalled()) {
            packs.recordInstalled(CORE_PACK)
            withContext(Dispatchers.Main) {
                engine.load()
                studio.refreshGate()
            }
        }
    }

    /** Import a zip of the pack (HF `web/` layout, one wrapper folder tolerated). */
    fun importZip(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            _busy.value = true
            try {
                val summary = packs.importZip(uri)
                afterImport()
                _notice.value = if (summary.imported > 0) {
                    TtsNotice.Imported(summary.imported, summary.skipped, !packs.coreInstalled())
                } else {
                    TtsNotice.ImportNothing
                }
            } catch (e: Exception) {
                _notice.value = if (e is ModelsLocation.NameClashException) TtsNotice.NameClash
                else TtsNotice.ImportFailed(e.message ?: e.javaClass.simpleName)
            } finally {
                _busy.value = false
            }
        }
    }

    /** Copy the known pack files out of a picked folder. */
    fun importTree(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            _busy.value = true
            try {
                val summary = packs.importTree(uri)
                afterImport()
                _notice.value = if (summary.imported > 0) {
                    TtsNotice.Imported(summary.imported, summary.skipped, !packs.coreInstalled())
                } else {
                    TtsNotice.ImportNothing
                }
            } catch (e: Exception) {
                _notice.value = if (e is ModelsLocation.NameClashException) TtsNotice.NameClash
                else TtsNotice.ImportFailed(e.message ?: e.javaClass.simpleName)
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * Remember a folder the learner picked — no copy, no download — so
     * files already in Download/Langosphere/models/ (browser download,
     * `hf download`, another device) are served as-is.
     */
    fun locateTree(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            _busy.value = true
            try {
                val found = packs.locateTree(uri)
                afterImport()
                _notice.value = if (found > 0) TtsNotice.Located(found) else TtsNotice.LocateNothing
            } catch (e: Exception) {
                _notice.value = TtsNotice.LocateFailed(e.message ?: e.javaClass.simpleName)
            } finally {
                _busy.value = false
            }
        }
    }

    /** Release both WebViews (process-level teardown). */
    fun release() {
        _player.value = null
        stop()
        cancelDownload()
        stopLoadTicker()
        engine.release()
        studio.release()
    }
}
