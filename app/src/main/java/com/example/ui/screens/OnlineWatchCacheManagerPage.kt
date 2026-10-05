package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.logic.OnlineWatchCache
import com.example.ui.components.EmptyState
import com.example.ui.components.GlassCard
import com.example.ui.components.SoftIconButton
import com.example.ui.components.rememberConfinedSwipeConnection
import com.example.ui.theme.AppStrings
import java.util.Locale

/**
 * «مدیریت حافظه» — the Online tab's watch-cache manager, opened by the
 * storage button next to the instance picker. One page, three controls:
 *
 * 1. The global «ذخیره هنگام پخش» switch — one on/off for ALL videos;
 * 2. The default quality new videos are played and saved with;
 * 3. The storage itself: what each watched clip holds on disk, deleting
 *    one clip or everything at once.
 */
@Composable
internal fun OnlineWatchCacheManagerPage(
    strings: AppStrings,
    saveEnabled: Boolean,
    onToggleSave: (Boolean) -> Unit,
    defaultQualityHeight: Int,
    onPickDefaultQuality: (Int) -> Unit,
    cachedVideos: List<OnlineWatchCache.CachedVideo>,
    /** Tapping a stored clip closes the manager and plays that video. */
    onOpenVideo: (OnlineWatchCache.CachedVideo) -> Unit,
    onDeleteVideo: (String) -> Unit,
    onDeleteAll: () -> Unit,
    onDismiss: () -> Unit
) {
    // The header's total is the sum of what the list itself holds; the
    // manager's delete-all is the way to reclaim every byte at once.
    val totalCacheBytes = cachedVideos.sumOf { it.totalBytes }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header ──
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(strings.onlineSaveWhilePlaying, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text(
                    strings.onlineWatchCacheTotal(formatCacheSize(totalCacheBytes)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onDeleteAll, enabled = cachedVideos.isNotEmpty()) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text(strings.onlineWatchCacheDeleteAll)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── The one switch for every video ──
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                strings.onlineSaveWhilePlaying,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                strings.onlineWatchCacheGlobalDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Switch(checked = saveEnabled, onCheckedChange = onToggleSave)
                    }
                }
            }

            // ── The default play & save quality ──
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, contentPadding = PaddingValues(14.dp)) {
                    Text(strings.onlineWatchCacheDefaultQuality, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        strings.onlineWatchCacheDefaultQualityDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    val options = remember {
                        listOf(
                            OnlineWatchCache.QUALITY_AUTO to "auto",
                            OnlineWatchCache.QUALITY_HIGHEST to "highest",
                            1080 to "1080",
                            720 to "720",
                            480 to "480",
                            360 to "360"
                        )
                    }
                    Row(
                        // Confined swipe: picking a quality must never
                        // escape into a tab turn at the row's edge.
                        modifier = Modifier
                            .nestedScroll(rememberConfinedSwipeConnection())
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        options.forEach { (height, tag) ->
                            val label = when (tag) {
                                "auto" -> strings.onlineQualityAuto
                                "highest" -> strings.onlineQualityHighest
                                else -> "${tag}p"
                            }
                            FilterChip(
                                selected = defaultQualityHeight == height,
                                onClick = { onPickDefaultQuality(height) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // ── The stored videos ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        strings.onlineWatchCacheStorage,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (cachedVideos.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, contentPadding = PaddingValues(20.dp)) {
                        EmptyState(
                            icon = Icons.Filled.Storage,
                            title = strings.onlineWatchCacheEmpty
                        )
                    }
                }
            } else {
                items(cachedVideos, key = { it.videoId }) { video ->
                    // The whole card opens the clip — the watched parts then
                    // serve from the cache — with the thumbnail as the
                    // preview; the bin deletes the stored data.
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        contentPadding = PaddingValues(start = 10.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
                        onClick = { onOpenVideo(video) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = "https://i.ytimg.com/vi/${video.videoId}/mqdefault.jpg",
                                contentDescription = strings.onlineWatchCacheOpenCd,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 96.dp, height = 54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    video.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    formatCacheSize(video.totalBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            SoftIconButton(
                                icon = Icons.Filled.Delete,
                                contentDescription = strings.onlineWatchCacheDeleteOneCd,
                                size = 34.dp,
                                onClick = { onDeleteVideo(video.videoId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 1.2 MB / 3.4 GB — one decimal, the unit the size fits into. */
internal fun formatCacheSize(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        else -> String.format(Locale.US, "%.0f KB", bytes / kb)
    }
}
