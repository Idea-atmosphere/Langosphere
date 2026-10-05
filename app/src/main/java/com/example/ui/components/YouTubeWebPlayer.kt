package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.abs

/**
 * «پخش در حالت جایگزین» — the fallback web player.
 *
 * When YouTube blocks direct stream extraction on every Piped/Invidious
 * instance (HTTP 403 on googlevideo, "sign in to confirm you're not a bot",
 * no playable stream), the clip is played through YouTube's official IFrame
 * embed inside a clean WebView instead, so the user is never locked out of
 * a video they want to study.
 *
 * The important part is that this is a real Media3 [Player]: the embed's
 * clock, play/pause, seeking and speed are bridged through the IFrame
 * JavaScript API. [VideoPlayerScreen] drives it exactly like ExoPlayer, so
 * `player.currentPosition` keeps powering the active-subtitle highlight,
 * transcript auto-scroll, smart pause, A-B repeat, word lookups and Leitner
 * additions without a single special case in the subtitle engine.
 *
 * Positions arrive from JavaScript four times a second and are extrapolated
 * in between (like ExoPlayer does), so subtitle highlighting stays smooth.
 *
 * All [Player] calls must happen on the main thread (the application
 * looper), which is where Compose runs; JavaScript callbacks arrive on a
 * WebView binder thread and are posted to the main thread.
 */
