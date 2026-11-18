package com.example.zonos

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * v0.2.18 Task A — the WEB Studio's voice designs flow into the PDF
 * reader. Pure Kotlin, no Android dependencies, so the JVM harnesses
 * can test it directly.
 *
 * WHERE THE STUDIO KEEPS ITS DESIGNS (verified in Zyphra's source,
 * NOT guessed — studio/src/state/projectStore.ts + projectDoc.ts +
 * projects.ts):
 * - IndexedDB database `zvs-projects`, object store `docs`: key =
 *   project id, value = the project document as a JSON string.
 * - localStorage key `zvs.project` holds the open project's id
 *   (LAST = 'zvs.project', projects.ts:54). Autosave writes the doc
 *   800 ms after a change (AUTOSAVE_MS, projects.ts:55).
 * - The doc's `cast.design` is the Cast BENCH design; `cast.personas`
 *   are the saved cast members (Persona: {id, name, design, ...}).
 * - A Design is {baseId, mode, values, pin}: values are UI control
 *   ids -> numbers (the SAME fixture ids as the reader's control table
 *   — verified against controls.json ui.controls), pin is the 2D accent-map pin
 *   {tab, x, y}. mode is 'embedding' or 'reference'.
 * - Decoder / LACI are NOT in the design: they live in the project's
 *   `settings` ({language, voiceMode, decoder, laci} — projectSchema.ts;
 *   LaciMode 'off' | 'stream' | 'whole', lib/laci.ts).
 * - The reader engine page and the Studio page share the origin
 *   https://appassets.androidplatform.net, so the reader's WebView
 *   reads the same IndexedDB/localStorage (ZonoEngine.fetchStudioDoc).
 *
 * PRECEDENCE (defined here, in code, not just in prose):
 *   Studio design for this voice WINS; then defaults (v0.2.20: no
 *   saved-design fallback any more).
 *   Studio design pick: the BENCH design when its baseId is this
 *   voice, else the LAST-saved persona whose design.baseId is this
 *   voice (personas are stored in save order, so the last match is
 *   the most recently saved one).
 *   When a Studio design applies, decoder/LACI come from the Studio
 *   project's settings (decoder null = don't send; laci 'off' = don't
 *   send); otherwise defaults apply (no decoder/LACI sent).
 *   PACE RULE (v0.2.17, unchanged): the reader speed slider owns
 *   "delivery.tempo" — a saved pace (Studio) is dropped
 *   before the merge; the slider's dose wins.
 *
 * ACCENT PIN (studio/src/state/atlas.ts + geo.ts): the pin's depth
 * into a region sets that accent's strength (range max at the region's
 * deepest point, a fifth of it at the outline, fading to 0 just
 * outside the halo). The region polygons are REAL country outlines
 * (Natural Earth 110m, Mercator per panel) — porting that geometry
 * into Kotlin would mean shipping the whole world map, so the
 * geometry was computed ONCE at build time from the same data with a
 * direct port of geo.ts tabGeo() (see /tmp/build_atlas.py) and is
 * shipped as the compact asset studio-atlas.json (15 regions, 2017
 * points, 29 KB). mixAt() below is the same algorithm as atlas.ts
 * mixAt/strengthAt + geo.ts depth/inside/edgeDistance, verified
 * against a Python reference (pole = full strength, home = empty,
 * border blends).
 */

// ---- accent atlas geometry (asset studio-atlas.json) ----

/** One accent's geometry: which tab/region it lives in, and its strength cap (fixture range[1]; 1.5 for mandarin, 2.0 for the rest). */
data class StudioAccentGeo(
    val id: String,
    val tab: String,
    val region: String,
    val rangeMax: Float
)

