package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.logic.KnownWordsStore
import com.example.logic.OnlinePlaybackHeaders
import com.example.logic.OrientationToggle
import com.example.logic.SaveWhilePlayingDataSourceFactory
import com.example.logic.StreamCacheController
import com.example.logic.StudyModeState
import com.example.logic.TtsSpeaker
import com.example.logic.autoTextDirection
import com.example.model.JsonSubtitle
import com.example.model.JsonSubtitlePackage
import com.example.model.LeitnerCard
import com.example.model.SubtitleEntry
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AppFontState
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.AppStrings
import com.example.ui.theme.LanguageWeightState
import com.example.ui.theme.SubtitleColorState
import com.example.ui.theme.resolvedSubtitleFamily
import androidx.compose.ui.zIndex
import com.example.ui.components.anime.ToonChip
import com.example.ui.components.anime.inkBorder
import com.example.ui.components.anime.inkShadow
import com.example.ui.theme.AnimeColors
import com.example.ui.theme.isAnimeDesign
import com.example.ui.theme.isNeobrutalismDesign
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

enum class OverlayButton { CONTINUE, AUTO_PREV, AUTO_CURRENT }

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoUri: Uri?,
    videoFileName: String = "",
    subEnList: List<SubtitleEntry>,
    subFaList: List<SubtitleEntry>,
    isFullScreen: Boolean,
    onFullScreenToggle: (Boolean) -> Unit,
    onWordClick: (String, String?, String?) -> Unit,
    onShiftSubEn: (Double) -> Unit = {},
    onShiftSubFa: (Double) -> Unit = {},
    subEnOffset: Double = 0.0,
    subFaOffset: Double = 0.0,
    // JSON subtitle time sync (same feature as the EN/FA offsets, applied
    // to the JSON learning package's timestamps).
    onShiftJson: (Double) -> Unit = {},
    onResetJson: () -> Unit = {},
    jsonOffset: Double = 0.0,
    // Fired while the USER scrolls the subtitle list: `true` once they
    // scroll past the top of the list (MainScreen folds the import section
    // away to give the list more room), `false` when they scroll back to
    // the very top. Programmatic auto-scroll (following the active
    // subtitle) never triggers this.
    onUserScrollCollapse: (Boolean) -> Unit = {},
    // Focus mode: hides the top bar/tabs, the import section and the
    // subtitle time-sync cards so only the video + subtitle list remain
    // (the state itself lives in MainScreen).
    focusMode: Boolean = false,
    onFocusModeToggle: () -> Unit = {},
    onTranslateSubtitle: (Int) -> Unit = {},
    onSaveSrt: () -> Unit = {},
    isTranslatingSingle: Boolean = false,
    translatingIndex: Int = -1,
    singleTranslateError: String? = null,
    onStopTranslation: () -> Unit = {},
    appLanguage: AppLanguage = AppLanguage.FA,
    // JSON subtitle-learning package (highest-priority subtitle source).
    // Display priority while non-null: JSON learning file > imported EN/FA
    // subtitle files > default subtitle source. When JSON subtitles are
    // shown, normal EN/FA subtitle lines are NOT rendered again (no
    // duplicated rendering); they only remain in memory as a fallback.
    jsonPackage: JsonSubtitlePackage? = null,
    // Fired when the user clicks an English subtitle SENTENCE (outside any
    // word) — opens the learning lesson for that sentence.
    onSentenceClick: (sentence: String, translation: String?) -> Unit = { _, _ -> },
    // Online tab: the container of a network stream cannot always be
    // inferred from its URL (Invidious/Piped HLS manifests have no .m3u8
    // suffix), so the caller may name it explicitly
    // (MimeTypes.APPLICATION_M3U8 / APPLICATION_MPD). null = infer.
    streamMimeType: String? = null,
    // Piped/Invidious adaptive formats expose video and audio separately.
    // Supplying this URI merges both files onto one playback timeline.
    streamAudioUri: Uri? = null,
    // Opening a subtitle lesson must pause playback underneath the modal.
    pauseForLesson: Boolean = false,
    // Online tab: notified when ExoPlayer cannot open a network rendition so
    // the caller can try another signed URL or manifest automatically.
    onPlaybackError: (String) -> Unit = {},
    // Online tab: stream URLs carry expiring signatures and change with the
    // chosen quality, so the resume position is keyed by the video id
    // instead of the URL. null = derive the key from the URI (local files).
    resumeStateKey: String? = null,
    /**
     * The study controls shared with the book reader, offered on every JSON
     * subtitle line: hear the line, hide the translation, keep one line in
     * focus and add the whole line to the Leitner box. Optional, so a caller
     * that does not pass them keeps the list exactly as it was.
     */
    studyLabels: SentenceStudyLabels? = null,
    /** Normalized text of the lines already in the Leitner box. */
    savedSentenceTexts: Set<String> = emptySet(),
    /** Builds and stores the sentence card for the line at [index]. */
    onAddSentenceToLeitner: ((JsonSubtitle, String, Int) -> Unit)? = null,
    /**
     * Online tab, «پخش در حالت جایگزین»: when non-null the clip with this
     * YouTube id is played through the official IFrame embed in a WebView
     * ([YouTubeWebPlayer]) instead of ExoPlayer. The embed is bridged as a
     * Media3 Player, so subtitles, smart pause and study tools keep running
     * off the same `currentPosition` clock. [videoUri] should still be a
     * non-null placeholder (e.g. the watch URL) so the player chrome shows.
     */
    webFallbackVideoId: String? = null,
    /**
     * Online tab: if a source has not become ready this many milliseconds
     * after it was opened (and no error was raised), it is reported through
     * [onPlaybackError] so the caller can move on instead of leaving an
     * endless spinner. 0 = no watchdog (local files).
     */
    startupTimeoutMs: Long = 0L,
    /**
     * Online tab: shows «چرخش صفحه», a manual portrait ⇄ landscape switch
     * on the player chrome (see [OrientationToggle]). Off by default, so the
     * Video tab's player is exactly as it was.
     */
    showOrientationToggle: Boolean = false,
    /**
     * Online tab, «صفحه پخش و مطالعه تعاملی»: the redesigned learning
     * player — a rounded 16:9 video card and the transcript as [CueCard]s whose
     * active card is kept centred. Off by default, so the Video tab's player
     * (split handle, subtitle rows) is exactly as it was.
     */
    onlineStudio: Boolean = false,
    /** Online tab: extra floating buttons on the picture (e.g. ⭐ save), top corner. */
    overlayActions: (@Composable () -> Unit)? = null,
    /** Online stream renditions are selected from the action drawer under the video. */
    streamQualities: List<String> = emptyList(),
    selectedStreamQuality: Int = 0,
    onSelectStreamQuality: ((Int) -> Unit)? = null,
    /** Caller actions that formerly lived in normal-view overflow/toolbars. */
    fabActions: List<PlayerFabAction> = emptyList(),
    /**
     * Online tab: the callbacks behind the collapsible action drawer
     * (کشوی ابزار) that replaced the interactive transcript beneath the
     * video. The drawer is rendered only while this is non-null.
     */
    onlineDrawerActions: OnlineDrawerActions? = null,
    /**
     * Online tab, «ذخیره هنگام پخش»: while on, every byte the player
     * downloads for viewing is written to the disk cache as it is watched
     * (see [com.example.logic.OnlineWatchCache]). The flag is read at
     * data-source open time, so flipping it mid-play applies from the next
     * opened source (seek, quality switch, next clip) without a player
     * rebuild. The Video tab never passes true.
     */
    saveWhilePlaying: Boolean = false,
    /**
     * Online tab: resolves a stream URI to its stable cross-session cache
     * key — the signed googlevideo URLs rotate between sessions, so the
     * video id + rendition identity keys the cache instead.
     */
    streamCacheKeyFor: (Uri) -> String? = { null },
) {
    val context = LocalContext.current
    val strings = remember(appLanguage, context) { AppStrings(appLanguage, context) }
    val prefs = rememberPlayerPrefs()

    // Locale.US matters here: with the Persian locale the default
    // String.format produces Persian digits, which then could not be parsed
    // back by toDoubleOrNull() in the "exact time" field.
    fun offsetText(v: Double): String {
        val formatted = String.format(Locale.US, "%.2f", v)
        return formatted.replace("-", strings.negativeMinusReplacement)
    }

    val videoStateKey = remember(videoUri, resumeStateKey) { resumeStateKey ?: "video_state_${videoUri?.hashCode() ?: 0}" }

    // ── Playback clock ──
    var currentTime by remember { mutableStateOf(0.0) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var bufferedMs by remember { mutableStateOf(0L) }

    val isAudio = remember(videoUri, videoFileName) {
        val name = videoFileName.lowercase()
        val uriStr = videoUri?.toString()?.lowercase() ?: ""
        // The loose "audio" substring test is for local content:// paths;
        // a network stream URL (Online tab) can contain that word in its
        // query string while being a plain video.
        val isNetwork = uriStr.startsWith("http://") || uriStr.startsWith("https://")
        name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".wav") || name.endsWith(".aac") || name.endsWith(".ogg") || name.endsWith(".flac") ||
            uriStr.endsWith(".mp3") || uriStr.endsWith(".m4a") || uriStr.endsWith(".wav") || uriStr.endsWith(".aac") || uriStr.endsWith(".ogg") || uriStr.endsWith(".flac") ||
            (!isNetwork && uriStr.contains("audio"))
    }
    var albumArtBitmap by remember(videoUri) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(videoUri) {
        // Album art only makes sense for local files; probing a network
        // stream would download part of it just to look for a cover.
        val isNetwork = videoUri?.scheme == "http" || videoUri?.scheme == "https"
        if (videoUri != null && !isNetwork) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var retriever: android.media.MediaMetadataRetriever? = null
                try {
                    retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(context, videoUri)
                    val artBytes = retriever.embeddedPicture
                    albumArtBitmap = if (artBytes != null) {
                        android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                    } else null
                } catch (e: Exception) {
                    e.printStackTrace(); albumArtBitmap = null
                } finally {
                    try { retriever?.release() } catch (e: Exception) { e.printStackTrace() }
                }
            }
        } else albumArtBitmap = null
    }

    // ── Subtitle color resolution ──
    // Overlay colors sit on an always-dark picture, while cue cards sit on
    // the current theme surface. A custom SubtitleColorState choice always
    // overrides either default.
    val subtitleOverlayColorEn = SubtitleColorState.colorEn ?: Color.White
    val subtitleOverlayColorFa = SubtitleColorState.colorFa ?: AccentAmber
    // Cue cards sit on theme surfaces: text / soft text unless the user
    // picked their own subtitle colours. The same tokens now serve the
    // Online transcript and the local Video tab.
    val studioColorEn = SubtitleColorState.colorEn ?: MaterialTheme.colorScheme.onSurface
    val studioColorFa = SubtitleColorState.colorFa ?: MaterialTheme.colorScheme.onSurfaceVariant
    val overlayTextShadow = Shadow(
        color = Color.Black.copy(alpha = 0.6f),
        offset = Offset(0f, 1.5f),
        blurRadius = 6f
    )
    // ── Subtitle fonts (Settings ▸ Theme ▸ Font ▸ Subtitles) ──
    // The player's own EN/FA choice wins; when a language is left on
    // "default" it inherits the whole-app font for that language — so the
    // app font reaches the subtitles too unless the user defined a separate
    // subtitle font. (The prefs instance is process-wide shared, so picks
    // made in the font dialog are visible here immediately.) The renderer
    // parameters are non-null: with nothing chosen anywhere this falls back
    // to the platform default, exactly like the old fontFamilyFor() did.
    val appFontEn = AppFontState.app
    val appFontFa = AppFontState.appFa
    val subtitleFamilyEn = remember(prefs.fontEn, prefs.customFontPathEn, appFontEn) {
        resolvedSubtitleFamily(prefs.fontEn, prefs.customFontPathEn, fa = false) ?: FontFamily.Default
    }
    val subtitleFamilyFa = remember(prefs.fontFa, prefs.customFontPathFa, appFontEn, appFontFa) {
        resolvedSubtitleFamily(prefs.fontFa, prefs.customFontPathFa, fa = true) ?: FontFamily.Default
    }

    var showSubtitleSettings by remember { mutableStateOf(false) }
    // Online tab: the «کارهای دیگر پخش‌کننده» sheet opened from the action
    // drawer (the extra player actions that used to close the 3-dot
    // settings sheet).
    var showOnlineExtraActions by remember { mutableStateOf(false) }
    var containerWidth by remember { mutableStateOf(0) }
    var containerHeightPx by remember { mutableStateOf(1f) }
    var isSplitDragging by remember { mutableStateOf(false) }

    // ── Chrome visibility ──
    var controlsVisible by remember { mutableStateOf(true) }
    var skipBadgeNonce by remember { mutableStateOf(0) }
    var skipBadgeVisible by remember { mutableStateOf(false) }
    var skipBadgeForward by remember { mutableStateOf(true) }
    // Everything that is not needed on every single tap now hides behind
    // one button instead of lining up eight icons over the picture.
    var showToolCluster by remember { mutableStateOf(false) }
    // In normal playback the exact same three-dot cluster lives in the
    // transcript pane, replacing the old single-purpose settings FAB.
    var showTranscriptToolCluster by remember { mutableStateOf(false) }

    // ── Playback speed & A-B repeat ──
    // The loop markers are per-file on purpose (a range from the previous
    // video means nothing in the next one), so they reset with videoUri.
    var showSpeedPanel by remember { mutableStateOf(false) }
    var loopStartMs by remember(videoUri) { mutableStateOf<Long?>(null) }
    var loopEndMs by remember(videoUri) { mutableStateOf<Long?>(null) }

    // ── Listen mode ──
    // Reading along is comfortable but it is not listening practice: in
    // listen mode the subtitles stay hidden and are revealed one line at a
    // time, only when the learner asks. The choice is remembered.
    var listenMode by remember { mutableStateOf(prefs.raw.getBoolean("listen_mode", false)) }
    var revealCurrent by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    // ── User-scroll detection (for the collapsible import section) ──
    // The player auto-scrolls the list to follow the active subtitle line;
    // only USER scrolls should fold the import section, so programmatic
    // scrolls are tracked with isAutoScrolling and excluded here.
    var isAutoScrolling by remember { mutableStateOf(false) }
    var isUserScrolling by remember { mutableStateOf(false) }
    // Timestamp of the last manual scroll: auto-follow backs off for a few
    // seconds afterwards instead of yanking the list back immediately.
    var lastUserScrollAt by remember { mutableStateOf(0L) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { inProgress ->
                isUserScrolling = inProgress && !isAutoScrolling
                if (isUserScrolling) lastUserScrollAt = System.currentTimeMillis()
            }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .map { (index, offset) ->
                if (isUserScrolling) {
                    lastUserScrollAt = System.currentTimeMillis()
                    when {
                        index > 0 || offset > 100 -> true   // scrolled away from the top
                        index == 0 && offset == 0 -> false  // back at the very top
                        else -> null
                    }
                } else null
            }
            .distinctUntilChanged()
            .collect { collapse -> collapse?.let(onUserScrollCollapse) }
    }

    var autoPauseAtTime by remember { mutableStateOf<Double?>(null) }
    // The key remains armed after one pause so replaying the cue can stop at
    // the same -0.1 s point again until the learner explicitly toggles it off.
    var cuePauseAtEndKey by remember(videoUri) { mutableStateOf<String?>(null) }
    var skipNextAutoScroll by remember { mutableStateOf(false) }

    // ── Free-form overlay buttons (positions persisted) ──
    data class ButtonTransform(var x: Float, var y: Float, var scale: Float, var rotation: Float)
    fun loadTransform(key: String, defaultX: Float, defaultY: Float): ButtonTransform {
        val raw = prefs.raw.getString("overlay_$key", null)
        if (raw != null) {
            val parts = raw.split("|")
            if (parts.size == 4) {
                return ButtonTransform(
                    parts[0].toFloatOrNull() ?: defaultX,
                    parts[1].toFloatOrNull() ?: defaultY,
                    parts[2].toFloatOrNull() ?: 1f,
                    parts[3].toFloatOrNull() ?: 0f
                )
            }
        }
        return ButtonTransform(defaultX, defaultY, 1f, 0f)
    }
    fun saveTransform(key: String, t: ButtonTransform) {
        prefs.raw.edit().putString("overlay_$key", "${t.x}|${t.y}|${t.scale}|${t.rotation}").apply()
    }
    var showOverlaySettings by remember { mutableStateOf(false) }
    val continueTransform = remember { mutableStateOf(loadTransform("continue", 0f, -80f)) }
    val autoPrevTransform = remember { mutableStateOf(loadTransform("auto_prev", 0f, 0f)) }
    val autoCurrentTransform = remember { mutableStateOf(loadTransform("auto_current", 0f, 80f)) }
    val smartPauseGearTransform = remember { mutableStateOf(loadTransform("smart_pause_gear", 0f, 0f)) }

    val subtitleAlignmentMap = remember(subEnList, subFaList) { alignSubtitles(subEnList, subFaList) }
    /*
     * googlevideo.com and the Piped/Invidious proxies in front of it answer
     * HTTP 403 to ExoPlayer's default identity (`ExoPlayerLib/…`, no
     * Referer). Media requests therefore present a regular desktop-Chrome
     * user agent plus a youtube.com Referer, follow http↔https redirects
     * (proxies bounce between the two) and give up on a dead host after 15 s
     * so the fallback chain can move on. Local files are unaffected:
     * DefaultDataSource only uses the HTTP factory for http(s) URIs.
     */
    // «ذخیره هنگام پخش» is read through this volatile holder, not through
    // the remember keys below: flipping the toggle must NOT rebuild the
    // player (that would drop the playing position); the running connection
    // keeps its behaviour and the next opened source honours the new state.
    val streamCacheController = remember { StreamCacheController() }
    SideEffect {
        streamCacheController.enabled = saveWhilePlaying
        streamCacheController.keyFor = streamCacheKeyFor
    }
    val networkDataSourceFactory = remember(context) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(OnlinePlaybackHeaders.USER_AGENT)
            .setDefaultRequestProperties(OnlinePlaybackHeaders.requestProperties)
            .setConnectTimeoutMs(OnlinePlaybackHeaders.CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(OnlinePlaybackHeaders.READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)
        SaveWhilePlayingDataSourceFactory(context, httpFactory, streamCacheController)
    }
    /*
     * The player behind every control on this screen. Normally ExoPlayer;
     * in the Online tab's fallback mode the YouTube IFrame embed bridged as a
     * Media3 Player. It is typed as the Player interface (and keeps its
     * historical name) so the subtitle engine, smart pause, A-B repeat and
     * transport controls drive either one through `currentPosition`,
     * `seekTo`, `play` and `pause` with no special cases. Switching modes
     * builds a new player; the lifecycle effect below saves the resume
     * point of the old one and releases it.
     */
    val exoPlayer: Player = remember(networkDataSourceFactory, webFallbackVideoId) {
        if (webFallbackVideoId != null) {
            YouTubeWebPlayer(context, webFallbackVideoId)
        } else {
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(networkDataSourceFactory))
                .build()
                .apply { playWhenReady = true }
        }
    }
    // The web player is started here rather than where it is built, so the
    // resume point saved by the previous (released) player is picked up.
    LaunchedEffect(exoPlayer) {
        val web = exoPlayer as? YouTubeWebPlayer ?: return@LaunchedEffect
        web.setStartPosition(prefs.savedPosition(videoStateKey))
        web.playWhenReady = true
        web.prepare()
        try {
            web.setPlaybackSpeed(prefs.playbackSpeed)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    LaunchedEffect(exoPlayer, pauseForLesson) {
        if (pauseForLesson) exoPlayer.pause()
    }
    val currentOnPlaybackError = rememberUpdatedState(onPlaybackError)
    var isPlaying by remember { mutableStateOf(false) }
    // Whether the source opened last has reached STATE_READY at least once;
    // drives the startup watchdog (a later rebuffer is not a startup failure).
    var sourceReady by remember(exoPlayer, videoUri, streamAudioUri) { mutableStateOf(false) }
    var audioTrackGroups by remember { mutableStateOf<List<Tracks.Group>>(emptyList()) }

    fun performSkip(deltaSeconds: Int) {
        val target = (exoPlayer.currentPosition + deltaSeconds * 1000L).coerceAtLeast(0L)
        exoPlayer.seekTo(target)
        positionMs = target
        skipBadgeForward = deltaSeconds > 0
        skipBadgeNonce += 1
        controlsVisible = true
    }

    fun togglePlayback() {
        if (exoPlayer.isPlaying) exoPlayer.pause()
        else if (!prefs.pauseRequireContinue || !prefs.smartPause) exoPlayer.play()
    }

    /**
     * Three-state A-B repeat, driven by a single button:
     * nothing set → drop marker A → drop marker B (and start looping) → clear.
     * A B that lands too close to A just moves A instead of creating a
     * useless quarter-second loop.
     */
    fun cycleAbRepeat() {
        val start = loopStartMs
        val end = loopEndMs
        when {
            start == null -> {
                loopStartMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                loopEndMs = null
            }
            end == null -> {
                val candidate = exoPlayer.currentPosition
                if (candidate > start + 700L) {
                    loopEndMs = candidate
                    exoPlayer.seekTo(start)
                    exoPlayer.play()
                } else {
                    loopStartMs = candidate.coerceAtLeast(0L)
                }
            }
            else -> {
                loopStartMs = null
                loopEndMs = null
            }
        }
        controlsVisible = true
    }

    // Speed is a player-level parameter: applied once here and re-applied
    // whenever the stored value changes. ExoPlayer keeps the pitch intact,
    // so 0.75x sounds slower without sounding lower.
    LaunchedEffect(exoPlayer, prefs.playbackSpeed) {
        try {
            exoPlayer.setPlaybackSpeed(prefs.playbackSpeed)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Known words and the speech engine are both shared singletons: they
    // are prepared once here and torn down politely when the player leaves.
    LaunchedEffect(Unit) {
        // Challenge/blur is shared with the book reader; load its persisted
        // reader-blur-enabled state before any cue is rendered.
        StudyModeState.load(context)
        KnownWordsStore.ensureLoaded(context)
        TtsSpeaker.ensureInit(context)
    }
    DisposableEffect(Unit) {
        onDispose { TtsSpeaker.stop() }
    }

    LaunchedEffect(skipBadgeNonce) {
        if (skipBadgeNonce > 0) {
            skipBadgeVisible = true
            delay(750)
            skipBadgeVisible = false
        }
    }
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3800)
            controlsVisible = false
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlayingChange: Boolean) {
                isPlaying = isPlayingChange
                if (!isPlayingChange) controlsVisible = true
            }

            override fun onTracksChanged(tracks: Tracks) {
                audioTrackGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_READY) sourceReady = true
            }

            // Keep listening after STATE_READY as a stream can fail later
            // while buffering or when a signed segment URL expires.
            override fun onPlayerError(error: PlaybackException) {
                val detail = listOfNotNull(
                    error.errorCodeName.takeIf { it.isNotBlank() },
                    error.message?.takeIf { it.isNotBlank() && it != error.errorCodeName }
                ).joinToString(": ")
                currentOnPlaybackError.value(detail.ifBlank { "Playback failed" })
            }
        }
        exoPlayer.addListener(listener)
        isPlaying = exoPlayer.isPlaying
        if (exoPlayer.playbackState == androidx.media3.common.Player.STATE_READY) sourceReady = true
        audioTrackGroups = exoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Online tab: switching quality swaps the URL of the SAME video, so the
    // current position is carried over instead of restarting the clip.
    var lastResumeKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(exoPlayer, videoUri, streamAudioUri, streamMimeType) {
        // The fallback web player loads its clip by id (see above).
        val nativePlayer = exoPlayer as? ExoPlayer ?: return@LaunchedEffect
        videoUri?.let {
            val sameVideo = resumeStateKey != null && lastResumeKey == resumeStateKey
            val carryOverMs = if (sameVideo) nativePlayer.currentPosition else 0L
            val carryOverPlaying = if (sameVideo) nativePlayer.playWhenReady else true
            lastResumeKey = resumeStateKey
            val mediaItem = if (streamMimeType != null) {
                MediaItem.Builder().setUri(it).setMimeType(streamMimeType).build()
            } else {
                MediaItem.fromUri(it)
            }
            if (streamAudioUri != null) {
                val progressiveFactory = ProgressiveMediaSource.Factory(networkDataSourceFactory)
                val videoSource = progressiveFactory.createMediaSource(mediaItem)
                val audioSource = progressiveFactory.createMediaSource(MediaItem.fromUri(streamAudioUri))
                nativePlayer.setMediaSource(MergingMediaSource(videoSource, audioSource))
            } else {
                nativePlayer.setMediaItem(mediaItem)
            }
            val savedPos = if (sameVideo) carryOverMs else prefs.savedPosition(videoStateKey)
            val wasPlaying = if (sameVideo) carryOverPlaying else prefs.savedWasPlaying(videoStateKey)
            val readyListener = object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == androidx.media3.common.Player.STATE_READY) {
                        if (savedPos > 0) nativePlayer.seekTo(savedPos)
                        nativePlayer.playWhenReady = wasPlaying
                        nativePlayer.removeListener(this)
                    }
                }

                // Without this the listener leaked forever whenever a file
                // failed to open (it was only removed on STATE_READY).
                override fun onPlayerError(error: PlaybackException) {
                    nativePlayer.removeListener(this)
                }
            }
            nativePlayer.addListener(readyListener)
            nativePlayer.prepare()
            try {
                nativePlayer.setPlaybackSpeed(prefs.playbackSpeed)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Startup watchdog (Online tab): a proxy that accepts the connection but
    // never sends data leaves ExoPlayer buffering forever without an error.
    // Treat "not ready after startupTimeoutMs" as a failure of this source.
    LaunchedEffect(exoPlayer, videoUri, streamAudioUri, startupTimeoutMs) {
        if (startupTimeoutMs <= 0L || videoUri == null) return@LaunchedEffect
        delay(startupTimeoutMs)
        if (!sourceReady && exoPlayer.playerError == null) {
            currentOnPlaybackError.value("Timed out: the stream did not start within ${startupTimeoutMs / 1000} s")
        }
    }

    val activity = remember { context.findActivity() }
    // «چرخش صفحه»: the one rotate handler, shared by every place the
    // switch lives — the fullscreen 3-dot cluster, the below-video 3-dot
    // cluster and the corner glass button. The activity handles orientation
    // changes itself (manifest configChanges), so playback — ExoPlayer or
    // the embed WebView — keeps running through the turn.
    val rotateScreen = {
        activity?.let { act ->
            val configLandscape = act.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            act.requestedOrientation = OrientationToggle.next(act.requestedOrientation, configLandscape)
        }
        controlsVisible = true
    }
    DisposableEffect(isFullScreen) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        if (isFullScreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            controller?.let {
                it.hide(WindowInsetsCompat.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.let {
                it.show(WindowInsetsCompat.Type.navigationBars())
                it.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.let {
                it.show(WindowInsetsCompat.Type.navigationBars())
                it.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    // ── Playback ticker ──
    LaunchedEffect(exoPlayer, autoPauseAtTime, videoUri) {
        // `armed` only becomes true once the position has been seen BEFORE
        // the target. Right after seekTo() ExoPlayer may briefly still
        // report the OLD position (already past the target when jumping
        // BACK to the previous subtitle); without this guard the player
        // paused instantly and the previous-subtitle button never arrived.
        var armed = false
        var lastSaveAt = 0L
        while (isActive) {
            val pos = exoPlayer.currentPosition
            positionMs = pos
            bufferedMs = exoPlayer.bufferedPosition
            durationMs = if (exoPlayer.duration > 0) exoPlayer.duration else 0L
            currentTime = pos / 1000.0
            autoPauseAtTime?.let { target ->
                if (currentTime < target) armed = true
                if (armed && exoPlayer.isPlaying && currentTime >= target) {
                    exoPlayer.pause()
                    autoPauseAtTime = null
                    skipNextAutoScroll = true
                }
            }
            // A-B repeat: jump back to A shortly before B so the loop is
            // seamless. Only while playing — pausing inside the range must
            // not fight the user by seeking under their finger.
            val loopFrom = loopStartMs
            val loopTo = loopEndMs
            if (loopFrom != null && loopTo != null && loopTo > loopFrom &&
                exoPlayer.isPlaying && pos >= loopTo - 80L
            ) {
                exoPlayer.seekTo(loopFrom)
                positionMs = loopFrom
            }
            // Periodic save so the resume point survives the process being
            // killed in the background (onDispose never runs on a kill).
            val now = System.currentTimeMillis()
            if (videoUri != null && now - lastSaveAt > 4000L) {
                lastSaveAt = now
                prefs.savePlayback(videoStateKey, pos, exoPlayer.isPlaying)
            }
            delay(200)
        }
    }

    val activeIndex = remember(subEnList, currentTime) {
        subEnList.indexOfFirst { it.start <= currentTime && it.end >= currentTime }
    }
    val activeJsonIndex = remember(jsonPackage, currentTime) {
        jsonPackage?.subtitles?.indexOfFirst { s ->
            s.start != null && s.end != null && s.start!! <= currentTime && currentTime <= s.end!!
        } ?: -1
    }
    val jsonModeActive = jsonPackage != null && jsonPackage.subtitles.isNotEmpty()
    val isAutoStoppingActive = autoPauseAtTime != null

    // A revealed line stays revealed only until the next one starts.
    LaunchedEffect(activeIndex, activeJsonIndex) {
        if (revealCurrent) revealCurrent = false
    }

    // ── Coverage report ──
    // "How much of this film can I already follow?" is the most motivating
    // number in language learning, and it also ranks what to learn next.
    val coverageTexts = remember(subEnList, jsonPackage) {
        val jsonTexts = jsonPackage?.subtitles?.map { it.english }?.filter { it.isNotBlank() } ?: emptyList()
        if (jsonTexts.isNotEmpty()) jsonTexts else subEnList.map { it.text }
    }
    val knownWords = KnownWordsStore.words
    val coverage = remember(coverageTexts, knownWords) {
        KnownWordsStore.computeCoverage(coverageTexts)
    }

    LaunchedEffect(activeIndex, activeJsonIndex, jsonModeActive, isAutoStoppingActive) {
        if (isAutoStoppingActive) return@LaunchedEffect
        // Never fight the user: skip auto-follow while a drag is in progress
        // or within a few seconds of a manual scroll.
        if (listState.isScrollInProgress && !isAutoScrolling) return@LaunchedEffect
        if (System.currentTimeMillis() - lastUserScrollAt < 4000L) return@LaunchedEffect

        val target = if (jsonModeActive && activeJsonIndex >= 0) activeJsonIndex
        else if (activeIndex >= 0 && subEnList.isNotEmpty()) activeIndex
        else -1
        if (target < 0) return@LaunchedEffect
        if (skipNextAutoScroll) {
            skipNextAutoScroll = false
            return@LaunchedEffect
        }
        isAutoScrolling = true
        if (onlineStudio) {
            // Online cue cards: keep the active card centred on screen.
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == target }) {
                listState.scrollToItem(target)
            }
            val placed = withTimeoutOrNull(400L) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target } }
                    .filterNotNull()
                    .first()
            }
            if (placed != null) {
                val info = listState.layoutInfo
                val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                val delta = placed.offset + placed.size / 2f - viewportCenter
                if (kotlin.math.abs(delta) > 1f) listState.animateScrollBy(delta)
            }
        } else {
            listState.animateScrollToItem(target)
        }
        isAutoScrolling = false
    }

    // ── Lifecycle ──
    // Previously the player was released BOTH by the lifecycle observer
    // (ON_DESTROY) and by onDispose, and a separate DisposableEffect read
    // exoPlayer.currentPosition after the release to save the resume
    // position — so the position was regularly lost. Now everything happens
    // exactly once, in the right order: save, then release.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer, videoStateKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (videoUri != null) {
                    prefs.savePlayback(videoStateKey, exoPlayer.currentPosition, exoPlayer.isPlaying)
                }
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (videoUri != null) {
                prefs.savePlayback(videoStateKey, exoPlayer.currentPosition, exoPlayer.isPlaying)
            }
            exoPlayer.release()
        }
    }

    val currentEn = subEnList.find { it.start <= currentTime && it.end >= currentTime }
    val currentFa = subFaList.find { it.start <= currentTime && it.end >= currentTime }

    // ── JSON subtitle resolution (priority: JSON > imported files) ──
    // Synchronized by TIMESTAMP first, then by matching English text, then
    // by subtitle ID, then by list index — so JSON packages with or without
    // timing data both stay in sync with playback.
    val currentJson = remember(jsonPackage, currentTime, currentEn, activeIndex) {
        jsonPackage?.let { pkg ->
            pkg.subtitles.firstOrNull { s ->
                s.start != null && s.end != null && s.start!! <= currentTime && currentTime <= s.end!!
            } ?: currentEn?.let { en ->
                pkg.subtitles.firstOrNull { s -> s.english.trim().equals(en.text.trim(), ignoreCase = true) }
            } ?: if (activeIndex >= 0) {
                pkg.subtitles.firstOrNull { s -> s.id != null && s.id == (activeIndex + 1).toString() }
            } else null
                ?: pkg.subtitles.getOrNull(activeIndex)
        }
    }

    // Smart-pause overlay targets: follow the JSON learning package when
    // that is what is displayed, otherwise the imported EN/FA subtitles.
    val currentJsonDisplayIndex = currentJson?.let { cj -> jsonPackage?.subtitles?.indexOfFirst { it === cj } ?: -1 } ?: -1
    val prevJsonPaused = jsonPackage?.subtitles?.getOrNull(currentJsonDisplayIndex - 1)
    val currentJsonPaused = currentJson

    // Whatever English line is on screen right now can be spoken aloud.
    val speakText = (currentJson?.english?.takeIf { it.isNotBlank() } ?: currentEn?.text)
        ?.takeIf { it.isNotBlank() }

    val smartPause = prefs.smartPause
    val showChrome = controlsVisible || !isPlaying
    // Smart pause is meant to be a BARE frame you can study: no transport
    // bar, no progress line, nothing across the bottom of the picture. The
    // clock is kept (as a small floating pill) because knowing where you
    // are in the file is not visual noise. The normal mode keeps the full
    // seek bar.
    val showTransportBar = !smartPause

    // In listen mode nothing is shown until the learner asks for the line.
    val subtitlesHiddenForListen = listenMode && !revealCurrent

    // The cluster folds itself away with the chrome, so it never reappears
    // expanded on the next tap.
    LaunchedEffect(showChrome) {
        if (!showChrome) showToolCluster = false
    }

    // ── "Loop this line" ──
    // The most useful loop for a learner is the sentence they are hearing
    // right now, so it gets a one-tap shortcut instead of two manual
    // markers. Resolved from the JSON package first, then the EN track.
    val loopLineRange: Pair<Double, Double>? = currentJson?.let { j ->
        j.start?.let { s -> j.end?.let { e -> s to e } }
    } ?: currentEn?.let { it.start to it.end }

    fun loopCurrentLine() {
        val range = loopLineRange ?: return
        val startMs = (range.first * 1000).toLong().coerceAtLeast(0L)
        val endMs = (range.second * 1000).toLong()
        if (endMs <= startMs) return
        loopStartMs = startMs
        loopEndMs = endMs
        exoPlayer.seekTo(startMs)
        exoPlayer.play()
        controlsVisible = true
    }

    val loopBannerText = when {
        loopStartMs != null && loopEndMs != null ->
            "A " + formatClock(loopStartMs ?: 0L) + "  →  B " + formatClock(loopEndMs ?: 0L)
        loopStartMs != null -> "A " + formatClock(loopStartMs ?: 0L) + "  →  B ?"
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { containerHeightPx = it.size.height.toFloat().coerceAtLeast(1f) }
    ) {
        // The video surface itself (gestures, seek bar, overlay buttons) stays
        // LTR so raw x offsets and drag gestures never fight an RTL mirror.
        // The list below inherits the page's RTL when the app language is FA.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            // The toon skin frames the video like a cartoon TV set: 28dp
            // rounded corners, a 3dp ink bezel and a hard offset shadow. In
            // fullscreen the frame is dropped so nothing eats into the
            // picture.
            val toonTv = isAnimeDesign() && !isFullScreen
            val tvShape = RoundedCornerShape(28.dp)
            // Online learning player: a rounded 16:9 card with a soft shadow
            // instead of the draggable split (never taller than ~58 % of
            // the screen, so landscape still leaves room for the transcript).
            val studioCard = onlineStudio && !isFullScreen
            val studioShape = RoundedCornerShape(OnlineStudioTokens.radius)
            val density = LocalDensity.current
            val studioMaxHeight = if (containerHeightPx > 1f) with(density) { (containerHeightPx * 0.58f).toDp() } else Dp.Unspecified
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (studioCard) {
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .heightIn(max = studioMaxHeight)
                                .then(if (toonTv) Modifier else Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 6.dp))
                                .aspectRatio(16f / 9f)
                        } else {
                            Modifier.weight(if (isFullScreen) 1f else prefs.videoWeight)
                        }
                    )
                    .then(
                        if (toonTv) {
                            Modifier
                                .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 6.dp)
                                .inkShadow(offset = 4.dp, shape = tvShape)
                                .clip(tvShape)
                        } else if (studioCard) {
                            Modifier
                                .shadow(elevation = 10.dp, shape = studioShape)
                                .clip(studioShape)
                        } else Modifier
                    )
                    .background(Color.Black)
                    .then(if (toonTv) Modifier.inkBorder(3.dp, tvShape) else Modifier)
                    .onGloballyPositioned { containerWidth = it.size.width }
            ) {
            if (toonTv) {
                // The "REC" pill: pure set dressing that sells the TV frame.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .zIndex(2f)
                ) {
                    ToonChip(text = "● REC", selected = true, fill = AnimeColors.Sunny)
                }
            }
            // The built-in ExoPlayer controller is always off now: the app
            // draws its own controls, so the experience (and the seek bar)
            // is identical in smart-pause and normal mode. Before, the
            // smart-pause mode had no timeline at all.
            val webPlayer = exoPlayer as? YouTubeWebPlayer
            if (webPlayer != null) {
                // Fallback mode: the YouTube embed. The gesture layer and the
                // app's own controls sit on top, exactly as with ExoPlayer.
                key(webPlayer) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            (webPlayer.view.parent as? android.view.ViewGroup)?.removeView(webPlayer.view)
                            webPlayer.view
                        }
                    )
                }
            } else {
                key(exoPlayer) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            PlayerView(context).apply {
                                player = exoPlayer
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                subtitleView?.visibility = android.view.View.GONE
                            }
                        },
                        update = { playerView -> playerView.useController = false }
                    )
                }
            }

            if (isAudio && videoUri != null) {
                AudioArtworkStage(
                    albumArt = albumArtBitmap,
                    isPlaying = isPlaying,
                    isFullScreen = isFullScreen,
                    fileName = videoFileName
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isFullScreen) 140.dp else 92.dp)
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black.copy(alpha = 0.32f))
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val jsonCurrent = currentJson
                        if (subtitlesHiddenForListen && (jsonCurrent != null || currentEn != null || currentFa != null)) {
                            PlayerTextPill(
                                text = strings.revealLine,
                                contentDescription = null,
                                onClick = { revealCurrent = true },
                                active = true,
                                height = 34.dp
                            )
                        } else if (jsonCurrent != null && (jsonCurrent.english.isNotBlank() || !jsonCurrent.translation.isNullOrBlank())) {
                            SubtitleOverlayContent(
                                english = jsonCurrent.english,
                                translation = jsonCurrent.translation,
                                enColor = subtitleOverlayColorEn,
                                faColor = subtitleOverlayColorFa,
                                shadow = overlayTextShadow,
                                enFont = subtitleFamilyEn,
                                faFont = subtitleFamilyFa,
                                fontScale = 1.05f,
                                onWordClick = { word ->
                                    exoPlayer.pause()
                                    onWordClick(word, jsonCurrent.english, jsonCurrent.translation)
                                },
                                onSentenceClick = {
                                    exoPlayer.pause()
                                    onSentenceClick(jsonCurrent.english, jsonCurrent.translation)
                                }
                            )
                        } else if (currentEn != null || currentFa != null) {
                            SubtitleOverlayContent(
                                english = currentEn?.text,
                                translation = currentFa?.text,
                                enColor = subtitleOverlayColorEn,
                                faColor = subtitleOverlayColorFa,
                                shadow = overlayTextShadow,
                                enFont = subtitleFamilyEn,
                                faFont = subtitleFamilyFa,
                                fontScale = 1.05f,
                                onWordClick = { word ->
                                    exoPlayer.pause()
                                    onWordClick(word, currentEn?.text, currentFa?.text)
                                },
                                onSentenceClick = {
                                    exoPlayer.pause()
                                    currentEn?.let { onSentenceClick(it.text, currentFa?.text) }
                                }
                            )
                        } else {
                            Text(
                                text = strings.audioPlayingHint,
                                color = Color.White.copy(alpha = 0.45f),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // ── Gesture layer ──
            // Single tap: pause/resume in smart-pause mode (unchanged
            // behavior), otherwise reveal/hide the controls.
            // Double tap on the left/right half: skip by the configured
            // amount, now with a visible badge.
            if (videoUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(containerWidth, smartPause, prefs.skipSeconds, prefs.pauseRequireContinue) {
                            detectTapGestures(
                                onTap = {
                                    if (smartPause) togglePlayback() else controlsVisible = !controlsVisible
                                },
                                onDoubleTap = { offset ->
                                    val isLeft = offset.x < (containerWidth / 2)
                                    performSkip(if (isLeft) -prefs.skipSeconds else prefs.skipSeconds)
                                }
                            )
                        }
                )
            }

            // Dim layer while paused in smart-pause mode.
            if (!isPlaying && videoUri != null && smartPause) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (prefs.pauseDim) Modifier.background(Color.Black.copy(alpha = 0.55f)) else Modifier)
                        .then(if (!prefs.pauseRequireContinue) Modifier.clickable { exoPlayer.play() } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    @Composable
                    fun FreeFormButton(
                        transformState: MutableState<ButtonTransform>,
                        saveKey: String,
                        modifier: Modifier = Modifier,
                        content: @Composable () -> Unit
                    ) {
                        var transform by transformState
                        Box(
                            modifier = modifier
                                .offset { IntOffset(transform.x.roundToInt(), transform.y.roundToInt()) }
                                .graphicsLayer {
                                    scaleX = transform.scale
                                    scaleY = transform.scale
                                    rotationZ = transform.rotation
                                }
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, rotation ->
                                        transform = transform.copy(
                                            x = transform.x + pan.x,
                                            y = transform.y + pan.y,
                                            scale = (transform.scale * zoom).coerceIn(0.4f, 2.5f),
                                            rotation = transform.rotation + rotation
                                        )
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragEnd = {
                                            transformState.value = transform
                                            saveTransform(saveKey, transform)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            transform = transform.copy(
                                                x = transform.x + dragAmount.x,
                                                y = transform.y + dragAmount.y
                                            )
                                        }
                                    )
                                }
                        ) { content() }
                    }

                    // The smart-pause gear is movable like the other overlay
                    // buttons and is NEVER hidden by the "hide subtitles &
                    // buttons" option — it is the way back into the panel.
                    // Normal-view settings live only in the transcript FAB.
                    // The movable gear remains a fullscreen smart-pause aid.
                    if (isFullScreen) {
                        FreeFormButton(smartPauseGearTransform, "smart_pause_gear", modifier = Modifier.align(Alignment.TopCenter)) {
                            PlayerGlassButton(
                                icon = Icons.Default.Settings,
                                contentDescription = strings.smartPauseSettingsCd,
                                onClick = { showOverlaySettings = !showOverlaySettings },
                                modifier = Modifier.padding(top = 10.dp),
                                size = 38.dp
                            )
                        }
                    }

                    val prevSubEn = if (activeIndex > 0) subEnList[activeIndex - 1] else null
                    val prevFaText = prevSubEn?.let { subtitleAlignmentMap[it]?.text }
                    val prevStartEnd: Pair<Double, Double>? = prevJsonPaused?.let { j ->
                        j.start?.let { s -> j.end?.let { e -> s to e } }
                    } ?: prevSubEn?.let { it.start to it.end }
                    val currentStartEnd: Pair<Double, Double>? = currentJsonPaused?.let { j ->
                        j.start?.let { s -> j.end?.let { e -> s to e } }
                    } ?: currentEn?.let { it.start to it.end }
                    val prevLineText = prevJsonPaused?.let {
                        it.translation?.takeIf { t -> t.isNotBlank() } ?: it.english
                    } ?: (prevFaText ?: prevSubEn?.text ?: "")
                    val currentLineText = currentJsonPaused?.let {
                        it.translation?.takeIf { t -> t.isNotBlank() } ?: it.english
                    } ?: (currentFa?.text ?: currentEn?.text ?: "")
                    // With hide-UI ON the buttons and subtitle text
                    // disappear, but Continue must stay when tap-to-resume is
                    // off, otherwise playback could never be resumed.
                    val showSmartButtons = !prefs.pauseHideUi || prefs.pauseRequireContinue

                    if (showSmartButtons && isFullScreen && (currentEn != null || currentJsonPaused != null)) {
                        FreeFormButton(continueTransform, "continue") {
                            GradientButton(
                                text = strings.resumePlayBtn,
                                onClick = { exoPlayer.play() },
                                icon = Icons.Default.PlayArrow
                            )
                        }
                        if (!prefs.pauseHideUi && prevStartEnd != null) {
                            val (prevStart, prevEnd) = prevStartEnd
                            FreeFormButton(autoPrevTransform, "auto_prev") {
                                SmartPauseChip(
                                    title = strings.autoStopPrevSubtitle,
                                    subtitle = prevLineText,
                                    subtitleColor = if (prevFaText != null) AccentAmber else Color.White.copy(alpha = 0.65f),
                                    onClick = {
                                        autoPauseAtTime = prevEnd
                                        exoPlayer.seekTo((prevStart * 1000).toLong())
                                        exoPlayer.play()
                                    }
                                )
                            }
                        }
                        if (!prefs.pauseHideUi && currentStartEnd != null) {
                            val (currentStart, currentEnd) = currentStartEnd
                            FreeFormButton(autoCurrentTransform, "auto_current") {
                                SmartPauseChip(
                                    title = strings.autoStopCurrentSubtitle,
                                    subtitle = currentLineText,
                                    subtitleColor = if (currentFa != null) AccentAmber else Color.White.copy(alpha = 0.65f),
                                    onClick = {
                                        autoPauseAtTime = currentEnd
                                        exoPlayer.seekTo((currentStart * 1000).toLong())
                                        exoPlayer.play()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── Overlay subtitles ──
            // The box keeps a dark backdrop so bright overlay colors stay
            // readable in every theme; the text also carries a soft shadow.
            // With hide-UI ON the overlay text is hidden while paused so the
            // frame can be inspected precisely, and in listen mode it is
            // hidden until the learner reveals it.
            if (prefs.subtitlesEnabled && !isAudio && !(prefs.pauseHideUi && !isPlaying) && !subtitlesHiddenForListen) {
                val jsonCurrent = currentJson
                val overlayEnglish: String?
                val overlayTranslation: String?
                if (jsonCurrent != null && (jsonCurrent.english.isNotBlank() || !jsonCurrent.translation.isNullOrBlank())) {
                    overlayEnglish = jsonCurrent.english
                    overlayTranslation = jsonCurrent.translation
                } else {
                    overlayEnglish = currentEn?.text
                    overlayTranslation = currentFa?.text
                }
                if (!overlayEnglish.isNullOrBlank() || !overlayTranslation.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                // No bar in smart pause, so the subtitles sit
                                // low on the frame instead of leaving a gap
                                // for controls that are never drawn.
                                bottom = (prefs.bottomPadding + if (showChrome && showTransportBar) 74f else 12f).dp,
                                start = 20.dp,
                                end = 20.dp
                            )
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black.copy(alpha = 0.66f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                            .clickable { togglePlayback() }
                            .padding(vertical = 10.dp, horizontal = 16.dp)
                    ) {
                        SubtitleOverlayContent(
                            english = overlayEnglish,
                            translation = overlayTranslation,
                            enColor = subtitleOverlayColorEn,
                            faColor = subtitleOverlayColorFa,
                            shadow = overlayTextShadow,
                            enFont = subtitleFamilyEn,
                            faFont = subtitleFamilyFa,
                            fontScale = prefs.fontSizeFactor,
                            onWordClick = { word ->
                                exoPlayer.pause()
                                onWordClick(word, overlayEnglish, overlayTranslation)
                            },
                            onSentenceClick = {
                                exoPlayer.pause()
                                overlayEnglish?.let { onSentenceClick(it, overlayTranslation) }
                            }
                        )
                    }
                }
            }

            // Listen mode: one button to reveal the line you just heard.
            // It sits where the subtitle would be, so the eye does not have
            // to hunt for it.
            if (videoUri != null && !isAudio && prefs.subtitlesEnabled && subtitlesHiddenForListen &&
                (currentEn != null || currentJson != null || currentFa != null)
            ) {
                PlayerTextPill(
                    text = strings.revealLine,
                    contentDescription = null,
                    onClick = { revealCurrent = true },
                    active = true,
                    height = 34.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            bottom = (prefs.bottomPadding + if (showChrome && showTransportBar) 74f else 12f).dp
                        )
                )
            }

            // ── Top chrome ──
            // ChromeFade instead of AnimatedVisibility on purpose: inside a
            // Box nested in a Column, Kotlin resolves the ColumnScope
            // overload of AnimatedVisibility, which the layout DSL marker
            // then rejects.
            // Normal playback stays intentionally clean. Its controls are in
            // the transcript FAB; these picture overlays exist only fullscreen.
            if (isFullScreen) {
            ChromeFade(
                visible = showChrome,
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                PlayerScrim(fromTop = true, height = 88.dp)
            }
            ChromeFade(
                visible = showChrome,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Online tab: floating extras such as «⭐ ذخیره ویدیو».
                    overlayActions?.invoke()
                    // Online tab: the manual rotation switch stays on the
                    // picture (not in the cluster) so it is one tap away in
                    // fullscreen. The activity handles orientation changes
                    // itself (manifest configChanges), so playback — ExoPlayer
                    // or the embed WebView — keeps running through the turn.
                    if (showOrientationToggle && videoUri != null) {
                        PlayerGlassButton(
                            icon = Icons.Default.ScreenRotation,
                            contentDescription = strings.rotateScreenCd,
                            onClick = rotateScreen,
                            modifier = Modifier.testTag("btnToggleOrientation"),
                            active = isFullScreen
                        )
                    }
                    // Only two things stay permanently on the picture: the
                    // speed / study pill, which is what a learner touches
                    // most, and one button that unfolds everything else.
                    PlayerTextPill(
                        text = formatSpeedLabel(prefs.playbackSpeed),
                        contentDescription = strings.studyToolsCd,
                        onClick = {
                            showSpeedPanel = !showSpeedPanel
                            controlsVisible = true
                        },
                        active = showSpeedPanel || abs(prefs.playbackSpeed - 1f) > 0.01f || listenMode
                    )
                    PlayerToolCluster(
                        expanded = showToolCluster,
                        onToggle = {
                            showToolCluster = !showToolCluster
                            controlsVisible = true
                        },
                        toggleDescription = strings.moreControlsCd,
                        actions = listOf(
                            PlayerToolAction(
                                icon = Icons.Default.Settings,
                                contentDescription = strings.playerSettingsCd,
                                onClick = {
                                    showSubtitleSettings = true
                                    showToolCluster = false
                                },
                                active = showSubtitleSettings
                            ),
                            PlayerToolAction(
                                icon = Icons.Default.Refresh,
                                contentDescription = strings.abRepeatCd,
                                onClick = { cycleAbRepeat() },
                                active = loopStartMs != null
                            ),
                            PlayerToolAction(
                                icon = Icons.Default.Subtitles,
                                contentDescription = strings.showSubtitlesTitle,
                                onClick = { prefs.subtitlesEnabled = !prefs.subtitlesEnabled },
                                active = prefs.subtitlesEnabled
                            ),
                            // Focus mode hides the top bar/tabs, the import
                            // section and the time-sync cards (MainScreen).
                            PlayerToolAction(
                                icon = if (focusMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = strings.focusModeCd,
                                onClick = onFocusModeToggle,
                                active = focusMode
                            ),
                            PlayerToolAction(
                                icon = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isFullScreen) strings.exitFullscreenCd else strings.fullscreenCd,
                                onClick = { onFullScreenToggle(!isFullScreen) },
                                active = isFullScreen
                            )
                        ) + if (showOrientationToggle && videoUri != null) {
                            // Online tab: «چرخش صفحه» lives in the clusters
                            // now, not in the extra-actions sheet.
                            listOf(
                                PlayerToolAction(
                                    icon = Icons.Default.ScreenRotation,
                                    contentDescription = strings.rotateScreenCd,
                                    onClick = rotateScreen
                                )
                            )
                        } else emptyList(),
                    )
                }
            }

            // Study drawer, opened from the speed pill.
            if (videoUri != null) {
                ChromeFade(
                    visible = showChrome && showSpeedPanel,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    StudyPanel(
                        currentSpeed = prefs.playbackSpeed,
                        strings = strings,
                        canLoopLine = loopLineRange != null,
                        listenMode = listenMode,
                        coverage = coverage,
                        onSelectSpeed = { value ->
                            prefs.playbackSpeed = value
                            controlsVisible = true
                        },
                        onLoopLine = {
                            loopCurrentLine()
                            showSpeedPanel = false
                        },
                        onToggleListen = {
                            listenMode = !listenMode
                            prefs.raw.edit().putBoolean("listen_mode", listenMode).apply()
                            revealCurrent = false
                            controlsVisible = true
                        },
                        onMarkKnown = { word -> KnownWordsStore.markKnown(context, word) },
                        canSpeak = speakText != null,
                        onSpeak = { speakText?.let { TtsSpeaker.speak(context, it) } },
                        onSpeakSlow = { speakText?.let { TtsSpeaker.speak(context, it, slow = true) } },
                        modifier = Modifier.padding(top = 62.dp, end = 12.dp)
                    )
                }
                // Live A-B state, and the fastest way out of it. The top-left
                // corner is free now that the gear moved into the cluster.
                ChromeFade(
                    visible = showChrome && loopStartMs != null,
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    PlayerTextPill(
                        text = loopBannerText,
                        contentDescription = strings.clearAbLoopCd,
                        onClick = {
                            loopStartMs = null
                            loopEndMs = null
                            controlsVisible = true
                        },
                        active = true,
                        height = 32.dp,
                        modifier = Modifier.padding(top = 14.dp, start = 12.dp)
                    )
                }
            }

            }

            // Smart pause keeps the time — and only the time.
            if (videoUri != null && !showTransportBar) {
                ChromeFade(
                    visible = showChrome,
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    SmartPauseClock(positionMs = positionMs, durationMs = durationMs)
                }
            }

            // ── Bottom chrome: transport controls (normal mode only) ──
            if (videoUri != null && showTransportBar) {
                ChromeFade(
                    visible = showChrome,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        PlayerScrim(fromTop = false, height = 132.dp)
                        PlayerControls(
                            isPlaying = isPlaying,
                            positionMs = positionMs,
                            durationMs = durationMs,
                            bufferedMs = bufferedMs,
                            skipSeconds = prefs.skipSeconds,
                            playPauseDescription = strings.resumeCd,
                            loopStartMs = loopStartMs,
                            loopEndMs = loopEndMs,
                            onPlayPause = {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                controlsVisible = true
                            },
                            onSkip = { delta -> performSkip(delta) },
                            onSeek = { target ->
                                exoPlayer.seekTo(target)
                                positionMs = target
                                controlsVisible = true
                            }
                        )
                    }
                }
                // Thin progress line while the chrome is hidden, so the
                // position is always visible without covering the frame.
                if (!showChrome && durationMs > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.14f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((positionMs.toFloat() / durationMs).coerceIn(0.002f, 1f))
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.tertiary,
                                            MaterialTheme.colorScheme.primary
                                        )
                                    )
                                )
                        )
                    }
                }
            }

            // The skip badge is feedback for a gesture, not chrome, so it
            // stays available in smart pause too.
            if (videoUri != null) {
                SeekPulseBadge(
                    visible = skipBadgeVisible,
                    forward = skipBadgeForward,
                    seconds = prefs.skipSeconds,
                    modifier = Modifier.align(
                        if (skipBadgeForward) Alignment.CenterEnd else Alignment.CenterStart
                    )
                )
            }

            // ── Smart-pause gear panel ──
            if (showOverlaySettings) {
                Dialog(
                    onDismissRequest = { showOverlaySettings = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(
                        modifier = Modifier.padding(18.dp).fillMaxWidth(0.94f),
                        shape = if (isNeobrutalismDesign()) {
                            RoundedCornerShape(0.dp)
                        } else {
                            RoundedCornerShape(26.dp)
                        },
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = if (isNeobrutalismDesign()) 0.dp else 6.dp,
                        border = if (isNeobrutalismDesign()) {
                            BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                        } else {
                            null
                        }
                    ) {
                        Column(modifier = Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.smartPausePanelTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                SoftIconButton(
                                    icon = Icons.Default.Close,
                                    contentDescription = strings.close,
                                    onClick = { showOverlaySettings = false },
                                    size = 34.dp
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.resetPositionsTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                GradientButton(
                                    text = strings.resetBtn,
                                    icon = Icons.Default.Refresh,
                                    onClick = {
                                        continueTransform.value = ButtonTransform(0f, -80f, 1f, 0f)
                                        autoPrevTransform.value = ButtonTransform(0f, 0f, 1f, 0f)
                                        autoCurrentTransform.value = ButtonTransform(0f, 80f, 1f, 0f)
                                        smartPauseGearTransform.value = ButtonTransform(0f, 0f, 1f, 0f)
                                        saveTransform("continue", continueTransform.value)
                                        saveTransform("auto_prev", autoPrevTransform.value)
                                        saveTransform("auto_current", autoCurrentTransform.value)
                                        saveTransform("smart_pause_gear", smartPauseGearTransform.value)
                                        showOverlaySettings = false
                                    }
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 14.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                            )
                            PanelToggle(
                                title = strings.pauseDimTitle,
                                description = strings.pauseDimDesc,
                                checked = prefs.pauseDim,
                                onCheckedChange = { prefs.pauseDim = it }
                            )
                            PanelToggle(
                                title = strings.pauseHideUiTitle,
                                description = strings.pauseHideUiDesc,
                                checked = prefs.pauseHideUi,
                                onCheckedChange = { prefs.pauseHideUi = it }
                            )
                            PanelToggle(
                                title = strings.pauseRequireContinueTitle,
                                description = strings.pauseRequireContinueDesc,
                                checked = prefs.pauseRequireContinue,
                                onCheckedChange = { prefs.pauseRequireContinue = it }
                            )
                        }
                    }
                }
            }

            if (showSubtitleSettings) {
                PlayerSettingsSheet(
                    prefs = prefs,
                    strings = strings,
                    audioTracks = audioTrackGroups,
                    onSelectAudioTrack = { group, trackIndex ->
                        try {
                            // Force-select this audio track via the stable
                            // Player.trackSelectionParameters API.
                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                .buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                .addOverride(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                                .build()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    subEnOffset = subEnOffset,
                    subFaOffset = subFaOffset,
                    jsonOffset = jsonPackage
                        ?.takeIf { it.subtitles.any { sub -> sub.start != null && sub.end != null } }
                        ?.let { jsonOffset },
                    onShiftSubEn = onShiftSubEn,
                    onShiftSubFa = onShiftSubFa,
                    onResetSubEn = { onShiftSubEn(-subEnOffset) },
                    onResetSubFa = { onShiftSubFa(-subFaOffset) },
                    onShiftJson = onShiftJson,
                    onResetJson = onResetJson,
                    offsetText = { value -> offsetText(value) },
                    canSaveSrt = subFaList.isNotEmpty(),
                    onSaveSrt = onSaveSrt,
                    focusMode = focusMode,
                    onToggleFocus = onFocusModeToggle,
                    // The Online tab's action drawer owns the «کارهای دیگر
                    // پخش‌کننده» card (and the quality picker); everything
                    // else — including the subtitle display + sync cards —
                    // stays in this sheet, as it always was.
                    includeExtraActions = !onlineStudio,
                    fabActions = fabActions,
                    onDismiss = { showSubtitleSettings = false }
                )
            }

            // Online tab: the «کارهای دیگر پخش‌کننده» opened from the
            // action drawer's own button — the extra player actions that
            // used to close the 3-dot settings sheet.
            if (showOnlineExtraActions && onlineStudio) {
                PlayerExtraActionsSheet(
                    actions = fabActions,
                    strings = strings,
                    onDismiss = { showOnlineExtraActions = false }
                )
            }
            }
        }

        if (!isFullScreen) {
            if (!onlineStudio) {
                // Draggable divider: the video/list ratio is now up to the user
                // (and remembered), instead of a hardcoded 38/62 split.
                SplitDragHandle(
                    active = isSplitDragging,
                    modifier = Modifier.draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            prefs.videoWeight = prefs.videoWeight + delta / containerHeightPx
                        },
                        onDragStarted = { isSplitDragging = true },
                        onDragStopped = { isSplitDragging = false }
                    )
                )
            }

            // One pane beneath the video, shared by both tabs: the Online
            // tab tops it with the collapsible action drawer (کشوی ابزار)
            // that replaced the old «متن تعاملی» header, and BOTH tabs keep
            // the cue-card transcript — the Online tab got its subtitle
            // cards back beneath the drawer — plus the floating 3-dot
            // cluster in the lower-right corner (the subtitles toggle, the
            // player settings and focus mode live there, so they never
            // depend on the drawer being open).
            Box(
                modifier = Modifier
                    .weight(if (onlineStudio) 1f else 1f - prefs.videoWeight)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val jsonList = jsonPackage?.subtitles

                    // Online tab: the drawer sits between the video and the
                    // cue cards. Focus mode keeps it hidden, like before.
                    if (onlineStudio && !focusMode && onlineDrawerActions != null) {
                        OnlineActionDrawer(
                            qualities = streamQualities,
                            selectedQuality = selectedStreamQuality,
                            onSelectQuality = onSelectStreamQuality,
                            onOpenExtraActions = { showOnlineExtraActions = true },
                            actions = onlineDrawerActions,
                            strings = strings,
                            jsonAttached = jsonPackage != null,
                            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                        )
                    }

                singleTranslateError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }

                if (jsonList != null && jsonList.isNotEmpty()) {
                    // One cue-card renderer is shared by online and local
                    // playback. This removes the dense local-only row shell
                    // (metadata pills, wide action buttons and a second
                    // sync panel) while keeping every study action on the
                    // card that owns its subtitle.
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().fadingEdges(),
                        // Leave a clear landing area for the transcript
                        // settings FAB below the last cue.
                        contentPadding = PaddingValues(top = 4.dp, bottom = 84.dp)
                    ) {
                        items(jsonList) { jsonSub: JsonSubtitle ->
                            val cueIndex = jsonList.indexOf(jsonSub)
                            val cueStart = jsonSub.start
                            val cueEnd = jsonSub.end
                            val grammarTopic = jsonSub.lesson?.grammar?.takeIf { it.isNotBlank() }
                            val lessonLabel = when {
                                grammarTopic != null -> grammarTopic
                                jsonSub.lesson != null || jsonSub.words.isNotEmpty() -> strings.onlineCueLessonBadge
                                // A local JSON row has always opened its
                                // lesson; keep that as the small cue-card badge.
                                !onlineStudio -> strings.lessonSheetTitle
                                else -> null
                            }
                            CueCard(
                                timeLabel = cueStart?.let { formatTime(it) },
                                englishText = jsonSub.english,
                                translationText = jsonSub.translation,
                                isActive = cueStart != null && cueEnd != null && cueStart <= currentTime && currentTime <= cueEnd,
                                enColor = studioColorEn,
                                faColor = studioColorFa,
                                enFont = subtitleFamilyEn,
                                faFont = subtitleFamilyFa,
                                strings = strings,
                                onSeek = {
                                    if (cueStart != null) {
                                        val cueKey = "json-${jsonSub.id ?: cueIndex}-${cueStart}-${cueEnd}"
                                        autoPauseAtTime = if (cueEnd != null && cuePauseAtEndKey == cueKey) {
                                            (cueEnd - 0.1).coerceAtLeast(cueStart)
                                        } else null
                                        exoPlayer.seekTo((cueStart * 1000).toLong())
                                        exoPlayer.play()
                                    }
                                },
                                onReplay = if (cueStart != null && cueEnd != null) {
                                    {
                                        val cueKey = "json-${jsonSub.id ?: cueIndex}-${cueStart}-${cueEnd}"
                                        autoPauseAtTime = if (cuePauseAtEndKey == cueKey) {
                                            (cueEnd - 0.1).coerceAtLeast(cueStart)
                                        } else null
                                        exoPlayer.seekTo((cueStart * 1000).toLong())
                                        exoPlayer.play()
                                    }
                                } else null,
                                onToggleLoop = if (cueStart != null && cueEnd != null) {
                                    {
                                        val startMs = (cueStart * 1000).toLong()
                                        val endMs = (cueEnd * 1000).toLong()
                                        if (loopStartMs == startMs && loopEndMs == endMs) {
                                            loopStartMs = null; loopEndMs = null
                                        } else {
                                            loopStartMs = startMs; loopEndMs = endMs
                                            exoPlayer.seekTo(startMs); exoPlayer.play()
                                        }
                                    }
                                } else null,
                                loopActive = cueStart != null && cueEnd != null &&
                                    loopStartMs == (cueStart * 1000).toLong() && loopEndMs == (cueEnd * 1000).toLong(),
                                onTogglePauseAtEnd = if (cueStart != null && cueEnd != null) {
                                    {
                                        val cueKey = "json-${jsonSub.id ?: cueIndex}-${cueStart}-${cueEnd}"
                                        if (cuePauseAtEndKey == cueKey) {
                                            cuePauseAtEndKey = null
                                            autoPauseAtTime = null
                                        } else {
                                            cuePauseAtEndKey = cueKey
                                            autoPauseAtTime = (cueEnd - 0.1).coerceAtLeast(cueStart)
                                        }
                                    }
                                } else null,
                                pauseAtEndActive = cuePauseAtEndKey == "json-${jsonSub.id ?: cueIndex}-${cueStart}-${cueEnd}",
                                onWordClick = { word ->
                                    exoPlayer.pause()
                                    onWordClick(word, jsonSub.english, jsonSub.translation)
                                },
                                onAddToLeitner = onAddSentenceToLeitner?.let { add ->
                                    { add(jsonSub, cueStart?.let { formatTime(it) } ?: "", cueIndex) }
                                },
                                leitnerSaved = LeitnerCard.normalizeFront(jsonSub.english) in savedSentenceTexts,
                                lessonLabel = lessonLabel,
                                onLesson = lessonLabel?.let {
                                    {
                                        exoPlayer.pause()
                                        onSentenceClick(jsonSub.english, jsonSub.translation)
                                    }
                                },
                                studyLabels = studyLabels,
                                focusKey = "sub-${jsonSub.id ?: cueIndex}"
                            )
                        }
                    }
                } else if (subEnList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Default.Subtitles,
                            title = strings.loadSubtitleHint
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().fadingEdges(),
                        // Leave a clear landing area for the transcript
                        // settings FAB below the last cue.
                        contentPadding = PaddingValues(top = 4.dp, bottom = 84.dp)
                    ) {
                        itemsIndexed(subEnList) { index, enSub ->
                            val faMatch = subtitleAlignmentMap[enSub]
                            val cueTime = formatTime(enSub.start)
                            CueCard(
                                timeLabel = cueTime,
                                englishText = enSub.text,
                                translationText = faMatch?.text,
                                isActive = enSub.start <= currentTime && enSub.end >= currentTime,
                                enColor = studioColorEn,
                                faColor = studioColorFa,
                                enFont = subtitleFamilyEn,
                                faFont = subtitleFamilyFa,
                                strings = strings,
                                onSeek = {
                                    val cueKey = "sub-$index-${enSub.start}-${enSub.end}"
                                    autoPauseAtTime = if (cuePauseAtEndKey == cueKey) {
                                        (enSub.end - 0.1).coerceAtLeast(enSub.start)
                                    } else null
                                    exoPlayer.seekTo((enSub.start * 1000).toLong())
                                    exoPlayer.play()
                                },
                                onReplay = {
                                    val cueKey = "sub-$index-${enSub.start}-${enSub.end}"
                                    autoPauseAtTime = if (cuePauseAtEndKey == cueKey) {
                                        (enSub.end - 0.1).coerceAtLeast(enSub.start)
                                    } else null
                                    exoPlayer.seekTo((enSub.start * 1000).toLong())
                                    exoPlayer.play()
                                },
                                onToggleLoop = {
                                    val startMs = (enSub.start * 1000).toLong()
                                    val endMs = (enSub.end * 1000).toLong()
                                    if (loopStartMs == startMs && loopEndMs == endMs) {
                                        loopStartMs = null; loopEndMs = null
                                    } else {
                                        loopStartMs = startMs; loopEndMs = endMs
                                        exoPlayer.seekTo(startMs); exoPlayer.play()
                                    }
                                },
                                loopActive = loopStartMs == (enSub.start * 1000).toLong() && loopEndMs == (enSub.end * 1000).toLong(),
                                onTogglePauseAtEnd = {
                                    val cueKey = "sub-$index-${enSub.start}-${enSub.end}"
                                    if (cuePauseAtEndKey == cueKey) {
                                        cuePauseAtEndKey = null
                                        autoPauseAtTime = null
                                    } else {
                                        cuePauseAtEndKey = cueKey
                                        autoPauseAtTime = (enSub.end - 0.1).coerceAtLeast(enSub.start)
                                    }
                                },
                                pauseAtEndActive = cuePauseAtEndKey == "sub-$index-${enSub.start}-${enSub.end}",
                                onWordClick = { word ->
                                    exoPlayer.pause()
                                    onWordClick(word, enSub.text, faMatch?.text)
                                },
                                // Existing local rows already had a speaker;
                                // the compact cue header keeps it. The extra
                                // Leitner shortcut stays available online,
                                // where it was part of the original flow.
                                onAddToLeitner = if (onlineStudio) {
                                    onAddSentenceToLeitner?.let { add ->
                                        {
                                            add(
                                                JsonSubtitle(start = enSub.start, end = enSub.end, english = enSub.text, translation = faMatch?.text),
                                                cueTime,
                                                index
                                            )
                                        }
                                    }
                                } else null,
                                leitnerSaved = LeitnerCard.normalizeFront(enSub.text) in savedSentenceTexts,
                                // Translation is an exception action: show
                                // it only when the cue still lacks one,
                                // instead of placing an AI button on every
                                // local subtitle card.
                                onTranslate = if (faMatch?.text.isNullOrBlank()) { { onTranslateSubtitle(index) } } else null,
                                isTranslating = isTranslatingSingle && translatingIndex == index,
                                translateEnabled = !isTranslatingSingle,
                                onStopTranslation = onStopTranslation,
                                studyLabels = studyLabels,
                                focusKey = if (onlineStudio) "cue-$index" else null
                            )
                        }
                    }
                }
            }

                // The normal transcript keeps the identical three-dot
                // player cluster rather than a one-off settings FAB. It
                // opens leftward from the lower-right corner, exactly like
                // the player chrome — the very cluster the Online tab's
                // drawer pane reuses below the video.
                BelowVideoToolCluster(
                    strings = strings,
                    expanded = showTranscriptToolCluster,
                    onToggleExpanded = { showTranscriptToolCluster = !showTranscriptToolCluster },
                    settingsOpen = showSubtitleSettings,
                    onOpenSettings = {
                        showSubtitleSettings = true
                        showTranscriptToolCluster = false
                    },
                    abRepeatActive = loopStartMs != null,
                    onCycleAbRepeat = { cycleAbRepeat() },
                    subtitlesEnabled = prefs.subtitlesEnabled,
                    onToggleSubtitles = { prefs.subtitlesEnabled = !prefs.subtitlesEnabled },
                    // The Online tab's rotation switch, moved here from the
                    // extra-actions sheet (the Video tab never passes it).
                    showRotation = showOrientationToggle && videoUri != null,
                    onRotate = rotateScreen,
                    focusMode = focusMode,
                    onToggleFocus = onFocusModeToggle,
                    onEnterFullscreen = { onFullScreenToggle(true) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                )
            } // shared below-video pane: Online drawer above the cue cards
        }
    }
}

