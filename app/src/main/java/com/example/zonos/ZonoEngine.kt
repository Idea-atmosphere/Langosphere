package com.example.zonos

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Looper
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Owns the hidden WebView that runs the Zonos3-Mini web engine
 * (assets/zonos/index.html) via WebViewAssetLoader on the https scheme —
 * file:// is not a secure context and navigator.gpu needs one.
 *
 * Page contract (assets/zonos/zono.js):
 *   window.__zonoGenerate(index, voiceId, text, optsJson) -> Promise "OK"
 *   window.__zonoStop()
 *   window.__zonoSetModelsBase(url) / window.__zonoCheckModels()
 *   window.__zonoControls() / window.__zonoCapabilities()
 *   JS -> Kotlin: window.ZonoNative.onEvent(jsonString)
 *     {"type":"ready"} | {"type":"progress","loaded","total","phase"}
 *     | {"type":"playing","index"} | {"type":"done","index"}
 *     | {"type":"error","message"} | {"type":"voices","count"}
 *     | {"type":"takes","keys":[...]} (cached generated takes, see [takes])
 *     | {"type":"time","index","position","duration","paused","rate"} (see [playback])
 *     | {"type":"no-model","missing":[...]} | {"type":"models-ok"}
 *
 * Ported from the ZonoLingua reader engine; the export and diagnostics
 * log features were left out (the Studio page offers its own export).
 */
class ZonoEngine(private val context: Context) {

    companion object {
        const val ORIGIN = "https://appassets.androidplatform.net"
        const val ENGINE_URL = "$ORIGIN/assets/zonos/index.html"
        const val MODELS_BASE = "$ORIGIN/models/"
        const val MODEL_CACHE_NAME = "zonos3-web-models-v6"

        /**
         * Read the open Studio project document from this WebView's
         * shared storage (same origin as the Studio page, so the same
         * IndexedDB/localStorage). The Studio keeps projects in IndexedDB
         * `zvs-projects`, store `docs` (key = project id, value = the
         * document JSON string), and the open project's id in
         * localStorage `zvs.project`. Returns only what the readers need
         * (settings, bench design, personas) as a compact JSON string
         * posted through the bridge as {type:'studioDoc', json}.
         */
        const val STUDIO_DOC_READ_JS = """
(function(){try{
var post=function(s){try{window.ZonoNative.onEvent(JSON.stringify({type:'studioDoc',json:s}))}catch(e){}};
var id=null;try{id=localStorage.getItem('zvs.project')}catch(e){id=null}
if(!id){post(null);return;}
var req=indexedDB.open('zvs-projects',1);
req.onerror=function(){post(null)};
req.onsuccess=function(){
var db=req.result;
var tx=db.transaction(['docs'],'readonly');
var get=tx.objectStore('docs').get(id);
get.onsuccess=function(){
var json=get.result;
db.close();
if(!json){post(null);return;}
try{
var doc=JSON.parse(json);
var cast=doc.cast||{};
var out={settings:doc.settings||null,bench:cast.design||null,personas:[]};
var ps=cast.personas||[];
for(var i=0;i<ps.length;i++){out.personas.push({name:ps[i].name||'',design:ps[i].design||null});}
post(JSON.stringify(out));
}catch(e){post(null);}
};
get.onerror=function(){db.close();post(null);};
};
}catch(e){try{window.ZonoNative.onEvent(JSON.stringify({type:'studioDoc',json:null}))}catch(_){}}})()
"""
    }

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state

    /**
     * Keys of the generated takes the page still holds (zono.js keeps the
     * last few WAVs so a sentence can be heard again without generating).
     * Empty after every page (re)load.
     */
    private val _takes = MutableStateFlow<Set<String>>(emptySet())
    val takes: StateFlow<Set<String>> = _takes

    /** Transport state of the playing take (position / duration / paused); null when idle. */
    private val _playback = MutableStateFlow<Playback?>(null)
    val playback: StateFlow<Playback?> = _playback

