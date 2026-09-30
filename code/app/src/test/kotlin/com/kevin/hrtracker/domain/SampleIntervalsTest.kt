package com.kevin.hrtracker.domain

import com.kevin.hrtracker.data.entity.HrSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SampleIntervalsTest {

    private fun s(t: Long, bpm: Int) = HrSample(sessionId = 1, timestampMs = t, bpm = bpm)

    @Test
    fun `active time skips gaps above threshold`() {
        // 0→1 s, 1→2 s zählen; 2 s → 62 s ist Pause; 62→63 s zählt
        val samples = listOf(s(0, 100), s(1_000, 100), s(2_000, 100), s(62_000, 100), s(63_000, 100))
        assertEquals(3_000L, SampleIntervals.activeMs(samples))
    }

    @Test
    fun `gap exactly at threshold still counts`() {
        val samples = listOf(s(0, 100), s(SampleIntervals.MAX_GAP_MS, 100))
        assertEquals(SampleIntervals.MAX_GAP_MS, SampleIntervals.activeMs(samples))
    }

    @Test
    fun `unsorted input is sorted first`() {
        val samples = listOf(s(2_000, 100), s(0, 100), s(1_000, 100))
        assertEquals(2_000L, SampleIntervals.activeMs(samples))
    }

    @Test
    fun `avg bpm is weighted by time not sample count`() {
        // 4 s @100 (1 Intervall à 4 s), dann 4 Intervalle à 1 s @160 → (400 + 640) / 8 = 130
        val samples = listOf(
            s(0, 100), s(4_000, 160), s(5_000, 160), s(6_000, 160), s(7_000, 160), s(8_000, 999)
        )
        assertEquals(130, SampleIntervals.avgBpm(samples))
    }

    @Test
    fun `avg bpm null without valid interval`() {
        assertNull(SampleIntervals.avgBpm(listOf(s(0, 100))))
        assertNull(SampleIntervals.avgBpm(listOf(s(0, 100), s(60_000, 100))))
    }

    @Test
    fun `activeToWallMs empty list returns null`() {
        assertNull(SampleIntervals.activeToWallMs(emptyList(), 1000))
    }

    @Test
    fun `activeToWallMs contiguous samples reaches target`() {
        // 0 ms, +1000ms → 1000 ms, +1000ms → 2000 ms, +1000ms → 3000 ms
        // activeToWallMs(..., 2500) should find that 2500 ms is between sample at 2000 and 3000,
        // return timestamp of sample at 3000
        val samples = listOf(s(0, 100), s(1000, 100), s(2000, 100), s(3000, 100), s(4000, 100))
        assertEquals(3000L, SampleIntervals.activeToWallMs(samples, 2500))
    }

    @Test
    fun `activeToWallMs with gap skips pause interval`() {
        // 0→1 s (+1000 ms), 1→2 s (+1000 ms), then gap to 62 s (skipped), 62→63 s (+1000 ms)
        // activeToWallMs(..., 2500) reaches 2000 ms after first two intervals, then 3000 ms after 62→63 interval
        val samples = listOf(
            s(0, 100), s(1_000, 100), s(2_000, 100), s(62_000, 100), s(63_000, 100)
        )
        assertEquals(63_000L, SampleIntervals.activeToWallMs(samples, 2500))
    }

    @Test
    fun `activeToWallMs never reached returns last timestamp`() {
        // Only 2 s of active time total
        val samples = listOf(s(0, 100), s(1_000, 100), s(2_000, 100))
        assertEquals(2_000L, SampleIntervals.activeToWallMs(samples, 5_000))
    }

    @Test
    fun `activeToWallMs zero or negative active time returns first timestamp`() {
        val samples = listOf(s(0, 100), s(1_000, 100))
        assertEquals(0L, SampleIntervals.activeToWallMs(samples, 0))
        assertEquals(0L, SampleIntervals.activeToWallMs(samples, -100))
    }
}
