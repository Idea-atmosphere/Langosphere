package com.example.zonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The CORE pack table and the per-folder repair logic: everything the
 * engine needs at init, nothing else, and "complete the pack" must only
 * ask for the files that are really missing.
 */
class CorePackTest {

    @Test
    fun corePackListsExactlyTheFiveEngineFolders() {
        assertEquals(145, CORE_PACK.files.size)
        assertEquals(942_693_446L, CORE_PACK.totalBytes)
        assertEquals(
            listOf("backbone", "zac", "voices", "laci", "companions"),
            CORE_PACK.folders.map { it.substringBefore('/') }.distinct()
        )
        assertEquals(listOf(CORE_PACK), MODEL_PACKS)
        assertEquals(CORE_PACK, packById("core"))
    }

    @Test
    fun urlsAreEncodedPerSegment() {
        val nested = CORE_PACK.files.first { it.folder.startsWith("voices/Mandarin Chinese") }
        assertTrue(nested.url.startsWith(HF_WEB_BASE + "voices/Mandarin%20Chinese/"))
        assertFalse(nested.url.contains(' '))
        val manifest = CORE_PACK.files.first { it.name == "manifest.json" && it.folder == "backbone" }
        assertEquals(HF_WEB_BASE + "backbone/manifest.json", manifest.url)
    }

    @Test
    fun backboneAndZacAloneLeaveVoicesLaciAndSteerMissing() {
        val sizes: Map<String, Long?> = CORE_PACK.files
            .filter { it.folder == "backbone" || it.folder == "zac" }
            .associate { it.relPath to it.size }
        val statuses = folderStatuses(CORE_PACK, sizes)
        assertEquals(listOf("backbone", "zac", "voices", "laci", "companions"), statuses.map { it.folder })
        assertTrue(statuses.first { it.folder == "backbone" }.complete)
        assertTrue(statuses.first { it.folder == "zac" }.complete)
        val voices = statuses.first { it.folder == "voices" }
        assertEquals(0, voices.presentFiles)
        assertEquals(82, voices.totalFiles)
        assertEquals(145 - 18, missingFiles(CORE_PACK, sizes).size)
        assertTrue(missingBytes(CORE_PACK, sizes) < 25L * 1_048_576)
    }

    @Test
    fun wrongSizeCountsAsMissing() {
        val sizes = HashMap<String, Long?>()
        for (f in CORE_PACK.files) sizes[f.relPath] = f.size
        val victim = CORE_PACK.files.first { it.folder == "zac" && it.name.startsWith("shard") }
        sizes[victim.relPath] = victim.size - 1
        val missing = missingFiles(CORE_PACK, sizes)
        assertEquals(listOf(victim), missing)
        assertFalse(folderStatuses(CORE_PACK, sizes).first { it.folder == "zac" }.complete)
    }
}