@OptIn(UnstableApi::class)
class YouTubeWebPlayer(
    context: Context,
    val videoId: String
) : SimpleBasePlayer(Looper.getMainLooper()) {

    /** The view to show in place of ExoPlayer's PlayerView. Owned (and destroyed) by this player. */
    val view: FrameLayout = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val origin = "https://" + context.packageName
    private var webView: WebView? = null

    // ── Mirrored embed state ──
    private var prepared = false
    private var released = false
    private var embedReady = false
    private var wantsToPlay = true
    /** YT.PlayerState: -1 unstarted, 0 ended, 1 playing, 2 paused, 3 buffering, 5 cued. */
    private var ytState = -1
    private var positionMs = 0L
    private var positionSampledAt = SystemClock.elapsedRealtime()
    private var durationMs = C.TIME_UNSET
    private var loadedFraction = 0f
    private var speed = 1f
    private var error: PlaybackException? = null
    private var startPositionMs = 0L
    private var pendingSeekMs: Long? = null
    private var seekTargetMs = 0L
    private var seekGuardUntil = 0L
    private var autoplayRetries = 0

    private val mediaItem: MediaItem = MediaItem.Builder()
        .setMediaId(videoId)
        .setUri("https://www.youtube.com/watch?v=$videoId")
        .build()

    /** Where playback should start (the saved resume point); must be called before [prepare]. */
    fun setStartPosition(positionMs: Long) {
        startPositionMs = positionMs.coerceAtLeast(0L)
        this.positionMs = startPositionMs
        positionSampledAt = SystemClock.elapsedRealtime()
    }

    // ── SimpleBasePlayer ──

    override fun getState(): SimpleBasePlayer.State {
        val commands = Player.Commands.Builder()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_PREPARE)
            .add(Player.COMMAND_STOP)
            .add(Player.COMMAND_RELEASE)
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
            .add(Player.COMMAND_SEEK_BACK)
            .add(Player.COMMAND_SEEK_FORWARD)
            .add(Player.COMMAND_SET_SPEED_AND_PITCH)
            .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_GET_TIMELINE)
            .build()
        val playbackState = when {
            released || !prepared || error != null -> Player.STATE_IDLE
            !embedReady -> Player.STATE_BUFFERING
            ytState == 0 -> Player.STATE_ENDED
            ytState == 3 -> Player.STATE_BUFFERING
            else -> Player.STATE_READY
        }
        val builder = SimpleBasePlayer.State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(wantsToPlay, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(playbackState)
            .setIsLoading(playbackState == Player.STATE_BUFFERING)
            .setPlayerError(error)
            .setPlaybackParameters(PlaybackParameters(speed))
        if (released) return builder.build()
        val item = SimpleBasePlayer.MediaItemData.Builder(videoId)
            .setMediaItem(mediaItem)
            .setDurationUs(if (durationMs > 0) durationMs * 1000L else C.TIME_UNSET)
            .setIsSeekable(true)
            .build()
        val position = estimatedPositionMs()
        val buffered = if (durationMs > 0) (durationMs * loadedFraction).toLong().coerceAtLeast(position) else position
        return builder
            .setPlaylist(listOf(item))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(
                if (isAdvancing()) SimpleBasePlayer.PositionSupplier.getExtrapolating(position, speed)
                else SimpleBasePlayer.PositionSupplier.getConstant(position)
            )
            .setContentBufferedPositionMs(SimpleBasePlayer.PositionSupplier.getConstant(buffered))
            .build()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        if (!prepared && !released) {
            prepared = true
            error = null
            createWebView()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        // Freeze the extrapolated clock at the moment of the change.
        positionMs = estimatedPositionMs()
        positionSampledAt = SystemClock.elapsedRealtime()
        wantsToPlay = playWhenReady
        if (embedReady) js(if (playWhenReady) "player.playVideo();" else "player.pauseVideo();")
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val target = if (positionMs == C.TIME_UNSET) 0L else positionMs.coerceAtLeast(0L)
        this.positionMs = target
        positionSampledAt = SystemClock.elapsedRealtime()
        seekTargetMs = target
        // The embed keeps reporting the old time for a moment after seekTo;
        // ignore those stale samples so the subtitle highlight does not flicker back.
        seekGuardUntil = SystemClock.elapsedRealtime() + 1200L
        if (ytState == 0) ytState = 2
        if (embedReady) {
            js("player.seekTo(${target / 1000.0}, true);")
        } else {
            pendingSeekMs = target
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        positionMs = estimatedPositionMs()
        positionSampledAt = SystemClock.elapsedRealtime()
        // The embed supports 0.25x–2x.
        speed = playbackParameters.speed.coerceIn(0.25f, 2f)
        if (embedReady) js("player.setPlaybackRate($speed);")
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        if (embedReady) js("player.pauseVideo();")
        wantsToPlay = false
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        released = true
        mainHandler.removeCallbacksAndMessages(null)
        webView?.let { wv ->
            try {
                wv.stopLoading()
                wv.removeJavascriptInterface(BRIDGE_NAME)
                wv.loadUrl("about:blank")
                // Detach first: a WebView must not be destroyed while attached.
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
            } catch (_: Exception) {
            }
        }
        webView = null
        return Futures.immediateVoidFuture()
    }

    // ── Internals ──

    private fun isAdvancing(): Boolean =
        !released && error == null && embedReady && ytState == 1 && wantsToPlay

    private fun estimatedPositionMs(): Long {
        val base = positionMs
        if (!isAdvancing()) return base
        val elapsed = SystemClock.elapsedRealtime() - positionSampledAt
        val estimate = base + (elapsed * speed).toLong()
        return if (durationMs > 0) estimate.coerceAtMost(durationMs) else estimate
    }

    private fun js(script: String) {
        val wv = webView ?: return
        if (released) return
        wv.evaluateJavascript("try{if(window.player){$script}}catch(e){}", null)
    }

    private fun fail(message: String) {
        if (released) return
        error = PlaybackException(message, null, PlaybackException.ERROR_CODE_REMOTE_ERROR)
        refreshState()
    }

    /** Re-reads [getState] and notifies listeners of whatever changed. */
    private fun refreshState() {
        if (!released) invalidateState()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        if (!VIDEO_ID.matches(videoId)) {
            // Not from inside prepare(): report on the next main-loop turn.
            mainHandler.post { fail("Invalid video id") }
            return
        }
        val wv = WebView(view.context)
        wv.setBackgroundColor(Color.BLACK)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            // No pop-ups: "Watch on YouTube", the channel avatar and end-screen
            // cards try to open new windows; with multiple windows off they
            // fall back to a main-frame navigation, which the client below
            // refuses. The learner stays on the clip and our transcript.
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
        }
        // Keep the page on the embed: never navigate the top frame to
        // youtube.com (tapping the logo, end-screen links, …).
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.isForMainFrame
        }
        wv.webChromeClient = object : WebChromeClient() {
            // Hide the grey "play" poster the system draws before the first frame.
            override fun getDefaultVideoPoster(): Bitmap =
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        wv.addJavascriptInterface(Bridge(), BRIDGE_NAME)
        installEmbedCleanup(wv)
        view.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        webView = wv
        // A real https base URL (not about:blank / file://) plus a matching
        // `origin` and a strict-origin referrer policy: YouTube now refuses
        // embeds without a client identity ("Error 153").
        wv.loadDataWithBaseURL("$origin/", embedHtml(), "text/html", "utf-8", null)
    }

    /**
     * The embed lives in a cross-origin iframe, so the host page cannot style
     * it. AndroidX WebKit's document-start scripts, however, run in *every
     * frame* whose origin matches — including the youtube-nocookie iframe —
     * so a stylesheet is injected there that hides YouTube's own clutter:
     * the title bar with the channel avatar, "Watch on YouTube" / share /
     * watch-later buttons, the watermark, pause-screen suggestions, cards,
     * end-screen tiles and YouTube's own caption window (the app renders the
     * captions itself). Must be registered before the page is loaded.
     * WebViews without the feature (very old System WebView) simply keep the
     * stock embed.
     */
    private fun installEmbedCleanup(wv: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        try {
            WebViewCompat.addDocumentStartJavaScript(wv, YouTubeEmbedCleanup.SCRIPT, YouTubeEmbedCleanup.ORIGINS)
        } catch (e: Exception) {
            // Cosmetic only — playback works without it.
        }
    }

    private fun embedHtml(): String {
        val startSeconds = startPositionMs / 1000L
        return """
            <!DOCTYPE html>
            <html><head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
            <meta name="referrer" content="strict-origin-when-cross-origin">
            <style>
              html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }
              #player { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: 0; }
              /* The iframe is cross-origin: its chrome is hidden by the
                 document-start stylesheet (YouTubeEmbedCleanup). As a second
                 line of defence the end screen is covered once the clip ended. */
              #cover { position: absolute; top: 0; left: 0; width: 100%; height: 100%;
                       background: #000; display: none; z-index: 2; }
              body.ended #cover { display: block; }
            </style>
            </head><body>
            <div id="player"></div>
            <div id="cover"></div>
            <script>
              var player = null;
              var ready = false;
              var tag = document.createElement('script');
              tag.src = 'https://www.youtube.com/iframe_api';
              tag.onerror = function() { $BRIDGE_NAME.onApiError(); };
              document.head.appendChild(tag);
              function onYouTubeIframeAPIReady() {
                player = new YT.Player('player', {
                  width: '100%', height: '100%',
                  videoId: '$videoId',
                  host: 'https://www.youtube-nocookie.com',
                  playerVars: {
                    autoplay: ${if (wantsToPlay) 1 else 0}, start: $startSeconds, playsinline: 1,
                    // controls=0: the app's own player chrome (play, seek, speed,
                    // subtitles, rotation) drives the embed through this API, so
                    // YouTube's bar and its logo are not shown at all; fs=0
                    // because the app's fullscreen is used instead; rel=0 +
                    // iv_load_policy=3 keep suggestions and annotations to the
                    // minimum, and the injected stylesheet hides the rest.
                    controls: 0, disablekb: 1, fs: 0, rel: 0, modestbranding: 1,
                    iv_load_policy: 3, cc_load_policy: 0, enablejsapi: 1,
                    origin: '$origin'
                  },
                  events: {
                    onReady: function(e) { ready = true; $BRIDGE_NAME.onReady(e.target.getDuration() || 0); tick(); },
                    onStateChange: function(e) {
                      // 0 = ended: hide the suggested-videos end screen.
                      document.body.className = (e.data === 0) ? 'ended' : '';
                      $BRIDGE_NAME.onStateChange(e.data); tick();
                    },
                    onError: function(e) { $BRIDGE_NAME.onError(e.data); },
                    onPlaybackRateChange: function(e) { $BRIDGE_NAME.onRate(e.data); }
                  }
                });
              }
              function tick() {
                if (!ready || !player || !player.getCurrentTime) return;
                try {
                  $BRIDGE_NAME.onProgress(player.getCurrentTime() || 0, player.getDuration() || 0,
                    player.getPlayerState(), player.getVideoLoadedFraction() || 0);
                } catch (e) {}
              }
              setInterval(tick, 250);
            </script>
            </body></html>
        """.trimIndent()
    }

    /** JavaScript → Kotlin. Called on a WebView thread; everything is handed to the main thread. */
    private inner class Bridge {
        @JavascriptInterface
        fun onReady(durationSeconds: Double) = post {
            embedReady = true
            if (durationSeconds > 0) durationMs = (durationSeconds * 1000).toLong()
            if (speed != 1f) js("player.setPlaybackRate($speed);")
            val seek = pendingSeekMs ?: startPositionMs.takeIf { it > 0 }
            pendingSeekMs = null
            if (seek != null) {
                seekTargetMs = seek
                seekGuardUntil = SystemClock.elapsedRealtime() + 1500L
                js("player.seekTo(${seek / 1000.0}, true);")
            }
            js(if (wantsToPlay) "player.playVideo();" else "player.pauseVideo();")
        }

        @JavascriptInterface
        fun onStateChange(state: Int) = post {
            ytState = state
            if (state == 0 && durationMs > 0) positionMs = durationMs
            positionSampledAt = SystemClock.elapsedRealtime()
        }

        @JavascriptInterface
        fun onProgress(currentSeconds: Double, durationSeconds: Double, state: Int, fraction: Double) = post {
            val now = SystemClock.elapsedRealtime()
            val reported = (currentSeconds * 1000).toLong()
            if (durationSeconds > 0) durationMs = (durationSeconds * 1000).toLong()
            loadedFraction = fraction.toFloat().coerceIn(0f, 1f)
            ytState = state
            val staleAfterSeek = now < seekGuardUntil && abs(reported - seekTargetMs) > 1500L
            if (!staleAfterSeek) {
                positionMs = reported
                positionSampledAt = now
            }
            // Autoplay can be refused silently; nudge it a few times.
            if (wantsToPlay && (state == -1 || state == 5) && autoplayRetries < 3) {
                autoplayRetries++
                js("player.playVideo();")
            }
        }

        @JavascriptInterface
        fun onRate(rate: Double) = post {
            if (rate > 0) {
                positionMs = estimatedPositionMs()
                positionSampledAt = SystemClock.elapsedRealtime()
                speed = rate.toFloat()
            }
        }

        @JavascriptInterface
        fun onError(code: Int) = post {
            fail(
                when (code) {
                    2 -> "YouTube embed: invalid video id"
                    5 -> "YouTube embed: the video cannot be played in the web player"
                    100 -> "YouTube embed: video not found or private"
                    101, 150 -> "YouTube embed: the owner does not allow playback in other apps"
                    152, 153 -> "YouTube embed: player configuration error ($code)"
                    else -> "YouTube embed error $code"
                }
            )
        }

        @JavascriptInterface
        fun onApiError() = post { fail("YouTube embed could not be loaded (no connection?)") }

        private fun post(block: () -> Unit) {
            mainHandler.post {
                if (released) return@post
                block()
                refreshState()
            }
        }
    }

    private companion object {
        const val BRIDGE_NAME = "LangosphereBridge"
        val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
    }
}

