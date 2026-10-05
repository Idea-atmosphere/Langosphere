package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import com.example.ui.components.OnlineDrawerActions
import com.example.ui.components.OnlineStudioTokens
import com.example.ui.components.PlayerFabAction
import com.example.ui.components.PlayerGlassButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.logic.InnerTubeClient
import com.example.logic.OnlineInstanceStore
import com.example.logic.OnlineWatchCache
import com.example.logic.OnlineStreamSelector
import com.example.logic.SentenceCardFactory
import com.example.model.JsonSubtitlePackage
import com.example.model.OnlineCaptionTrack
import com.example.model.OnlineChannel
import com.example.model.OnlineInstance
import com.example.model.OnlineInstanceKind
import com.example.model.OnlineStream
import com.example.model.OnlineVideo
import com.example.ui.components.EmptyState
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.PillTone
import com.example.ui.components.SectionHeader
import com.example.ui.components.rememberConfinedSwipeConnection
import com.example.ui.components.SentenceStudyLabels
import com.example.ui.components.SoftIconButton
import com.example.ui.components.StatusPill
import com.example.ui.components.VideoPlayerScreen
import com.example.ui.components.formatTime
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.AppStrings
import java.util.Locale

/**
 * JSON-slot scope used while the Online tab is on screen but no clip is open:
 * not the local film (null) and not any real clip, so nothing is shown.
 */
private const val ONLINE_NO_CLIP_SCOPE = "__online_none__"

/**
 * The Online tab: browse YouTube channels of English teachers (and any
 * channel the user follows) through Invidious / Piped instances, watch a
 * clip with its captions in the app's regular subtitle player, export or
 * copy those captions, and attach the app's JSON learning package to the
 * clip.
 *
 * Two screens live here: the browser (feed / one channel / search) and the
 * player. The player is the same [VideoPlayerScreen] the Video tab uses, so
 * every study tool (word tap → dictionary, sentence tap → lesson, loop,
 * speed, smart pause, focus mode) works on online clips too.
 */
@Composable
fun OnlineVideoScreen(
    appViewModel: AppViewModel,
    appLanguage: AppLanguage,
    isFullScreen: Boolean,
    onFullScreenToggle: (Boolean) -> Unit,
    focusMode: Boolean,
    onFocusModeToggle: () -> Unit,
    useDictionaryWithJson: Boolean,
    pauseForLesson: Boolean,
    viewModel: OnlineVideoViewModel = viewModel()
) {
    val context = LocalContext.current
    val strings = remember(appLanguage, context) { AppStrings(appLanguage, context) }

    // Online subtitles are strictly per clip. The shared JSON lesson slot is
    // switched to the open clip (its own online_json/<id>.json, or nothing)
    // for as long as this tab is on screen, and handed back to the Video
    // tab's film when the tab goes away — the film's package is never read
    // or written from here.
    val currentVideoId by viewModel.currentVideoId.collectAsState()
    DisposableEffect(currentVideoId) {
        appViewModel.useOnlineJsonScope(currentVideoId ?: ONLINE_NO_CLIP_SCOPE)
        onDispose { appViewModel.useOnlineJsonScope(null) }
    }
    val jsonScope by appViewModel.jsonScope.collectAsState()
    val scopedJson by appViewModel.jsonSubtitles.collectAsState()
    // Belt and braces: during the one frame in which the scope is still
    // switching, show nothing rather than another clip's (or the film's) lesson.
    val jsonPackage = scopedJson.takeIf { currentVideoId != null && jsonScope == currentVideoId }
    val details by viewModel.details.collectAsState()
    val isLoadingVideo by viewModel.isLoadingVideo.collectAsState()
    val videoError by viewModel.videoError.collectAsState()
    val webFallbackVideo by viewModel.webFallbackVideo.collectAsState()
    val message by viewModel.message.collectAsState()

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }

    val playerOpen = details != null || isLoadingVideo || videoError != null || webFallbackVideo != null

    // Back closes the player first, then leaves a channel / search back to the feed.
    val browse by viewModel.browse.collectAsState()
    BackHandler(enabled = playerOpen || browse !is OnlineVideoViewModel.Browse.Feed) {
        when {
            isFullScreen -> onFullScreenToggle(false)
            playerOpen -> viewModel.closeVideo()
            else -> viewModel.loadFeed()
        }
    }

    AnimatedContent(
        targetState = playerOpen,
        transitionSpec = {
            if (targetState) {
                (fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 12 }) togetherWith fadeOut(tween(180))
            } else {
                fadeIn(tween(260)) togetherWith (fadeOut(tween(180)) + slideOutVertically(tween(240)) { it / 12 })
            }
        },
        modifier = Modifier.fillMaxSize(),
        label = "onlineHubPlayer"
    ) { open ->
      if (open) {
        OnlinePlayerPane(
            appViewModel = appViewModel,
            viewModel = viewModel,
            strings = strings,
            appLanguage = appLanguage,
            isFullScreen = isFullScreen,
            onFullScreenToggle = onFullScreenToggle,
            focusMode = focusMode,
            onFocusModeToggle = onFocusModeToggle,
            jsonPackage = jsonPackage,
            useDictionaryWithJson = useDictionaryWithJson,
            pauseForLesson = pauseForLesson
        )
      } else {
        OnlineBrowsePane(viewModel = viewModel, strings = strings, appLanguage = appLanguage)
      }
    }
}

// ─────────────────────────── Hub (داشبورد ویدیوهای آنلاین) ───────────────────────────

