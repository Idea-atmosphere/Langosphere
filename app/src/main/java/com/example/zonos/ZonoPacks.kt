package com.example.zonos

import java.net.URLEncoder

/*
 * Model pack definition for the in-app TTS (Zonos3-Mini web export).
 *
 * Generated from the Hugging Face API tree for Zyphra/ZONOS3-Mini, path
 * web/ (HF commit d2d142a86b1e6aeb4252679673c86ecd86177bf8, 2026-11-14):
 * exact shard lists + sizes, Zyphra's sharded web format as-is (one
 * manifest.json per folder + shard-*.bin).
 *
 * Only the CORE pack is offered: it is everything plain speech needs.
 * The engine's worker init REQUIRES voices/voices.json and every voice
 * codes/embedding file (fetch throws on 404), plus backbone/ + zac/;
 * laci/index.json and companions/steer.gguf tolerate 404, but at 0.5 MB
 * they ride along so LACI and steering work immediately. voices/ is
 * NESTED by language on HF and in voices.json (voices/English/...,
 * voices/Mandarin Chinese/..., 10 language subfolders, 82 files); laci/
 * and companions/ are flat.
 *
 * The optional packs of the origin app (speaker encoder, raw-reference
 * clone, speech edit, full_mr decoder) are deliberately NOT listed: the
 * app only speaks with the bundled voices.
 */

/** One file of a pack: its folder (may be nested), name and exact size. */
data class PackFile(val folder: String, val name: String, val size: Long) {
    val relPath: String get() = "$folder/$name"
    val url: String get() = HF_WEB_BASE +
        relPath.split('/').joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
}

/** One downloadable pack. */
data class PackDef(
    val id: String,
    val required: Boolean,
    val files: List<PackFile>
) {
    val totalBytes: Long get() = files.sumOf { it.size }
    val folders: List<String> get() = files.map { it.folder }.distinct()
}

/** Where every pack file is fetched from (never by the engine itself; always by the Kotlin downloader). */
const val HF_WEB_BASE = "https://huggingface.co/Zyphra/ZONOS3-Mini/resolve/main/web/"

const val CORE_PACK_ID = "core"

/** The CORE pack: backbone + zac + 27 default voices + LACI + steering (145 files, 942693446 bytes).
 */
