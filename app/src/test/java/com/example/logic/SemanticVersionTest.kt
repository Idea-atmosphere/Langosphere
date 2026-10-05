package com.example.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [SemanticVersion]: how the in-app updater turns a GitHub release
 * tag (or a versionName) into comparable numbers, and the MAJOR.MINOR.PATCH
 * ordering itself.
 */
class SemanticVersionTest {

    // ── parsing ──

    @Test
    fun `parses a plain three-part version`() {
        assertEquals(SemanticVersion(0, 0, 72), SemanticVersion.parse("0.0.72"))
        assertEquals(SemanticVersion(1, 23, 456), SemanticVersion.parse("1.23.456"))
    }

    @Test
    fun `parses a leading v or V`() {
        assertEquals(SemanticVersion(0, 0, 73), SemanticVersion.parse("v0.0.73"))
        assertEquals(SemanticVersion(2, 0, 0), SemanticVersion.parse("V2.0.0"))
    }

    @Test
    fun `missing components count as zero`() {
        assertEquals(SemanticVersion(1, 2, 0), SemanticVersion.parse("1.2"))
        assertEquals(SemanticVersion(7, 0, 0), SemanticVersion.parse("7"))
    }

    @Test
    fun `pre-release and build metadata are ignored`() {
        assertEquals(SemanticVersion(2, 1, 0), SemanticVersion.parse("2.1.0-beta.1"))
        assertEquals(SemanticVersion(2, 1, 0), SemanticVersion.parse("v2.1.0+build.42"))
        assertEquals(SemanticVersion(2, 1, 0), SemanticVersion.parse("v2.1-beta"))
    }

    @Test
    fun `junk returns null instead of guessing`() {
        assertNull(SemanticVersion.parse(null))
        assertNull(SemanticVersion.parse(""))
        assertNull(SemanticVersion.parse("   "))
        assertNull(SemanticVersion.parse("latest"))
        assertNull(SemanticVersion.parse("release-2026"))
        assertNull(SemanticVersion.parse("1.2.x"))
        assertNull(SemanticVersion.parse("1..2"))
        assertNull(SemanticVersion.parse("1.2.3.4"))
        assertNull(SemanticVersion.parse("v"))
        assertNull(SemanticVersion.parse("v-1.2.3"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals(SemanticVersion(0, 0, 72), SemanticVersion.parse(" 0.0.72\n"))
    }

    @Test
    fun `leading zeros are decimal not octal`() {
        assertEquals(SemanticVersion(0, 0, 9), SemanticVersion.parse("0.0.09"))
    }

    // ── ordering ──

    @Test
    fun `patch increments order`() {
        assertTrue(SemanticVersion.parse("v0.0.72")!! < SemanticVersion.parse("v0.0.73")!!)
        assertFalse(SemanticVersion.parse("v0.0.73")!! < SemanticVersion.parse("v0.0.72")!!)
        assertTrue(SemanticVersion.parse("v0.0.72")!! < SemanticVersion.parse("v0.0.710")!!)
    }

    @Test
    fun `a bigger minor beats any patch`() {
        assertTrue(SemanticVersion.parse("0.1.0")!! > SemanticVersion.parse("0.0.99")!!)
    }

    @Test
    fun `a bigger major beats any minor and patch`() {
        assertTrue(SemanticVersion.parse("2.0.0")!! > SemanticVersion.parse("1.999.999")!!)
    }

    @Test
    fun `equal versions compare as zero`() {
        assertEquals(0, SemanticVersion.parse("1.2")!!.compareTo(SemanticVersion.parse("v1.2.0")!!))
        assertEquals(SemanticVersion.parse("1.2"), SemanticVersion.parse("v1.2.0"))
    }

    @Test
    fun `toString round trips`() {
        assertEquals("0.0.73", SemanticVersion.parse("v0.0.73").toString())
    }
}