/** One entry of a video card's ⋮ menu. */
private data class CardMenuItem(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/** The status pill of a card: an AI lesson is attached, or raw captions. */
private data class CardStatus(val label: String, val smart: Boolean)

@Composable
private fun OnlineBrowsePane(viewModel: OnlineVideoViewModel, strings: AppStrings, appLanguage: AppLanguage) {
    val context = LocalContext.current
    val browse by viewModel.browse.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val isLoading by viewModel.isLoadingList.collectAsState()
    val listError by viewModel.listError.collectAsState()
    val canLoadMore by viewModel.canLoadMore.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val instances by viewModel.instances.collectAsState()
    val preferred by viewModel.preferredInstance.collectAsState()
    val instanceHealth by viewModel.instanceHealth.collectAsState()
    val isCheckingInstances by viewModel.isCheckingInstances.collectAsState()
    val hubTab by viewModel.hubTab.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val history by viewModel.history.collectAsState()
    val channelsWithNew by viewModel.channelsWithNew.collectAsState()
    val jsonLessonIds by viewModel.jsonLessonIds.collectAsState()
    val savedIds = remember(saved) { saved.map { it.id }.toSet() }

    var query by rememberSaveable { mutableStateOf("") }
    var showInstances by remember { mutableStateOf(false) }
    var showWatchCache by remember { mutableStateOf(false) }
    var showFollow by remember { mutableStateOf(false) }
    var channelToUnfollow by remember { mutableStateOf<OnlineChannel?>(null) }

    if (showWatchCache) {
        BackHandler { showWatchCache = false }
        val saveEnabled by viewModel.saveWhilePlaying.collectAsState()
        val defaultQuality by viewModel.defaultQualityHeight.collectAsState()
        val cachedList by viewModel.cachedVideos.collectAsState()
        LaunchedEffect(Unit) { viewModel.refreshCachedVideos() }
        OnlineWatchCacheManagerPage(
            strings = strings,
            saveEnabled = saveEnabled,
            onToggleSave = { viewModel.setSaveWhilePlaying(it) },
            defaultQualityHeight = defaultQuality,
            onPickDefaultQuality = { viewModel.setDefaultQualityHeight(it) },
            cachedVideos = cachedList,
            onOpenVideo = { cached ->
                showWatchCache = false
                viewModel.openVideoId(cached.videoId, OnlineVideo(id = cached.videoId, title = cached.title))
            },
            onDeleteVideo = { viewModel.deleteCachedVideo(it) },
            onDeleteAll = {
                viewModel.clearWatchCache()
                viewModel.refreshCachedVideos()
            },
            onDismiss = { showWatchCache = false }
        )
        return
    }

    if (showInstances) {
        BackHandler { showInstances = false }
        InstanceManagerPage(
            strings = strings,
            instances = instances,
            preferred = preferred,
            health = instanceHealth,
            isChecking = isCheckingInstances,
            onAdd = { url, kind -> viewModel.addInstance(url, kind) },
            onRemove = { viewModel.removeInstance(it) },
            onToggle = { inst, enabled -> viewModel.setInstanceEnabled(inst, enabled) },
            onMoveTop = { viewModel.moveInstanceToTop(it) },
            onReset = { viewModel.resetInstances() },
            onCheckOne = { viewModel.checkInstance(it) },
            onCheckAll = { viewModel.checkInstances() },
            onDismiss = { showInstances = false }
        )
        return
    }

    fun runSearch(text: String) {
        if (text.isBlank()) return
        viewModel.selectHubTab(OnlineVideoViewModel.HubTab.FOLLOWING)
        viewModel.search(text)
    }

    // Card menus: what makes sense depends on the shelf the card is on.
    fun saveToggleItem(video: OnlineVideo) = if (video.id in savedIds) {
        CardMenuItem(strings.onlineMenuUnsave, Icons.Filled.BookmarkRemove) { viewModel.toggleSaved(video) }
    } else {
        CardMenuItem(strings.onlineMenuSave, Icons.Filled.BookmarkAdd) { viewModel.toggleSaved(video) }
    }
    fun copyItem(video: OnlineVideo) = CardMenuItem(strings.onlineMenuCopyUrl, Icons.Filled.Link) { viewModel.copyLink(video) }
    fun statusOf(video: OnlineVideo, alwaysShow: Boolean): CardStatus? = when {
        video.id in jsonLessonIds -> CardStatus(strings.onlineStatusSmart, smart = true)
        alwaysShow -> CardStatus(strings.onlineStatusRaw, smart = false)
        else -> null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // The tab's top chrome follows the app language: Persian mirrors it
        // (title on the right, the manager buttons on the left, chips and
        // search laid out right-to-left); English keeps the LTR order. The
        // hub content below stays LTR, like the rest of the app's layout.
        val topDirection = if (appLanguage == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides topDirection) {
        // Header: title, the answering source, and the instance manager.
        SectionHeader(
            title = strings.onlineTitle,
            subtitle = preferred?.let { OnlineInstance(it, OnlineInstanceStore.detectKind(it)).host } ?: strings.onlineSubtitle,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // «مدیریت حافظه»: the watch cache — the saving switch for
                    // all videos, the storage each clip holds and the default
                    // play/save quality — one button beside the instances.
                    SoftIconButton(
                        icon = Icons.Filled.Storage,
                        contentDescription = strings.onlineWatchCacheCd,
                        onClick = { showWatchCache = true }
                    )
                    Spacer(Modifier.width(6.dp))
                    SoftIconButton(icon = Icons.Filled.Dns, contentDescription = strings.onlineInstancesTitle, onClick = { showInstances = true })
                }
            }
        )

        HubSearchBar(
            query = query,
            hint = strings.onlineHubSearchHint,
            strings = strings,
            onQueryChange = { query = it },
            onSearch = { runSearch(query) },
            onClear = {
                query = ""
                if (browse is OnlineVideoViewModel.Browse.Search) viewModel.loadFeed()
            },
            onPaste = {
                val text = readClipboardText(context)
                if (text.isNullOrBlank()) {
                    Toast.makeText(context, strings.onlineClipboardEmpty, Toast.LENGTH_SHORT).show()
                } else {
                    query = text
                    runSearch(text)
                }
            }
        )

        HubTabs(
            selected = hubTab,
            strings = strings,
            savedCount = saved.size,
            onSelect = { viewModel.selectHubTab(it) }
        )
        } // end of the mirrored top chrome

        // Tabs slide in from the side they sit on (mirrored in RTL).
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        AnimatedContent(
            targetState = hubTab,
            transitionSpec = {
                val forward = (targetState.ordinal > initialState.ordinal) != rtl
                (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { w -> if (forward) w / 10 else -w / 10 }) togetherWith
                    fadeOut(tween(140))
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "hubTab"
        ) { tab ->
            when (tab) {
                OnlineVideoViewModel.HubTab.FOLLOWING -> {
                    val contextLabel = when (val b = browse) {
                        is OnlineVideoViewModel.Browse.Feed -> strings.onlineFeedTitle
                        is OnlineVideoViewModel.Browse.Channel -> b.channel.name
                        is OnlineVideoViewModel.Browse.Search -> strings.onlineSearchResultsFor(b.query)
                    }
                    HubVideoGrid(
                        videos = videos,
                        strings = strings,
                        statusOf = { statusOf(it, alwaysShow = false) },
                        menuOf = { listOf(saveToggleItem(it), copyItem(it)) },
                        onOpen = { viewModel.openVideo(it) },
                        header = {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "__tray") {
                                // The channel tray belongs to the tab's top
                                // chrome, so it mirrors for Persian too.
                                CompositionLocalProvider(
                                    LocalLayoutDirection provides if (appLanguage == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr
                                ) {
                                    ChannelTray(
                                        channels = channels,
                                        selectedId = (browse as? OnlineVideoViewModel.Browse.Channel)?.channel?.id,
                                        feedSelected = browse is OnlineVideoViewModel.Browse.Feed,
                                        newIds = channelsWithNew,
                                        strings = strings,
                                        onFollow = { showFollow = true },
                                        onAll = { query = ""; viewModel.loadFeed() },
                                        onChannel = { query = ""; viewModel.openChannel(it) }
                                    )
                                }
                            }
                            item(span = { GridItemSpan(maxLineSpan) }, key = "__context") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        contextLabel,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val b = browse
                                    if (b is OnlineVideoViewModel.Browse.Channel) {
                                        TextButton(onClick = { channelToUnfollow = b.channel }) { Text(strings.onlineUnfollowBtn) }
                                    }
                                    SoftIconButton(
                                        icon = Icons.Filled.Refresh,
                                        contentDescription = strings.onlineRefresh,
                                        size = 32.dp,
                                        onClick = {
                                            when (val cur = browse) {
                                                is OnlineVideoViewModel.Browse.Feed -> viewModel.loadFeed()
                                                is OnlineVideoViewModel.Browse.Channel -> viewModel.openChannel(cur.channel)
                                                is OnlineVideoViewModel.Browse.Search -> viewModel.search(cur.query)
                                            }
                                        }
                                    )
                                }
                            }
                            if (isLoading && videos.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "__loading") {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp))
                                }
                            }
                            if (listError != null && videos.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "__error") {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        EmptyState(icon = Icons.Filled.Public, title = strings.onlineErrorTitle, description = listError)
                                        Spacer(Modifier.height(12.dp))
                                        GradientButton(text = strings.onlineRetry, icon = Icons.Filled.Refresh, onClick = { viewModel.loadFeed() })
                                        Spacer(Modifier.height(8.dp))
                                        TextButton(onClick = { showInstances = true }) { Text(strings.onlineManageInstancesBtn) }
                                    }
                                }
                            } else if (videos.isEmpty() && !isLoading) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "__empty") {
                                    EmptyState(
                                        icon = Icons.Filled.Subscriptions,
                                        title = if (channels.isEmpty()) strings.onlineNoChannelsTitle else strings.onlineNoVideosTitle,
                                        description = if (channels.isEmpty()) strings.onlineNoChannelsDesc else strings.onlineNoVideosDesc,
                                        modifier = Modifier.padding(vertical = 24.dp)
                                    )
                                }
                            }
                        },
                        footer = {
                            if (canLoadMore && videos.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "__more") {
                                    Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                        else TextButton(onClick = { viewModel.loadMore() }) { Text(strings.onlineLoadMore) }
                                    }
                                }
                            }
                        }
                    )
                }
                OnlineVideoViewModel.HubTab.SAVED -> HubVideoGrid(
                    videos = saved,
                    strings = strings,
                    statusOf = { statusOf(it, alwaysShow = true) },
                    menuOf = { video ->
                        listOf(
                            CardMenuItem(strings.onlineMenuUnsave, Icons.Filled.BookmarkRemove, destructive = true) { viewModel.removeSaved(video.id) },
                            copyItem(video),
                            CardMenuItem(strings.onlineMenuReextract, Icons.Filled.Refresh) { viewModel.reextract(video) }
                        )
                    },
                    onOpen = { viewModel.openVideo(it) },
                    header = {
                        if (saved.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "__empty") {
                                EmptyState(
                                    icon = Icons.Filled.BookmarkBorder,
                                    title = strings.onlineSavedEmptyTitle,
                                    description = strings.onlineSavedEmptyDesc,
                                    modifier = Modifier.padding(vertical = 32.dp)
                                )
                            }
                        }
                    }
                )
                OnlineVideoViewModel.HubTab.HISTORY -> HubVideoGrid(
                    videos = history,
                    strings = strings,
                    statusOf = { statusOf(it, alwaysShow = true) },
                    menuOf = { video ->
                        listOf(
                            saveToggleItem(video),
                            copyItem(video),
                            CardMenuItem(strings.onlineMenuRemoveHistory, Icons.Filled.Delete, destructive = true) { viewModel.removeFromHistory(video.id) }
                        )
                    },
                    onOpen = { viewModel.openVideo(it) },
                    header = {
                        if (history.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "__empty") {
                                EmptyState(
                                    icon = Icons.Filled.History,
                                    title = strings.onlineHistoryEmptyTitle,
                                    description = strings.onlineHistoryEmptyDesc,
                                    modifier = Modifier.padding(vertical = 32.dp)
                                )
                            }
                        } else {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "__clear") {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { viewModel.clearHistory() }) {
                                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.onlineClearHistory)
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    if (showFollow) {
        FollowChannelDialog(
            strings = strings,
            viewModel = viewModel,
            onDismiss = { showFollow = false; viewModel.clearChannelResults() }
        )
    }

    channelToUnfollow?.let { ch ->
        AlertDialog(
            onDismissRequest = { channelToUnfollow = null },
            title = { Text(strings.onlineUnfollowConfirmTitle(ch.name), fontWeight = FontWeight.Bold) },
            text = { Text(strings.onlineUnfollowConfirmDesc) },
            confirmButton = {
                TextButton(onClick = { viewModel.unfollowChannel(ch); channelToUnfollow = null }) {
                    Text(strings.onlineUnfollowBtn, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { channelToUnfollow = null }) { Text(strings.cancel) } }
        )
    }
}

private fun readClipboardText(context: Context): String? = try {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
} catch (e: Exception) {
    null
}

/** Floating search / paste-a-link bar. */
@Composable
private fun HubSearchBar(
    query: String,
    hint: String,
    strings: AppStrings,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onPaste: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(28.dp),
        color = scheme.surface,
        contentColor = scheme.onSurface,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f).padding(vertical = 15.dp), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(hint, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.fillMaxWidth().testTag("onlineHubSearch")
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = strings.close, tint = scheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onPaste, modifier = Modifier.testTag("btnOnlinePaste")) {
                Icon(Icons.Filled.ContentPaste, contentDescription = strings.onlinePasteCd, tint = scheme.primary)
            }
        }
    }
}

