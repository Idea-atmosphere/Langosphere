package com.example.zonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The percent / ETA shown while the model loads on the first playback. */
class LoadEstimatorTest {

    @Test
    fun `nothing before start`() {
        assertNull(LoadEstimator().snapshot(1000L))
        assertFalse(LoadEstimator().running)
    }

    @Test
    fun `history gives an ETA before any bytes are counted`() {
        val e = LoadEstimator(expectedTotalMs = 90_000L)
        e.start(10_000L)
        val p = e.snapshot(15_000L)!!
        assertNull(p.fraction)
        assertEquals(5_000L, p.elapsedMs)
        assertEquals(85_000L, p.etaMs)
        assertTrue(p.etaFromHistory)
        assertFalse(p.finalizing)
    }

    @Test
    fun `no history and no bytes means no ETA`() {
        val e = LoadEstimator()
        e.start(0L)
        val p = e.snapshot(3_000L)!!
        assertNull(p.etaMs)
        assertNull(p.fraction)
        assertEquals(3_000L, p.elapsedMs)
    }

    @Test
    fun `rate based ETA from the average since the first sample`() {
        val e = LoadEstimator(expectedTotalMs = 999_999L) // history must NOT win once bytes flow
        e.start(0L)
        e.sample(loaded = 0L, total = 1_000L, phase = "backbone", nowMs = 1_000L)
        e.sample(loaded = 200L, total = 1_000L, phase = "backbone", nowMs = 3_000L) // 100 B/s
        val p = e.snapshot(3_000L)!!
        assertEquals(0.2f, p.fraction!!, 0.001f)
        assertEquals(8_000L, p.etaMs) // 800 B left at 100 B/s
        assertFalse(p.etaFromHistory)
        // Time keeps passing without a new sample: the ETA shrinks accordingly.
        assertEquals(6_000L, e.snapshot(5_000L)!!.etaMs)
        // ...but never below zero.
        assertEquals(0L, e.snapshot(60_000L)!!.etaMs)
    }

    @Test
    fun `too short a window falls back to history`() {
        val e = LoadEstimator(expectedTotalMs = 20_000L)
        e.sample(0L, 1_000L, "backbone", 0L)
        e.sample(100L, 1_000L, "backbone", 500L) // only 0.5 s of history
        val p = e.snapshot(500L)!!
        assertTrue(p.etaFromHistory)
        assertEquals(19_500L, p.etaMs)
    }

    @Test
    fun `all bytes in but not ready is finalizing`() {
        val e = LoadEstimator()
        e.sample(0L, 500L, "backbone", 0L)
        e.sample(500L, 500L, "spk", 4_000L)
        val p = e.snapshot(4_500L)!!
        assertTrue(p.finalizing)
        assertEquals(1f, p.fraction!!, 0.0f)
        assertNull(p.etaMs)
        assertEquals("spk", p.phase)
    }

    @Test
    fun `a restart from zero re-anchors the rate`() {
        val e = LoadEstimator()
        e.sample(0L, 1_000L, "backbone", 0L)
        e.sample(900L, 1_000L, "backbone", 9_000L)
        e.sample(0L, 2_000L, "zac", 10_000L)   // a new count from zero
        e.sample(400L, 2_000L, "zac", 12_000L) // 200 B/s
        val p = e.snapshot(12_000L)!!
        assertEquals(0.2f, p.fraction!!, 0.001f)
        assertEquals(8_000L, p.etaMs)
    }

    @Test
    fun `elapsed and reset`() {
        val e = LoadEstimator()
        e.start(1_000L)
        assertEquals(41_000L, e.elapsed(42_000L))
        assertTrue(e.running)
        e.reset()
        assertFalse(e.running)
        assertNull(e.snapshot(50_000L))
        assertEquals(0L, e.elapsed(50_000L))
    }

    @Test
    fun `formatEta rounds to whole seconds`() {
        assertEquals("0:00", formatEta(0L))
        assertEquals("0:07", formatEta(7_400L))
        assertEquals("1:40", formatEta(100_000L))
        assertEquals("12:05", formatEta(725_000L))
        assertNotNull(formatEta(Long.MAX_VALUE / 4))
    }
}