/**
 * The stylesheet injected into the YouTube embed frame by
 * [YouTubeWebPlayer] (see `installEmbedCleanup`). Only visual overlays are
 * hidden — never the `<video>` element or YouTube's error screen, so a
 * refused clip still reports its error through the IFrame API.
 */
internal object YouTubeEmbedCleanup {
    /** The embed frame's origins (the player uses youtube-nocookie). */
    val ORIGINS: Set<String> = setOf("https://www.youtube-nocookie.com", "https://www.youtube.com")

    val HIDDEN_SELECTORS: List<String> = listOf(
        // Title bar: video title, channel avatar, share / watch later / more.
        ".ytp-chrome-top", ".ytp-chrome-top-buttons", ".ytp-gradient-top",
        ".ytp-title", ".ytp-title-channel", ".ytp-title-channel-logo", ".ytp-show-cards-title",
        ".ytp-share-button", ".ytp-watch-later-button", ".ytp-overflow-button",
        ".ytp-copylink-button",
        // Bottom bar, the YouTube logo and the watermark.
        ".ytp-chrome-bottom", ".ytp-gradient-bottom", ".ytp-youtube-button",
        ".ytp-watermark", ".iv-branding", ".branding-img-container",
        // "More videos" on pause, cards, end-screen elements, paid promotion.
        ".ytp-pause-overlay", ".ytp-pause-overlay-container",
        ".ytp-ce-element", ".ytp-ce-covering-overlay", ".ytp-endscreen-content",
        ".ytp-videowall-still", ".ytp-cards-button", ".ytp-cards-teaser",
        ".ytp-impression-link", ".ytp-paid-content-overlay", ".ytp-suggested-action",
        ".ytp-featured-product",
        // The big red play button and YouTube's own caption window (the app
        // shows its own subtitles and its own play button).
        ".ytp-large-play-button", ".ytp-caption-window-container", ".ytp-contextmenu",
        ".annotation"
    )

    val CSS: String = HIDDEN_SELECTORS.joinToString(",") +
        "{display:none!important;visibility:hidden!important;opacity:0!important;pointer-events:none!important}"

    /** Adds the stylesheet as early as possible and again when the DOM is ready. */
    val SCRIPT: String = """
        (function() {
          var css = ${jsString(CSS)};
          function add() {
            if (document.getElementById('lango-clean')) return;
            var root = document.head || document.documentElement;
            if (!root) return;
            var s = document.createElement('style');
            s.id = 'lango-clean';
            s.textContent = css;
            root.appendChild(s);
          }
          add();
          document.addEventListener('DOMContentLoaded', add);
          window.addEventListener('load', add);
        })();
    """.trimIndent()

    private fun jsString(value: String): String =
        "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"
}