/** Fixture ui.accents: id, tab, region, range[1] (controls.json). The map is in VOICE_ACCENTS; this is the geometry side. */
val STUDIO_ACCENT_GEO = listOf(
    StudioAccentGeo("american", "english", "north_america", 2.0f),
    StudioAccentGeo("british", "english", "england", 2.0f),
    StudioAccentGeo("scottish", "english", "scotland", 2.0f),
    StudioAccentGeo("irish", "english", "ireland", 2.0f),
    StudioAccentGeo("welsh", "english", "wales", 2.0f),
    StudioAccentGeo("australian", "english", "australia", 2.0f),
    StudioAccentGeo("indian", "english", "india", 2.0f),
    StudioAccentGeo("hin", "world", "india", 2.0f),
    StudioAccentGeo("spa", "world", "spain", 2.0f),
    StudioAccentGeo("fra", "world", "france", 2.0f),
    StudioAccentGeo("por", "world", "portugal", 2.0f),
    StudioAccentGeo("deu", "world", "germany", 2.0f),
    StudioAccentGeo("vie", "world", "vietnam", 2.0f),
    StudioAccentGeo("pol", "world", "poland", 2.0f),
    StudioAccentGeo("mandarin", "world", "china", 1.5f)
)

/** One atlas region: projected polygons + deepest point + inradius (from studio-atlas.json). */
data class StudioAtlasRegion(
    val polys: List<List<Pair<Double, Double>>>,
    val pole: Pair<Double, Double>,
    val inradius: Double
)

/**
 * The accent atlas: region geometry per tab + mixAt(). Pure math;
 * parse the asset JSON with [parseStudioAtlas] (MiniJson — no org.json
 * on the JVM harness classpath).
 */
class StudioAtlas(val tabs: Map<String, Map<String, StudioAtlasRegion>>) {
    companion object {
        /** Influence just outside a region's outline (atlas units) — geo.ts HALO. */
        const val HALO = 18.0
        /** Share of the strength reached right at the outline — geo.ts EDGE_W. */
        const val EDGE_W = 0.2
    }

    /** Accent strengths at a pin position (atlas.ts mixAt + strengthAt). */
    fun mixAt(tab: String, x: Double, y: Double): Map<String, Float> {
        val regions = tabs[tab] ?: return emptyMap()
        val out = LinkedHashMap<String, Float>()
        for (a in STUDIO_ACCENT_GEO) {
            if (a.tab != tab) continue
            val region = regions[a.region] ?: continue
            val s = strengthAt(a, region, x, y)
            if (s > 0f) out[a.id] = s
        }
        return out
    }

    /** atlas.ts strengthAt: round(rangeMax * depth * 20) / 20. */
    fun strengthAt(a: StudioAccentGeo, region: StudioAtlasRegion, x: Double, y: Double): Float {
        val d = depth(region, x, y)
        val r = Math.round(a.rangeMax.toDouble() * d * 20) / 20.0
        return r.toFloat()
    }

    /** geo.ts depth: 1 at the deepest point, EDGE_W at the outline, 0 beyond the halo. */
    fun depth(region: StudioAtlasRegion, x: Double, y: Double): Double {
        val d = edgeDistance(region.polys, x, y)
        return if (inside(region.polys, x, y)) {
            EDGE_W + (1 - EDGE_W) * min(1.0, d / region.inradius)
        } else {
            EDGE_W * max(0.0, 1 - d / HALO)
        }
    }

    /** geo.ts inside: ray-casting point-in-polygon over all rings. */
    fun inside(rings: List<List<Pair<Double, Double>>>, px: Double, py: Double): Boolean {
        var hit = false
        for (r in rings) {
            var j = r.size - 1
            for (i in r.indices) {
                val (xi, yi) = r[i]
                val (xj, yj) = r[j]
                if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) hit = !hit
                j = i
            }
        }
        return hit
    }

    /** geo.ts edgeDistance: distance to the nearest polygon edge. */
    fun edgeDistance(rings: List<List<Pair<Double, Double>>>, px: Double, py: Double): Double {
        var best = Double.POSITIVE_INFINITY
        for (r in rings) {
            var j = r.size - 1
            for (i in r.indices) {
                val (ax, ay) = r[j]
                val (bx, by) = r[i]
                val dx = bx - ax
                val dy = by - ay
                val den = dx * dx + dy * dy
                val t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (if (den == 0.0) 1.0 else den)))
                best = min(best, hypot(px - ax - t * dx, py - ay - t * dy))
                j = i
            }
        }
        return best
    }
}