/**
 * The three-dot cluster that floats at the lower-right corner of the pane
 * beneath the video — the one pane both tabs share (the Online tab's cue
 * cards sit under its action drawer), so both keep the same one-tap access
 * to the player settings, the subtitles toggle, AB repeat, focus mode and
 * fullscreen. (The video-player settings themselves — subtitle display and
 * sync included — live in the sheet this cluster opens, not in the drawer.)
 */
@Composable
private fun BelowVideoToolCluster(
    strings: AppStrings,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    settingsOpen: Boolean,
    onOpenSettings: () -> Unit,
    abRepeatActive: Boolean,
    onCycleAbRepeat: () -> Unit,
    subtitlesEnabled: Boolean,
    onToggleSubtitles: () -> Unit,
    /** Online tab: show the «چرخش صفحه» action in this cluster too. */
    showRotation: Boolean = false,
    onRotate: () -> Unit = {},
    focusMode: Boolean,
    onToggleFocus: () -> Unit,
    onEnterFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlayerToolCluster(
        expanded = expanded,
        onToggle = onToggleExpanded,
        toggleDescription = strings.moreControlsCd,
        actions = listOf(
            PlayerToolAction(
                icon = Icons.Default.Settings,
                contentDescription = strings.playerSettingsCd,
                onClick = onOpenSettings,
                active = settingsOpen
            ),
            PlayerToolAction(
                icon = Icons.Default.Refresh,
                contentDescription = strings.abRepeatCd,
                onClick = onCycleAbRepeat,
                active = abRepeatActive
            ),
            PlayerToolAction(
                icon = Icons.Default.Subtitles,
                contentDescription = strings.showSubtitlesTitle,
                onClick = onToggleSubtitles,
                active = subtitlesEnabled
            ),
            PlayerToolAction(
                icon = if (focusMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = strings.focusModeCd,
                onClick = onToggleFocus,
                active = focusMode
            ),
            PlayerToolAction(
                icon = Icons.Default.Fullscreen,
                contentDescription = strings.fullscreenCd,
                onClick = onEnterFullscreen
            )
        ) + if (showRotation) {
            listOf(
                PlayerToolAction(
                    icon = Icons.Default.ScreenRotation,
                    contentDescription = strings.rotateScreenCd,
                    onClick = onRotate
                )
            )
        } else emptyList(),
        modifier = modifier
    )
}

/**
 * The only piece of chrome smart pause keeps across the picture: a small
 * floating clock, so you always know where you are without a control bar.
 */
@Composable
private fun SmartPauseClock(positionMs: Long, durationMs: Long) {
    Box(
        modifier = Modifier
            .padding(top = 18.dp)
            .clip(RoundedCornerShape(40.dp))
            .background(Color.Black.copy(alpha = 0.42f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(40.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatClock(positionMs),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            if (durationMs > 0) {
                Text(
                    text = "  /  " + formatClock(durationMs),
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/** Overlay subtitle text (video overlay and audio backdrop share this). */
@Composable
private fun SubtitleOverlayContent(
    english: String?,
    translation: String?,
    enColor: Color,
    faColor: Color,
    shadow: Shadow,
    enFont: FontFamily,
    faFont: FontFamily,
    fontScale: Float,
    onWordClick: (String) -> Unit,
    onSentenceClick: () -> Unit
) {
    // Source language weight -> English overlay, target -> translation overlay
    // (default pair EN->FA; user swaps weights if they swapped languages).
    val enWeight = LanguageWeightState.sourceWeight.weight
    val faWeight = LanguageWeightState.targetWeight.weight
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (!english.isNullOrBlank()) {
            ClickableWordText(
                text = english,
                style = MaterialTheme.typography.titleLarge.copy(
                    color = enColor,
                    shadow = shadow,
                    fontFamily = enFont,
                    fontWeight = enWeight,
                    textAlign = TextAlign.Center,
                    fontSize = MaterialTheme.typography.titleLarge.fontSize * fontScale
                ),
                highlightColor = enColor,
                onWordClick = onWordClick,
                onTextClick = onSentenceClick,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        if (!translation.isNullOrBlank()) {
            Text(
                text = translation,
                color = faColor,
                style = MaterialTheme.typography.titleMedium.copy(
                    shadow = shadow,
                    fontFamily = faFont,
                    fontWeight = faWeight,
                    fontSize = MaterialTheme.typography.titleMedium.fontSize * fontScale,
                    textDirection = translation.autoTextDirection()
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Overlay chip for the smart-pause "replay previous/current line" buttons. */
@Composable
private fun SmartPauseChip(
    title: String,
    subtitle: String,
    subtitleColor: Color,
    onClick: () -> Unit
) {
    val neo = isNeobrutalismDesign()
    Box(
        modifier = Modifier
            .clip(if (neo) RoundedCornerShape(0.dp) else RoundedCornerShape(16.dp))
            .background(
                if (neo) MaterialTheme.colorScheme.surfaceContainerLowest
                else Color.Black.copy(alpha = 0.5f)
            )
            .border(
                width = if (neo) 1.5.dp else 1.dp,
                color = if (neo) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.22f),
                shape = if (neo) RoundedCornerShape(0.dp) else RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PanelToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Timestamp label. Locale.US keeps the digits latin (the Persian locale
 * rendered them as Persian numerals) and hours are finally supported, so a
 * 90-minute film no longer shows "90:00".
 */
fun formatTime(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0L)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secs = total % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, secs)
    }
}

fun alignSubtitles(enList: List<SubtitleEntry>, faList: List<SubtitleEntry>): Map<SubtitleEntry, SubtitleEntry> {
    val sortedFaList = faList.sortedBy { it.start }
    val matched = mutableMapOf<SubtitleEntry, SubtitleEntry>()
    for (enSub in enList) {
        var bestFa: SubtitleEntry? = null
        var bestDiff = Double.MAX_VALUE
        for (faSub in sortedFaList) {
            val diff = Math.abs(faSub.start - enSub.start)
            if (diff < 1.0) {
                if (diff < bestDiff) {
                    bestDiff = diff
                    bestFa = faSub
                }
            } else if (faSub.start > enSub.start + 1.0) {
                break
            }
        }
        if (bestFa != null) {
            matched[enSub] = bestFa
        }
    }
    return matched
}