/** ⭐ Following · 📑 Saved · 🕒 History. */
@Composable
private fun HubTabs(
    selected: OnlineVideoViewModel.HubTab,
    strings: AppStrings,
    savedCount: Int,
    onSelect: (OnlineVideoViewModel.HubTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HubTabPill(
            icon = Icons.Filled.Star,
            label = strings.onlineTabFollowing,
            selected = selected == OnlineVideoViewModel.HubTab.FOLLOWING,
            onClick = { onSelect(OnlineVideoViewModel.HubTab.FOLLOWING) },
            modifier = Modifier.weight(1f)
        )
        HubTabPill(
            icon = Icons.Filled.Bookmarks,
            label = if (savedCount > 0) "${strings.onlineTabSaved} · $savedCount" else strings.onlineTabSaved,
            selected = selected == OnlineVideoViewModel.HubTab.SAVED,
            onClick = { onSelect(OnlineVideoViewModel.HubTab.SAVED) },
            modifier = Modifier.weight(1f)
        )
        HubTabPill(
            icon = Icons.Filled.History,
            label = strings.onlineTabHistory,
            selected = selected == OnlineVideoViewModel.HubTab.HISTORY,
            onClick = { onSelect(OnlineVideoViewModel.HubTab.HISTORY) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HubTabPill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) scheme.primary else scheme.surface,
        tween(OnlineStudioTokens.DURATION_MS),
        label = "hubTabBg"
    )
    val content by animateColorAsState(
        if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        tween(OnlineStudioTokens.DURATION_MS),
        label = "hubTabFg"
    )
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
        border = if (selected) null else BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Responsive card grid (two columns on a phone, more on wide screens). */
@Composable
private fun HubVideoGrid(
    videos: List<OnlineVideo>,
    strings: AppStrings,
    statusOf: (OnlineVideo) -> CardStatus?,
    menuOf: (OnlineVideo) -> List<CardMenuItem>,
    onOpen: (OnlineVideo) -> Unit,
    header: LazyGridScope.() -> Unit = {},
    footer: LazyGridScope.() -> Unit = {}
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 158.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        header()
        items(videos, key = { it.id }) { video ->
            HubVideoCard(
                video = video,
                strings = strings,
                status = statusOf(video),
                menu = menuOf(video),
                onClick = { onOpen(video) }
            )
        }
        footer()
    }
}

/** Horizontal avatar tray of the followed channels. */
@Composable
private fun ChannelTray(
    channels: List<OnlineChannel>,
    selectedId: String?,
    feedSelected: Boolean,
    newIds: Set<String>,
    strings: AppStrings,
    onFollow: () -> Unit,
    onAll: () -> Unit,
    onChannel: (OnlineChannel) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    LazyRow(
        // The tray's edge swipe must stay inside the tray: without the
        // confined-swipe guard the leftover drag escaped to the tab pager
        // and flipped the tab («رفتن به تب بعدی»).
        modifier = Modifier.nestedScroll(rememberConfinedSwipeConnection()),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "__follow") {
            TrayItem(label = strings.onlineTrayFollow, selected = false, onClick = onFollow, modifier = Modifier.testTag("btnFollowChannelTray")) {
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = scheme.primary)
                }
            }
        }
        item(key = "__all") {
            TrayItem(label = strings.onlineTrayAll, selected = feedSelected, onClick = onAll) {
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(scheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Subscriptions, contentDescription = null, tint = scheme.onSurfaceVariant)
                }
            }
        }
        items(channels, key = { it.id }) { ch ->
            TrayItem(
                label = ch.name,
                selected = ch.id == selectedId,
                showDot = ch.id in newIds,
                dotDescription = strings.onlineChannelNewCd,
                onClick = { onChannel(ch) }
            ) {
                ChannelAvatar(url = ch.avatarUrl, name = ch.name, size = 52.dp)
            }
        }
    }
}

@Composable
private fun TrayItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDot: Boolean = false,
    dotDescription: String? = null,
    avatar: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, tween(OnlineStudioTokens.DURATION_MS), label = "trayScale")
    val ring by animateColorAsState(
        if (selected) scheme.primary else scheme.primary.copy(alpha = 0.45f),
        tween(OnlineStudioTokens.DURATION_MS),
        label = "trayRing"
    )
    Column(
        modifier = modifier
            .width(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(if (selected) 3.dp else 2.dp, ring, CircleShape),
                contentAlignment = Alignment.Center
            ) { avatar() }
            if (showDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(scheme.surface)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(scheme.error)
                        .semantics { contentDescription = dotDescription ?: "" }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
        )
    }
}