/** Parse studio-atlas.json (MiniJson). Returns null when the JSON is unusable (caller degrades to no accents). */
fun parseStudioAtlas(json: String): StudioAtlas? {
    val root = MiniJson.parse(json) as? JVal.Obj ?: return null
    val tabsObj = root.map["tabs"] as? JVal.Obj ?: return null
    val tabs = LinkedHashMap<String, Map<String, StudioAtlasRegion>>()
    for ((tab, tabVal) in tabsObj.map) {
        val tabObj = tabVal as? JVal.Obj ?: continue
        val regionsObj = tabObj.map["regions"] as? JVal.Obj ?: continue
        val regions = LinkedHashMap<String, StudioAtlasRegion>()
        for ((rid, regVal) in regionsObj.map) {
            val regObj = regVal as? JVal.Obj ?: continue
            val polysArr = regObj.map["polys"] as? JVal.Arr ?: continue
            val polys = ArrayList<List<Pair<Double, Double>>>()
            for (ringVal in polysArr.items) {
                val ringArr = ringVal as? JVal.Arr ?: continue
                val ring = ArrayList<Pair<Double, Double>>()
                for (ptVal in ringArr.items) {
                    val ptArr = ptVal as? JVal.Arr ?: continue
                    if (ptArr.items.size < 2) continue
                    val x = (ptArr.items[0] as? JVal.Num)?.d ?: continue
                    val y = (ptArr.items[1] as? JVal.Num)?.d ?: continue
                    ring.add(x to y)
                }
                if (ring.size >= 3) polys.add(ring)
            }
            if (polys.isEmpty()) continue
            val poleArr = (regObj.map["pole"] as? JVal.Arr)?.items
            val pole = if (poleArr != null && poleArr.size >= 2) {
                ((poleArr[0] as? JVal.Num)?.d ?: 0.0) to ((poleArr[1] as? JVal.Num)?.d ?: 0.0)
            } else 0.0 to 0.0
            val inradius = (regObj.map["inradius"] as? JVal.Num)?.d ?: 4.0
            regions[rid] = StudioAtlasRegion(polys, pole, inradius)
        }
        tabs[tab] = regions
    }
    if (tabs.isEmpty()) return null
    return StudioAtlas(tabs)
}

// ---- Studio project document ----

/** One Studio voice design (state/design.ts Design). */
data class StudioDesign(
    val baseId: String,
    /** 'embedding' or 'reference'. */
    val mode: String,
    val values: Map<String, Float>,
    val pinTab: String,
    val pinX: Double,
    val pinY: Double
)

/** One saved cast member (state/library.ts Persona). */
data class StudioPersona(val name: String, val design: StudioDesign)

/** What the reader needs from the open project document. */
data class StudioDoc(
    /** Project settings decoder id (null = server default); only 'full_mr' is ever sent by the reader. */
    val settingsDecoder: String?,
    /** Project settings LACI mode ('off' | 'stream' | 'whole'). */
    val settingsLaci: String,
    val bench: StudioDesign?,
    val personas: List<StudioPersona>
)

/**
 * Parse the compact doc JSON the extraction JS returns
 * ({settings, bench, personas:[{name, design}]} — see
 * ZonoEngine.STUDIO_DOC_READ_JS). Defensive: a bad design/persona is
 * skipped, the rest load; returns null only when the JSON itself is
 * unusable.
 */
fun parseStudioDoc(json: String): StudioDoc? {
    val root = MiniJson.parse(json) as? JVal.Obj ?: return null
    var settingsDecoder: String? = null
    var settingsLaci = "off"
    val settingsObj = root.map["settings"] as? JVal.Obj
    if (settingsObj != null) {
        settingsDecoder = (settingsObj.map["decoder"] as? JVal.Str)?.s
        settingsLaci = (settingsObj.map["laci"] as? JVal.Str)?.s
            ?.takeIf { it in listOf("off", "stream", "whole") } ?: "off"
    }
    val bench = (root.map["bench"] as? JVal.Obj)?.let { parseStudioDesign(it) }
    val personas = ArrayList<StudioPersona>()
    val personasArr = root.map["personas"] as? JVal.Arr
    if (personasArr != null) {
        for (pVal in personasArr.items) {
            val pObj = pVal as? JVal.Obj ?: continue
            val designObj = pObj.map["design"] as? JVal.Obj ?: continue
            val design = parseStudioDesign(designObj) ?: continue
            personas.add(StudioPersona((pObj.map["name"] as? JVal.Str)?.s ?: "", design))
        }
    }
    return StudioDoc(settingsDecoder, settingsLaci, bench, personas)
}

