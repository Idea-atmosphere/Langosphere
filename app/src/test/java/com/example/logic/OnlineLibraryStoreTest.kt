package com.example.logic

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.model.OnlineVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Online hub's own shelves: Saved, History and the "new upload" dots of
 * the channel tray.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlineLibraryStoreTest {

    private fun v(id: String, title: String = "Title $id", authorId: String? = null, at: Long = 0L) =
        OnlineVideo(id = id, title = title, authorId = authorId, publishedAt = at)

    @Test
    fun historyMovesReopenedClipToTopWithoutDuplicates() {
        var list = emptyList<OnlineLibraryStore.Entry>()
        list = OnlineLibraryStore.addToHistory(list, v("a"), now = 1)
        list = OnlineLibraryStore.addToHistory(list, v("b"), now = 2)
        list = OnlineLibraryStore.addToHistory(list, v("a"), now = 3)
        assertEquals(listOf("a", "b"), list.map { it.video.id })
        assertEquals(3L, list.first().at)
    }

    @Test
    fun historyIsCappedAndIgnoresBlankIds() {
        var list = emptyList<OnlineLibraryStore.Entry>()
        (1..5).forEach { list = OnlineLibraryStore.addToHistory(list, v("v$it"), now = it.toLong(), max = 3) }
        assertEquals(listOf("v5", "v4", "v3"), list.map { it.video.id })
        assertEquals(list, OnlineLibraryStore.addToHistory(list, v(""), now = 9, max = 3))
    }

    @Test
    fun historyKeepsKnownMetadataWhenAPlaceholderArrives() {
        val full = OnlineVideo(id = "x", title = "Real title", author = "Teacher", thumbnailUrl = "https://t/x.jpg", lengthSeconds = 300)
        var list = OnlineLibraryStore.addToHistory(emptyList(), full, now = 1)
        // Opening a pasted link only knows the id at first.
        list = OnlineLibraryStore.addToHistory(list, OnlineVideo(id = "x", title = ""), now = 2)
        val merged = list.single().video
        assertEquals("Real title", merged.title)
        assertEquals("Teacher", merged.author)
        assertEquals("https://t/x.jpg", merged.thumbnailUrl)
        assertEquals(300L, merged.lengthSeconds)
    }

    @Test
    fun toggleSavedAddsThenRemoves() {
        var list = OnlineLibraryStore.toggleSaved(emptyList(), v("a"), now = 1)
        list = OnlineLibraryStore.toggleSaved(list, v("b"), now = 2)
        assertEquals(listOf("b", "a"), list.map { it.video.id })
        list = OnlineLibraryStore.toggleSaved(list, v("a"), now = 3)
        assertEquals(listOf("b"), list.map { it.video.id })
        assertTrue(OnlineLibraryStore.remove(list, "b").isEmpty())
    }

    @Test
    fun refreshUpdatesMetadataInPlace() {
        val list = listOf(
            OnlineLibraryStore.Entry(v("a", title = ""), 1),
            OnlineLibraryStore.Entry(v("b"), 2)
        )
        val out = OnlineLibraryStore.refresh(list, OnlineVideo(id = "a", title = "Now known", lengthSeconds = 61))
        assertEquals(listOf("a", "b"), out.map { it.video.id })
        assertEquals("Now known", out[0].video.title)
        assertEquals(61L, out[0].video.lengthSeconds)
        assertEquals(1L, out[0].at)
        assertEquals(list[1], out[1])
    }

    @Test
    fun firstSightingOnlySetsBaselineThenNewUploadsLightTheDot() {
        val feed = listOf(v("1", authorId = "chA", at = 100), v("2", authorId = "chA", at = 150), v("3", authorId = "chB", at = 90))
        val seen = OnlineLibraryStore.baselineSeen(feed, emptyMap())
        assertEquals(mapOf("chA" to 150L, "chB" to 90L), seen)
        assertTrue(OnlineLibraryStore.channelsWithNewVideos(feed, seen).isEmpty())

        val later = feed + v("4", authorId = "chB", at = 200)
        assertEquals(seen, OnlineLibraryStore.baselineSeen(later, seen))
        assertEquals(setOf("chB"), OnlineLibraryStore.channelsWithNewVideos(later, seen))
    }

    @Test
    fun videosWithoutChannelOrTimestampAreIgnored() {
        val feed = listOf(v("1", authorId = null, at = 100), v("2", authorId = "chA", at = 0))
        assertTrue(OnlineLibraryStore.latestUploadByChannel(feed).isEmpty())
        assertTrue(OnlineLibraryStore.channelsWithNewVideos(feed, mapOf("chA" to 1L)).isEmpty())
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val list = listOf(
            OnlineLibraryStore.Entry(
                OnlineVideo(
                    id = "abc",
                    title = "Learn English «با زیرنویس»",
                    author = "Teacher",
                    authorId = "UC1",
                    thumbnailUrl = "https://i.ytimg.com/vi/abc/hq.jpg",
                    lengthSeconds = 754,
                    viewCount = 12345,
                    publishedText = "2 days ago",
                    publishedAt = 1_700_000_000,
                    isLive = false
                ),
                42L
            ),
            OnlineLibraryStore.Entry(OnlineVideo(id = "def", title = "Bare"), 7L)
        )
        val back = OnlineLibraryStore.decode(OnlineLibraryStore.encode(list))
        assertEquals(list, back)
        assertNull(back[1].video.viewCount)
        assertNull(back[1].video.authorId)
    }

    @Test
    fun decodeToleratesGarbage() {
        assertTrue(OnlineLibraryStore.decode(null).isEmpty())
        assertTrue(OnlineLibraryStore.decode("not json").isEmpty())
        assertTrue(OnlineLibraryStore.decode("""[{"title":"no id"}]""").isEmpty())
        assertTrue(OnlineLibraryStore.decodeSeen("{").isEmpty())
    }

    @Test
    fun seenMarksRoundTrip() {
        val seen = mapOf("UC1" to 10L, "UC2" to 20L)
        assertEquals(seen, OnlineLibraryStore.decodeSeen(OnlineLibraryStore.encodeSeen(seen)))
    }

    @Test
    fun storagePersistsAcrossLoads() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val saved = listOf(OnlineLibraryStore.Entry(v("s1"), 5L))
        OnlineLibraryStore.storeSaved(context, saved)
        OnlineLibraryStore.storeHistory(context, emptyList())
        assertEquals(saved, OnlineLibraryStore.loadSaved(context))
        assertFalse(OnlineLibraryStore.loadHistory(context).isNotEmpty())
    }
}