    /** index -> continuation waiting for that sentence's "done" event. */
    private val pending = ConcurrentHashMap<Int, (Result<String>) -> Unit>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * The live WebView, built ONLY with an Activity context: a WebView
     * built with the application context cannot open system popup
     * windows (Chromium's <select> needs an Activity window token). Null
     * until the Compose layer attaches it via [attachedWebView];
     * pre-attach calls no-op or record pending work (see [load]).
     */
    private var attachedWv: WebView? = null

    /** The Activity the live WebView was built with (null until attached). */
    private var attachedActivity: Activity? = null

    /** Last URL load() requested; replayed on attach. */
    private var lastUrl: String? = null

    val isAttached: Boolean get() = attachedWv != null

    /**
     * Attach (or re-attach) the WebView, built with the Activity's
     * context. Same Activity -> returns the existing instance; a
     * different Activity (or a first attach after a pre-attach load)
     * destroys the old WebView, builds a fresh one, and replays the
     * pending load.
     */
    fun attachedWebView(context: Context): WebView {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "attachedWebView must run on the main thread"
        }
        val activity = requireNotNull(context.findActivity()) {
            "ZonoEngine WebView needs an Activity context"
        }
        val existing = attachedWv
        if (existing != null && attachedActivity === activity) {
            // Re-attach to a new Compose host: the previous AndroidView
            // holder still owns the view until it is removed from it.
            try { (existing.parent as? ViewGroup)?.removeView(existing) } catch (_: Exception) { }
            return existing
        }
        if (existing != null) destroyAttached()
        val wv = buildWebView(activity)
        attachedWv = wv
        attachedActivity = activity
        if (lastUrl != null) load()
        return wv
    }

    /** Tear the WebView down (Activity finishing). A later attach rebuilds it. */
    fun release() {
        stop()
        destroyAttached()
        _state.value = EngineState()
        _takes.value = emptySet()
        _playback.value = null
    }

    private fun destroyAttached() {
        val old = attachedWv ?: return
        attachedWv = null
        attachedActivity = null
        try { old.stopLoading() } catch (_: Exception) { }
        try { (old.parent as? ViewGroup)?.removeView(old) } catch (_: Exception) { }
        try { old.destroy() } catch (_: Exception) { }
    }

    /** Load generation counter: the delayed probe only reports for the latest load. */
    @Volatile
    private var loadSeq = 0