/** Parse one Design object. Null when baseId is missing (a design without a base matches no voice). */
fun parseStudioDesign(obj: JVal.Obj): StudioDesign? {
    val baseId = (obj.map["baseId"] as? JVal.Str)?.s ?: return null
    val mode = (obj.map["mode"] as? JVal.Str)?.s?.takeIf { it == "reference" } ?: "embedding"
    val values = LinkedHashMap<String, Float>()
    val valuesObj = obj.map["values"] as? JVal.Obj
    if (valuesObj != null) {
        for ((k, n) in valuesObj.map) {
            val d = (n as? JVal.Num)?.d ?: continue
            values[k] = d.toFloat()
        }
    }
    val pinObj = obj.map["pin"] as? JVal.Obj
    val pinTab = (pinObj?.map?.get("tab") as? JVal.Str)?.s ?: "english"
    val pinX = (pinObj?.map?.get("x") as? JVal.Num)?.d ?: 0.0
    val pinY = (pinObj?.map?.get("y") as? JVal.Num)?.d ?: 0.0
    return StudioDesign(baseId, mode, values, pinTab, pinX, pinY)
}

/**
 * Where the effective design of a voice came from. The UI turns this
 * into a localized label (the origin carried a Persian string here).
 */
sealed class DesignSource {
    /** No Studio design for this voice: the engine's defaults are used. */
    object Default : DesignSource()

    /** The Cast bench design of the open Studio project. */
    object Bench : DesignSource()

    /** A saved cast member of the open Studio project. */
    data class Persona(val name: String) : DesignSource()
}

/**
 * Pick the Studio design for one voice: the bench when its baseId is
 * this voice, else the last-saved persona whose design.baseId is this
 * voice. Returns the design + its source, or null.
 */
fun studioDesignForVoice(doc: StudioDoc?, voiceId: String): Pair<StudioDesign, DesignSource>? {
    if (doc == null) return null
    val bench = doc.bench
    if (bench != null && bench.baseId == voiceId) {
        return bench to DesignSource.Bench
    }
    val persona = doc.personas.lastOrNull { it.design.baseId == voiceId }
    if (persona != null) {
        return persona.design to DesignSource.Persona(persona.name)
    }
    return null
}

/**
 * Controls usable in 'reference' (Exact clone) mode — computed from
 * the fixture (controls.json sets/directions): a control is usable
 * when EVERY direction it maps to allows 'reference'. Accents are ALL
 * embedding-only in reference mode (mandarin fails too: its
 * mandarin.cmn direction does not allow reference, and controlUsable
 * requires all directions). Verified against the fixture, NOT guessed.
 */
val REFERENCE_USABLE_CONTROLS = setOf(
    "projection", "pace", "chatty", "hesitant", "lively", "emphasis",
    "valence", "arousal", "angry", "happy", "sad", "calm", "scared", "confused"
)

/**
 * v0.2.19 — the EMBEDDING-MOVING direction ids: every runtime
 * direction in a `space: "raw"` set (controls.json sets/directions).
 * steer.ts resolveControls throws "Controls <ids> move the speaker
 * embedding and need speaker_mode=embedding" when ANY of these is
 * dosed without an embedding present — which is exactly log #12
 * (texture.shrill/husky/raspy). 26 ids, from the fixture, NOT guessed:
 * identity (age, gender), pitch (pitch, pitch_range), accents_en (7),
 * accents_l1 (7), mandarin.cmn, texture (deep, shrill, husky, raspy,
 * soft, authoritative), room.clean. The hidden-space sets (delivery,
 * expressive, disfluency, mood, emotions, emphasis_nv_nodur,
 * mandarin_hidden) work in both modes and are NOT in this set.
 * Every one of these 26 is emitted by the app's own maps
 * (VOICE_CONTROLS plus/minus + VOICE_ACCENTS) — the harness
 * asserts the coverage against the fixture, so no Studio design id
 * can ever be silently dropped or sent in the wrong mode.
 */
