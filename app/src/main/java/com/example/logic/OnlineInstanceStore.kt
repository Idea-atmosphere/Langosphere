package com.example.logic

import android.content.Context
import android.content.SharedPreferences
import com.example.model.OnlineChannel
import com.example.model.OnlineInstance
import com.example.model.OnlineInstanceKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistence for the Online tab: the user's instance list (Invidious /
 * Piped API servers) and the channels they follow. Everything is stored as
 * JSON in the `online_prefs` SharedPreferences file.
 *
 * The defaults are the app's opinionated starting point:
 *  * a handful of well-known public instances of each kind, so the tab works
 *    out of the box; the user can add their own (including a self-hosted
 *    one), remove any of them or switch them off temporarily;
 *  * a list of YouTube channels run by English teachers, pre-followed.
 *
 * The pure helpers ([normalizeBaseUrl], [detectKind], [extractVideoId],
 * [extractChannelId]) are kept free of Android types so they can be unit
 * tested on the JVM.
 */
object OnlineInstanceStore {
    private const val PREFS = "online_prefs"
    private const val KEY_INSTANCES = "instances_json"
    private const val KEY_CHANNELS = "channels_json"
    private const val KEY_REMOVED_DEFAULTS = "removed_default_channels"
    private const val KEY_LAST_INSTANCE = "last_good_instance"
    private const val KEY_INSTANCES_VERSION = "instances_version"

    /**
     * Bump when [defaultInstances] changes. A saved list written by an older
     * default set is upgraded on load (see [loadInstances]) so users who
     * already ran the app are not stuck with instances that have since gone
     * away.
     */
    internal const val INSTANCES_VERSION = 5

    /**
     * The failover pool, in the order it is tried. The first block is the
     * core pool the playback failover is built around (Piped API servers
     * first, then Invidious); the rest are extra public mirrors kept as
     * spares. Public instances come and go — and YouTube rate-limits or
     * IP-blocks individual ones on its googlevideo CDN (HTTP 403) — which is
     * exactly why playback walks this list automatically (see
     * OnlineVideoRepository.videoDetails), why the list is editable in the
     * app, and why channel listings fall back to the channels' Atom feeds.
     */
    val defaultInstances: List<OnlineInstance> = listOf(
        // Core Piped pool.
        OnlineInstance("https://pipedapi.kavin.rocks", OnlineInstanceKind.PIPED),
        OnlineInstance("https://api.piped.privacydev.net", OnlineInstanceKind.PIPED),
        OnlineInstance("https://piped-api.lunar.icu", OnlineInstanceKind.PIPED),
        OnlineInstance("https://api-piped.mha.fi", OnlineInstanceKind.PIPED),
        // Core Invidious pool: the official active list from
        // instances.invidious.io (the public list is short since YouTube's
        // crackdown — these are the ones the Invidious maintainers vouch
        // for; every other former default is retired below).
        OnlineInstance("https://inv.nadeko.net", OnlineInstanceKind.INVIDIOUS),
        OnlineInstance("https://invidious.nerdvpn.de", OnlineInstanceKind.INVIDIOUS),
        OnlineInstance("https://yt.chocolatemoo53.com", OnlineInstanceKind.INVIDIOUS),
        OnlineInstance("https://invidious.tiekoetter.com", OnlineInstanceKind.INVIDIOUS),
        OnlineInstance("https://invidious.f5.si", OnlineInstanceKind.INVIDIOUS),
        // Spare mirrors.
        OnlineInstance("https://pipedapi.ducks.party", OnlineInstanceKind.PIPED),
        OnlineInstance("https://api.piped.private.coffee", OnlineInstanceKind.PIPED),
        OnlineInstance("https://pipedapi.reallyaweso.me", OnlineInstanceKind.PIPED),
        OnlineInstance("https://pipedapi.drgns.space", OnlineInstanceKind.PIPED),
        OnlineInstance("https://pipedapi.leptons.xyz", OnlineInstanceKind.PIPED),
        OnlineInstance("https://pipedapi.r4fo.com", OnlineInstanceKind.PIPED),
        OnlineInstance("https://pipedapi.in.projectsegfau.lt", OnlineInstanceKind.PIPED),
        OnlineInstance("https://pipedapi-libre.kavin.rocks", OnlineInstanceKind.PIPED),
    )