val CORE_PACK: PackDef = PackDef(
    CORE_PACK_ID, true, listOf(
        PackFile("backbone", "manifest.json", 33646L),
        PackFile("backbone", "shard-000.bin", 101007360L),
        PackFile("backbone", "shard-001.bin", 65155584L),
        PackFile("backbone", "shard-002.bin", 54345984L),
        PackFile("backbone", "shard-003.bin", 64696832L),
        PackFile("backbone", "shard-004.bin", 67090688L),
        PackFile("backbone", "shard-005.bin", 67090688L),
        PackFile("backbone", "shard-006.bin", 53144320L),
        PackFile("backbone", "shard-007.bin", 67090688L),
        PackFile("backbone", "shard-008.bin", 67090688L),
        PackFile("backbone", "shard-009.bin", 67090688L),
        PackFile("backbone", "shard-010.bin", 1184000L),
        PackFile("zac", "manifest.json", 36774L),
        PackFile("zac", "shard-000.bin", 6291456L),
        PackFile("zac", "shard-001.bin", 75497472L),
        PackFile("zac", "shard-002.bin", 66758656L),
        PackFile("zac", "shard-003.bin", 67106816L),
        PackFile("zac", "shard-004.bin", 29884152L),
        PackFile("voices/English", "Cillian_en-Irish_M_1.codes.npy", 5928L),
        PackFile("voices/English", "Cillian_en-Irish_M_1.embedding.npy", 896L),
        PackFile("voices/English", "Cillian_en-Irish_M_1.wav", 852644L),
        PackFile("voices/English", "Imogen_en-British_F_1.codes.npy", 7728L),
        PackFile("voices/English", "Imogen_en-British_F_1.embedding.npy", 896L),
        PackFile("voices/English", "Imogen_en-British_F_1.wav", 1117244L),
        PackFile("voices/English", "Jett_en-energetic_M_1.codes.npy", 6608L),
        PackFile("voices/English", "Jett_en-energetic_M_1.embedding.npy", 896L),
        PackFile("voices/English", "Jett_en-energetic_M_1.wav", 952604L),
        PackFile("voices/English", "Morgana_en-witch_F_1.codes.npy", 12448L),
        PackFile("voices/English", "Morgana_en-witch_F_1.embedding.npy", 896L),
        PackFile("voices/English", "Morgana_en-witch_F_1.mp3", 329812L),
        PackFile("voices/English", "Rupert_en-British_M_1.codes.npy", 8048L),
        PackFile("voices/English", "Rupert_en-British_M_1.embedding.npy", 896L),
        PackFile("voices/English", "Rupert_en-British_M_1.wav", 1164284L),
        PackFile("voices/English", "Sakura_en-anime_F_1.codes.npy", 9168L),
        PackFile("voices/English", "Sakura_en-anime_F_1.embedding.npy", 896L),
        PackFile("voices/English", "Sakura_en-anime_F_1.wav", 1328924L),
        PackFile("voices/English", "Savannah_en-American_F_1.codes.npy", 6968L),
        PackFile("voices/English", "Savannah_en-American_F_1.embedding.npy", 896L),
        PackFile("voices/English", "Savannah_en-American_F_1.mp3", 183527L),
        PackFile("voices/English", "Thaddeus_en-announcervoice_M_1.codes.npy", 9368L),
        PackFile("voices/English", "Thaddeus_en-announcervoice_M_1.embedding.npy", 896L),
        PackFile("voices/English", "Thaddeus_en-announcervoice_M_1.mp3", 247474L),
        PackFile("voices/English", "Willow_en-asrm_F_1.codes.npy", 15048L),
        PackFile("voices/English", "Willow_en-asrm_F_1.embedding.npy", 896L),
        PackFile("voices/English", "Willow_en-asrm_F_1.wav", 2193284L),
        PackFile("voices/English", "Wyatt_en-American_M_1.codes.npy", 10248L),
        PackFile("voices/English", "Wyatt_en-American_M_1.embedding.npy", 896L),
        PackFile("voices/English", "Wyatt_en-American_M_1.ogg", 147400L),
        PackFile("voices/French", "Gaspard_fr_M_1.codes.npy", 11488L),
        PackFile("voices/French", "Gaspard_fr_M_1.embedding.npy", 896L),
        PackFile("voices/French", "Gaspard_fr_M_1.mp3", 303899L),
        PackFile("voices/German", "Liesel_de_F_1.codes.npy", 6928L),
        PackFile("voices/German", "Liesel_de_F_1.embedding.npy", 896L),
        PackFile("voices/German", "Liesel_de_F_1.mp3", 182273L),
        PackFile("voices/German", "Wolfgang_de_M_1.codes.npy", 7488L),
        PackFile("voices/German", "Wolfgang_de_M_1.embedding.npy", 896L),
        PackFile("voices/German", "Wolfgang_de_M_1.mp3", 196483L),
        PackFile("voices/Hindi", "Ananya_hi_F_1.codes.npy", 7768L),
        PackFile("voices/Hindi", "Ananya_hi_F_1.embedding.npy", 896L),
        PackFile("voices/Hindi", "Ananya_hi_F_1.wav", 1123124L),
        PackFile("voices/Hindi", "Arjun_hi_M_1.codes.npy", 7128L),
        PackFile("voices/Hindi", "Arjun_hi_M_1.embedding.npy", 896L),
        PackFile("voices/Hindi", "Arjun_hi_M_1.wav", 1029044L),
        PackFile("voices/Italian", "Ginevra_it_F_1.codes.npy", 6808L),
        PackFile("voices/Italian", "Ginevra_it_F_1.embedding.npy", 896L),
        PackFile("voices/Italian", "Ginevra_it_F_1.mp3", 179347L),
        PackFile("voices/Italian", "Lorenzo_it_M_1.codes.npy", 7488L),
        PackFile("voices/Italian", "Lorenzo_it_M_1.embedding.npy", 896L),
        PackFile("voices/Italian", "Lorenzo_it_M_1.mp3", 196483L),
        PackFile("voices/Korean", "Minjun_ko_M_1.codes.npy", 7288L),
        PackFile("voices/Korean", "Minjun_ko_M_1.embedding.npy", 896L),
        PackFile("voices/Korean", "Minjun_ko_M_1.wav", 1052564L),
        PackFile("voices/Korean", "Seoyeon_ko_F_1.codes.npy", 7048L),
        PackFile("voices/Korean", "Seoyeon_ko_F_1.embedding.npy", 896L),
        PackFile("voices/Korean", "Seoyeon_ko_F_1.wav", 1017284L),
        PackFile("voices/Mandarin Chinese", "Haoran_zh-energetic_M_1.codes.npy", 6608L),
        PackFile("voices/Mandarin Chinese", "Haoran_zh-energetic_M_1.embedding.npy", 896L),
        PackFile("voices/Mandarin Chinese", "Haoran_zh-energetic_M_1.mp3", 173914L),
        PackFile("voices/Mandarin Chinese", "Meilin_zh-energetic_F_1.codes.npy", 6888L),
        PackFile("voices/Mandarin Chinese", "Meilin_zh-energetic_F_1.embedding.npy", 896L),
        PackFile("voices/Mandarin Chinese", "Meilin_zh-energetic_F_1.wav", 993764L),
        PackFile("voices/Portuguese", "Leonor_pt_F_1.codes.npy", 7448L),
        PackFile("voices/Portuguese", "Leonor_pt_F_1.embedding.npy", 896L),
        PackFile("voices/Portuguese", "Leonor_pt_F_1.wav", 1076084L),
        PackFile("voices/Portuguese", "Thiago_pt_M_1.codes.npy", 8848L),
        PackFile("voices/Portuguese", "Thiago_pt_M_1.embedding.npy", 896L),
        PackFile("voices/Portuguese", "Thiago_pt_M_1.wav", 1281884L),
        PackFile("voices/Spanish", "Rodrigo_es_M_1.codes.npy", 6408L),
        PackFile("voices/Spanish", "Rodrigo_es_M_1.embedding.npy", 896L),
        PackFile("voices/Spanish", "Rodrigo_es_M_1.wav", 923204L),
        PackFile("voices/Spanish", "Ximena_es_F_1.codes.npy", 7968L),
        PackFile("voices/Spanish", "Ximena_es_F_1.embedding.npy", 896L),
        PackFile("voices/Spanish", "Ximena_es_F_1.wav", 1152524L),
        PackFile("voices/Telugu", "Chaitanya_te_M_1.codes.npy", 6368L),
        PackFile("voices/Telugu", "Chaitanya_te_M_1.embedding.npy", 896L),
        PackFile("voices/Telugu", "Chaitanya_te_M_1.wav", 917324L),
        PackFile("voices/Telugu", "Sravya_te_F_1.codes.npy", 6928L),
        PackFile("voices/Telugu", "Sravya_te_F_1.embedding.npy", 896L),
        PackFile("voices/Telugu", "Sravya_te_F_1.wav", 999644L),
        PackFile("voices", "voices.json", 5760L),
        PackFile("laci", "index.json", 1593L),
        PackFile("laci", "zonos3_mini_heads.json", 7495L),
        PackFile("laci", "zonos3_mini_heads_ar.json", 7661L),
        PackFile("laci", "zonos3_mini_heads_bg.json", 7608L),
        PackFile("laci", "zonos3_mini_heads_bn.json", 7654L),
        PackFile("laci", "zonos3_mini_heads_cs.json", 7571L),
        PackFile("laci", "zonos3_mini_heads_da.json", 7616L),
        PackFile("laci", "zonos3_mini_heads_de.json", 7539L),
        PackFile("laci", "zonos3_mini_heads_el.json", 7603L),
        PackFile("laci", "zonos3_mini_heads_es.json", 7571L),
        PackFile("laci", "zonos3_mini_heads_fi.json", 7580L),
        PackFile("laci", "zonos3_mini_heads_fr.json", 7571L),
        PackFile("laci", "zonos3_mini_heads_gu.json", 7362L),
        PackFile("laci", "zonos3_mini_heads_he.json", 7639L),
        PackFile("laci", "zonos3_mini_heads_hi.json", 7579L),
        PackFile("laci", "zonos3_mini_heads_hu.json", 7614L),
        PackFile("laci", "zonos3_mini_heads_id.json", 7597L),
        PackFile("laci", "zonos3_mini_heads_it.json", 7557L),
        PackFile("laci", "zonos3_mini_heads_ja.json", 7584L),
        PackFile("laci", "zonos3_mini_heads_ka.json", 7568L),
        PackFile("laci", "zonos3_mini_heads_kn.json", 7698L),
        PackFile("laci", "zonos3_mini_heads_ko.json", 7566L),
        PackFile("laci", "zonos3_mini_heads_ml.json", 7639L),
        PackFile("laci", "zonos3_mini_heads_mr.json", 7591L),
        PackFile("laci", "zonos3_mini_heads_ms.json", 7564L),
        PackFile("laci", "zonos3_mini_heads_nl.json", 7626L),
        PackFile("laci", "zonos3_mini_heads_no.json", 7566L),
        PackFile("laci", "zonos3_mini_heads_or.json", 7434L),
        PackFile("laci", "zonos3_mini_heads_pa.json", 7582L),
        PackFile("laci", "zonos3_mini_heads_pl.json", 7569L),
        PackFile("laci", "zonos3_mini_heads_pt.json", 7557L),
        PackFile("laci", "zonos3_mini_heads_ro.json", 7574L),
        PackFile("laci", "zonos3_mini_heads_ru.json", 7573L),
        PackFile("laci", "zonos3_mini_heads_sk.json", 7578L),
        PackFile("laci", "zonos3_mini_heads_sv.json", 7584L),
        PackFile("laci", "zonos3_mini_heads_ta.json", 7452L),
        PackFile("laci", "zonos3_mini_heads_te.json", 7459L),
        PackFile("laci", "zonos3_mini_heads_th.json", 7630L),
        PackFile("laci", "zonos3_mini_heads_tl.json", 7602L),
        PackFile("laci", "zonos3_mini_heads_tr.json", 7679L),
        PackFile("laci", "zonos3_mini_heads_uk.json", 7631L),
        PackFile("laci", "zonos3_mini_heads_ur.json", 7628L),
        PackFile("laci", "zonos3_mini_heads_vi.json", 7644L),
        PackFile("laci", "zonos3_mini_heads_zh.json", 7558L),
        PackFile("companions", "steer.gguf", 204960L),
    )
)

val MODEL_PACKS: List<PackDef> = listOf(CORE_PACK)

fun packById(id: String): PackDef? = MODEL_PACKS.firstOrNull { it.id == id }