val EMBEDDING_MOVING_DIRECTIONS = setOf(
    "identity.age", "identity.gender",
    "pitch.pitch", "pitch.pitch_range",
    "accents_en.american", "accents_en.australian", "accents_en.british",
    "accents_en.indian", "accents_en.irish", "accents_en.scottish", "accents_en.welsh",
    "accents_l1.deu", "accents_l1.fra", "accents_l1.hin", "accents_l1.pol",
    "accents_l1.por", "accents_l1.spa", "accents_l1.vie",
    "mandarin.cmn",
    "texture.authoritative", "texture.deep", "texture.husky",
    "texture.raspy", "texture.shrill", "texture.soft",
    "room.clean"
)

/**
 * Convert a Studio Design to the shared VoiceDesign shape:
 * values are filtered to known fixture ids, clamped to the fixture
 * range, and (in reference mode) restricted to the reference-usable
 * controls; the accent mix comes from the pin via the atlas (empty
 * when the atlas is unavailable, and always empty in reference mode —
 * every accent is embedding-only there). Span-only controls
 * (emphasis) survive here and are dropped later by controlDoses.
 * Decoder/LACI are NOT in the design —
 * they come from the project settings (see resolveReaderDesign).
 * v0.2.19: the converted design CARRIES the Studio design's mode, so
 * the reader sends the matching speaker_mode (the mode is what made
 * log #12 fail: embedding-moving controls with the server defaulting
 * to 'reference'). "shrill" needs no special case: it is the bright
 * control's plus direction (fixture map "+" {"texture.shrill": 1}),
 * and bright is one of the 25 fixture ids this already covers.
 */
fun StudioDesign.toVoiceDesign(atlas: StudioAtlas?): VoiceDesign {
    val controls = LinkedHashMap<String, Float>()
    for ((k, v) in values) {
        val def = VOICE_CONTROLS.firstOrNull { it.id == k } ?: continue
        if (mode == "reference" && k !in REFERENCE_USABLE_CONTROLS) continue
        if (v == 0f) continue
        controls[k] = v.coerceIn(def.min, def.max)
    }
    val accents: Map<String, Float> =
        if (mode == "reference" || atlas == null) emptyMap()
        else atlas.mixAt(pinTab, pinX, pinY)
    return VoiceDesign(controls, accents, "off", "keep2_plain", mode)
}

/**
 * Resolve the effective design for one voice, with precedence
 * Studio > defaults. Returns the design (null = defaults) plus its
 * [DesignSource]. When a Studio design applies, its decoder/LACI come
 * from the Studio project's settings. Language is never carried (the
 * caller passes the spoken language separately).
 */
fun resolveReaderDesign(
    doc: StudioDoc?,
    voiceId: String,
    atlas: StudioAtlas?
): Pair<VoiceDesign?, DesignSource> {
    val studio = studioDesignForVoice(doc, voiceId)
    if (studio != null) {
        val (design, source) = studio
        val converted = design.toVoiceDesign(atlas)
        val decoder = if (doc?.settingsDecoder == "full_mr") "full_mr" else "keep2_plain"
        val laci = doc?.settingsLaci ?: "off"
        return converted.copy(laci = laci, decoder = decoder) to source
    }
    return null to DesignSource.Default
}

// ---- Studio UI language toggle ----

/** Normalize a stored Studio language: "fa" or "en"; anything else falls back to [default]. */
fun normalizeStudioLang(raw: String?, default: String = "en"): String = when (raw) {
    "en", "fa" -> raw
    else -> if (default == "fa") "fa" else "en"
}