    /**
     * Page/script error handlers injected after page load. window.onerror
     * catches runtime errors, the capture-phase 'error' listener catches
     * failed (module) script loads, 'unhandledrejection' rejected
     * promises. All forward through the bridge as type 'pageerror'.
     */
    private val errorHandlersJs = "(function(){" +
        "if(window.__zonoDiagInstalled)return;window.__zonoDiagInstalled=true;" +
        "function fwd(m,s,l){try{window.ZonoNative.onEvent(JSON.stringify({type:'pageerror',message:String(m),source:String(s||''),line:(l||0)}))}catch(_){}};" +
        "window.onerror=function(m,s,l,c,e){fwd(m,s,l);return false;};" +
        "window.addEventListener('unhandledrejection',function(e){var r=e.reason;fwd('unhandledrejection: '+(r&&r.message?r.message:String(r)),'',0);});" +
        "window.addEventListener('error',function(e){var t=e.target||{};var src=t.src||t.href||'';fwd(e.message||('load failed: '+src),e.filename||src,e.lineno||0);},true);" +
        "})()"

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(activityContext: Context): WebView {
        check(activityContext is Activity) { "WebView requires an Activity context" }
        val wv = WebView(activityContext)
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.cacheMode = WebSettings.LOAD_DEFAULT
        wv.settings.mediaPlaybackRequiresUserGesture = false
        wv.settings.allowFileAccess = false
        val webViewVersion = try {
            if (Build.VERSION.SDK_INT >= 26) WebView.getCurrentWebViewPackage()?.versionName else null
        } catch (e: Exception) {
            null
        }
        _state.value = _state.value.copy(webViewVersion = webViewVersion)
        wv.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR &&
                    !message.message().contains("favicon", ignoreCase = true)
                ) {
                    val detail = "${message.message()} @ ${message.sourceId()}:${message.lineNumber()}"
                    _state.value = _state.value.copy(lastConsoleError = detail)
                }
                return super.onConsoleMessage(message)
            }
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val res = ModelFileServer.intercept(context, request.url, request.method, request.requestHeaders)
                    ?: loader.shouldInterceptRequest(request.url)
                val err = ModelFileServer.lastError
                if (_state.value.lastInterceptorError != err) {
                    _state.value = _state.value.copy(lastInterceptorError = err)
                }
                return res
            }

            override fun onPageFinished(view: WebView, url: String) {
                _state.value = _state.value.copy(pageLoaded = true)
                view.evaluateJavascript(errorHandlersJs, null)
                // Point the wrapper at the LOCAL packs base (absolute URL:
                // engine.js resolves 'voices/' against it) and ask it to
                // check the packs — the no-model / models-ok events drive
                // the empty state. No auto-init happens on page load.
                view.evaluateJavascript(
                    "try{window.__zonoSetModelsBase('$MODELS_BASE');window.__zonoCheckModels();}catch(e){}",
                    null
                )
                probeEngineFunctions(delayed = false)
                val seq = loadSeq
                scope.launch {
                    delay(2000)
                    if (seq == loadSeq) probeEngineFunctions(delayed = true)
                }
            }
        }
        wv.addJavascriptInterface(JsBridge(), "ZonoNative")
        return wv
    }

    /**
     * Load the engine page — but ONLY when the CORE pack is installed (no
     * auto-download on first run). Without models the state is NO_MODEL.
     * The engine's model base is /models/ on this origin (see zono.js),
     * served from Download/Langosphere/models/ by ModelFileServer.
     */
    fun load() {
        if (!ModelPackManager(context).coreInstalled()) {
            _state.value = EngineState(status = EngineStatus.NO_MODEL, webViewVersion = _state.value.webViewVersion)
            lastUrl = null
            return
        }
        loadSeq++
        val prev = _state.value
        _state.value = EngineState(status = EngineStatus.LOADING, webViewVersion = prev.webViewVersion)
        _takes.value = emptySet()
        _playback.value = null
        lastUrl = ENGINE_URL
        attachedWv?.loadUrl(ENGINE_URL)
    }

    /**
     * Probe which engine functions exist in the page. The immediate probe
     * can run before the module finished evaluating, so only the delayed
     * probe (2 s after page finish) turns a still-missing __zonoGenerate
     * into an ERROR instead of leaving the engine stuck on LOADING. Never
     * downgrades READY.
     */
    private fun probeEngineFunctions(delayed: Boolean) {
        val js = "(function(){try{return JSON.stringify({" +
            "generate:typeof window.__zonoGenerate," +
            "setBase:typeof window.__zonoSetModelsBase," +
            "check:typeof window.__zonoCheckModels," +
            "worker:typeof Worker," +
            "gpu:!!navigator.gpu})}catch(e){return JSON.stringify({probeError:String(e)})}})()"
        val view = attachedWv ?: return
        view.post {
            view.evaluateJavascript(js) { json ->
                try {
                    val inner = unwrapJsStringResult(json) ?: return@evaluateJavascript
                    if (inner == "null") return@evaluateJavascript
                    val o = JSONObject(inner)
                    if (o.has("probeError")) return@evaluateJavascript
                    val gen = o.optString("generate", "undefined")
                    val worker = o.optString("worker", "undefined")
                    val gpu = o.optBoolean("gpu", false)
                    val cur = _state.value
                    var next = cur.copy(gpuSupported = gpu, functionsReady = cur.functionsReady || gen == "function")
                    if (!gpu && cur.status != EngineStatus.READY) {
                        next = next.copy(status = EngineStatus.ERROR, error = "no_webgpu")
                    } else if (gen == "function" && cur.status == EngineStatus.LOADING && cur.total == 0L) {
                        // Page up, functions there, model load not started: the
                        // model is loaded on the first playback (see IDLE).
                        next = next.copy(status = EngineStatus.IDLE)
                    } else if (delayed && gen != "function" && cur.status != EngineStatus.READY) {
                        val console = cur.lastConsoleError ?: "none"
                        next = next.copy(
                            status = EngineStatus.ERROR,
                            error = "engine functions missing: generate=$gen Worker=$worker gpu=$gpu " +
                                "webview=${cur.webViewVersion ?: "?"} console=$console"
                        )
                    }
                    _state.value = next
                } catch (_: Exception) {
                    // ignore probe parse errors; engine events still flow
                }
            }
        }
    }

    @Volatile
    private var pendingControlsCallback: ((String?) -> Unit)? = null

    @Volatile
    private var pendingCapabilitiesCallback: ((String?) -> Unit)? = null

    @Volatile
    private var pendingStudioDocCallback: ((String?) -> Unit)? = null

    /**
     * Read the steering-controls document (/v2/controls) at runtime. The
     * pace/speed control id is intentionally NOT hardcoded — it comes
     * from here. Calls [onResult] with the raw JSON string, or null.
     */
    fun fetchControls(onResult: (String?) -> Unit) {
        val view = attachedWv ?: run { onResult(null); return }
        view.post {
            view.evaluateJavascript(
                "window.__zonoControls().then(function(r){window.ZonoNative.onEvent(JSON.stringify({type:'controlsDoc',json:JSON.stringify(r||null)}))}).catch(function(){window.ZonoNative.onEvent(JSON.stringify({type:'controlsDoc',json:null}))})",
                null
            )
            pendingControlsCallback = onResult
        }
    }

    /** Read /v2/capabilities (decoders served, laci_languages). */
    fun fetchCapabilities(onResult: (String?) -> Unit) {
        val view = attachedWv ?: run { onResult(null); return }
        view.post {
            view.evaluateJavascript(
                "window.__zonoCapabilities().then(function(r){window.ZonoNative.onEvent(JSON.stringify({type:'capabilitiesDoc',json:JSON.stringify(r||null)}))}).catch(function(){window.ZonoNative.onEvent(JSON.stringify({type:'capabilitiesDoc',json:null}))})",
                null
            )
            pendingCapabilitiesCallback = onResult
        }
    }

    /**
     * Read the open Studio project document (bench + personas + project
     * settings) from shared storage, for the reader merge. Calls
     * [onResult] with the compact doc JSON string, or null when no Studio
     * project is saved yet or the read fails. Never throws.
     */
    fun fetchStudioDoc(onResult: (String?) -> Unit) {
        val view = attachedWv ?: run { onResult(null); return }
        view.post {
            view.evaluateJavascript(STUDIO_DOC_READ_JS, null)
            pendingStudioDocCallback = onResult
        }
    }

    /**
     * Drop the engine's Cache Storage copy of the models. Call after
     * deleting or replacing a pack so a stale cached pack is never
     * silently mixed with the new files.
     */
    fun clearModelCache() {
        val view = attachedWv ?: return
        view.post {
            view.evaluateJavascript("try{caches.delete('$MODEL_CACHE_NAME')}catch(e){}", null)
        }
    }

    /**
     * Generate + play one sentence; completes when its "done" event arrives.
     * With a [takeKey] the page keeps the generated WAV so [replay] can
     * play the same take again without generating.
     */
    suspend fun generate(index: Int, voiceId: String, text: String, optsJson: String, takeKey: String? = null): Result<String> {
        val call = buildString {
            append("window.__zonoGenerate(")
            append(index).append(",")
            append(JSONObject.quote(voiceId)).append(",")
            append(JSONObject.quote(text)).append(",")
            append(JSONObject.quote(optsJson)).append(",")
            append(if (takeKey != null) JSONObject.quote(takeKey) else "null")
            append(")")
        }
        return runPlayback(index, call)
    }

    /**
     * Play the cached take of [takeKey] again (no generation). Resolves
     * "OK" after playback, or "MISS" when the page no longer has it — the
     * caller then generates afresh.
     */
    suspend fun replay(index: Int, takeKey: String): Result<String> =
        runPlayback(index, "window.__zonoReplay(" + index + "," + JSONObject.quote(takeKey) + ")")

    /** Run one page playback promise and wait for its genDone / genError event. */
    private suspend fun runPlayback(index: Int, promiseCall: String): Result<String> {
        if (_state.value.status == EngineStatus.MISSING) {
            return Result.failure(IllegalStateException("engine missing"))
        }
        val view = attachedWv
            ?: return Result.failure(IllegalStateException("engine WebView not attached"))
        return suspendCancellableCoroutine { cont ->
            pending[index] = { result ->
                if (cont.isActive) cont.resume(result)
            }
            cont.invokeOnCancellation { pending.remove(index) }
            val js = buildString {
                append("(function(){try{")
                append("var p=").append(promiseCall).append(";")
                append("if(p&&p.then){p.then(function(r){window.ZonoNative.onEvent(JSON.stringify({type:'genDone',index:")
                append(index).append(",result:String(r)}))}).catch(function(e){window.ZonoNative.onEvent(JSON.stringify({type:'genError',index:")
                append(index).append(",message:String(e&&e.message?e.message:e)}))});}")
                append("}catch(e){window.ZonoNative.onEvent(JSON.stringify({type:'genError',index:")
                append(index).append(",message:String(e&&e.message?e.message:e)}))}})()")
            }
            view.post { view.evaluateJavascript(js, null) }
        }
    }

    fun stop() {
        pending.values.forEach { it(Result.failure(IllegalStateException("stopped"))) }
        pending.clear()
        if (_state.value.playingIndex != null) _state.value = _state.value.copy(playingIndex = null)
        _playback.value = null
        val view = attachedWv ?: return
        view.post { view.evaluateJavascript("try{window.__zonoStop()}catch(e){}", null) }
    }

    // ------------------------------------------------- transport (playing take)

    private fun runJs(js: String) {
        val view = attachedWv ?: return
        view.post { view.evaluateJavascript("try{$js}catch(e){}", null) }
    }

    fun pause() = runJs("window.__zonoPause()")
    fun resume() = runJs("window.__zonoResume()")
    fun seekTo(positionMs: Long) = runJs("window.__zonoSeek(" + (positionMs.coerceAtLeast(0L) / 1000.0) + ")")

    /** Playback speed 0.5–2.0 (pitch preserved); applies to the playing take and the next ones. */
    fun setRate(rate: Float) = runJs("window.__zonoSetRate(" + rate.coerceIn(0.5f, 2.0f) + ")")

    inner class JsBridge {
        @JavascriptInterface
        fun onEvent(json: String) {
            scope.launch {
                try {
                    val o = JSONObject(json)
                    when (o.optString("type")) {
                        "no-model" -> {
                            val missing = o.optJSONArray("missing")
                            val names = (0 until (missing?.length() ?: 0)).map { missing!!.optString(it) }
                            val coreMissing = names.any { it == "backbone" || it == "zac" }
                            // Only CORE missing is an engine error; optional
                            // packs are expected to be absent.
                            _state.value = _state.value.copy(
                                status = if (coreMissing) EngineStatus.NO_MODEL else _state.value.status,
                                error = if (coreMissing) "missing packs: " + names.joinToString(", ") else _state.value.error
                            )
                        }
                        "models-ok" -> _state.value = _state.value.copy(error = null)
                        "ready" -> _state.value = _state.value.copy(status = EngineStatus.READY, error = null)
                        "progress" -> _state.value = _state.value.copy(
                            status = EngineStatus.LOADING,
                            loaded = o.optLong("loaded"),
                            total = o.optLong("total"),
                            phase = o.optString("phase")
                        )
                        "playing" -> _state.value = _state.value.copy(playingIndex = o.optInt("index", -1))
                        "time" -> {
                            val idx = o.optInt("index", -1)
                            _playback.value = Playback(
                                index = idx,
                                positionMs = (o.optDouble("position", 0.0) * 1000).toLong().coerceAtLeast(0L),
                                durationMs = (o.optDouble("duration", 0.0) * 1000).toLong().coerceAtLeast(0L),
                                paused = o.optBoolean("paused", false),
                                rate = o.optDouble("rate", 1.0).toFloat(),
                            )
                        }
                        "done" -> {
                            val idx = o.optInt("index", -1)
                            if (_state.value.playingIndex == idx) _state.value = _state.value.copy(playingIndex = null)
                            if (_playback.value?.index == idx) _playback.value = null
                            pending.remove(idx)?.invoke(Result.success("OK"))
                        }
                        "genDone" -> {
                            val idx = o.optInt("index", -1)
                            if (_state.value.playingIndex == idx) _state.value = _state.value.copy(playingIndex = null)
                            if (_playback.value?.index == idx) _playback.value = null
                            pending.remove(idx)?.invoke(Result.success(o.optString("result", "OK")))
                        }
                        "genError" -> {
                            val idx = o.optInt("index", -1)
                            if (_state.value.playingIndex == idx) _state.value = _state.value.copy(playingIndex = null)
                            if (_playback.value?.index == idx) _playback.value = null
                            pending.remove(idx)?.invoke(
                                Result.failure(IllegalStateException(o.optString("message", "generation failed")))
                            )
                        }
                        "takes" -> {
                            val arr = o.optJSONArray("keys")
                            val keys = LinkedHashSet<String>()
                            if (arr != null) for (i in 0 until arr.length()) arr.optString(i)?.takeIf { it.isNotEmpty() }?.let(keys::add)
                            _takes.value = keys
                        }
                        "error" -> {
                            val msg = o.optString("message")
                            val missing = msg.contains("engine missing", ignoreCase = true)
                            _state.value = _state.value.copy(
                                status = if (missing) EngineStatus.MISSING else EngineStatus.ERROR,
                                error = msg
                            )
                        }
                        "pageerror" -> {
                            val detail = o.optString("message") +
                                (o.optString("source").takeIf { it.isNotEmpty() }
                                    ?.let { " @ $it:${o.optInt("line", 0)}" } ?: "")
                            if (detail.isNotBlank() && !detail.contains("favicon", ignoreCase = true)) {
                                val cur = _state.value
                                _state.value = cur.copy(
                                    lastPageError = detail,
                                    status = if (cur.status != EngineStatus.READY) EngineStatus.ERROR else cur.status,
                                    error = if (cur.status != EngineStatus.READY) "page error: $detail" else cur.error
                                )
                            }
                        }
                        "voices" -> _state.value = _state.value.copy(voicesCount = o.optInt("count", 0))
                        "gpu" -> _state.value = _state.value.copy(gpuSupported = o.optBoolean("supported", false))
                        "controlsDoc" -> {
                            pendingControlsCallback?.invoke(
                                o.optString("json").takeIf { it != "null" && it.isNotEmpty() }
                            )
                            pendingControlsCallback = null
                        }
                        "capabilitiesDoc" -> {
                            pendingCapabilitiesCallback?.invoke(
                                o.optString("json").takeIf { it != "null" && it.isNotEmpty() }
                            )
                            pendingCapabilitiesCallback = null
                        }
                        "studioDoc" -> {
                            pendingStudioDocCallback?.invoke(
                                o.optString("json").takeIf { it != "null" && it.isNotEmpty() }
                            )
                            pendingStudioDocCallback = null
                        }
                    }
                } catch (_: Exception) {
                    // Malformed event JSON: ignore.
                }
            }
        }
    }
}