    /** Default sets of earlier versions; a saved list equal to one of these is silently replaced. */
    private val previousDefaultUrls: List<Set<String>> = listOf(
        // Version 4 — the last set before the Invidious pool was cut down
        // to the official active instances.
        setOf(
            "https://pipedapi.kavin.rocks", "https://api.piped.privacydev.net", "https://piped-api.lunar.icu",
            "https://api-piped.mha.fi", "https://inv.tux.pizza", "https://invidious.nerdvpn.de",
            "https://invidious.flokinet.to", "https://pipedapi.ducks.party", "https://api.piped.private.coffee",
            "https://pipedapi.reallyaweso.me", "https://pipedapi.drgns.space", "https://pipedapi.leptons.xyz",
            "https://pipedapi.r4fo.com", "https://pipedapi.in.projectsegfau.lt", "https://pipedapi-libre.kavin.rocks",
            "https://inv.nadeko.net", "https://invidious.private.coffee", "https://invidious.jing.rocks",
            "https://invidious.n8n7.io", "https://inv.us.projectsegfau.lt", "https://invidious.fdn.fr"
        ),
        setOf(
            "https://inv.nadeko.net", "https://yewtu.be", "https://invidious.nerdvpn.de", "https://inv.tux.pizza",
            "https://pipedapi.kavin.rocks", "https://pipedapi.adminforge.de", "https://api.piped.private.coffee"
        ),
        // Version 3.
        setOf(
            "https://pipedapi.ducks.party", "https://api.piped.private.coffee", "https://pipedapi.kavin.rocks",
            "https://pipedapi.reallyaweso.me", "https://pipedapi.drgns.space", "https://pipedapi.leptons.xyz",
            "https://pipedapi.r4fo.com", "https://pipedapi.in.projectsegfau.lt", "https://pipedapi-libre.kavin.rocks",
            "https://inv.nadeko.net", "https://invidious.nerdvpn.de", "https://invidious.private.coffee",
            "https://invidious.jing.rocks", "https://invidious.n8n7.io", "https://inv.us.projectsegfau.lt",
            "https://invidious.fdn.fr"
        )
    )

    /** Instances that used to be defaults but are gone for good; dropped from saved lists on upgrade. */
    private val retiredDefaultUrls: Set<String> = setOf(
        "https://yewtu.be", "https://pipedapi.adminforge.de",
        // Left the official instances.invidious.io list (dead or delisted).
        "https://inv.tux.pizza", "https://invidious.flokinet.to", "https://invidious.private.coffee",
        "https://invidious.jing.rocks", "https://invidious.n8n7.io", "https://inv.us.projectsegfau.lt",
        "https://invidious.fdn.fr"
    )

    /**
     * English-teaching YouTube channels the app follows out of the box. The
     * ids are the channels' permanent `UC…` ids (handles can change, ids
     * cannot). Names/avatars are refreshed from the instance when opened.
     */
    val defaultChannels: List<OnlineChannel> = listOf(
        OnlineChannel("UCHaHD477h-FeBbVh9Sh7syA", "BBC Learning English", isDefault = true),
        OnlineChannel("UCz4tgANd4yy8Oe0iXCdSWfA", "English with Lucy", isDefault = true),
        OnlineChannel("UCvn_XCl_mgQmt3sD753zdJA", "Rachel's English", isDefault = true),
        OnlineChannel("UCrRiVfHqBIIvSgKmgnSY66g", "mmmEnglish", isDefault = true),
        OnlineChannel("UCxJGMJbjokfnr2-s4_RXPxQ", "Speak English With Vanessa", isDefault = true),
        OnlineChannel("UCeTVoczn9NOZA9blls3YgUg", "Learn English with EnglishClass101.com", isDefault = true),
        OnlineChannel("UCZJJTxA36ZPNTJ1WFIByaeA", "Learn English with Bob the Canadian", isDefault = true),
        OnlineChannel("UCKgpamMlm872zkGDcBJHYDg", "Learn English With TV Series", isDefault = true),
        OnlineChannel("UCTRHegh7UqWuKRymXoqzbzA", "Easy English", isDefault = true),
        OnlineChannel("UCCoLYcsDxFjnFPYF057l-7w", "Easy Stories in English", isDefault = true),
        OnlineChannel("UCrJHj7MDQhmQ9iFuACdoWCg", "EnglishAnyone", isDefault = true),
        OnlineChannel("UCQyQinUTGYTpTz6TTS__xBQ", "Daily English", isDefault = true),
        OnlineChannel("UCXqIlSRBuqbq41hfl7cwGWw", "Tarle Speech & Language Services", isDefault = true),
        OnlineChannel("UCgzuT-fpJiyThTUlMiFRCKQ", "Learn English with Valen (ValenESL)", isDefault = true),
    )

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Instances ──

