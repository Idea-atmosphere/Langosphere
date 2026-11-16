package com.example.mdict

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * MdictLibrary against the synthetic test dictionaries: MDX/MDD pairing by
 * basename, shared MDDs, lookups across files, case fallback, unreadable
 * files reported instead of breaking the rest, and name sanitising.
 */
class MdictLibraryTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File.createTempFile("mdict-lib", "").apply {
            delete()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun copyResource(name: String, target: String = name): File {
        val url = javaClass.classLoader!!.getResource(name)
            ?: throw IllegalStateException("missing test resource: $name")
        val out = File(dir, target)
        url.openStream().use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    @Test
    fun emptyDirectoryOpensNothing() {
        val lib = MdictLibrary(File(dir, "missing"))
        lib.reload()
        assertTrue(lib.isEmpty)
        assertTrue(lib.lookup("apple").isEmpty())
        assertNull(lib.resource("pic.png"))
        assertTrue(lib.headwords().isEmpty())
        lib.close()
    }

    @Test
    fun pairsMdxWithItsMddAndLooksWordsUp() {
        copyResource("testdict.mdx", "Test Dictionary.mdx")
        copyResource("testdict.mdd", "Test Dictionary.mdd")
        val lib = MdictLibrary(dir)
        lib.reload()
        try {
            assertFalse(lib.isEmpty)
            assertTrue(lib.errors.isEmpty())
            assertEquals(1, lib.dictionaries.size)
            assertEquals("Test Dictionary.mdx", lib.dictionaries[0].fileName)

            val hits = lib.lookup("apple")
            assertEquals(1, hits.size)
            assertEquals("Test Dictionary.mdx", hits[0].fileName)
            assertEquals("Test Dictionary", hits[0].title)
            assertEquals("سیب؛ میوه‌ای گرد و سرخ.", hits[0].html)

            // Redirect followed, resource served through the paired MDD.
            assertEquals("سلام! خوش آمدید. <img src=\"pic.png\">", lib.lookup("hi")[0].html)
            assertNotNull(lib.resource("pic.png"))
            assertNotNull(lib.resource("/images/logo.png"))
            assertNull(lib.resource("nope.png"))

            // The popup lowercases words; capitalised input still hits.
            assertEquals(1, lib.lookup("Apple").size)
            assertEquals(1, lib.lookup("  apple ").size)
            assertTrue(lib.lookup("notaword").isEmpty())
            assertTrue(lib.lookup("").isEmpty())

            assertEquals(25, lib.headwords().size)
            assertTrue(lib.suggestions("w", 10).containsAll(listOf("water", "world", "word")))
        } finally {
            lib.close()
        }
    }

    @Test
    fun sharedMddServesEveryDictionaryAndBadFilesAreReported() {
        copyResource("testdict.mdx", "A.mdx")
        copyResource("testdict-v12.mdx", "B.mdx")
        copyResource("testdict.mdd", "Sound.mdd") // no matching MDX → shared
        File(dir, "broken.mdx").writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        File(dir, "notes.txt").writeText("ignored")
        val lib = MdictLibrary(dir)
        lib.reload()
        try {
            assertEquals(2, lib.dictionaries.size)
            assertEquals(listOf("A.mdx", "B.mdx"), lib.dictionaries.map { it.fileName })
            assertEquals(setOf("broken.mdx"), lib.errors.keys)
            assertEquals(MdictException.Kind.CORRUPT, lib.errors["broken.mdx"]!!.kind)

            // Both dictionaries answer; import order is kept.
            val hits = lib.lookup("cat")
            assertEquals(listOf("A.mdx", "B.mdx"), hits.map { it.fileName })
            // The shared MDD is reachable even though no MDX shares its basename.
            assertNotNull(lib.resource("pic.png"))
            // Headwords are deduplicated across the two identical word lists.
            assertEquals(25, lib.headwords().size)
        } finally {
            lib.close()
        }
    }

    @Test
    fun mddOnlyLibraryStillServesResources() {
        copyResource("testdict.mdd", "Sound.mdd")
        val lib = MdictLibrary(dir)
        lib.reload()
        try {
            assertTrue(lib.isEmpty)
            assertTrue(lib.lookup("apple").isEmpty())
            assertNotNull(lib.resource("pic.png"))
        } finally {
            lib.close()
        }
    }

    @Test
    fun reloadAfterDeletingAFileDropsIt() {
        val a = copyResource("testdict.mdx", "A.mdx")
        copyResource("testdict-v12.mdx", "B.mdx")
        val lib = MdictLibrary(dir)
        lib.reload()
        assertEquals(2, lib.dictionaries.size)
        assertTrue(a.delete())
        lib.reload()
        assertEquals(listOf("B.mdx"), lib.dictionaries.map { it.fileName })
        lib.close()
        assertTrue(lib.isEmpty)
    }

    @Test
    fun namingHelpers() {
        assertEquals("Oxford", MdictLibrary.baseName("Oxford.mdx"))
        assertEquals("Oxford", MdictLibrary.baseName("Oxford.mdd"))
        assertEquals("Oxford", MdictLibrary.baseName("Oxford.1.mdd"))
        assertEquals("Oxford", MdictLibrary.baseName("Oxford.12.mdd"))
        assertEquals("Oxford.v2", MdictLibrary.baseName("Oxford.v2.mdd"))
        assertTrue(MdictLibrary.belongsTo("oxford.1.mdd", "Oxford"))
        assertFalse(MdictLibrary.belongsTo("Sound.mdd", "Oxford"))

        assertEquals("My Dict.mdx", MdictLibrary.sanitizeFileName("  My Dict.mdx "))
        assertEquals("a_b.mdd", MdictLibrary.sanitizeFileName("a/b.mdd"))
        assertEquals("_a_b.mdx", MdictLibrary.sanitizeFileName("..\\a\\b.mdx"))
        assertEquals("dictionary.mdx", MdictLibrary.sanitizeFileName("   "))
        assertEquals("words.mdx", MdictLibrary.sanitizeFileName("words"))
        assertEquals("hidden.mdx", MdictLibrary.sanitizeFileName(".hidden.mdx"))
        assertTrue(MdictLibrary.isMdictName("X.MDX"))
        assertFalse(MdictLibrary.isMdictName("X.db"))

        assertEquals(listOf("Hello", "hello", "HELLO"), MdictLibrary.caseVariants("Hello"))
        assertEquals(listOf("hello", "Hello", "HELLO"), MdictLibrary.caseVariants("hello"))
    }
}
