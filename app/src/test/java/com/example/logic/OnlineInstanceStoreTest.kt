package com.example.logic

import com.example.model.OnlineChannel
import com.example.model.OnlineInstance
import com.example.model.OnlineInstanceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the Online tab's pure helpers: instance address clean-up, link
 * parsing and the JSON round-trip of the persisted lists.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlineInstanceStoreTest {

    @Test
    fun `instance addresses are normalised`() {
        assertEquals("https://yewtu.be", OnlineInstanceStore.normalizeBaseUrl("yewtu.be"))
        assertEquals("https://inv.nadeko.net", OnlineInstanceStore.normalizeBaseUrl(" https://inv.nadeko.net/ "))
        assertEquals("http://localhost:3000", OnlineInstanceStore.normalizeBaseUrl("http://localhost:3000/api/v1"))
        assertEquals("https://pipedapi.kavin.rocks", OnlineInstanceStore.normalizeBaseUrl("https://pipedapi.kavin.rocks/streams/x?y=1"))
        assertEquals("https://piped.example.org:8443", OnlineInstanceStore.normalizeBaseUrl("Piped.Example.ORG:8443/"))
        assertNull(OnlineInstanceStore.normalizeBaseUrl(""))
        assertNull(OnlineInstanceStore.normalizeBaseUrl("   "))
        assertNull(OnlineInstanceStore.normalizeBaseUrl("not a url"))
        assertNull(OnlineInstanceStore.normalizeBaseUrl("ftp://x.y"))
        assertNull(OnlineInstanceStore.normalizeBaseUrl(null))
    }

    @Test
    fun `instance kind is guessed from the host`() {
        assertEquals(OnlineInstanceKind.PIPED, OnlineInstanceStore.detectKind("https://pipedapi.kavin.rocks"))
        assertEquals(OnlineInstanceKind.PIPED, OnlineInstanceStore.detectKind("https://api.piped.private.coffee"))
        assertEquals(OnlineInstanceKind.INVIDIOUS, OnlineInstanceStore.detectKind("https://yewtu.be"))
    }

    @Test
    fun `video ids are extracted from every common link shape`() {
        val id = "dQw4w9WgXcQ"
        listOf(
            "https://www.youtube.com/watch?v=$id&t=10",
            "https://youtu.be/$id?si=abc",
            "https://youtube.com/shorts/$id",
            "https://www.youtube.com/embed/$id",
            "https://www.youtube.com/live/$id",
            "https://yewtu.be/watch?v=$id",
            "/watch?v=$id",
            id
        ).forEach { assertEquals(it, id, OnlineInstanceStore.extractVideoId(it)) }
        assertNull(OnlineInstanceStore.extractVideoId("hello"))
        assertNull(OnlineInstanceStore.extractVideoId(""))
        assertNull(OnlineInstanceStore.extractVideoId("https://www.youtube.com/@bbclearningenglish"))
    }

    @Test
    fun `channel ids and handles are extracted`() {
        val ucid = "UCHaHD477h-FeBbVh9Sh7syA"
        assertEquals(ucid, OnlineInstanceStore.extractChannelId("https://www.youtube.com/channel/$ucid/videos"))
        assertEquals(ucid, OnlineInstanceStore.extractChannelId(ucid))
        assertNull(OnlineInstanceStore.extractChannelId("https://www.youtube.com/@bbclearningenglish"))
        assertEquals("@bbclearningenglish", OnlineInstanceStore.extractChannelHandle("https://www.youtube.com/@bbclearningenglish"))
        assertEquals("@lucy", OnlineInstanceStore.extractChannelHandle("@lucy"))
        assertEquals("c/EnglishWithLucy", OnlineInstanceStore.extractChannelHandle("https://youtube.com/c/EnglishWithLucy"))
        assertEquals("user/bbclearningenglish", OnlineInstanceStore.extractChannelHandle("https://www.youtube.com/user/bbclearningenglish"))
        assertNull(OnlineInstanceStore.extractChannelHandle("just words"))
    }

    @Test
    fun `instance list survives a json round trip`() {
        val list = listOf(
            OnlineInstance("https://yewtu.be", OnlineInstanceKind.INVIDIOUS, enabled = false),
            OnlineInstance("https://pipedapi.kavin.rocks", OnlineInstanceKind.PIPED),
            OnlineInstance("https://yewtu.be", OnlineInstanceKind.INVIDIOUS) // duplicate dropped
        )
        val parsed = OnlineInstanceStore.parseInstances(OnlineInstanceStore.serializeInstances(list))
        assertEquals(2, parsed.size)
        assertEquals(list[0], parsed[0])
        assertEquals(list[1], parsed[1])
        // Unknown kinds / bad urls do not break the whole list.
        val messy = """[{"url":"yewtu.be","kind":"WHATEVER"},{"url":"","kind":"PIPED"},{"url":"https://a.b","kind":"PIPED","enabled":false}]"""
        val tolerant = OnlineInstanceStore.parseInstances(messy)
        assertEquals(listOf("https://yewtu.be", "https://a.b"), tolerant.map { it.baseUrl })
        assertEquals(OnlineInstanceKind.INVIDIOUS, tolerant[0].kind)
        assertEquals(false, tolerant[1].enabled)
    }

    @Test
    fun `channel list survives a json round trip`() {
        val list = listOf(
            OnlineChannel("UCz4tgANd4yy8Oe0iXCdSWfA", "English with Lucy", avatarUrl = "https://x/y.jpg", description = "Lessons"),
            OnlineChannel("UCvn_XCl_mgQmt3sD753zdJA", "Rachel's English")
        )
        val parsed = OnlineInstanceStore.parseChannels(OnlineInstanceStore.serializeChannels(list))
        assertEquals(list, parsed)
    }

    @Test
    fun `default channels are well-formed and unique`() {
        val ids = OnlineInstanceStore.defaultChannels.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { Regex("^UC[A-Za-z0-9_-]{22}$").matches(it) })
        assertTrue(OnlineInstanceStore.defaultChannels.all { it.isDefault && it.name.isNotBlank() })
        assertTrue(OnlineInstanceStore.defaultInstances.any { it.kind == OnlineInstanceKind.INVIDIOUS })
        assertTrue(OnlineInstanceStore.defaultInstances.any { it.kind == OnlineInstanceKind.PIPED })
        assertTrue(OnlineInstanceStore.defaultInstances.all { it.baseUrl.startsWith("https://") && !it.baseUrl.endsWith("/") })
    }

    @Test
    fun `saved instance lists are upgraded to the current defaults`() {
        val defaults = OnlineInstanceStore.defaultInstances
        // An untouched list from the previous version is replaced wholesale.
        val v1Defaults = listOf(
            "https://inv.nadeko.net", "https://yewtu.be", "https://invidious.nerdvpn.de", "https://inv.tux.pizza",
            "https://pipedapi.kavin.rocks", "https://pipedapi.adminforge.de", "https://api.piped.private.coffee"
        ).map { OnlineInstance(it, OnlineInstanceStore.detectKind(it)) }
        assertEquals(defaults, OnlineInstanceStore.upgradeInstances(v1Defaults))
        assertEquals(defaults, OnlineInstanceStore.upgradeInstances(v1Defaults.reversed()))

        // A customised list keeps the user's entries (order and on/off state),
        // drops retired defaults and gains the new ones at the end.
        val mine = OnlineInstance("https://piped.mine.example", OnlineInstanceKind.PIPED)
        val off = OnlineInstance("https://inv.nadeko.net", OnlineInstanceKind.INVIDIOUS, enabled = false)
        val custom = listOf(mine, OnlineInstance("https://yewtu.be", OnlineInstanceKind.INVIDIOUS), off)
        val upgraded = OnlineInstanceStore.upgradeInstances(custom)
        assertEquals(mine, upgraded[0])
        assertEquals(off, upgraded[1])
        assertTrue(upgraded.none { it.baseUrl == "https://yewtu.be" })
        assertEquals(defaults.map { it.baseUrl }.toSet() + mine.baseUrl, upgraded.map { it.baseUrl }.toSet())
        assertEquals(upgraded.size, upgraded.distinctBy { it.baseUrl }.size)

        // Already-current lists are left alone.
        assertEquals(defaults, OnlineInstanceStore.upgradeInstances(defaults))
    }

    @Test
    fun `failover pool starts with the core piped and invidious mirrors`() {
        val urls = OnlineInstanceStore.defaultInstances.map { it.baseUrl }
        assertEquals(
            listOf(
                "https://pipedapi.kavin.rocks", "https://api.piped.privacydev.net",
                "https://piped-api.lunar.icu", "https://api-piped.mha.fi",
                "https://inv.nadeko.net", "https://invidious.nerdvpn.de", "https://yt.chocolatemoo53.com",
                "https://invidious.tiekoetter.com", "https://invidious.f5.si"
            ),
            urls.take(9)
        )
        assertEquals(urls.size, urls.toSet().size)
        assertTrue(OnlineInstanceStore.defaultInstances.take(4).all { it.kind == OnlineInstanceKind.PIPED })
        assertTrue(OnlineInstanceStore.defaultInstances.subList(4, 9).all { it.kind == OnlineInstanceKind.INVIDIOUS })
        // The Invidious pool is exactly the official active list.
        assertTrue(urls.none { listOf("inv.tux.pizza", "invidious.flokinet.to", "invidious.private.coffee", "invidious.jing.rocks", "invidious.n8n7.io", "inv.us.projectsegfau.lt", "invidious.fdn.fr").any { dead -> it.endsWith(dead) } })

        // An untouched version-3 default list becomes the new pool.
        val v3 = listOf(
            "https://pipedapi.ducks.party", "https://api.piped.private.coffee", "https://pipedapi.kavin.rocks",
            "https://pipedapi.reallyaweso.me", "https://pipedapi.drgns.space", "https://pipedapi.leptons.xyz",
            "https://pipedapi.r4fo.com", "https://pipedapi.in.projectsegfau.lt", "https://pipedapi-libre.kavin.rocks",
            "https://inv.nadeko.net", "https://invidious.nerdvpn.de", "https://invidious.private.coffee",
            "https://invidious.jing.rocks", "https://invidious.n8n7.io", "https://inv.us.projectsegfau.lt",
            "https://invidious.fdn.fr"
        ).map { OnlineInstance(it, OnlineInstanceStore.detectKind(it)) }
        assertEquals(OnlineInstanceStore.defaultInstances, OnlineInstanceStore.upgradeInstances(v3))

        // An untouched version-4 default list becomes the new pool too.
        val v4 = listOf(
            "https://pipedapi.kavin.rocks", "https://api.piped.privacydev.net", "https://piped-api.lunar.icu",
            "https://api-piped.mha.fi", "https://inv.tux.pizza", "https://invidious.nerdvpn.de",
            "https://invidious.flokinet.to", "https://pipedapi.ducks.party", "https://api.piped.private.coffee",
            "https://pipedapi.reallyaweso.me", "https://pipedapi.drgns.space", "https://pipedapi.leptons.xyz",
            "https://pipedapi.r4fo.com", "https://pipedapi.in.projectsegfau.lt", "https://pipedapi-libre.kavin.rocks",
            "https://inv.nadeko.net", "https://invidious.private.coffee", "https://invidious.jing.rocks",
            "https://invidious.n8n7.io", "https://inv.us.projectsegfau.lt", "https://invidious.fdn.fr"
        ).map { OnlineInstance(it, OnlineInstanceStore.detectKind(it)) }
        assertEquals(OnlineInstanceStore.defaultInstances, OnlineInstanceStore.upgradeInstances(v4))
    }

    @Test
    fun `stale saved instance list is upgraded on load and the upgrade is persisted`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences("online_prefs", android.content.Context.MODE_PRIVATE)
        val old = listOf(
            OnlineInstance("https://yewtu.be", OnlineInstanceKind.INVIDIOUS),
            OnlineInstance("https://piped.mine.example", OnlineInstanceKind.PIPED)
        )
        prefs.edit()
            .putString("instances_json", OnlineInstanceStore.serializeInstances(old))
            .putString("last_good_instance", "https://yewtu.be")
            .remove("instances_version")
            .commit()

        val loaded = OnlineInstanceStore.loadInstances(context)
        assertTrue(loaded.none { it.baseUrl == "https://yewtu.be" })
        assertEquals("https://piped.mine.example", loaded.first().baseUrl)
        assertTrue(OnlineInstanceStore.defaultInstances.all { d -> loaded.any { it.baseUrl == d.baseUrl } })
        assertNull(OnlineInstanceStore.lastGoodInstance(context))
        assertEquals(OnlineInstanceStore.INSTANCES_VERSION, prefs.getInt("instances_version", -1))
        // Second load is a no-op.
        assertEquals(loaded, OnlineInstanceStore.loadInstances(context))

        OnlineInstanceStore.resetInstances(context)
        assertEquals(OnlineInstanceStore.defaultInstances, OnlineInstanceStore.loadInstances(context))
    }

    @Test
    fun `follow and unfollow are persisted`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val defaults = OnlineInstanceStore.loadChannels(context)
        assertEquals(OnlineInstanceStore.defaultChannels.size, defaults.size)

        val custom = OnlineChannel("UCAAAAAAAAAAAAAAAAAAAAAA", "My teacher")
        OnlineInstanceStore.followChannel(context, custom)
        assertTrue(OnlineInstanceStore.loadChannels(context).any { it.id == custom.id && !it.isDefault })

        val firstDefault = OnlineInstanceStore.defaultChannels.first()
        OnlineInstanceStore.unfollowChannel(context, firstDefault.id)
        assertTrue(OnlineInstanceStore.loadChannels(context).none { it.id == firstDefault.id })

        // Re-following a default brings it back; restoring brings every default back.
        OnlineInstanceStore.followChannel(context, firstDefault)
        assertTrue(OnlineInstanceStore.loadChannels(context).any { it.id == firstDefault.id })
        OnlineInstanceStore.unfollowChannel(context, firstDefault.id)
        OnlineInstanceStore.restoreDefaultChannels(context)
        assertTrue(OnlineInstanceStore.loadChannels(context).any { it.id == firstDefault.id })

        OnlineInstanceStore.unfollowChannel(context, custom.id)
        assertTrue(OnlineInstanceStore.loadChannels(context).none { it.id == custom.id })
    }
}
