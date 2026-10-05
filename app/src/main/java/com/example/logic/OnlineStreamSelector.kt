package com.example.logic

import com.example.model.OnlinePlaybackSource
import com.example.model.OnlineStream
import com.example.model.OnlineVideoDetails

/**
 * Decides which rendition of an online video the player opens first, and
 * which ones it falls back to when a rendition refuses to play.
 *
 * The rule of thumb is "muxed first": a progressive MP4 that carries BOTH
 * video and audio in one file (Piped `videoOnly: false`, Invidious
 * `formatStreams`, usually 720p or 360p) opens immediately and cannot end up
 * silent or out of sync. Adaptive renditions keep video and audio in two
 * separate files; they are only used after the muxed ones and are always
 * handed to the player as a pair (a DASH manifest or an explicit audio URL
 * that the player merges), never as a lone video-only URL.
 *
 * Pure Kotlin (no Android types) so it is unit tested on the JVM.
 */
object OnlineStreamSelector {

    /**
     * How many sources are tried on one instance before the player asks the
     * repository for another instance. Every source on the same proxy tends
     * to fail the same way, so walking a dozen qualities only adds waiting.
     */
    const val MAX_SOURCES_PER_INSTANCE = 4

    /** Muxed heights, most wanted first: sharp enough for a phone, light on a public proxy. */
    private val preferredMuxedHeights = listOf(720, 360)

    private const val MAX_DEFAULT_HEIGHT = 720

    /**
     * Index of the rendition the player should start with, or -1 when
     * there is none. Order: muxed 720p, muxed 360p, best muxed at or below
     * 720p, any muxed, best adaptive at or below 720p, anything.
     * MP4 is preferred over WebM at the same height (it is what every
     * Android decoder handles, and what Piped labels `MPEG_4`).
     */
    fun defaultStreamIndex(streams: List<OnlineStream>): Int {
        if (streams.isEmpty()) return -1
        val indexed = streams.withIndex().toList()
        val muxed = indexed.filter { it.value.isMuxed() }
        for (height in preferredMuxedHeights) {
            muxed.filter { it.value.height == height }.bestByContainer()?.let { return it.index }
        }
        muxed.filter { it.value.height in 1..MAX_DEFAULT_HEIGHT }
            .maxWithOrNull(heightThenMp4)?.let { return it.index }
        muxed.maxWithOrNull(heightThenMp4)?.let { return it.index }
        val adaptive = indexed.filter { !it.value.isMuxed() }
        adaptive.filter { it.value.height in 1..MAX_DEFAULT_HEIGHT }
            .maxWithOrNull(heightThenMp4)?.let { return it.index }
        return 0
    }

    /**
     * The ordered fallback chain for one instance: the [selectedIndex]
     * rendition, then the other muxed files, then the instance's HLS and
     * DASH manifests, then the remaining adaptive pairs. At most
     * [maxSources] entries, no duplicates.
     */
    fun playbackSources(
        details: OnlineVideoDetails,
        selectedIndex: Int,
        maxSources: Int = MAX_SOURCES_PER_INSTANCE
    ): List<OnlinePlaybackSource> {
        val streams = details.progressiveStreams
        val out = mutableListOf<OnlinePlaybackSource>()
        fun addStream(index: Int) {
            val stream = streams.getOrNull(index) ?: return
            // A video-only URL without its audio partner would play silently
            // (or not at all) — never offer one.
            if (!stream.isMuxed() && stream.audioUrl == null && !stream.isManifest()) return
            out.add(OnlinePlaybackSource(stream.url, stream.audioUrl, mediaMimeType(stream.mimeType), index))
        }
        addStream(selectedIndex)
        streams.withIndex()
            .filter { it.index != selectedIndex && it.value.isMuxed() }
            .sortedWith(compareBy<IndexedValue<OnlineStream>>({ muxedRank(it.value) }, { -it.value.height }))
            .forEach { addStream(it.index) }
        details.hlsUrl?.let { out.add(OnlinePlaybackSource(it, mimeType = MIME_HLS)) }
        details.dashUrl?.let { out.add(OnlinePlaybackSource(it, mimeType = MIME_DASH)) }
        streams.withIndex()
            .filter { it.index != selectedIndex && !it.value.isMuxed() }
            // Phone-sized adaptive pairs before the heavy ones.
            .sortedWith(compareBy<IndexedValue<OnlineStream>>({ it.value.height > MAX_DEFAULT_HEIGHT }, { -it.value.height }))
            .forEach { addStream(it.index) }
        return out.distinctBy { it.url to it.audioUrl }.take(maxSources.coerceAtLeast(1))
    }

    const val MIME_HLS = "application/x-mpegURL"
    const val MIME_DASH = "application/dash+xml"

    /** "video/mp4; codecs=…" → "video/mp4"; blank → null (let the player infer it). */
    fun mediaMimeType(mimeType: String?): String? =
        mimeType?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }

    private fun OnlineStream.isMuxed(): Boolean = isProgressive && audioUrl == null && !isManifest()

    private fun OnlineStream.isManifest(): Boolean =
        mimeType.startsWith(MIME_DASH, ignoreCase = true) || mimeType.startsWith(MIME_HLS, ignoreCase = true) ||
            mimeType.contains("mpegurl", ignoreCase = true)

    private fun OnlineStream.isMp4(): Boolean = mimeType.contains("mp4", ignoreCase = true)

    private fun muxedRank(stream: OnlineStream): Int {
        val rank = preferredMuxedHeights.indexOf(stream.height)
        return if (rank >= 0) rank else preferredMuxedHeights.size + if (stream.height in 1..MAX_DEFAULT_HEIGHT) 0 else 1
    }

    private val heightThenMp4 = compareBy<IndexedValue<OnlineStream>>({ it.value.height }, { it.value.isMp4() })

    private fun List<IndexedValue<OnlineStream>>.bestByContainer(): IndexedValue<OnlineStream>? =
        firstOrNull { it.value.isMp4() } ?: firstOrNull()
}