    fun loadInstances(context: Context): List<OnlineInstance> {
        val p = prefs(context)
        val raw = p.getString(KEY_INSTANCES, null) ?: return defaultInstances
        val saved = try {
            parseInstances(raw).ifEmpty { return defaultInstances }
        } catch (e: Exception) {
            return defaultInstances
        }
        val version = p.getInt(KEY_INSTANCES_VERSION, 1)
        if (version >= INSTANCES_VERSION) return saved
        val upgraded = upgradeInstances(saved)
        p.edit()
            .putString(KEY_INSTANCES, serializeInstances(upgraded))
            .putInt(KEY_INSTANCES_VERSION, INSTANCES_VERSION)
            .apply()
        // The remembered "last good" instance may be one that was retired.
        if (p.getString(KEY_LAST_INSTANCE, null)?.let { last -> upgraded.none { it.baseUrl == last } } == true) {
            p.edit().remove(KEY_LAST_INSTANCE).apply()
        }
        return upgraded
    }

    /**
     * Brings a list saved by an older app version up to date: an untouched
     * old default list becomes the new default list; a customised list keeps
     * the user's entries (and their order / on-off state), loses retired
     * defaults and gains the new defaults it did not have yet.
     */
    internal fun upgradeInstances(saved: List<OnlineInstance>): List<OnlineInstance> {
        val urls = saved.map { it.baseUrl }.toSet()
        if (previousDefaultUrls.any { it == urls }) return defaultInstances
        val kept = saved.filter { it.baseUrl !in retiredDefaultUrls }
        val missing = defaultInstances.filter { d -> kept.none { it.baseUrl == d.baseUrl } }
        return (kept + missing).ifEmpty { defaultInstances }
    }

    fun saveInstances(context: Context, instances: List<OnlineInstance>) {
        prefs(context).edit()
            .putString(KEY_INSTANCES, serializeInstances(instances))
            .putInt(KEY_INSTANCES_VERSION, INSTANCES_VERSION)
            .apply()
    }

    fun resetInstances(context: Context) {
        prefs(context).edit().remove(KEY_INSTANCES).remove(KEY_LAST_INSTANCE).remove(KEY_INSTANCES_VERSION).apply()
    }

    /** Base URL of the instance that answered last, tried first next time. */
    fun lastGoodInstance(context: Context): String? = prefs(context).getString(KEY_LAST_INSTANCE, null)

    fun rememberGoodInstance(context: Context, instance: OnlineInstance) {
        prefs(context).edit().putString(KEY_LAST_INSTANCE, instance.baseUrl).apply()
    }

