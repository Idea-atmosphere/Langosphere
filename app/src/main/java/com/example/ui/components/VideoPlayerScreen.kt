package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.logic.KnownWordsStore
import com.example.logic.TtsSpeaker
import com.example.logic.autoTextDirection
import com.example.model.JsonSubtitle
import com.example.model.JsonSubtitlePackage
import com.example.model.SubtitleEntry
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.AppStrings
import com.example.ui.theme.SubtitleColorState
import com.example.ui.theme.isNeobrutalismDesign
import com.example.ui.theme.SubtitleEnOnLight
import com.example.ui.theme.SubtitleFaOnLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
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
    onSentenceClick: (sentence: String, translation: String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val strings = remember(appLanguage) { AppStrings(appLanguage) }
    val prefs = rememberPlayerPrefs()

    // Locale.US matters here: with the Persian locale the default
    // String.format produces Persian digits, which then could not be parsed
    // back by toDoubleOrNull() in the "exact time" field.
    fun offsetText(v: Double): String {
        val formatted = String.format(Locale.US, "%.2f", v)
        return if (strings.isEn) formatted else formatted.replace("-", "منفی ")
    }

    val videoStateKey = remember(videoUri) { "video_state_${videoUri?.hashCode() ?: 0}" }

    // ── Playback clock ──
    var currentTime by remember { mutableStateOf(0.0) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var bufferedMs by remember { mutableStateOf(0L) }

    val isAudio = remember(videoUri, videoFileName) {
        val name = videoFileName.lowercase()
        val uriStr = videoUri?.toString()?.lowercase() ?: ""
        name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".wav") || name.endsWith(".aac") || name.endsWith(".ogg") || name.endsWith(".flac") ||
            uriStr.endsWith(".mp3") || uriStr.endsWith(".m4a") || uriStr.endsWith(".wav") || uriStr.endsWith(".aac") || uriStr.endsWith(".ogg") || uriStr.endsWith(".flac") ||
            uriStr.contains("audio")
    }
    var albumArtBitmap by remember(videoUri) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(videoUri) {
        if (videoUri != null) {
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

    // ── Subtitle color resolution (light/dark themes) ──
    // Two sets of defaults on purpose:
    //  • Overlay colors (on the video / audio backdrop, always dark):
    //    bright white + amber with a soft shadow for contrast.
    //  • List colors (on the THEME background below the player): adaptive,
    //    so subtitles stay readable in light mode too. A custom
    //    SubtitleColorState choice always overrides both.
    val isDarkUi = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val subtitleOverlayColorEn = SubtitleColorState.colorEn ?: Color.White
    val subtitleOverlayColorFa = SubtitleColorState.colorFa ?: AccentAmber
    val subtitleListColorEn = SubtitleColorState.colorEn ?: if (isDarkUi) Color.White else SubtitleEnOnLight
    val subtitleListColorFa = SubtitleColorState.colorFa ?: if (isDarkUi) AccentAmber else SubtitleFaOnLight
    val overlayTextShadow = Shadow(
        color = Color.Black.copy(alpha = 0.6f),
        offset = Offset(0f, 1.5f),
        blurRadius = 6f
    )
    val listTextShadow = Shadow(
        color = if (isDarkUi) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.85f),
        offset = Offset(0f, 1f),
        blurRadius = 4f
    )
    val customFontFamilyEn = rememberCustomFontFamily(prefs.customFontPathEn)
    val customFontFamilyFa = rememberCustomFontFamily(prefs.customFontPathFa)
    val subtitleFamilyEn = fontFamilyFor(prefs.fontEn, customFontFamilyEn)
    val subtitleFamilyFa = fontFamilyFor(prefs.fontFa, customFontFamilyFa)

    var showSubtitleSettings by remember { mutableStateOf(false) }
    var isSyncExpanded by remember { mutableStateOf(false) }
    var isJsonSyncExpanded by remember { mutableStateOf(false) }
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
    val exoPlayer = remember { ExoPlayer.Builder(context).build().apply { playWhenReady = true } }
    var isPlaying by remember { mutableStateOf(false) }
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
        }
        exoPlayer.addListener(listener)
        isPlaying = exoPlayer.isPlaying
        audioTrackGroups = exoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(videoUri) {
        videoUri?.let {
            exoPlayer.setMediaItem(MediaItem.fromUri(it))
            val savedPos = prefs.savedPosition(videoStateKey)
            val wasPlaying = prefs.savedWasPlaying(videoStateKey)
            val readyListener = object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == androidx.media3.common.Player.STATE_READY) {
                        if (savedPos > 0) exoPlayer.seekTo(savedPos)
                        exoPlayer.playWhenReady = wasPlaying
                        exoPlayer.removeListener(this)
                    }
                }

                // Without this the listener leaked forever whenever a file
                // failed to open (it was only removed on STATE_READY).
                override fun onPlayerError(error: PlaybackException) {
                    exoPlayer.removeListener(this)
                }
            }
            exoPlayer.addListener(readyListener)
            exoPlayer.prepare()
            try {
                exoPlayer.setPlaybackSpeed(prefs.playbackSpeed)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val activity = remember { context.findActivity() }
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
        listState.animateScrollToItem(target)
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (isFullScreen) 1f else prefs.videoWeight)
                    .background(Color.Black)
                    .onGloballyPositioned { containerWidth = it.size.width }
            ) {
            // The built-in ExoPlayer controller is always off now: the app
            // draws its own controls, so the experience (and the seek bar)
            // is identical in smart-pause and normal mode. Before, the
            // smart-pause mode had no timeline at all.
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
                                text = if (strings.isEn) "Reveal the line" else "نمایش جمله",
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
                    FreeFormButton(smartPauseGearTransform, "smart_pause_gear", modifier = Modifier.align(Alignment.TopCenter)) {
                        PlayerGlassButton(
                            icon = Icons.Default.Settings,
                            contentDescription = strings.smartPauseSettingsCd,
                            onClick = { showOverlaySettings = !showOverlaySettings },
                            modifier = Modifier.padding(top = 10.dp),
                            size = 38.dp
                        )
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
                    text = if (strings.isEn) "Reveal the line" else "نمایش جمله",
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
                    // Only two things stay permanently on the picture: the
                    // speed / study pill, which is what a learner touches
                    // most, and one button that unfolds everything else.
                    PlayerTextPill(
                        text = formatSpeedLabel(prefs.playbackSpeed),
                        contentDescription = if (strings.isEn) "Study tools" else "ابزارهای یادگیری",
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
                        toggleDescription = if (strings.isEn) "More controls" else "ابزارهای بیشتر",
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
                                contentDescription = if (strings.isEn) "A-B repeat" else "تکرار A-B",
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
                        )
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
                        isEn = strings.isEn,
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
                        contentDescription = if (strings.isEn) "Clear A-B loop" else "پاک کردن حلقه A-B",
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
                    onShiftSubEn = onShiftSubEn,
                    onShiftSubFa = onShiftSubFa,
                    offsetText = { value -> offsetText(value) },
                    canSaveSrt = subFaList.isNotEmpty(),
                    onSaveSrt = onSaveSrt,
                    onDismiss = { showSubtitleSettings = false }
                )
            }
            }
        }

        if (!isFullScreen) {
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

            Column(
                modifier = Modifier
                    .weight(1f - prefs.videoWeight)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val jsonList = jsonPackage?.subtitles
                SectionHeader(
                    title = strings.allSubtitlesListTitle,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Coverage at a glance, without opening a panel.
                            if (coverage.totalTokens > 0) {
                                StatusPill(
                                    text = if (strings.isEn) "Known ${coverage.percent}%" else "بلدی ٪${coverage.percent}",
                                    tone = if (coverage.percent >= 90) PillTone.Positive else PillTone.Neutral
                                )
                            }
                            if (listenMode) {
                                StatusPill(
                                    text = if (strings.isEn) "Listen" else "گوش کن",
                                    tone = PillTone.Warning
                                )
                            }
                            if (jsonList != null && jsonList.isNotEmpty()) {
                                StatusPill(text = strings.jsonActiveBadge, tone = PillTone.Accent)
                            }
                        }
                    }
                )
                singleTranslateError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }

                if (jsonList != null && jsonList.isNotEmpty()) {
                    // JSON priority rendering: when a JSON learning package
                    // is loaded its subtitles replace the normal EN/FA list
                    // (no duplicated rendering). The EN/FA lists stay in
                    // memory as a fallback for when the JSON is removed.
                    if (!focusMode) {
                        SyncPanel(
                            title = strings.jsonSyncRowTitle,
                            expanded = isJsonSyncExpanded,
                            expandLabel = if (isJsonSyncExpanded) strings.collapseSync else strings.expandSync,
                            onToggle = { isJsonSyncExpanded = !isJsonSyncExpanded }
                        ) {
                            if (jsonList.any { it.start != null && it.end != null }) {
                                SubtitleShiftControls(
                                    title = strings.subJsonLabel,
                                    offsetLabel = strings.shiftValueLabel(offsetText(jsonOffset)),
                                    currentOffset = jsonOffset,
                                    onShift = onShiftJson,
                                    footer = {
                                        Surface(
                                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                            contentColor = MaterialTheme.colorScheme.error,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = jsonOffset != 0.0,
                                            onClick = onResetJson
                                        ) {
                                            Text(
                                                text = strings.jsonResetBtn,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 10.dp)
                                            )
                                        }
                                    }
                                )
                            } else {
                                Text(
                                    text = strings.jsonSyncNoTimings,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().fadingEdges(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp)
                    ) {
                        items(jsonList) { jsonSub: JsonSubtitle ->
                            JsonSubtitleRow(
                                timeLabel = jsonSub.start?.let { formatTime(it) },
                                idLabel = jsonSub.id?.let { "ID $it" },
                                level = jsonSub.level,
                                difficulty = jsonSub.difficulty,
                                englishText = jsonSub.english,
                                translationText = jsonSub.translation,
                                isActive = jsonSub.start != null && jsonSub.end != null &&
                                    jsonSub.start!! <= currentTime && currentTime <= jsonSub.end!!,
                                glass = prefs.glassmorphism,
                                enColor = subtitleListColorEn,
                                faColor = subtitleListColorFa,
                                textShadow = listTextShadow,
                                enFont = subtitleFamilyEn,
                                faFont = subtitleFamilyFa,
                                strings = strings,
                                onSeek = {
                                    jsonSub.start?.let { start ->
                                        exoPlayer.seekTo((start * 1000).toLong())
                                        exoPlayer.play()
                                    }
                                },
                                onWordClick = { word ->
                                    exoPlayer.pause()
                                    onWordClick(word, jsonSub.english, jsonSub.translation)
                                },
                                onSentenceClick = { onSentenceClick(jsonSub.english, jsonSub.translation) }
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
                    if (!focusMode) {
                        SyncPanel(
                            title = strings.syncSettingsRowTitle,
                            expanded = isSyncExpanded,
                            expandLabel = if (isSyncExpanded) strings.collapseSync else strings.expandSync,
                            onToggle = { isSyncExpanded = !isSyncExpanded }
                        ) {
                            SubtitleShiftControls(
                                title = strings.langCodeEn,
                                offsetLabel = strings.shiftValueLabel(offsetText(subEnOffset)),
                                currentOffset = subEnOffset,
                                onShift = onShiftSubEn
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            SubtitleShiftControls(
                                title = strings.langCodeFa,
                                offsetLabel = strings.shiftValueLabel(offsetText(subFaOffset)),
                                currentOffset = subFaOffset,
                                onShift = onShiftSubFa
                            )
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().fadingEdges(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp)
                    ) {
                        itemsIndexed(subEnList) { index, enSub ->
                            val faMatch = subtitleAlignmentMap[enSub]
                            SubtitleLineRow(
                                timeLabel = formatTime(enSub.start),
                                englishText = enSub.text,
                                translationText = faMatch?.text,
                                isActive = enSub.start <= currentTime && enSub.end >= currentTime,
                                glass = prefs.glassmorphism,
                                enColor = subtitleListColorEn,
                                faColor = subtitleListColorFa,
                                textShadow = listTextShadow,
                                enFont = subtitleFamilyEn,
                                faFont = subtitleFamilyFa,
                                strings = strings,
                                isTranslating = isTranslatingSingle && translatingIndex == index,
                                translateEnabled = !isTranslatingSingle,
                                onSeek = {
                                    autoPauseAtTime = null
                                    exoPlayer.seekTo((enSub.start * 1000).toLong())
                                    exoPlayer.play()
                                },
                                onPlayWithAutoStop = {
                                    autoPauseAtTime = enSub.end
                                    exoPlayer.seekTo((enSub.start * 1000).toLong())
                                    exoPlayer.play()
                                },
                                onWordClick = { word ->
                                    exoPlayer.pause()
                                    onWordClick(word, enSub.text, faMatch?.text)
                                },
                                onSentenceClick = {
                                    exoPlayer.pause()
                                    onSentenceClick(enSub.text, faMatch?.text)
                                },
                                onTranslate = { onTranslateSubtitle(index) },
                                onStopTranslation = onStopTranslation
                            )
                        }
                    }
                }
            }
        }
    }
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (!english.isNullOrBlank()) {
            ClickableWordText(
                text = english,
                style = MaterialTheme.typography.titleLarge.copy(
                    color = enColor,
                    shadow = shadow,
                    fontFamily = enFont,
                    fontWeight = FontWeight.Bold,
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
                // Centered on the video, but the paragraph direction still
                // must follow the translation's own script.
                style = MaterialTheme.typography.titleMedium.copy(
                    shadow = shadow,
                    fontFamily = faFont,
                    fontWeight = FontWeight.Medium,
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

/** Collapsible glass panel used by the EN/FA and JSON time-sync controls. */
@Composable
private fun SyncPanel(
    title: String,
    expanded: Boolean,
    expandLabel: String,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "syncArrow"
    )
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggle() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = expandLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = arrowRotation }
                )
            }
        }
        AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
            Column(modifier = Modifier.padding(top = 12.dp)) { content() }
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
