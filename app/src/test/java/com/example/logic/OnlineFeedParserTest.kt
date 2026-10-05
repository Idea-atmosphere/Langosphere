package com.example.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the Atom feed parser used as the last-resort source of channel
 * listings (youtube.com `feeds/videos.xml` and Invidious `/feed/channel`).
 */
class OnlineFeedParserTest {

    private val youtubeFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/" xmlns="http://www.w3.org/2005/Atom">
         <link rel="self" href="http://www.youtube.com/feeds/videos.xml?channel_id=UCHaHD477h-FeBbVh9Sh7syA"/>
         <id>yt:channel:HaHD477h-FeBbVh9Sh7syA</id>
         <yt:channelId>HaHD477h-FeBbVh9Sh7syA</yt:channelId>
         <title>BBC Learning English</title>
         <author><name>BBC Learning English</name><uri>https://www.youtube.com/channel/UCHaHD477h-FeBbVh9Sh7syA</uri></author>
         <published>2008-11-14T09:32:42+00:00</published>
         <entry>
          <id>yt:video:Y681hXWwhQY</id>
          <yt:videoId>Y681hXWwhQY</yt:videoId>
          <yt:channelId>UCHaHD477h-FeBbVh9Sh7syA</yt:channelId>
          <title>Learn &amp; use &#39;whatever&#39; 🤷 #shorts</title>
          <link rel="alternate" href="https://www.youtube.com/watch?v=Y681hXWwhQY"/>
          <author><name>BBC Learning English</name><uri>https://www.youtube.com/channel/UCHaHD477h-FeBbVh9Sh7syA</uri></author>
          <published>2026-09-11T08:35:14+00:00</published>
          <updated>2026-09-12T01:00:00+00:00</updated>
          <media:group>
           <media:title>Learn &amp; use 'whatever'</media:title>
           <media:content url="https://www.youtube.com/v/Y681hXWwhQY?version=3" type="application/x-shockwave-flash" width="640" height="390"/>
           <media:thumbnail url="https://i2.ytimg.com/vi/Y681hXWwhQY/hqdefault.jpg" width="480" height="360"/>
           <media:description>Whatever!</media:description>
           <media:community><media:starRating count="12" average="5.00" min="1" max="5"/><media:statistics views="12345"/></media:community>
          </media:group>
         </entry>
         <entry>
          <id>yt:video:cQ54GDm1eL0</id>
          <yt:videoId>cQ54GDm1eL0</yt:videoId>
          <title><![CDATA[Second <b>one</b>]]></title>
          <published>2026-09-10T09:15:27-07:00</published>
         </entry>
         <entry>
          <id>yt:video:bad</id>
          <yt:videoId>not-an-id</yt:videoId>
          <title>Broken entry is skipped</title>
         </entry>
        </feed>
    """.trimIndent()

    @Test
    fun `youtube feed entries become videos`() {
        val feed = OnlineFeedParser.parse(youtubeFeed, fallbackChannelId = "UCHaHD477h-FeBbVh9Sh7syA")
        assertEquals("UCHaHD477h-FeBbVh9Sh7syA", feed.channelId)
        assertEquals("BBC Learning English", feed.channelTitle)
        assertEquals(listOf("Y681hXWwhQY", "cQ54GDm1eL0"), feed.videos.map { it.id })

        val first = feed.videos[0]
        assertEquals("Learn & use 'whatever' 🤷 #shorts", first.title)
        assertEquals("BBC Learning English", first.author)
        assertEquals("UCHaHD477h-FeBbVh9Sh7syA", first.authorId)
        assertEquals("https://i2.ytimg.com/vi/Y681hXWwhQY/hqdefault.jpg", first.thumbnailUrl)
        assertEquals(12345L, first.viewCount)
        assertEquals(1789115714L, first.publishedAt) // 2026-09-11T08:35:14Z
        assertEquals("2026-09-11", first.publishedText)
        assertEquals(0L, first.lengthSeconds)

        // Sparse entry: CDATA title, author / channel id inherited from the feed, thumbnail synthesised, offset applied.
        val second = feed.videos[1]
        assertEquals("Second <b>one</b>", second.title)
        assertEquals("BBC Learning English", second.author)
        assertEquals("UCHaHD477h-FeBbVh9Sh7syA", second.authorId)
        assertEquals("https://i.ytimg.com/vi/cQ54GDm1eL0/hqdefault.jpg", second.thumbnailUrl)
        assertNull(second.viewCount)
        assertEquals(1789056927L, second.publishedAt) // 2026-09-10T16:15:27Z
    }

    @Test
    fun `channel id in the feed header is completed to a UC id when no fallback is given`() {
        val feed = OnlineFeedParser.parse(youtubeFeed)
        assertEquals("UCHaHD477h-FeBbVh9Sh7syA", feed.channelId)
        assertEquals("UCHaHD477h-FeBbVh9Sh7syA", feed.videos[1].authorId)
    }

    @Test
    fun `invidious feed shape is accepted`() {
        val xml = """
            <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/" xmlns="http://www.w3.org/2005/Atom" xml:lang="en-US">
              <id>yt:channel:UCz4tgANd4yy8Oe0iXCdSWfA</id>
              <yt:channelId>UCz4tgANd4yy8Oe0iXCdSWfA</yt:channelId>
              <title>English with Lucy</title>
              <entry>
                <id>yt:video:txhZ_2jiazU</id>
                <yt:videoId>txhZ_2jiazU</yt:videoId>
                <yt:channelId>UCz4tgANd4yy8Oe0iXCdSWfA</yt:channelId>
                <title>You need to stop saying "thank you"</title>
                <author><name>English with Lucy</name><uri>https://inv.example/channel/UCz4tgANd4yy8Oe0iXCdSWfA</uri></author>
                <content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml"><a href="https://inv.example/watch?v=txhZ_2jiazU"><img src="https://inv.example/vi/txhZ_2jiazU/mqdefault.jpg"/></a><p>desc</p></div></content>
                <published>2026-09-11T12:30:09+00:00</published>
                <media:group><media:title>You need to stop saying "thank you"</media:title><media:thumbnail url="https://inv.example/vi/txhZ_2jiazU/mqdefault.jpg" width="320" height="180"/></media:group>
              </entry>
            </feed>
        """.trimIndent()
        val feed = OnlineFeedParser.parse(xml)
        assertEquals("English with Lucy", feed.channelTitle)
        assertEquals(1, feed.videos.size)
        val v = feed.videos[0]
        assertEquals("txhZ_2jiazU", v.id)
        assertEquals("You need to stop saying \"thank you\"", v.title)
        assertEquals("https://inv.example/vi/txhZ_2jiazU/mqdefault.jpg", v.thumbnailUrl)
        assertEquals("UCz4tgANd4yy8Oe0iXCdSWfA", v.authorId)
        assertTrue(v.publishedAt > 1_700_000_000L)
    }

    @Test
    fun `garbage and empty input yield no videos`() {
        assertTrue(OnlineFeedParser.parse("").videos.isEmpty())
        assertTrue(OnlineFeedParser.parse("<!DOCTYPE html><html><title>Endpoint disabled</title></html>").videos.isEmpty())
        assertTrue(OnlineFeedParser.parse("<feed><entry><title>no id</title></entry></feed>").videos.isEmpty())
    }

    @Test
    fun `iso timestamps are converted with their offset`() {
        assertEquals(1789115714L, OnlineFeedParser.parseIsoSeconds("2026-09-11T08:35:14+00:00"))
        assertEquals(1789115714L, OnlineFeedParser.parseIsoSeconds("2026-09-11T08:35:14Z"))
        assertEquals(1789115714L, OnlineFeedParser.parseIsoSeconds("2026-09-11T10:35:14+02:00"))
        assertEquals(1789115714L, OnlineFeedParser.parseIsoSeconds("2026-09-11T04:05:14-04:30"))
        assertEquals(1789115714L, OnlineFeedParser.parseIsoSeconds("2026-09-11T08:35:14.123Z"))
        assertEquals(0L, OnlineFeedParser.parseIsoSeconds("yesterday"))
        assertEquals(0L, OnlineFeedParser.parseIsoSeconds(""))
    }
}
