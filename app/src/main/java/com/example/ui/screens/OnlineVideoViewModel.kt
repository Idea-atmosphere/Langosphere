package com.example.ui.screens

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.logic.DownloadsSaver
import com.example.logic.InnerTubeClient
import com.example.logic.OnlineCaptionParser
import com.example.logic.OnlineInstanceStore
import com.example.logic.OnlineLibraryStore
import com.example.logic.OnlineVideoRepository
import com.example.logic.OnlineWatchCache
import com.example.logic.SubtitleParser
import com.example.model.OnlineCaptionTrack
import com.example.model.OnlineChannel
import com.example.model.OnlineInstance
import com.example.model.OnlineInstanceKind
import com.example.model.OnlineVideo
import com.example.model.OnlineVideoDetails
import com.example.model.SubtitleEntry
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Instances whose streams Media3 may reject for one clip before the clip is
 * moved to the fallback web player (each instance already gets up to
 * OnlineStreamSelector.MAX_SOURCES_PER_INSTANCE sources).
 */
private const val MAX_PLAYER_INSTANCE_FAILOVERS = 3

/** How the direct YouTube (InnerTube) attempt is named in failure lists. */
private const val DIRECT_FAILURE_HOST = "YouTube (direct)"

/**
 * State holder for the Online tab (YouTube through Invidious / Piped
 * instances). It owns:
 *  * the instance list and the followed channels ([OnlineInstanceStore]);
 *  * the browse state — the combined "latest from followed channels" feed,
 *    a single channel's uploads, search results;
 *  * the player state — the resolved stream, the caption tracks, the
 *    caption that is currently loaded as the English subtitle list, and
 *    an optional second track used as the translated subtitle list.
 *
 * The JSON learning package is NOT held here: attaching a JSON file to an
 * online clip reuses [AppViewModel]'s JSON slot (import / chunks / time
 * shift / export all keep working), so the Online screen simply reads
 * `AppViewModel.jsonSubtitles` and passes it to the shared player.
 */
class OnlineVideoViewModel(application: Application) : AndroidViewModel(application) {
    /*
     * «ذخیره هنگام پخش» — ONE source of truth, shared by the player's
     * top-of-screen switch, the browse tab's watch-cache manager and this
     * ViewModel's own caption persistence. While on, every byte downloaded
     * for viewing is written to the watch cache and every watched caption
     * track (main or translation) is persisted with it; a failed later
     * lookup falls back to that disk copy so the clip keeps its subtitles.
     */
    private val _saveWhilePlaying = MutableStateFlow(OnlineWatchCache.isSaveEnabled(app))
    val saveWhilePlaying: StateFlow<Boolean> = _saveWhilePlaying

    fun setSaveWhilePlaying(enabled: Boolean) {
        if (_saveWhilePlaying.value == enabled) return
        _saveWhilePlaying.value = enabled
        OnlineWatchCache.setSaveEnabled(app, enabled)
    }

    /** The default quality new videos are opened (and saved) with. */
    private val _defaultQualityHeight = MutableStateFlow(OnlineWatchCache.defaultQualityHeight(app))
    val defaultQualityHeight: StateFlow<Int> = _defaultQualityHeight

    fun setDefaultQualityHeight(height: Int) {
        if (_defaultQualityHeight.value == height) return
        _defaultQualityHeight.value = height
        OnlineWatchCache.setDefaultQualityHeight(app, height)
    }

    /** What the watch-cache manager lists, refreshed on demand. */
    private val _cachedVideos = MutableStateFlow<List<OnlineWatchCache.CachedVideo>>(emptyList())
    val cachedVideos: StateFlow<List<OnlineWatchCache.CachedVideo>> = _cachedVideos

    fun refreshCachedVideos() {
        viewModelScope.launch(Dispatchers.IO) {
            _cachedVideos.value = OnlineWatchCache.cachedVideos(app)
        }
    }