@Composable
private fun ChannelAvatar(url: String?, name: String, size: androidx.compose.ui.unit.Dp) {
    val scheme = MaterialTheme.colorScheme
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape).background(scheme.surfaceVariant)
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(scheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.trim().take(1).uppercase(),
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
                color = scheme.primary
            )
        }
    }
}

/** A video card of the hub grid (`.video-card`). */
@Composable
private fun HubVideoCard(
    video: OnlineVideo,
    strings: AppStrings,
    status: CardStatus?,
    menu: List<CardMenuItem>,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lift by animateFloatAsState(if (pressed) 0.97f else 1f, tween(160), label = "cardPress")
    val borderColor by animateColorAsState(
        if (pressed) scheme.primary else scheme.outlineVariant,
        tween(OnlineStudioTokens.DURATION_MS),
        label = "cardBorder"
    )
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = lift; scaleY = lift },
        shape = RoundedCornerShape(OnlineStudioTokens.radius),
        color = scheme.surface,
        contentColor = scheme.onSurface,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 2.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(OnlineStudioTokens.radiusSmall))
                    .background(scheme.surfaceVariant)
            ) {
                if (video.thumbnailUrl != null) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).size(32.dp)
                    )
                }
                val badge = when {
                    video.isLive -> strings.onlineLiveBadge
                    video.lengthSeconds > 0 -> formatTime(video.lengthSeconds.toDouble())
                    else -> null
                }
                if (badge != null) {
                    // Always dark on the picture, in Day and Night mode alike.
                    Text(
                        badge,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(start = 10.dp, end = 2.dp, bottom = 10.dp)) {
                Text(
                    video.title.ifBlank { video.id },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.sp,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        video.author.ifBlank { "YouTube" },
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (menu.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.MoreVert, contentDescription = strings.onlineMoreCd, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                menu.forEach { item ->
                                    val tint = if (item.destructive) scheme.error else scheme.onSurface
                                    DropdownMenuItem(
                                        text = { Text(item.label, color = tint) },
                                        leadingIcon = { Icon(item.icon, contentDescription = null, tint = tint) },
                                        onClick = {
                                            menuOpen = false
                                            item.onClick()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                val meta = listOfNotNull(
                    video.viewCount?.let { strings.onlineViews(compactCount(it)) },
                    video.publishedText
                ).joinToString(" · ")
                if (status == null && meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (status != null) {
                    Spacer(Modifier.height(2.dp))
                    StatusPill(
                        text = status.label,
                        icon = if (status.smart) Icons.Filled.AutoAwesome else Icons.Filled.Subtitles,
                        tone = if (status.smart) PillTone.Positive else PillTone.Neutral
                    )
                }
            }
        }
    }
}

private fun compactCount(n: Long): String = when {
    n >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", n / 1e9)
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1e6)
    n >= 1_000 -> String.format(Locale.US, "%.0fK", n / 1e3)
    else -> n.toString()
}

/** How long a direct stream may take to start before the next source is tried. */
private const val DIRECT_STARTUP_TIMEOUT_MS = 25_000L

/** How long the YouTube embed may take to become ready before it counts as failed. */
private const val WEB_STARTUP_TIMEOUT_MS = 30_000L

// ─────────────────────────── Player ───────────────────────────

@Composable
private fun OnlinePlayerPane(
    appViewModel: AppViewModel,
    viewModel: OnlineVideoViewModel,
    strings: AppStrings,
    appLanguage: AppLanguage,
    isFullScreen: Boolean,
    onFullScreenToggle: (Boolean) -> Unit,
    focusMode: Boolean,
    onFocusModeToggle: () -> Unit,
    jsonPackage: JsonSubtitlePackage?,
    useDictionaryWithJson: Boolean,
    pauseForLesson: Boolean
) {
    val context = LocalContext.current
    // Same shared study controls as the Video tab, in the same wording.
    val studyLabels = remember(appLanguage) { SentenceStudyLabels(appLanguage == AppLanguage.FA) }
    val savedSentenceTexts by appViewModel.savedSentenceTexts.collectAsState()
    val details by viewModel.details.collectAsState()
    val isLoadingVideo by viewModel.isLoadingVideo.collectAsState()
    val videoError by viewModel.videoError.collectAsState()
    val captions by viewModel.captions.collectAsState()
    val captionTracks by viewModel.captionTracks.collectAsState()
    val captionStatus by viewModel.captionStatus.collectAsState()
    val translationCaptions by viewModel.translationCaptions.collectAsState()
    val activeTrack by viewModel.activeTrack.collectAsState()
    val translationTrack by viewModel.translationTrack.collectAsState()
    val isLoadingCaptions by viewModel.isLoadingCaptions.collectAsState()
    val captionOffset by viewModel.captionOffset.collectAsState()
    val translationOffset by viewModel.translationOffset.collectAsState()
    val isTranslating by viewModel.isTranslating.collectAsState()
    val translatingIndex by viewModel.translatingIndex.collectAsState()
    val translateError by viewModel.translateError.collectAsState()
    // Subtitle files imported from the device (the action drawer's import
    // buttons); each overrides the online caption track of its slot.
    val importedMain by viewModel.importedMain.collectAsState()
    val importedTranslation by viewModel.importedTranslation.collectAsState()
    val jsonOffset by appViewModel.jsonOffset.collectAsState()
    val jsonSubFileName by appViewModel.jsonSubFileName.collectAsState()
    val webFallbackVideo by viewModel.webFallbackVideo.collectAsState()
    val webFallbackReason by viewModel.webFallbackReason.collectAsState()
    val webFallbackError by viewModel.webFallbackError.collectAsState()

    var showCaptionPicker by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val d = details
    val streams = d?.progressiveStreams ?: emptyList()
    // «پخش در حالت جایگزین»: the YouTube embed instead of a direct stream.
    val webVideo = webFallbackVideo
    val webId = webVideo?.id
    // Muxed (video+audio in one file) 720p/360p MP4 first: it starts at
    // once and cannot end up silent — unless the watch-cache manager chose
    // a default quality («کیفیت پیش‌فرض»), which every newly opened clip
    // then plays and saves with.
    val defaultQualityHeight by viewModel.defaultQualityHeight.collectAsState()
    var selectedQuality by remember(d?.video?.id, defaultQualityHeight) {
        mutableStateOf(OnlineWatchCache.pickDefaultStreamIndex(streams, defaultQualityHeight).coerceAtLeast(0))
    }
    /*
     * Public instances often return signed URLs that expire or that
     * googlevideo rejects with HTTP 403. Keep a short, deterministic
     * fallback chain per instance: the selected rendition, the other muxed
     * files, the instance's HLS/DASH manifests, then adaptive video+audio
     * pairs (see OnlineStreamSelector). VideoPlayerScreen reports a media
     * error — or a start-up timeout — so we can advance without making the
     * user reopen the clip; when the chain is exhausted the ViewModel moves
     * to the next instance, and eventually to the fallback web player.
     */
    val playbackSources = remember(
        d?.video?.id,
        selectedQuality,
        d?.progressiveStreams,
        d?.hlsUrl,
        d?.dashUrl
    ) {
        if (d == null) emptyList() else OnlineStreamSelector.playbackSources(d, selectedQuality)
    }
    var playbackAttempt by remember(d?.video?.id, selectedQuality) { mutableStateOf(0) }
    var playbackError by remember(d?.video?.id) { mutableStateOf<String?>(null) }
    val playbackSource = playbackSources.getOrNull(playbackAttempt)
    val playbackUrl = playbackSource?.url
    val playbackAudioUrl = playbackSource?.audioUrl
    val mimeType = playbackSource?.mimeType
    val playbackExhausted = webId == null && playbackError != null && playbackAttempt >= playbackSources.lastIndex
    val visiblePlaybackError = if (webId != null) webFallbackError?.let { strings.onlineFallbackFailed + "\n" + it } else videoError ?: playbackError

    // A retry from the repository gives us fresh signed URLs and starts the
    // fallback chain over from the preferred rendition.
    LaunchedEffect(d?.video?.id, playbackUrl) {
        playbackError = null
    }
    // In fallback mode the player loads the clip by id; the watch URL is only
    // a stable placeholder so the player chrome and resume key work as usual.
    val videoUri = remember(playbackUrl, webId) {
        if (webId != null) Uri.parse("https://www.youtube.com/watch?v=$webId") else playbackUrl?.let { Uri.parse(it) }
    }
    val audioUri = remember(playbackAudioUrl, webId) { if (webId != null) null else playbackAudioUrl?.let { Uri.parse(it) } }
    val shownVideo = d?.video ?: webVideo

    val savedVideos by viewModel.saved.collectAsState()
    val isBookmarked = shownVideo != null && savedVideos.any { it.id == shownVideo.id }

    /*
     * «ذخیره هنگام پخش»: on by default. The parts of the clip that get
     * downloaded for viewing are written to the disk cache as they are
     * watched — so replays and later viewings of those parts no longer
     * depend on the instance or on the signed URL still being alive — and
     * the clip's subtitle tracks are persisted alongside them. The choice
     * is remembered app-wide.
     */
    // ONE source of truth with the watch-cache manager: the ViewModel's
    // pref-backed flow. Flipping it here or there stays in sync everywhere.
    val saveWhilePlaying by viewModel.saveWhilePlaying.collectAsState()
    val onToggleSaveWhilePlaying = { viewModel.setSaveWhilePlaying(!saveWhilePlaying) }

    /*
     * Stable cross-session cache keys for the player's streams: the signed
     * googlevideo URLs rotate between sessions, so the key is derived from
     * the video id plus the rendition's identity (container, muxed/dash,
     * quality label). The adaptive pair's audio track is one shared file,
     * so it gets a single per-video key. URLs that match nothing (HLS/DASH
     * manifests and their segments) keep their own address as the key.
     */
    val streamCacheKeyFor: (Uri) -> String? = remember(d?.video?.id, d?.progressiveStreams) {
        val videoId = d?.video?.id
        val streams = if (d != null) d.progressiveStreams else emptyList()
        // Every raw media URL the player may open for this clip, mapped to
        // the stable cache key of its rendition: muxed URLs directly, and —
        // crucially — the URLs inside the locally built DASH manifests (the
        // data: URIs whose <BaseURL> entries are the real addresses; the
        // pair's audio maps to the one shared audio key). Without this map
        // the adaptive bytes were cached under keys the storage index never
        // listed, so the manager showed nothing saved.
        val rawUrlKeys = buildMap<String, String> {
            if (videoId != null) {
                streams.forEach { stream ->
                    val key = OnlineWatchCache.streamKey(videoId, stream)
                    if (stream.url.startsWith("http")) {
                        put(stream.url, key)
                    } else {
                        OnlineWatchCache.dashManifestUrls(stream.url).forEachIndexed { index, rawUrl ->
                            put(rawUrl, if (index == 0) key else OnlineWatchCache.audioKey(videoId))
                        }
                    }
                    stream.audioUrl?.let { put(it, OnlineWatchCache.audioKey(videoId)) }
                }
            }
        }
        val resolver: (Uri) -> String? = { uri -> rawUrlKeys[uri.toString()] }
        resolver
    }
    val trackLabel = when {
        activeTrack != null && captions.isNotEmpty() -> shortTrackLabel(activeTrack!!)
        isLoadingCaptions || captionStatus == OnlineVideoViewModel.CaptionStatus.LOADING -> strings.onlineCaptionsLoadingChip
        captionStatus == OnlineVideoViewModel.CaptionStatus.NONE ||
            captionStatus == OnlineVideoViewModel.CaptionStatus.FAILED -> strings.onlineNoCaptionsAvailable
        else -> strings.onlineNoCaptions
    }
    // «کپی خام زیرنویس» lives in the action drawer now (the old prompt-wrapped
    // copy-for-AI was replaced by a raw-only button); the drawer's import
    // buttons reuse the Video tab's document-picker pattern.
    val importMainLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.importMainSubtitle(it) }
    }
    val importTranslationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.importTranslationSubtitle(it) }
    }
    // The JSON attach chooser opens the same GlassCard popup as the subtitle
    // slots: pick the clip's learning JSON file, or paste it from the
    // clipboard. Both write into the clip's own online_json scope.
    var onlineJsonAttach by remember { mutableStateOf(false) }
    val importJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { appViewModel.loadJsonSubtitleFromUri(it) }
    }
    val onlineJsonFileName by appViewModel.jsonSubFileName.collectAsState()
    // The clip's own attach chooser: null = closed, 0 = primary subtitle,
    // 1 = secondary subtitle. Both slots open the same two-option chooser the
    // Video tab's import slots use (pick a file / paste from the clipboard).
    var onlineAttachTarget by remember { mutableStateOf<Int?>(null) }
    val clipboard = LocalClipboardManager.current
    // An imported file overrides the online caption track of its slot, the
    // same precedence a picked file has on the Video tab.
    val effectiveSubEn = importedMain?.entries ?: captions
    val effectiveSubFa = importedTranslation?.entries ?: translationCaptions

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isFullScreen && !focusMode) {
            // Slim header: back · title/author · info. Player actions live in the transcript FAB.
            // (Deliberately NOT mirrored for Persian: this header was fine
            // as it was — back left, save switch and info right.)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SoftIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, onClick = { viewModel.closeVideo() })
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        shownVideo?.title?.takeIf { it.isNotBlank() } ?: strings.onlineLoadingVideo,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val source = when {
                        webId != null -> strings.onlineFallbackHost
                        InnerTubeClient.isDirect(d?.instance) -> strings.onlineDirectSource
                        else -> d?.instance?.host
                    }
                    val sub = listOfNotNull(shownVideo?.author?.takeIf { it.isNotBlank() }, source).joinToString(" · ")
                    if (sub.isNotBlank()) {
                        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                // «ذخیره هنگام پخش»: the top-of-screen switch that keeps the
                // watched parts of the clip (and its subtitles) on disk.
                Surface(
                    onClick = onToggleSaveWhilePlaying,
                    shape = RoundedCornerShape(50),
                    color = if (saveWhilePlaying) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    },
                    contentColor = if (saveWhilePlaying) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (saveWhilePlaying) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.testTag("toggleSaveWhilePlaying")
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = strings.onlineSaveWhilePlayingCd,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = strings.onlineSaveWhilePlaying,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = { showInfo = true }) {
                    Icon(Icons.Outlined.Info, contentDescription = strings.onlineVideoInfo, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (webId != null && webFallbackError == null) {
                Text(
                    text = strings.onlineFallbackActive + (webFallbackReason?.let { "\n" + it.lineSequence().first() } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                isLoadingVideo && d == null && webId == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(strings.onlineResolvingStream, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                (visiblePlaybackError != null && videoUri == null) || playbackExhausted ||
                    (webId != null && webFallbackError != null) -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        EmptyState(
                            icon = Icons.Filled.Public,
                            title = strings.onlineErrorTitle,
                            description = visiblePlaybackError ?: strings.onlineErrorTitle
                        )
                        Spacer(Modifier.height(12.dp))
                        val failed = d
                        if (failed != null) {
                            GradientButton(text = strings.onlineRetry, icon = Icons.Filled.Refresh, onClick = { viewModel.openVideoId(failed.video.id, failed.video) })
                        } else if (webId != null) {
                            GradientButton(text = strings.onlineDirectPlayback, icon = Icons.Filled.Refresh, onClick = { viewModel.retryDirectPlayback() })
                        }
                        // Direct streams failed: the embed is one tap away.
                        if (webId == null && (failed != null || viewModel.pendingVideoId != null)) {
                            Spacer(Modifier.height(8.dp))
                            GradientButton(text = strings.onlineFallbackPlayer, icon = Icons.Filled.Public, onClick = { viewModel.openWebFallback() })
                        }
                        // When every instance is rate-limited the clip can still be
                        // watched in whatever app handles YouTube links (a browser,
                        // NewPipe, …); the caption tools simply stay unavailable.
                        val watchUrl = failed?.video?.watchUrl ?: webVideo?.watchUrl ?: viewModel.pendingVideoId?.let { "https://www.youtube.com/watch?v=$it" }
                        if (watchUrl != null) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = {
                                try {
                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(watchUrl)))
                                } catch (e: Exception) {
                                    Toast.makeText(context, strings.errorWithMessage(e.message), Toast.LENGTH_SHORT).show()
                                }
                            }) { Text(strings.onlineOpenExternally) }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.closeVideo() }) { Text(strings.back) }
                    }
                }
                else -> {
                    VideoPlayerScreen(
                        videoUri = videoUri,
                        videoFileName = shownVideo?.title ?: "",
                        subEnList = effectiveSubEn,
                        subFaList = effectiveSubFa,
                        isFullScreen = isFullScreen,
                        onFullScreenToggle = onFullScreenToggle,
                        onWordClick = { word, enContext, faContext ->
                            if (jsonPackage != null && !useDictionaryWithJson) {
                                appViewModel.openWordLesson(word, enContext ?: "", faContext)
                            } else {
                                appViewModel.lookupWord(word, enContext, faContext)
                            }
                        },
                        onSentenceClick = { sentence, translation -> appViewModel.openSentenceLesson(sentence, translation) },
                        // The shared study controls, exactly as on the Video
                        // tab and in the book reader.
                        studyLabels = studyLabels,
                        savedSentenceTexts = savedSentenceTexts,
                        onAddSentenceToLeitner = { sub, timeLabel, _ ->
                            appViewModel.addSentenceToLeitner(SentenceCardFactory.fromSubtitle(sub, timeLabel))
                        },
                        onShiftSubEn = { viewModel.shiftCaptions(it) },
                        onShiftSubFa = { viewModel.shiftTranslationCaptions(it) },
                        subEnOffset = captionOffset,
                        subFaOffset = translationOffset,
                        onTranslateSubtitle = { index ->
                            // Same AI settings as the Video tab (Settings ▸ AI tab).
                            val aiPrefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
                            viewModel.translateCaption(
                                index = index,
                                baseUrl = aiPrefs.getString("base_url", "http://localhost:20128/v1") ?: "http://localhost:20128/v1",
                                apiKey = aiPrefs.getString("api_key", "") ?: "",
                                model = aiPrefs.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini",
                                targetLang = aiPrefs.getString("target_lang", "Persian") ?: "Persian"
                            )
                        },
                        onSaveSrt = { viewModel.exportTranslation() },
                        isTranslatingSingle = isTranslating,
                        translatingIndex = translatingIndex,
                        singleTranslateError = translateError,
                        onStopTranslation = { viewModel.stopTranslation() },
                        appLanguage = appLanguage,
                        jsonPackage = jsonPackage,
                        jsonOffset = jsonOffset,
                        onShiftJson = { appViewModel.shiftJson(it) },
                        onResetJson = { appViewModel.resetJson() },
                        focusMode = focusMode,
                        onFocusModeToggle = onFocusModeToggle,
                        streamMimeType = if (webId != null) null else mimeType,
                        streamAudioUri = audioUri,
                        pauseForLesson = pauseForLesson,
                        resumeStateKey = (d?.video?.id ?: webId)?.let { "online_video_state_$it" },
                        webFallbackVideoId = webId,
                        showOrientationToggle = true,
                        onlineStudio = true,
                        overlayActions = {
                            if (shownVideo != null) {
                                PlayerGlassButton(
                                    icon = if (isBookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = strings.onlineBookmarkCd,
                                    onClick = { viewModel.currentVideo()?.let { viewModel.toggleSaved(it) } },
                                    active = isBookmarked,
                                    modifier = Modifier.testTag("btnOnlineBookmark")
                                )
                            }
                        },
                        streamQualities = if (webId == null) streams.map { stream ->
                            // Some instances return streams without any
                            // label field; the height still makes a usable,
                            // visible menu entry (and tile sub-label).
                            stream.qualityLabel.ifBlank { if (stream.height > 0) "${stream.height}p" else "—" }
                        } else emptyList(),
                        selectedStreamQuality = selectedQuality,
                        onSelectStreamQuality = { index -> selectedQuality = index.coerceIn(0, streams.lastIndex) },
                        // The action drawer beneath the video: the ⭐ save
                        // toggle on its handle, raw copy, and the subtitle /
                        // JSON imports (each opens the same popup design:
                        // pick a file, or paste from the clipboard).
                        // The embed fallback cannot be cached (it plays in
                        // a WebView); captions still are, via the ViewModel.
                        saveWhilePlaying = saveWhilePlaying && webId == null,
                        streamCacheKeyFor = streamCacheKeyFor,
                        onlineDrawerActions = OnlineDrawerActions(
                            isBookmarked = isBookmarked,
                            onToggleBookmark = { viewModel.currentVideo()?.let { video -> viewModel.toggleSaved(video) } },
                            onCopyRawSubtitle = { viewModel.copyRawSubtitle() },
                            onImportMainSubtitle = { onlineAttachTarget = 0 },
                            onImportTranslationSubtitle = { onlineAttachTarget = 1 },
                            onImportJsonSubtitle = { onlineJsonAttach = true },
                            mainAttachedName = importedMain?.fileName,
                            translationAttachedName = importedTranslation?.fileName,
                        ),
                        // The «کارهای دیگر پخش‌کننده» list (the drawer's own
                        // sheet): the bookmark lives on the drawer handle
                        // now, so the list carries the remaining tasks.
                        fabActions = buildList {
                            add(
                                PlayerFabAction(
                                    label = strings.onlineCaptionsMenu + " · " + trackLabel,
                                    icon = Icons.Filled.ClosedCaption,
                                    enabled = captionTracks.isNotEmpty(),
                                    active = activeTrack != null && captions.isNotEmpty(),
                                    onClick = { showCaptionPicker = true },
                                )
                            )
                            add(PlayerFabAction(strings.onlineExportChip, Icons.Filled.Download, { showExportMenu = true }, enabled = captions.isNotEmpty()))
                            if (shownVideo != null || viewModel.pendingVideoId != null) {
                                add(
                                    PlayerFabAction(
                                        label = if (webId != null) strings.onlineDirectPlayback else strings.onlineFallbackPlayer,
                                        icon = if (webId != null) Icons.Filled.Refresh else Icons.Filled.Public,
                                        onClick = { if (webId != null) viewModel.retryDirectPlayback() else viewModel.openWebFallback() },
                                    )
                                )
                            }
                            shownVideo?.let { video ->
                                add(PlayerFabAction(strings.onlineCopyLink, Icons.Filled.Link, { viewModel.copyLink(video) }))
                            }
                        },
                        startupTimeoutMs = if (webId != null) WEB_STARTUP_TIMEOUT_MS else DIRECT_STARTUP_TIMEOUT_MS,
                        onPlaybackError = { error ->
                            if (webId != null) {
                                // The embed failed too: show the error screen
                                // (retry direct / open in another app).
                                viewModel.reportWebFallbackError(error)
                            } else if (playbackAttempt < playbackSources.lastIndex) {
                                // Next source on the same instance.
                                playbackAttempt += 1
                            } else {
                                val failedDetails = d
                                if (failedDetails != null) {
                                    // Next instance (and, after a few, the embed).
                                    viewModel.retryVideoAfterPlaybackFailure(
                                        failedInstance = failedDetails.instance,
                                        video = failedDetails.video,
                                        error = error
                                    )
                                } else {
                                    playbackError = error
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // Caption track picker.
    // The clip's learning-JSON attach chooser — the same popup design as
    // the primary and secondary subtitle choosers below.
    if (onlineJsonAttach) {
        OnlineAttachChooserDialog(
            strings = strings,
            title = strings.addJsonSubtitleTitle(strings.subJsonLabel),
            loadedFileName = onlineJsonFileName.takeIf { it.isNotBlank() },
            fileOptionTitle = strings.selectJsonFileOption,
            fileOptionDesc = strings.selectJsonFileDesc,
            pasteOptionTitle = strings.pasteJsonOption,
            pasteOptionDesc = strings.pasteJsonDesc,
            onPickFile = {
                onlineJsonAttach = false
                importJsonLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            },
            onPasteClipboard = {
                onlineJsonAttach = false
                appViewModel.loadJsonFromClipboard()
            },
            onDismiss = { onlineJsonAttach = false }
        )
    }

    // The clip's attach chooser (primary / secondary subtitle): the same
    // two options the Video tab's import slots offer — pick a file, or paste
    // the subtitle straight from the clipboard.
    onlineAttachTarget?.let { target ->
        val isMain = target == 0
        OnlineAttachChooserDialog(
            strings = strings,
            title = if (isMain) strings.onlineImportMainBtn else strings.onlineImportTranslationBtn,
            loadedFileName = if (isMain) importedMain?.fileName else importedTranslation?.fileName,
            onPickFile = {
                onlineAttachTarget = null
                if (isMain) importMainLauncher.launch(arrayOf("*/*"))
                else importTranslationLauncher.launch(arrayOf("*/*"))
            },
            onPasteClipboard = {
                val text = clipboard.getText()?.text ?: ""
                onlineAttachTarget = null
                if (text.isNotBlank()) viewModel.importSubtitleText(text, isMain)
            },
            onDismiss = { onlineAttachTarget = null }
        )
    }

    if (showCaptionPicker && captionTracks.isNotEmpty()) {
        CaptionPickerDialog(
            strings = strings,
            tracks = captionTracks,
            activeTrack = activeTrack,
            translationTrack = translationTrack,
            onPickMain = { viewModel.loadCaptionTrack(it); showCaptionPicker = false },
            onPickTranslation = { viewModel.loadTranslationTrack(it) },
            onClearMain = { viewModel.clearCaptions(); showCaptionPicker = false },
            onDismiss = { showCaptionPicker = false }
        )
    }

    // Export / copy menu.
    if (showExportMenu) {
        AlertDialog(
            onDismissRequest = { showExportMenu = false },
            title = { Text(strings.onlineExportTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.onlineExportDesc(captions.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ExportOption(Icons.Filled.ContentCopy, strings.onlineCopySrt) { viewModel.copyCaptions(OnlineVideoViewModel.CaptionFormat.SRT); showExportMenu = false }
                    ExportOption(Icons.Filled.ContentCopy, strings.onlineCopyText) { viewModel.copyCaptions(OnlineVideoViewModel.CaptionFormat.TEXT); showExportMenu = false }
                    ExportOption(Icons.Filled.Download, strings.onlineSaveSrt) { viewModel.exportCaptions(OnlineVideoViewModel.CaptionFormat.SRT); showExportMenu = false }
                    ExportOption(Icons.Filled.Download, strings.onlineSaveText) { viewModel.exportCaptions(OnlineVideoViewModel.CaptionFormat.TEXT); showExportMenu = false }
                    ExportOption(Icons.Filled.Subtitles, strings.onlineSendToVideoTab) {
                        appViewModel.loadSubtitlesFromOnline(captions, translationCaptions, d?.video?.title ?: "online")
                        showExportMenu = false
                    }
                    ExportOption(Icons.Filled.Link, strings.onlineCopyLink) { viewModel.copyVideoLink(); showExportMenu = false }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showExportMenu = false }) { Text(strings.close) } }
        )
    }

    if (showInfo && d != null) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(d.video.title, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(
                            text = if (InnerTubeClient.isDirect(d.instance)) strings.onlineDirectSource else d.instance.kind.label + " · " + d.instance.host,
                            tone = PillTone.Accent
                        )
                        if (jsonPackage != null) StatusPill(text = jsonSubFileName.ifBlank { strings.subJsonLabel }, tone = PillTone.Positive)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(d.video.author, style = MaterialTheme.typography.titleSmall)
                    val meta = listOfNotNull(
                        d.video.viewCount?.let { strings.onlineViews(compactCount(it)) },
                        d.video.publishedText,
                        if (d.video.lengthSeconds > 0) formatTime(d.video.lengthSeconds.toDouble()) else null
                    ).joinToString(" · ")
                    if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(d.description.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(strings.onlineStreamsInfo(d.progressiveStreams.size, d.hlsUrl != null, d.captions.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                d.video.authorId?.let { authorId ->
                    val followed = viewModel.isFollowed(authorId)
                    TextButton(onClick = {
                        val ch = OnlineChannel(authorId, d.video.author.ifBlank { authorId })
                        if (followed) viewModel.unfollowChannel(ch) else viewModel.followChannel(ch)
                        showInfo = false
                    }) { Text(if (followed) strings.onlineUnfollowBtn else strings.onlineFollowBtn) }
                }
            },
            dismissButton = { TextButton(onClick = { showInfo = false }) { Text(strings.close) } }
        )
    }
}

@Composable
private fun ExportOption(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun shortTrackLabel(track: OnlineCaptionTrack): String {
    val base = track.label.ifBlank { track.languageCode }
    return if (base.length > 22) base.take(21) + "…" else base
}

@Composable
private fun CaptionPickerDialog(
    strings: AppStrings,
    tracks: List<OnlineCaptionTrack>,
    activeTrack: OnlineCaptionTrack?,
    translationTrack: OnlineCaptionTrack?,
    onPickMain: (OnlineCaptionTrack) -> Unit,
    onPickTranslation: (OnlineCaptionTrack?) -> Unit,
    onClearMain: () -> Unit,
    onDismiss: () -> Unit
) {
    // Two modes in one dialog: which track is the main (source-language)
    // list, and which one (optional) is shown as the translation line.
    var pickingTranslation by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (pickingTranslation) strings.onlinePickTranslationTitle else strings.onlinePickCaptionTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !pickingTranslation, onClick = { pickingTranslation = false }, label = { Text(strings.onlineMainTrackChip) })
                    FilterChip(selected = pickingTranslation, onClick = { pickingTranslation = true }, label = { Text(strings.onlineTranslationTrackChip) })
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (pickingTranslation) strings.onlinePickTranslationDesc else strings.onlinePickCaptionDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    if (pickingTranslation) {
                        item {
                            TrackRow(
                                label = strings.onlineNoTranslationTrack,
                                sub = null,
                                selected = translationTrack == null,
                                onClick = { onPickTranslation(null) }
                            )
                        }
                    }
                    itemsIndexed(tracks) { _, t ->
                        val selected = if (pickingTranslation) translationTrack?.url == t.url else activeTrack?.url == t.url
                        TrackRow(
                            label = t.label.ifBlank { t.languageCode },
                            sub = listOfNotNull(t.languageCode.takeIf { it.isNotBlank() }, if (t.autoGenerated) strings.onlineAutoGenerated else null).joinToString(" · ").ifBlank { null },
                            selected = selected,
                            onClick = { if (pickingTranslation) onPickTranslation(t) else onPickMain(t) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!pickingTranslation && activeTrack != null) {
                TextButton(onClick = onClearMain) { Text(strings.onlineHideCaptions) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.close) } }
    )
}

@Composable
private fun TrackRow(label: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────── Dialogs ───────────────────────────

@Composable
private fun InstanceManagerPage(
    strings: AppStrings,
    instances: List<OnlineInstance>,
    preferred: String?,
    health: Map<String, OnlineVideoViewModel.InstanceHealth>,
    isChecking: Boolean,
    /** «تست این اینستنس»: probe just this one, from its own row. */
    onCheckOne: (OnlineInstance) -> Unit,
    onAdd: (String, OnlineInstanceKind?) -> Boolean,
    onRemove: (OnlineInstance) -> Unit,
    onToggle: (OnlineInstance, Boolean) -> Unit,
    onMoveTop: (OnlineInstance) -> Unit,
    onReset: () -> Unit,
    onCheckAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var newUrl by remember { mutableStateOf("") }
    var newKind by remember { mutableStateOf<OnlineInstanceKind?>(null) }
    var addError by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SoftIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = strings.back,
                onClick = onDismiss
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.onlineInstancesTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    strings.onlineInstancesSummary(
                        configured = instances.size,
                        enabled = instances.count { it.enabled }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onCheckAll, enabled = !isChecking) {
                if (isChecking) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text(strings.onlineCheckAll)
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(strings.onlineInstancesDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(strings.onlineFeedFallbackNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, contentPadding = PaddingValues(10.dp)) {
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it; addError = false },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = addError,
                    placeholder = { Text(strings.onlineInstanceUrlPlaceholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingText = if (addError) ({ Text(strings.onlineInstanceUrlInvalid) }) else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (onAdd(newUrl, newKind)) { newUrl = ""; newKind = null } else addError = true
                    }),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = newKind == null, onClick = { newKind = null }, label = { Text(strings.onlineKindAuto) })
                    FilterChip(selected = newKind == OnlineInstanceKind.INVIDIOUS, onClick = { newKind = OnlineInstanceKind.INVIDIOUS }, label = { Text("Invidious") })
                    FilterChip(selected = newKind == OnlineInstanceKind.PIPED, onClick = { newKind = OnlineInstanceKind.PIPED }, label = { Text("Piped") })
                    Spacer(Modifier.weight(1f))
                    SoftIconButton(icon = Icons.Filled.Add, contentDescription = strings.onlineAddInstance, size = 34.dp, onClick = {
                        if (onAdd(newUrl, newKind)) { newUrl = ""; newKind = null } else addError = true
                    })
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(instances, key = { it.baseUrl }) { inst ->
                val probe = health[inst.baseUrl]
                val status = probe?.status ?: OnlineVideoViewModel.InstanceHealthStatus.UNKNOWN
                val statusLabel = when (status) {
                    OnlineVideoViewModel.InstanceHealthStatus.CHECKING -> strings.onlineInstanceChecking
                    OnlineVideoViewModel.InstanceHealthStatus.HEALTHY -> probe?.latencyMs?.let { "$it ms" } ?: strings.onlineInstancePlayable
                    OnlineVideoViewModel.InstanceHealthStatus.FAILED -> strings.onlineInstanceFailedDisabled
                    OnlineVideoViewModel.InstanceHealthStatus.UNKNOWN -> strings.onlineInstanceNotChecked
                }
                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = inst.enabled, onCheckedChange = { onToggle(inst, it) })
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(inst.host, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(inst.kind.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (status) {
                                        OnlineVideoViewModel.InstanceHealthStatus.HEALTHY -> MaterialTheme.colorScheme.primary
                                        OnlineVideoViewModel.InstanceHealthStatus.FAILED -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                if (inst.baseUrl == preferred) StatusPill(text = strings.onlineInstanceActive, tone = PillTone.Positive)
                            }
                            if (status == OnlineVideoViewModel.InstanceHealthStatus.FAILED && !probe?.error.isNullOrBlank()) {
                                Text(
                                    probe?.error.orEmpty().take(90),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        // «تست این اینستنس»: probe one instance on demand;
                        // the row's own spinner replaces the button while it
                        // runs, and nothing gets auto-disabled.
                        IconButton(
                            onClick = { onCheckOne(inst) },
                            enabled = status != OnlineVideoViewModel.InstanceHealthStatus.CHECKING
                        ) {
                            if (status == OnlineVideoViewModel.InstanceHealthStatus.CHECKING) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = strings.onlineCheckInstance, modifier = Modifier.size(20.dp))
                            }
                        }
                        IconButton(onClick = { onMoveTop(inst) }) {
                            Icon(Icons.Filled.VerticalAlignTop, contentDescription = strings.onlineMoveToTop, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { onRemove(inst) }) {
                            Icon(Icons.Filled.Delete, contentDescription = strings.deleteCd, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onReset) { Text(strings.onlineResetInstances) }
            TextButton(onClick = onDismiss) { Text(strings.close) }
        }
    }
}

@Composable
private fun FollowChannelDialog(
    strings: AppStrings,
    viewModel: OnlineVideoViewModel,
    onDismiss: () -> Unit
) {
    val results by viewModel.channelResults.collectAsState()
    val searching by viewModel.isSearchingChannels.collectAsState()
    val channels by viewModel.channels.collectAsState()
    var input by remember { mutableStateOf("") }
    val looksLikeLink = remember(input) {
        OnlineInstanceStore.extractChannelId(input) != null || OnlineInstanceStore.extractChannelHandle(input) != null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.onlineFollowChannelTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 460.dp)) {
                Text(strings.onlineFollowChannelDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(strings.onlineFollowPlaceholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = {
                        if (searching) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else IconButton(onClick = { if (looksLikeLink) { viewModel.followFromInput(input); onDismiss() } else viewModel.searchChannels(input) }) {
                            Icon(if (looksLikeLink) Icons.Filled.Check else Icons.Filled.Search, contentDescription = null)
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (looksLikeLink) { viewModel.followFromInput(input); onDismiss() } else viewModel.searchChannels(input) }),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(8.dp))
                if (results.isEmpty() && !searching) {
                    Text(strings.onlineDefaultChannelsHint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { viewModel.restoreDefaultChannels() }) { Text(strings.onlineRestoreDefaultChannels) }
                }
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(results, key = { it.id }) { ch ->
                        val followed = channels.any { it.id == ch.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { if (followed) viewModel.unfollowChannel(ch) else viewModel.followChannel(ch) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChannelAvatar(url = ch.avatarUrl, name = ch.name, size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ch.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                ch.description?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            }
                            Icon(
                                if (followed) Icons.Filled.Check else Icons.Filled.Add,
                                contentDescription = null,
                                tint = if (followed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.close) } }
    )
}

/**
 * The clip's attach chooser: pick a subtitle file or paste one from the
 * clipboard — the same two options (and the same wording) the Video tab's
 * import slots offer, so attaching a subtitle to an online video feels
 * exactly like attaching one to a local film.
 */
@Composable
private fun OnlineAttachChooserDialog(
    strings: AppStrings,
    title: String,
    loadedFileName: String?,
    onPickFile: () -> Unit,
    onPasteClipboard: () -> Unit,
    onDismiss: () -> Unit,
    // The two option rows' wording — the subtitle labels by default, the
    // JSON labels for the learning-JSON attach.
    fileOptionTitle: String = strings.selectSubtitleFileOption,
    fileOptionDesc: String = strings.selectSubtitleFileDesc,
    pasteOptionTitle: String = strings.pasteFromClipboardOption,
    pasteOptionDesc: String = strings.pasteFromClipboardDesc
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!loadedFileName.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = loadedFileName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(12.dp))
            OnlineAttachOption(
                icon = Icons.Filled.AttachFile,
                title = fileOptionTitle,
                description = fileOptionDesc,
                onClick = onPickFile
            )
            Spacer(Modifier.height(8.dp))
            OnlineAttachOption(
                icon = Icons.Filled.ContentPaste,
                title = pasteOptionTitle,
                description = pasteOptionDesc,
                onClick = onPasteClipboard
            )
        }
    }
}

/** One attach option row: icon circle, title, description. */
@Composable
private fun OnlineAttachOption(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    GlassCard(onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