    fun parseInstances(raw: String): List<OnlineInstance> {
        val arr = JSONArray(raw)
        val out = mutableListOf<OnlineInstance>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = normalizeBaseUrl(o.optString("url")) ?: continue
            val kind = try {
                OnlineInstanceKind.valueOf(o.optString("kind", OnlineInstanceKind.INVIDIOUS.name))
            } catch (e: IllegalArgumentException) {
                OnlineInstanceKind.INVIDIOUS
            }
            out.add(OnlineInstance(url, kind, o.optBoolean("enabled", true)))
        }
        return out.distinctBy { it.baseUrl }
    }

    fun serializeInstances(instances: List<OnlineInstance>): String {
        val arr = JSONArray()
        instances.forEach {
            arr.put(JSONObject().put("url", it.baseUrl).put("kind", it.kind.name).put("enabled", it.enabled))
        }
        return arr.toString()
    }

    /**
     * Cleans up a typed instance address: trims, adds `https://` when the
     * scheme is missing, drops paths / trailing slashes / a stray `/api/v1`.
     * Returns null when nothing usable remains.
     */
    fun normalizeBaseUrl(input: String?): String? {
        var s = input?.trim() ?: return null
        if (s.isEmpty()) return null
        if (!s.contains("://")) s = "https://$s"
        val schemeEnd = s.indexOf("://") + 3
        val rest = s.substring(schemeEnd)
        val hostAndPort = rest.substringBefore('/').substringBefore('?').substringBefore('#').trim().lowercase()
        if (hostAndPort.isEmpty() || hostAndPort.any { it.isWhitespace() }) return null
        val host = hostAndPort.substringBefore(':')
        if (!host.contains('.') && host != "localhost") return null
        val scheme = s.substring(0, schemeEnd).lowercase()
        if (scheme != "https://" && scheme != "http://") return null
        return scheme + hostAndPort
    }

    /**
     * Best guess of the API family from the host name: anything with "piped"
     * in it is treated as a Piped API server, everything else as Invidious.
     * The user can always override the kind in the add-instance dialog.
     */
    fun detectKind(baseUrl: String): OnlineInstanceKind =
        if (baseUrl.lowercase().contains("piped")) OnlineInstanceKind.PIPED else OnlineInstanceKind.INVIDIOUS

    // ── Followed channels ──

    fun loadChannels(context: Context): List<OnlineChannel> {
        val p = prefs(context)
        val removedDefaults = p.getStringSet(KEY_REMOVED_DEFAULTS, emptySet()) ?: emptySet()
        val custom = p.getString(KEY_CHANNELS, null)?.let {
            try { parseChannels(it) } catch (e: Exception) { emptyList() }
        } ?: emptyList()
        val customIds = custom.map { it.id }.toSet()
        // Defaults first (minus the ones the user unfollowed), then the
        // user's own channels in the order they were added. A default the
        // user re-followed lives in the custom list and keeps its flag off.
        val defaults = defaultChannels
            .filter { it.id !in removedDefaults && it.id !in customIds }
            .map { ch -> p.getString("avatar_${ch.id}", null)?.takeIf { it.isNotBlank() }?.let { ch.copy(avatarUrl = it) } ?: ch }
        return defaults + custom
    }

    fun followChannel(context: Context, channel: OnlineChannel) {
        val p = prefs(context)
        val custom = loadCustomChannels(p).filter { it.id != channel.id } + channel.copy(isDefault = false)
        val removed = (p.getStringSet(KEY_REMOVED_DEFAULTS, emptySet()) ?: emptySet()).toMutableSet()
        removed.remove(channel.id)
        p.edit()
            .putString(KEY_CHANNELS, serializeChannels(custom))
            .putStringSet(KEY_REMOVED_DEFAULTS, removed)
            .apply()
    }

    fun unfollowChannel(context: Context, channelId: String) {
        val p = prefs(context)
        val custom = loadCustomChannels(p).filter { it.id != channelId }
        val removed = (p.getStringSet(KEY_REMOVED_DEFAULTS, emptySet()) ?: emptySet()).toMutableSet()
        if (defaultChannels.any { it.id == channelId }) removed.add(channelId)
        p.edit()
            .putString(KEY_CHANNELS, serializeChannels(custom))
            .putStringSet(KEY_REMOVED_DEFAULTS, removed)
            .apply()
    }

    /** Stores a fresher name/avatar for a followed channel (defaults included). */
    fun updateChannelInfo(context: Context, channel: OnlineChannel) {
        val p = prefs(context)
        val custom = loadCustomChannels(p)
        if (custom.none { it.id == channel.id }) {
            // Defaults are not copied into the custom list just for metadata;
            // their avatars are cached separately. Info that came from a feed
            // has no avatar — keep whatever an instance gave us earlier.
            channel.avatarUrl?.takeIf { it.isNotBlank() }?.let { p.edit().putString("avatar_${channel.id}", it).apply() }
            return
        }
        val updated = custom.map { if (it.id == channel.id) it.copy(name = channel.name.ifBlank { it.name }, avatarUrl = channel.avatarUrl ?: it.avatarUrl) else it }
        p.edit().putString(KEY_CHANNELS, serializeChannels(updated)).apply()
    }

    fun cachedAvatar(context: Context, channelId: String): String? =
        prefs(context).getString("avatar_$channelId", null)?.takeIf { it.isNotBlank() }

    fun restoreDefaultChannels(context: Context) {
        prefs(context).edit().remove(KEY_REMOVED_DEFAULTS).apply()
    }

    private fun loadCustomChannels(p: SharedPreferences): List<OnlineChannel> =
        p.getString(KEY_CHANNELS, null)?.let { try { parseChannels(it) } catch (e: Exception) { emptyList() } } ?: emptyList()

    fun parseChannels(raw: String): List<OnlineChannel> {
        val arr = JSONArray(raw)
        val out = mutableListOf<OnlineChannel>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").trim()
            if (id.isEmpty()) continue
            out.add(
                OnlineChannel(
                    id = id,
                    name = o.optString("name", id),
                    avatarUrl = o.optString("avatar").takeIf { it.isNotBlank() },
                    description = o.optString("description").takeIf { it.isNotBlank() }
                )
            )
        }
        return out.distinctBy { it.id }
    }

    fun serializeChannels(channels: List<OnlineChannel>): String {
        val arr = JSONArray()
        channels.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("avatar", it.avatarUrl ?: "")
                    .put("description", it.description ?: "")
            )
        }
        return arr.toString()
    }

    // ── Link helpers ──

    private val videoIdRegex = Regex("^[A-Za-z0-9_-]{11}$")
    private val channelIdRegex = Regex("^UC[A-Za-z0-9_-]{22}$")

    /**
     * Pulls the 11-character video id out of anything the user may paste:
     * `youtube.com/watch?v=ID`, `youtu.be/ID`, `youtube.com/shorts/ID`,
     * `youtube.com/embed/ID`, `youtube.com/live/ID`, an Invidious/Piped
     * watch link, or the bare id itself.
     */
    fun extractVideoId(input: String?): String? {
        val s = input?.trim() ?: return null
        if (s.isEmpty()) return null
        if (videoIdRegex.matches(s)) return s
        Regex("[?&]v=([A-Za-z0-9_-]{11})").find(s)?.let { return it.groupValues[1] }
        Regex("(?:youtu\\.be/|/shorts/|/embed/|/live/|/v/|/watch/)([A-Za-z0-9_-]{11})").find(s)?.let { return it.groupValues[1] }
        return null
    }

    /**
     * Pulls a `UC…` channel id from a pasted link or bare id. Handles
     * (`@name`) and `/c/name` links cannot be resolved offline; they are
     * returned as null and the caller resolves them through an instance.
     */
    fun extractChannelId(input: String?): String? {
        val s = input?.trim() ?: return null
        if (s.isEmpty()) return null
        if (channelIdRegex.matches(s)) return s
        Regex("/channel/(UC[A-Za-z0-9_-]{22})").find(s)?.let { return it.groupValues[1] }
        return null
    }

    /** `@handle`, `/c/name`, `/user/name` → the path piece an instance can resolve, or null. */
    fun extractChannelHandle(input: String?): String? {
        val s = input?.trim() ?: return null
        if (s.isEmpty()) return null
        if (s.startsWith("@") && s.length > 1 && !s.contains('/')) return s
        Regex("youtube\\.com/(@[A-Za-z0-9._-]+)").find(s)?.let { return it.groupValues[1] }
        Regex("youtube\\.com/c/([A-Za-z0-9._-]+)").find(s)?.let { return "c/${it.groupValues[1]}" }
        Regex("youtube\\.com/user/([A-Za-z0-9._-]+)").find(s)?.let { return "user/${it.groupValues[1]}" }
        return null
    }
}