    fun deleteCachedVideo(videoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            OnlineWatchCache.deleteVideo(app, videoId)
            _cachedVideos.value = OnlineWatchCache.cachedVideos(app)
        }
    }

    fun clearWatchCache() {
        viewModelScope.launch(Dispatchers.IO) {
            OnlineWatchCache.clearAll(app)
            _cachedVideos.value = emptyList()
        }
    }


    private val app: Context get() = getApplication()
    private val appPrefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    enum class InstanceHealthStatus { UNKNOWN, CHECKING, HEALTHY, FAILED }

    data class InstanceHealth(
        val status: InstanceHealthStatus = InstanceHealthStatus.UNKNOWN,
        val latencyMs: Long? = null,
        val error: String? = null
    )

    /**
     * «تست این اینستنس»: probes ONE instance on demand (the manager's
     * per-row button) and reports the outcome on its own row. Purely
     * diagnostic: unlike the sweep above it never disables anything — the
     * row's switch stays the user's call.
     */
    fun checkInstance(instance: OnlineInstance) {
        viewModelScope.launch(Dispatchers.IO) {
            fun put(health: InstanceHealth) {
                _instanceHealth.value = _instanceHealth.value + (instance.baseUrl to health)
            }
            put(InstanceHealth(InstanceHealthStatus.CHECKING))
            try {
                val probe = OnlineVideoRepository.probeInstance(instance)
                put(InstanceHealth(status = InstanceHealthStatus.HEALTHY, latencyMs = probe.latencyMs))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                put(InstanceHealth(status = InstanceHealthStatus.FAILED, error = e.message ?: e.javaClass.simpleName))
            }
        }
    }

    // Same rule as AppViewModel: the saved in-app language, else the system language.
    private fun strings(): AppStrings {
        val code = appPrefs.getString("app_language", null)
            ?: if (app.resources.configuration.locales.get(0).language == "fa") "fa" else "en"
        return AppStrings(AppLanguage.fromCode(code), app)
    }

    // ── Instances ──
    private val _instances = MutableStateFlow(OnlineInstanceStore.loadInstances(application))
    val instances: StateFlow<List<OnlineInstance>> = _instances

    /** Base URL of the instance that answered most recently — tried first. */
    private val _preferredInstance = MutableStateFlow(OnlineInstanceStore.lastGoodInstance(application))
    val preferredInstance: StateFlow<String?> = _preferredInstance

    private val _instanceHealth = MutableStateFlow<Map<String, InstanceHealth>>(emptyMap())
    val instanceHealth: StateFlow<Map<String, InstanceHealth>> = _instanceHealth
    private val _isCheckingInstances = MutableStateFlow(false)
    val isCheckingInstances: StateFlow<Boolean> = _isCheckingInstances

    // ── Channels ──
    private val _channels = MutableStateFlow(OnlineInstanceStore.loadChannels(application))
    val channels: StateFlow<List<OnlineChannel>> = _channels

    // ── Browse state ──
    /** Which list is shown: the followed-channels feed, one channel, or search results. */
    sealed class Browse {
        object Feed : Browse()
        data class Channel(val channel: OnlineChannel) : Browse()
        data class Search(val query: String) : Browse()
    }

    private val _browse = MutableStateFlow<Browse>(Browse.Feed)
    val browse: StateFlow<Browse> = _browse

    private val _videos = MutableStateFlow<List<OnlineVideo>>(emptyList())
    val videos: StateFlow<List<OnlineVideo>> = _videos
    private val _isLoadingList = MutableStateFlow(false)
    val isLoadingList: StateFlow<Boolean> = _isLoadingList
    private val _listError = MutableStateFlow<String?>(null)
    val listError: StateFlow<String?> = _listError

    /** Continuation token for the current single-channel list (null = no more pages). */
    private var channelContinuation: String? = null
    private val _canLoadMore = MutableStateFlow(false)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore

    /** Channel search results (for the follow dialog). */
    private val _channelResults = MutableStateFlow<List<OnlineChannel>>(emptyList())
    val channelResults: StateFlow<List<OnlineChannel>> = _channelResults
    private val _isSearchingChannels = MutableStateFlow(false)
    val isSearchingChannels: StateFlow<Boolean> = _isSearchingChannels

    // ── Player state ──
    private val _details = MutableStateFlow<OnlineVideoDetails?>(null)
    val details: StateFlow<OnlineVideoDetails?> = _details
    private val _isLoadingVideo = MutableStateFlow(false)
    val isLoadingVideo: StateFlow<Boolean> = _isLoadingVideo
    private val _videoError = MutableStateFlow<String?>(null)
    val videoError: StateFlow<String?> = _videoError

    /**
     * «پخش در حالت جایگزین»: non-null while the current clip plays through
     * the YouTube IFrame embed instead of a direct Piped/Invidious stream.
     * Set automatically when no instance could deliver a working stream, or
     * by the user with one tap. [details] and the captions stay as they were,
     * so subtitles and JSON lessons keep following the video.
     */
    private val _webFallbackVideo = MutableStateFlow<OnlineVideo?>(null)
    val webFallbackVideo: StateFlow<OnlineVideo?> = _webFallbackVideo

    /** Why direct playback was given up (shown under the fallback banner); null for a manual switch. */
    private val _webFallbackReason = MutableStateFlow<String?>(null)
    val webFallbackReason: StateFlow<String?> = _webFallbackReason

    /** The fallback web player itself failed too (embedding disabled, offline, …). */
    private val _webFallbackError = MutableStateFlow<String?>(null)
    val webFallbackError: StateFlow<String?> = _webFallbackError

    /** The caption track loaded as the primary (source-language) subtitle list. */
    private val _activeTrack = MutableStateFlow<OnlineCaptionTrack?>(null)
    val activeTrack: StateFlow<OnlineCaptionTrack?> = _activeTrack
    private val _captions = MutableStateFlow<List<SubtitleEntry>>(emptyList())
    val captions: StateFlow<List<SubtitleEntry>> = _captions

    /** Optional second track shown as the translation line under each cue. */
    private val _translationTrack = MutableStateFlow<OnlineCaptionTrack?>(null)
    val translationTrack: StateFlow<OnlineCaptionTrack?> = _translationTrack
    private val _translationCaptions = MutableStateFlow<List<SubtitleEntry>>(emptyList())
    val translationCaptions: StateFlow<List<SubtitleEntry>> = _translationCaptions

    /**
     * A subtitle file imported from the device for the OPEN clip (the action
     * drawer's «زیرنویس اصلی / زیرنویس دوم» buttons). While present it
     * overrides the online caption track of its slot, exactly like a picked
     * file does on the Video tab; the format is remembered so the raw copy
     * can hand back the same .srt/.vtt the user gave us.
     */
    data class ImportedSubtitle(
        val entries: List<SubtitleEntry>,
        val fileName: String,
        val isVtt: Boolean
    )

    private val _importedMain = MutableStateFlow<ImportedSubtitle?>(null)
    val importedMain: StateFlow<ImportedSubtitle?> = _importedMain
    private val _importedTranslation = MutableStateFlow<ImportedSubtitle?>(null)
    val importedTranslation: StateFlow<ImportedSubtitle?> = _importedTranslation

    private val _isLoadingCaptions = MutableStateFlow(false)
    val isLoadingCaptions: StateFlow<Boolean> = _isLoadingCaptions

    /**
     * Where the caption lookup of the open clip stands. The copy-for-AI
     * button uses it to tell "still downloading, wait a moment" apart from
     * "this video has no captions".
     */
    enum class CaptionStatus { IDLE, LOADING, READY, NONE, FAILED }
    private val _captionStatus = MutableStateFlow(CaptionStatus.IDLE)
    val captionStatus: StateFlow<CaptionStatus> = _captionStatus

    /**
     * Every caption track found for the open clip — from the playing
     * instance, another instance or YouTube timedtext — for the track picker.
     * Unlike `details.captions` this is filled in fallback (embed) mode too.
     */
    private val _captionTracks = MutableStateFlow<List<OnlineCaptionTrack>>(emptyList())
    val captionTracks: StateFlow<List<OnlineCaptionTrack>> = _captionTracks

    /**
     * The YouTube id of the clip that is open (or being opened). Online
     * captions and the online JSON lesson slot are keyed by it, so nothing
     * from another clip — or from the Video tab — can show up in the player.
     */
    private val _currentVideoId = MutableStateFlow<String?>(null)
    val currentVideoId: StateFlow<String?> = _currentVideoId

    /** The clip [_captions] belong to (null = nothing loaded yet). */
    @Volatile private var captionsVideoId: String? = null
    /** The clip a running caption lookup is for. */
    @Volatile private var resolvingVideoId: String? = null
    /** Bumped on every reset / lookup, so a late answer for an old request is dropped. */
    @Volatile private var captionGeneration = 0
    private var translationTrackJob: Job? = null

    // ── Hub: Saved / History shelves and the channel "new video" dots ──

    /** The three sections of the Online hub. */
    enum class HubTab { FOLLOWING, SAVED, HISTORY }

    private val _hubTab = MutableStateFlow(HubTab.FOLLOWING)
    val hubTab: StateFlow<HubTab> = _hubTab
    fun selectHubTab(tab: HubTab) { _hubTab.value = tab }

    private val _savedEntries = MutableStateFlow(OnlineLibraryStore.loadSaved(application))
    private val _historyEntries = MutableStateFlow(OnlineLibraryStore.loadHistory(application))
    private val _saved = MutableStateFlow(_savedEntries.value.map { it.video })
    /** Bookmarked clips, newest first. */
    val saved: StateFlow<List<OnlineVideo>> = _saved
    private val _history = MutableStateFlow(_historyEntries.value.map { it.video })
    /** Recently opened clips, newest first. */
    val history: StateFlow<List<OnlineVideo>> = _history

    @Volatile private var channelSeen: Map<String, Long> = OnlineLibraryStore.loadSeen(application)
    private val _channelsWithNew = MutableStateFlow<Set<String>>(emptySet())
    /** Followed channels with an upload the learner has not caught up with. */
    val channelsWithNew: StateFlow<Set<String>> = _channelsWithNew

    private val _jsonLessonIds = MutableStateFlow<Set<String>>(emptySet())
    /** Clips that have an AI-made JSON lesson attached (online_json/<id>.json). */
    val jsonLessonIds: StateFlow<Set<String>> = _jsonLessonIds

    /** One-shot toast text (export / copy / follow confirmations and errors). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    private var listJob: Job? = null
    private var videoJob: Job? = null
    private var captionJob: Job? = null
    private val failedPlaybackInstances = linkedSetOf<String>()
    private var instanceHealthJob: Job? = null
    private var instanceHealthGeneration = 0

    // ── Instance management ──

    /** Adds (or re-enables) an instance typed by the user. Returns false when the address is unusable. */
    fun addInstance(rawUrl: String, kind: OnlineInstanceKind?): Boolean {
        val url = OnlineInstanceStore.normalizeBaseUrl(rawUrl) ?: return false
        val resolvedKind = kind ?: OnlineInstanceStore.detectKind(url)
        _instances.update { list ->
            val without = list.filter { it.baseUrl != url }
            // A user-added instance goes to the top: it is the one they want tried first.
            listOf(OnlineInstance(url, resolvedKind, enabled = true)) + without
        }
        _instanceHealth.update { it - url }
        persistInstances()
        return true
    }

    fun removeInstance(instance: OnlineInstance) {
        _instances.update { it.filter { i -> i.baseUrl != instance.baseUrl } }
        _instanceHealth.update { it - instance.baseUrl }
        if (_preferredInstance.value == instance.baseUrl) _preferredInstance.value = null
        persistInstances()
    }

    fun setInstanceEnabled(instance: OnlineInstance, enabled: Boolean) {
        _instances.update { it.map { i -> if (i.baseUrl == instance.baseUrl) i.copy(enabled = enabled) else i } }
        if (enabled) _instanceHealth.update { it - instance.baseUrl }
        persistInstances()
    }

    fun moveInstanceToTop(instance: OnlineInstance) {
        _instances.update { list -> listOf(instance) + list.filter { it.baseUrl != instance.baseUrl } }
        _preferredInstance.value = instance.baseUrl
        OnlineInstanceStore.rememberGoodInstance(app, instance)
        persistInstances()
    }

    fun resetInstances() {
        OnlineInstanceStore.resetInstances(app)
        _instances.value = OnlineInstanceStore.defaultInstances
        _preferredInstance.value = null
        _instanceHealth.value = emptyMap()
        OnlineVideoRepository.forgetOutages()
    }

    private fun persistInstances() {
        OnlineInstanceStore.saveInstances(app, _instances.value)
        OnlineVideoRepository.forgetOutages()
    }

    /**
     * Probes every configured instance against its real playback endpoint.
     * Failed hosts are disabled immediately; healthy hosts remain in their
     * current order so the user can still choose which one is preferred.
     */
    fun checkInstances() {
        instanceHealthJob?.cancel()
        val generation = ++instanceHealthGeneration
        val snapshot = _instances.value
        if (snapshot.isEmpty()) {
            _isCheckingInstances.value = false
            return
        }
        _instanceHealth.value = snapshot.associate { it.baseUrl to InstanceHealth(InstanceHealthStatus.CHECKING) }
        instanceHealthJob = viewModelScope.launch(Dispatchers.IO) {
            _isCheckingInstances.value = true
            try {
                val gate = Semaphore(4)
                val results = coroutineScope {
                    snapshot.map { instance ->
                        async {
                            gate.withPermit {
                                try {
                                    val probe = OnlineVideoRepository.probeInstance(instance)
                                    instance.baseUrl to InstanceHealth(
                                        status = InstanceHealthStatus.HEALTHY,
                                        latencyMs = probe.latencyMs
                                    )
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    instance.baseUrl to InstanceHealth(
                                        status = InstanceHealthStatus.FAILED,
                                        error = e.message ?: e.javaClass.simpleName
                                    )
                                }
                            }
                        }
                    }.awaitAll()
                }
                _instanceHealth.value = results.toMap()
                val failed = results
                    .filter { it.second.status == InstanceHealthStatus.FAILED }
                    .map { it.first }
                    .toSet()
                if (failed.isNotEmpty()) {
                    _instances.update { list ->
                        list.map { if (it.baseUrl in failed) it.copy(enabled = false) else it }
                    }
                    persistInstances()
                }
                val healthyCount = results.count { it.second.status == InstanceHealthStatus.HEALTHY }
                _message.value = strings().onlineInstancesChecked(
                    total = results.size,
                    playable = healthyCount,
                    disabled = failed.size
                )
            } finally {
                if (generation == instanceHealthGeneration) {
                    _isCheckingInstances.value = false
                }
            }
        }
    }

    /** Remembers the instance that just answered (null = the answer came from the youtube.com feed, nothing to remember). */
    private fun markGood(instance: OnlineInstance?) {
        if (instance == null) return
        if (_preferredInstance.value != instance.baseUrl) {
            _preferredInstance.value = instance.baseUrl
            OnlineInstanceStore.rememberGoodInstance(app, instance)
        }
    }

    private fun errorText(e: Throwable): String = when (e) {
        is OnlineVideoRepository.AllInstancesFailed -> {
            val s = strings()
            val hint = if (e.failures.any { looksLikeBotBlock(it.reason) }) "\n" + s.onlineBotBlockedHint else ""
            s.onlineAllInstancesFailed + hint + "\n" + failureLines(e.failures)
        }
        else -> strings().errorWithMessage(e.message)
    }

    private fun failureLines(failures: List<OnlineVideoRepository.Failure>): String =
        failures.take(6).joinToString("\n") { "• ${it.host}: ${it.reason.take(90)}" }

    /**
     * YouTube rate-limits the shared IP of a public instance and the
     * instance relays the refusal ("Sign in to confirm that you're not a
     * bot", LOGIN_REQUIRED, "Got HTML document, expected JSON"). Nothing on
     * the phone can fix that — the user needs a different instance.
     */
    private fun looksLikeBotBlock(reason: String): Boolean {
        val r = reason.lowercase()
        return r.contains("not a bot") || r.contains("login_required") || r.contains("sign in") ||
            r.contains("expected json") || r.contains("temporarily blocked")
    }

    // ── Channels ──

    fun isFollowed(channelId: String): Boolean = _channels.value.any { it.id == channelId }

    fun followChannel(channel: OnlineChannel) {
        OnlineInstanceStore.followChannel(app, channel)
        _channels.value = OnlineInstanceStore.loadChannels(app)
        _message.value = strings().onlineChannelFollowed(channel.name)
    }

    fun unfollowChannel(channel: OnlineChannel) {
        OnlineInstanceStore.unfollowChannel(app, channel.id)
        _channels.value = OnlineInstanceStore.loadChannels(app)
        _message.value = strings().onlineChannelUnfollowed(channel.name)
        if ((_browse.value as? Browse.Channel)?.channel?.id == channel.id) loadFeed()
    }

    fun restoreDefaultChannels() {
        OnlineInstanceStore.restoreDefaultChannels(app)
        _channels.value = OnlineInstanceStore.loadChannels(app)
        loadFeed()
    }

    /**
     * Follows a channel from a pasted link / id / @handle: `UC…` ids are
     * followed straight away (the name is fetched in the background), while
     * handles are resolved through an instance first.
     */
    fun followFromInput(input: String) {
        val id = OnlineInstanceStore.extractChannelId(input)
        val handle = OnlineInstanceStore.extractChannelHandle(input)
        if (id == null && handle == null) {
            _message.value = strings().onlineChannelLinkInvalid
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val served = if (id != null) {
                    OnlineVideoRepository.channelInfo(_instances.value, id, _preferredInstance.value)
                } else {
                    OnlineVideoRepository.resolveChannelHandle(_instances.value, handle!!, _preferredInstance.value)
                }
                markGood(served.instance)
                followChannel(served.value)
            } catch (e: Exception) {
                if (id != null) {
                    // The id is valid even if no instance answered right now.
                    followChannel(OnlineChannel(id = id, name = id))
                } else {
                    _message.value = errorText(e)
                }
            }
        }
    }

    fun searchChannels(query: String) {
        val q = query.trim()
        if (q.isEmpty()) { _channelResults.value = emptyList(); return }
        viewModelScope.launch(Dispatchers.IO) {
            _isSearchingChannels.value = true
            try {
                val served = OnlineVideoRepository.searchChannels(_instances.value, q, _preferredInstance.value)
                markGood(served.instance)
                _channelResults.value = served.value
            } catch (e: Exception) {
                _channelResults.value = emptyList()
                _message.value = errorText(e)
            } finally {
                _isSearchingChannels.value = false
            }
        }
    }

    fun clearChannelResults() { _channelResults.value = emptyList() }

    // ── Lists ──

    /** Latest uploads across every followed channel, merged newest-first. */
    fun loadFeed() {
        _browse.value = Browse.Feed
        channelContinuation = null
        _canLoadMore.value = false
        listJob?.cancel()
        listJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoadingList.value = true
            _listError.value = null
            try {
                val channels = _channels.value
                if (channels.isEmpty()) { _videos.value = emptyList(); return@launch }
                // A few channels at a time: fourteen simultaneous requests
                // would be rude to a public instance and slow on mobile data.
                val gate = Semaphore(4)
                val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
                val results = coroutineScope {
                    channels.map { ch ->
                        async {
                            gate.withPermit {
                                try {
                                    val served = OnlineVideoRepository.channelVideos(_instances.value, ch.id, null, _preferredInstance.value)
                                    markGood(served.instance)
                                    // Piped's channel items do not carry the uploader id; stamp it.
                                    served.value.videos.take(8).map { v ->
                                        if (v.authorId == null) v.copy(authorId = ch.id, author = v.author.ifBlank { ch.name }) else v
                                    }
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    errors.add(e)
                                    null
                                }
                            }
                        }
                    }.awaitAll()
                }
                val ok = results.filterNotNull()
                if (ok.isEmpty()) {
                    _videos.value = emptyList()
                    // Every channel failed: show *why* (per source), not just "unavailable".
                    val first = errors.firstOrNull()
                    _listError.value = if (first != null) {
                        strings().onlineFeedUnavailable + "\n" + errorText(first)
                    } else {
                        strings().onlineFeedUnavailable
                    }
                    return@launch
                }
                val merged = ok.flatten().distinctBy { it.id }
                // Newest first when the instance gave timestamps; otherwise
                // interleave the channels so no single channel floods the top.
                _videos.value = if (merged.all { it.publishedAt > 0 }) {
                    merged.sortedByDescending { it.publishedAt }
                } else {
                    interleave(ok)
                }
                updateChannelDots(merged)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _listError.value = errorText(e)
            } finally {
                _isLoadingList.value = false
            }
        }
    }

    private fun interleave(lists: List<List<OnlineVideo>>): List<OnlineVideo> {
        val out = mutableListOf<OnlineVideo>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) for (l in lists) l.getOrNull(i)?.let { out.add(it) }
        return out.distinctBy { it.id }
    }

    /** All uploads of one channel (paged). */
    fun openChannel(channel: OnlineChannel) {
        markChannelSeen(channel.id)
        _browse.value = Browse.Channel(channel)
        channelContinuation = null
        _videos.value = emptyList()
        listJob?.cancel()
        listJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoadingList.value = true
            _listError.value = null
            try {
                val served = OnlineVideoRepository.channelVideos(_instances.value, channel.id, null, _preferredInstance.value)
                markGood(served.instance)
                _videos.value = served.value.videos.map { v -> if (v.authorId == null) v.copy(authorId = channel.id) else v }
                channelContinuation = served.value.continuation
                _canLoadMore.value = channelContinuation != null
                // Refresh the channel's name / avatar in the background.
                launch {
                    try {
                        val info = OnlineVideoRepository.channelInfo(_instances.value, channel.id, _preferredInstance.value).value
                        if (info.name.isNotBlank()) {
                            OnlineInstanceStore.updateChannelInfo(app, info)
                            _channels.update { list ->
                                list.map { if (it.id == info.id) it.copy(name = info.name, avatarUrl = info.avatarUrl ?: it.avatarUrl, description = info.description ?: it.description) else it }
                            }
                            _browse.update { b -> if (b is Browse.Channel && b.channel.id == info.id) Browse.Channel(b.channel.copy(name = info.name, avatarUrl = info.avatarUrl ?: b.channel.avatarUrl, description = info.description ?: b.channel.description)) else b }
                        }
                    } catch (e: Exception) { /* cosmetic */ }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _listError.value = errorText(e)
            } finally {
                _isLoadingList.value = false
            }
        }
    }

    fun loadMore() {
        val b = _browse.value as? Browse.Channel ?: return
        val token = channelContinuation ?: return
        if (_isLoadingList.value) return
        listJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoadingList.value = true
            try {
                val served = OnlineVideoRepository.channelVideos(_instances.value, b.channel.id, token, _preferredInstance.value)
                markGood(served.instance)
                _videos.update { (it + served.value.videos).distinctBy { v -> v.id } }
                channelContinuation = served.value.continuation
                _canLoadMore.value = channelContinuation != null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = errorText(e)
            } finally {
                _isLoadingList.value = false
            }
        }
    }

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        // A pasted video link opens the video directly.
        OnlineInstanceStore.extractVideoId(q)?.let { id ->
            if (q.contains("/") || q.length == 11) { openVideoId(id); return }
        }
        _browse.value = Browse.Search(q)
        channelContinuation = null
        _canLoadMore.value = false
        _videos.value = emptyList()
        listJob?.cancel()
        listJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoadingList.value = true
            _listError.value = null
            try {
                val served = OnlineVideoRepository.searchVideos(_instances.value, q, _preferredInstance.value)
                markGood(served.instance)
                _videos.value = served.value.distinctBy { it.id }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _listError.value = errorText(e)
            } finally {
                _isLoadingList.value = false
            }
        }
    }

    // ── Player ──

    fun openVideo(video: OnlineVideo) = openVideoId(video.id, video)

    /** Id of the video being opened / that failed to open (for "open elsewhere" when no instance can play it). */
    val pendingVideoId: String? get() = _currentVideoId.value

    fun openVideoId(videoId: String, placeholder: OnlineVideo? = null) {
        recordHistory(placeholder ?: OnlineVideo(id = videoId, title = ""))
        failedPlaybackInstances.clear()
        clearWebFallback()
        loadVideo(videoId, placeholder)
    }

    /**
     * Media3 proved that every source from this instance is unusable (HTTP
     * 403 from googlevideo, a decoder error, a stalled proxy …). Continue with
     * another configured instance without bouncing back to one that has
     * already failed for this video. After [MAX_PLAYER_INSTANCE_FAILOVERS]
     * instances the clip switches to the fallback web player automatically,
     * so the user is never left without a way to watch it.
     */
    fun retryVideoAfterPlaybackFailure(failedInstance: OnlineInstance, video: OnlineVideo, error: String? = null) {
        failedPlaybackInstances.add(failedInstance.baseUrl)
        val remaining = _instances.value.count { it.enabled && it.baseUrl !in failedPlaybackInstances }
        // The direct YouTube source does not use up one of the mirror retries.
        val failedMirrors = failedPlaybackInstances.count { it != InnerTubeClient.DIRECT_BASE_URL }
        if (failedMirrors >= MAX_PLAYER_INSTANCE_FAILOVERS || remaining == 0) {
            val reason = listOfNotNull(
                strings().onlineFallbackAutoReason,
                failedPlaybackInstances.joinToString(", ") { it.removePrefix("https://") }.takeIf { it.isNotBlank() },
                error?.take(120)
            ).joinToString("\n")
            openWebFallback(video, reason)
            return
        }
        loadVideo(video.id, video)
    }

    /**
     * Plays the current clip through the YouTube IFrame embed. Works with
     * or without [details]: when no instance answered at all, [video] (or
     * the id being opened) is enough.
     */
    fun openWebFallback(video: OnlineVideo? = null, reason: String? = null) =
        switchToWebFallback(video, reason, cancelLookup = true)

    private fun switchToWebFallback(video: OnlineVideo?, reason: String?, cancelLookup: Boolean) {
        val target = video ?: _details.value?.video
            ?: pendingVideoId?.let { OnlineVideo(id = it, title = "") }
            ?: return
        // A still-running stream lookup must not replace the fallback afterwards.
        if (cancelLookup) videoJob?.cancel()
        if (_currentVideoId.value != target.id) {
            // A different clip: its captions start from scratch.
            _currentVideoId.value = target.id
            resetCaptionState()
        }
        // The embed plays without any instance, but the transcript still
        // needs captions: look them up on their own.
        ensureCaptions(target.id)
        // Fallback first, then clear the loading/error flags, so the player
        // pane never sees an in-between "nothing open" state and closes.
        _webFallbackError.value = null
        _webFallbackReason.value = reason
        _webFallbackVideo.value = target
        _videoError.value = null
        _isLoadingVideo.value = false
    }

    /** The fallback web player reported an error (e.g. embedding disabled by the owner). */
    fun reportWebFallbackError(error: String) {
        _webFallbackError.value = error
    }

    /** Leave the fallback web player and try direct streams again on the instances not tried yet. */
    fun retryDirectPlayback() {
        val video = _webFallbackVideo.value ?: _details.value?.video
        val id = video?.id ?: pendingVideoId ?: return
        // Continue with the mirrors not tried yet; once every instance has
        // failed for this clip, start the whole pool over.
        if (_instances.value.none { it.enabled && it.baseUrl !in failedPlaybackInstances }) {
            failedPlaybackInstances.clear()
            OnlineVideoRepository.forgetOutages()
        }
        // Show the spinner before leaving fallback mode (no flicker to "closed").
        _isLoadingVideo.value = true
        clearWebFallback()
        loadVideo(id, video)
    }

    private fun clearWebFallback() {
        _webFallbackVideo.value = null
        _webFallbackReason.value = null
        _webFallbackError.value = null
    }

    private fun loadVideo(videoId: String, placeholder: OnlineVideo? = null) {
        // Opening another clip wipes every caption of the previous one at
        // once, before anything is fetched. A retry of the SAME clip (next
        // instance, back from the embed) keeps captions that already loaded.
        val sameClip = _currentVideoId.value == videoId
        _currentVideoId.value = videoId
        videoJob?.cancel()
        if (!sameClip) resetCaptionState()
        if (captionsVideoId != videoId && _captionStatus.value != CaptionStatus.LOADING) {
            _captionStatus.value = CaptionStatus.LOADING
        }
        _videoError.value = null
        _details.value = null
        val candidates = _instances.value.filterNot { it.baseUrl in failedPlaybackInstances }
        // Direct YouTube extraction (InnerTube) comes first: clean Media3
        // playback with no instance in between. Skipped once it failed for
        // this clip, so a retry moves on to the mirrors.
        val tryDirect = InnerTubeClient.DIRECT_BASE_URL !in failedPlaybackInstances
        if (!tryDirect && candidates.none { it.enabled }) {
            // Nothing left to ask: go straight to the fallback web player.
            openWebFallback(placeholder ?: OnlineVideo(id = videoId, title = ""), strings().onlineNoPlayableStream)
            return
        }
        // Set before the coroutine starts so the player pane stays open
        // (spinner) while one instance is swapped for the next.
        _isLoadingVideo.value = true
        videoJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoadingVideo.value = true
            try {
                val preferred = _preferredInstance.value?.takeUnless { it in failedPlaybackInstances }
                var directError: Exception? = null
                if (tryDirect) {
                    val direct: OnlineVideoDetails? = try {
                        InnerTubeClient.videoDetails(videoId)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        directError = e
                        failedPlaybackInstances.add(InnerTubeClient.DIRECT_BASE_URL)
                        null
                    }
                    if (direct != null) {
                        showDetails(videoId, placeholder, direct)
                        return@launch
                    }
                }
                val directFailure = directError?.let {
                    OnlineVideoRepository.Failure(DIRECT_FAILURE_HOST, it.message ?: it.javaClass.simpleName)
                }
                if (candidates.none { it.enabled }) {
                    throw OnlineVideoRepository.AllInstancesFailed(listOfNotNull(directFailure))
                }
                val served = try {
                    OnlineVideoRepository.videoDetails(candidates, videoId, preferred)
                } catch (e: OnlineVideoRepository.AllInstancesFailed) {
                    throw if (directFailure != null) OnlineVideoRepository.AllInstancesFailed(listOf(directFailure) + e.failures) else e
                }
                markGood(served.instance)
                showDetails(videoId, placeholder, served.value)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Up to PLAYBACK_MAX_ATTEMPTS instances were asked (403, 5xx,
                // timeouts …). Remember them so "try direct playback again"
                // moves on to the next mirrors, and keep the user watching
                // through the fallback web player in the meantime.
                if (e is OnlineVideoRepository.AllInstancesFailed) {
                    e.failures.forEach { f ->
                        _instances.value.firstOrNull { it.host == f.host }?.let { failedPlaybackInstances.add(it.baseUrl) }
                    }
                }
                val reason = errorText(e)
                switchToWebFallback(placeholder ?: OnlineVideo(id = videoId, title = ""), reason, cancelLookup = false)
            } finally {
                _isLoadingVideo.value = false
            }
        }
    }

    private fun showDetails(videoId: String, placeholder: OnlineVideo?, d: OnlineVideoDetails) {
        val fixed = if (placeholder != null && d.video.title.isBlank()) d.copy(video = placeholder) else d
        _details.value = fixed
        // The shelves learn the real title / thumbnail / length.
        recordHistory(fixed.video)
        refreshSaved(fixed.video)
        if (fixed.playbackUrl == null) {
            _videoError.value = strings().onlineNoPlayableStream
        }
        // Captions: the best English track of this source first, then
        // YouTube's InnerTube tracks, the other instances and timedtext.
        if (fixed.captions.isNotEmpty() && _captionTracks.value.isEmpty()) _captionTracks.value = fixed.captions
        ensureCaptions(videoId, fixed.captions, fixed.instance)
        // «ذخیره هنگام پخش»: the manager's storage list knows this clip by
        // its stable rendition keys; entries with no cached bytes are pruned
        // on the next listing, so registering is always safe.
        OnlineWatchCache.registerVideo(
            app,
            videoId,
            fixed.video.title,
            keys = buildList {
                fixed.progressiveStreams.forEach { add(OnlineWatchCache.streamKey(videoId, it)) }
                add(OnlineWatchCache.audioKey(videoId))
            }
        )
    }

    fun closeVideo() {
        // A lesson may have been attached while the clip was open.
        refreshJsonLessonIds()
        videoJob?.cancel()
        _currentVideoId.value = null
        failedPlaybackInstances.clear()
        clearWebFallback()
        _details.value = null
        resetCaptionState()
        _videoError.value = null
        _isLoadingVideo.value = false
    }

    /**
     * Forgets every caption of the previous clip: lists, tracks, offsets,
     * running downloads and translations. Called the moment another clip is
     * opened, so its transcript never shows a single line that is not its own.
     */
    private fun resetCaptionState() {
        captionJob?.cancel()
        translationTrackJob?.cancel()
        stopTranslation()
        captionGeneration++
        captionsVideoId = null
        resolvingVideoId = null
        _captions.value = emptyList()
        _translationCaptions.value = emptyList()
        _activeTrack.value = null
        _translationTrack.value = null
        _captionTracks.value = emptyList()
        _captionOffset.value = 0.0
        _translationOffset.value = 0.0
        _isLoadingCaptions.value = false
        _captionStatus.value = CaptionStatus.IDLE
    }

    /**
     * Starts the caption lookup for [videoId] unless its captions are
     * already loaded or already being looked up.
     */
    private fun ensureCaptions(
        videoId: String,
        knownTracks: List<OnlineCaptionTrack> = _details.value?.takeIf { it.video.id == videoId }?.captions.orEmpty(),
        knownInstance: OnlineInstance? = _details.value?.takeIf { it.video.id == videoId }?.instance
    ) {
        if (captionsVideoId == videoId && _captions.value.isNotEmpty()) return
        if (resolvingVideoId == videoId && captionJob?.isActive == true) return
        resolveCaptions(videoId, knownTracks, knownInstance)
    }

    /** Looks captions up across every source (see [OnlineVideoRepository.findCaptions]). */
    private fun resolveCaptions(videoId: String, knownTracks: List<OnlineCaptionTrack>, knownInstance: OnlineInstance?) {
        captionJob?.cancel()
        val generation = ++captionGeneration
        resolvingVideoId = videoId
        _captionStatus.value = CaptionStatus.LOADING
        _isLoadingCaptions.value = true
        captionJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = OnlineVideoRepository.findCaptions(
                    instances = _instances.value,
                    videoId = videoId,
                    knownTracks = knownTracks,
                    knownInstance = knownInstance,
                    preferred = _preferredInstance.value
                )
                if (generation != captionGeneration || _currentVideoId.value != videoId) return@launch
                if (result != null) {
                    captionsVideoId = videoId
                    _captionTracks.value = result.tracks.ifEmpty { knownTracks }
                    _activeTrack.value = result.track
                    _captions.value = result.entries
                    _captionStatus.value = CaptionStatus.READY
                    // «ذخیره هنگام پخش»: persist what is being watched, so
                    // a later lookup that fails (dead or rate-limited
                    // instance, offline) still has this copy to fall back to.
                    if (_saveWhilePlaying.value && result.entries.isNotEmpty()) {
                        OnlineWatchCache.saveCaptions(
                            app, videoId, OnlineWatchCache.SLOT_MAIN,
                            result.track.languageCode, result.track.label, result.entries
                        )
                    }
                } else {
                    val cached = cachedMainCaptions(videoId)
                    if (cached != null) {
                        // Nothing online — the watched disk copy keeps the
                        // clip subtitled «برای استفاده‌های بعدی».
                        captionsVideoId = videoId
                        _activeTrack.value = cachedTrack(cached, OnlineWatchCache.SLOT_MAIN)
                        _captions.value = cached.entries
                        _captionStatus.value = CaptionStatus.READY
                    } else {
                        if (_captionTracks.value.isEmpty()) _captionTracks.value = knownTracks
                        _captionStatus.value = CaptionStatus.NONE
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == captionGeneration) {
                    val cached = cachedMainCaptions(videoId)
                    if (cached != null) {
                        captionsVideoId = videoId
                        _activeTrack.value = cachedTrack(cached, OnlineWatchCache.SLOT_MAIN)
                        _captions.value = cached.entries
                        _captionStatus.value = CaptionStatus.READY
                    } else {
                        _captionStatus.value = CaptionStatus.FAILED
                    }
                }
            } finally {
                if (generation == captionGeneration) {
                    _isLoadingCaptions.value = false
                    resolvingVideoId = null
                }
            }
        }
    }

    /**
     * The disk copy of the main captions, or null when «ذخیره هنگام پخش»
     * is off or nothing was ever saved for this clip.
     */
    private fun cachedMainCaptions(videoId: String): OnlineWatchCache.CachedCaptions? {
        if (!_saveWhilePlaying.value) return null
        return try {
            OnlineWatchCache.loadCaptions(app, videoId, OnlineWatchCache.SLOT_MAIN)
        } catch (e: Exception) {
            null
        }
    }

    /** The cached payload as a track again, so the UI keeps its label. */
    private fun cachedTrack(cached: OnlineWatchCache.CachedCaptions, slot: String): OnlineCaptionTrack =
        OnlineCaptionTrack(
            label = cached.label.ifBlank { cached.languageCode },
            languageCode = cached.languageCode,
            url = "cached:$slot"
        )

    internal fun pickDefaultTrack(tracks: List<OnlineCaptionTrack>): OnlineCaptionTrack? =
        tracks.firstOrNull { it.isEnglish && !it.autoGenerated }
            ?: tracks.firstOrNull { it.isEnglish }
            ?: tracks.firstOrNull { !it.autoGenerated }
            ?: tracks.firstOrNull()

    /** Downloads [track] (picked by the user) and shows it as the main subtitle list. */
    fun loadCaptionTrack(track: OnlineCaptionTrack) {
        val videoId = _currentVideoId.value ?: return
        captionJob?.cancel()
        val generation = ++captionGeneration
        resolvingVideoId = videoId
        _isLoadingCaptions.value = true
        captionJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = OnlineVideoRepository.fetchCaptions(track, track.languageCode.ifBlank { "en" })
                if (generation != captionGeneration || _currentVideoId.value != videoId) return@launch
                if (entries.isEmpty()) {
                    // Keep whatever was loaded before; this track is just empty.
                    _message.value = strings().onlineCaptionsEmpty
                } else {
                    captionsVideoId = videoId
                    _activeTrack.value = track
                    _captions.value = entries
                    _captionOffset.value = 0.0
                    _captionStatus.value = CaptionStatus.READY
                    // Picking an online track replaces an imported file in
                    // the main slot (and takes its sync offset back to zero).
                    _importedMain.value = null
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == captionGeneration) _message.value = errorText(e)
            } finally {
                if (generation == captionGeneration) {
                    _isLoadingCaptions.value = false
                    resolvingVideoId = null
                }
            }
        }
    }

    /** Downloads [track] as the translation shown under each line (null clears it). */
    fun loadTranslationTrack(track: OnlineCaptionTrack?) {
        if (track == null) {
            _translationTrack.value = null
            _translationCaptions.value = emptyList()
            return
        }
        val videoId = _currentVideoId.value ?: return
        translationTrackJob?.cancel()
        translationTrackJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = OnlineVideoRepository.fetchCaptions(track, track.languageCode.ifBlank { "fa" })
                if (_currentVideoId.value != videoId) return@launch
                _translationTrack.value = track
                _translationCaptions.value = entries
                // Picking an online translation track replaces an imported
                // file in the secondary slot.
                _importedTranslation.value = null
                // «ذخیره هنگام پخش»: same persistence as the main track.
                if (_saveWhilePlaying.value && entries.isNotEmpty()) {
                    OnlineWatchCache.saveCaptions(
                        app, videoId, OnlineWatchCache.SLOT_TRANSLATION,
                        track.languageCode, track.label, entries
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // The download failed — the previously watched disk copy
                // (if any) keeps the translation slot working.
                val cached = if (_saveWhilePlaying.value) {
                    try {
                        OnlineWatchCache.loadCaptions(app, videoId, OnlineWatchCache.SLOT_TRANSLATION)
                    } catch (ignored: Exception) {
                        null
                    }
                } else null
                if (cached != null && _currentVideoId.value == videoId) {
                    _translationTrack.value = OnlineCaptionTrack(
                        label = cached.label.ifBlank { cached.languageCode },
                        languageCode = cached.languageCode,
                        url = "cached:${OnlineWatchCache.SLOT_TRANSLATION}"
                    )
                    _translationCaptions.value = cached.entries
                    _importedTranslation.value = null
                } else {
                    _message.value = errorText(e)
                }
            }
        }
    }

    fun clearCaptions() {
        captionJob?.cancel()
        captionGeneration++
        resolvingVideoId = null
        _isLoadingCaptions.value = false
        _activeTrack.value = null
        _captions.value = emptyList()
        captionsVideoId = null
        _captionStatus.value = CaptionStatus.IDLE
    }

    /** Lets the user nudge the online captions like the file-based subtitles. */
    fun shiftCaptions(seconds: Double) {
        _captions.update { it.map { e -> e.copy(start = (e.start + seconds).coerceAtLeast(0.0), end = (e.end + seconds).coerceAtLeast(0.0)) } }
        _captionOffset.value += seconds
    }

    private val _captionOffset = MutableStateFlow(0.0)
    val captionOffset: StateFlow<Double> = _captionOffset

    fun shiftTranslationCaptions(seconds: Double) {
        _translationCaptions.update { it.map { e -> e.copy(start = (e.start + seconds).coerceAtLeast(0.0), end = (e.end + seconds).coerceAtLeast(0.0)) } }
        _translationOffset.value += seconds
    }

    private val _translationOffset = MutableStateFlow(0.0)
    val translationOffset: StateFlow<Double> = _translationOffset

    // ── Single-line AI translation (same as the Video tab's per-line button) ──

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating
    private val _translatingIndex = MutableStateFlow(-1)
    val translatingIndex: StateFlow<Int> = _translatingIndex
    private val _translateError = MutableStateFlow<String?>(null)
    val translateError: StateFlow<String?> = _translateError
    private var translateJob: Job? = null

    /**
     * Translates one caption line with the user's AI endpoint and writes the
     * result into the translation list (matched by start time, like
     * [AppViewModel.translateSingleSubtitle]).
     */
    fun translateCaption(index: Int, baseUrl: String, apiKey: String, model: String, targetLang: String) {
        val list = _captions.value
        val s = strings()
        if (index < 0 || index >= list.size) { _translateError.value = s.invalidIndexError; return }
        _isTranslating.value = true; _translatingIndex.value = index; _translateError.value = null
        translateJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val cue = list[index]
                val config = com.example.logic.AiService.TranslationConfig(apiKey, baseUrl, model)
                val systemPrompt = "You are a subtitle translator. Translate the given text to $targetLang. Return ONLY the translated text, nothing else."
                val result = com.example.logic.AiService.chat(config, listOf(Pair("user", cue.text)), systemPrompt, app, noteSavedMessage = s.aiNoteSaved)
                result.fold(
                    onSuccess = { response ->
                        val translation = response.trim().removeSurrounding("\"").removeSurrounding("'").trim()
                        if (translation.isBlank()) { _translateError.value = s.translationEmptyError; return@fold }
                        _translationCaptions.update { fa ->
                            val out = fa.toMutableList()
                            val idx = out.indexOfFirst { kotlin.math.abs(it.start - cue.start) < 0.5 }
                            if (idx >= 0) out[idx] = out[idx].copy(text = translation)
                            else { out.add(SubtitleEntry(cue.start, cue.end, translation, "fa")); out.sortBy { it.start } }
                            out
                        }
                    },
                    onFailure = { e -> _translateError.value = s.apiErrorMessage(e) }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _translateError.value = s.apiErrorMessage(e)
            } finally {
                _isTranslating.value = false; _translatingIndex.value = -1
            }
        }
    }

    fun stopTranslation() {
        translateJob?.cancel(); translateJob = null
        _isTranslating.value = false; _translatingIndex.value = -1
    }

    /** Saves the translation list (second track and/or AI translations) as SRT. */
    fun exportTranslation() {
        val entries = _translationCaptions.value
        val s = strings()
        if (entries.isEmpty()) { _message.value = s.noFaSubtitleExists; return }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lang = _translationTrack.value?.languageCode?.ifBlank { null } ?: "translation"
                val d = _details.value
                val title = d?.video?.title?.trim()?.replace(Regex("[^\\p{L}\\p{N} ._-]"), "")?.take(60)?.trim().orEmpty()
                val base = if (title.isNotBlank()) title else (d?.video?.id ?: "captions")
                val path = DownloadsSaver.saveText(app, "$base.$lang.srt", OnlineCaptionParser.toSrt(entries))
                _message.value = s.savedAtPath(path)
            } catch (e: Exception) {
                _message.value = s.errorWithMessage(e.message)
            }
        }
    }

    // ── Export / copy ──

    enum class CaptionFormat { SRT, TEXT }

    private fun exportFileName(ext: String): String {
        val d = _details.value
        val title = d?.video?.title?.trim()?.replace(Regex("[^\\p{L}\\p{N} ._-]"), "")?.take(60)?.trim().orEmpty()
        val lang = _activeTrack.value?.languageCode?.ifBlank { null } ?: "en"
        val base = if (title.isNotBlank()) title else (d?.video?.id ?: "captions")
        return "$base.$lang.$ext"
    }

    /** Saves the loaded captions to Downloads as SRT (or plain transcript). */
    fun exportCaptions(format: CaptionFormat = CaptionFormat.SRT) {
        val entries = _captions.value
        val s = strings()
        if (entries.isEmpty()) { _message.value = s.onlineNoCaptionsLoaded; return }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (name, text) = when (format) {
                    CaptionFormat.SRT -> exportFileName("srt") to OnlineCaptionParser.toSrt(entries)
                    CaptionFormat.TEXT -> exportFileName("txt") to OnlineCaptionParser.toPlainText(entries)
                }
                val path = DownloadsSaver.saveText(app, name, text)
                _message.value = s.savedAtPath(path)
            } catch (e: Exception) {
                _message.value = s.errorWithMessage(e.message)
            }
        }
    }

    /** Copies the loaded captions (SRT or plain transcript) to the clipboard. */
    fun copyCaptions(format: CaptionFormat = CaptionFormat.SRT) {
        val entries = _captions.value
        val s = strings()
        if (entries.isEmpty()) { _message.value = s.onlineNoCaptionsLoaded; return }
        val text = when (format) {
            CaptionFormat.SRT -> OnlineCaptionParser.toSrt(entries)
            CaptionFormat.TEXT -> OnlineCaptionParser.toPlainText(entries)
        }
        try {
            val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("langosphere_captions", text))
            _message.value = s.onlineCaptionsCopied(entries.size)
        } catch (e: Exception) {
            _message.value = s.errorWithMessage(e.message)
        }
    }

    /**
     * Imports a subtitle file from the device into the MAIN (primary) slot of
     * the open clip — the action drawer's «زیرنویس اصلی» button. Same file
     * handling and the same SRT/VTT/LRC parser as the Video tab's subtitle
     * pickers; while present the file overrides the online caption track.
     */
    fun importMainSubtitle(uri: Uri) {
        importSubtitle(uri, isMain = true)
    }

    /**
     * Imports a subtitle file into the SECONDARY (target-language) slot —
     * the action drawer's «زیرنویس دوم» button.
     */
    fun importTranslationSubtitle(uri: Uri) {
        importSubtitle(uri, isMain = false)
    }

    private fun importSubtitle(uri: Uri, isMain: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = app.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().decodeToString()
                } ?: run {
                    _message.value = strings().onlineImportFailed
                    return@launch
                }
                importSubtitleText(text, isMain, uriDisplayName(uri))
            } catch (e: Exception) {
                _message.value = strings().onlineImportFailed
            }
        }
    }

    /**
     * Imports subtitle CONTENT (a picked file or a clipboard paste) into the
     * clip's own subtitle slot — the attach chooser's paste option, the same
     * two-way import the Video tab offers. Safe to call from any thread.
     */
    fun importSubtitleText(text: String, isMain: Boolean, fileName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val s = strings()
            try {
                val name = fileName ?: s.onlineImportPastedName
                val isVtt = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
                    .startsWith("WEBVTT", ignoreCase = true) ||
                    name.endsWith(".vtt", ignoreCase = true)
                val entries = SubtitleParser.parseSubtitleContent(text, if (isMain) "en" else "fa")
                if (entries.isEmpty()) {
                    _message.value = s.onlineImportFailed
                    return@launch
                }
                val imported = ImportedSubtitle(
                    entries = entries,
                    fileName = name,
                    isVtt = isVtt
                )
                if (isMain) {
                    _importedMain.value = imported
                    _captionOffset.value = 0.0
                } else {
                    _importedTranslation.value = imported
                    _translationOffset.value = 0.0
                }
                _message.value = s.onlineImportSubtitleLoaded(imported.fileName, entries.size)
            } catch (e: Exception) {
                _message.value = s.onlineImportFailed
            }
        }
    }

    /** The picked file's name, for the "loaded" toast (same lookup as the Video tab). */
    private fun uriDisplayName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = app.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) result = cursor.getString(idx)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result ?: "subtitle"
    }

    /**
     * «کپی خام زیرنویس» — the action drawer's dedicated copy button: the raw
     * content of the ACTIVE subtitle and nothing else. No pre-configured
     * prompt, no wrapper block, no extra markdown: what lands in the
     * clipboard is a plain, valid subtitle file.
     *
     * The active source is the imported primary file when one drives the
     * main slot (copied back in its own format, .srt or .vtt), otherwise the
     * online caption track — serialised as WebVTT, the format those tracks
     * natively come in.
     */
    fun copyRawSubtitle() {
        val s = strings()
        val imported = _importedMain.value
        if (imported != null && imported.entries.isNotEmpty()) {
            val text = if (imported.isVtt) {
                OnlineCaptionParser.toVtt(imported.entries)
            } else {
                OnlineCaptionParser.toSrt(imported.entries)
            }
            if (copyToClipboard("langosphere_raw_subtitle", text)) {
                _message.value = if (imported.isVtt) {
                    s.onlineRawCopiedVtt(imported.entries.size)
                } else {
                    s.onlineRawCopiedSrt(imported.entries.size)
                }
            }
            return
        }
        val videoId = _currentVideoId.value
        if (videoId == null) { _message.value = s.onlineAiCopyNoCaptions; return }
        val entries = if (captionsVideoId == videoId) _captions.value else emptyList()
        if (entries.isEmpty()) {
            when (_captionStatus.value) {
                CaptionStatus.NONE, CaptionStatus.FAILED -> _message.value = s.onlineAiCopyNoCaptions
                else -> {
                    ensureCaptions(videoId)
                    _message.value = s.onlineAiCopyLoading
                }
            }
            return
        }
        val text = OnlineCaptionParser.toVtt(entries)
        if (copyToClipboard("langosphere_raw_subtitle", text)) {
            _message.value = s.onlineRawCopiedVtt(entries.size)
        }
    }

    /** Puts [text] on the system clipboard; false (with a toast queued) when that failed. */
    private fun copyToClipboard(label: String, text: String): Boolean = try {
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        true
    } catch (e: Exception) {
        _message.value = strings().errorWithMessage(e.message)
        false
    }

    /** Copies the video's YouTube link (handy for sharing or for the AI tab). */
    fun copyVideoLink() {
        val d = _details.value ?: return
        try {
            val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("langosphere_video_link", d.video.watchUrl))
            _message.value = strings().onlineLinkCopied
        } catch (e: Exception) {
            _message.value = strings().errorWithMessage(e.message)
        }
    }

    // ── Hub shelves ──

    fun isSaved(videoId: String): Boolean = _saved.value.any { it.id == videoId }

    /** ⭐ Saves [video] to the library, or removes it when it is already there. */
    fun toggleSaved(video: OnlineVideo) {
        val wasSaved = isSaved(video.id)
        val best = _history.value.firstOrNull { it.id == video.id }
            ?.let { OnlineLibraryStore.mergeMetadata(it, video) } ?: video
        _savedEntries.update { OnlineLibraryStore.toggleSaved(it, best, System.currentTimeMillis()) }
        persistSaved()
        _message.value = if (wasSaved) strings().onlineUnsavedToast else strings().onlineSavedToast
    }

    /** The clip that is open right now (details, or the embed's clip). */
    fun currentVideo(): OnlineVideo? = _details.value?.video ?: _webFallbackVideo.value
        ?: pendingVideoId?.let { id -> _history.value.firstOrNull { it.id == id } ?: OnlineVideo(id = id, title = "") }

    fun removeSaved(videoId: String) {
        _savedEntries.update { OnlineLibraryStore.remove(it, videoId) }
        persistSaved()
    }

    fun removeFromHistory(videoId: String) {
        _historyEntries.update { OnlineLibraryStore.remove(it, videoId) }
        persistHistory()
    }

    fun clearHistory() {
        _historyEntries.value = emptyList()
        persistHistory()
    }

    /** Copies the YouTube link of any clip on the shelves. */
    fun copyLink(video: OnlineVideo) {
        try {
            val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("langosphere_video_link", video.watchUrl))
            _message.value = strings().onlineLinkCopied
        } catch (e: Exception) {
            _message.value = strings().errorWithMessage(e.message)
        }
    }

    /**
     * «استخراج دوباره»: opens the clip with a completely fresh lookup —
     * streams and captions are extracted again, no remembered outage skips
     * a source.
     */
    fun reextract(video: OnlineVideo) {
        OnlineVideoRepository.forgetOutages()
        openVideo(video)
    }

    /** Rescans online_json/ for clips that have a JSON lesson attached. */
    fun refreshJsonLessonIds() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = java.io.File(app.filesDir, AppViewModel.ONLINE_JSON_DIR)
            val ids = dir.listFiles()?.asSequence()
                ?.filter { it.isFile && it.name.endsWith(".json") && it.length() > 0 }
                ?.map { it.name.removeSuffix(".json") }
                ?.toSet()
                .orEmpty()
            _jsonLessonIds.value = ids
        }
    }

    private fun recordHistory(video: OnlineVideo) {
        _historyEntries.update { OnlineLibraryStore.addToHistory(it, video, System.currentTimeMillis()) }
        persistHistory()
    }

    private fun refreshSaved(video: OnlineVideo) {
        if (!isSaved(video.id)) return
        _savedEntries.update { OnlineLibraryStore.refresh(it, video) }
        persistSaved()
    }

    private fun persistSaved() {
        val list = _savedEntries.value
        _saved.value = list.map { it.video }
        OnlineLibraryStore.storeSaved(app, list)
    }

    private fun persistHistory() {
        val list = _historyEntries.value
        _history.value = list.map { it.video }
        OnlineLibraryStore.storeHistory(app, list)
    }

    /** Feed loaded: set baselines for newly seen channels and light the dots. */
    private fun updateChannelDots(feed: List<OnlineVideo>) {
        val seen = OnlineLibraryStore.baselineSeen(feed, channelSeen)
        if (seen != channelSeen) {
            channelSeen = seen
            OnlineLibraryStore.storeSeen(app, seen)
        }
        _channelsWithNew.value = OnlineLibraryStore.channelsWithNewVideos(feed, seen)
    }

    /** The learner opened the channel: it has nothing new for them any more. */
    private fun markChannelSeen(channelId: String) {
        val latest = OnlineLibraryStore.latestUploadByChannel(_videos.value)[channelId] ?: 0L
        val mark = maxOf(latest, System.currentTimeMillis() / 1000L)
        channelSeen = channelSeen + (channelId to mark)
        OnlineLibraryStore.storeSeen(app, channelSeen)
        _channelsWithNew.update { it - channelId }
    }

    // Last on purpose: every property above must be initialised before the
    // first feed request is launched (the ViewModel is created the first
    // time the Online tab is opened, not at app start).
    init {
        loadFeed()
        refreshJsonLessonIds()
    }
}
