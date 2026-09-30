package com.kevin.hrtracker.domain

import com.kevin.hrtracker.data.entity.HrSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HrvCalculatorTest {

    private fun s(t: Long, rr: String?) = HrSample(sessionId = 1, timestampMs = t, bpm = 60, rrIntervalsMs = rr)

    @Test
    fun `rmssd over consecutive beats`() {
        // Diffs: +20, -20, +20 → RMSSD 20
        val samples = listOf(s(0, "1000"), s(1_000, "1020"), s(2_000, "1000"), s(3_000, "1020"))
        assertEquals(20, HrvCalculator.rmssd(samples))
    }

    @Test
    fun `multiple rr values per sample are chained`() {
        val samples = listOf(s(0, "1000,1020"), s(1_000, "1000,1020"))
        assertEquals(20, HrvCalculator.rmssd(samples))
    }

    @Test
    fun `no diff across a pause gap`() {
        // Vor der Pause 800 ms, danach 1200 ms: ohne Reset gäbe es einen 400-ms-Sprung
        val samples = listOf(
            s(0, "800"), s(1_000, "810"),
            s(120_000, "1200"), s(121_000, "1210")
        )
        assertEquals(10, HrvCalculator.rmssd(samples))
    }

    @Test
    fun `out of range rr values are ignored`() {
        val samples = listOf(s(0, "1000"), s(1_000, "5000"), s(2_000, "1010"))
        assertEquals(10, HrvCalculator.rmssd(samples))
    }

    @Test
    fun `null without rr data`() {
        assertNull(HrvCalculator.rmssd(listOf(s(0, null), s(1_000, null))))
        assertNull(HrvCalculator.rmssd(listOf(s(0, "1000"))))
    }
}
