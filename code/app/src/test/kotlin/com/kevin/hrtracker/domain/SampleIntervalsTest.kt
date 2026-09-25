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
}
