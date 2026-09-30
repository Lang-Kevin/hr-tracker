package com.kevin.hrtracker.domain

import com.kevin.hrtracker.data.entity.HrSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrimpCalculatorTest {

    /** Ein Sample pro Sekunde, [minutes] Minuten lang → minutes*60 Intervalle à 1 s. */
    private fun steady(bpm: Int, minutes: Int, startMs: Long = 0L): List<HrSample> =
        (0..minutes * 60).map { i ->
            HrSample(sessionId = 1, timestampMs = startMs + i * 1_000L, bpm = bpm)
        }

    @Test
    fun `banister with resting hr`() {
        // r = (150 - 60) / (190 - 60) = 0.6923; 60 × r × e^(1.92 r) = 156.9
        assertEquals(156, TrimpCalculator.compute(steady(150, 60), maxHr = 190, restingHr = 60))
    }

    @Test
    fun `pause gap is not counted`() {
        // 2 × 30 min mit 10 min Pause dazwischen = gleicher TRIMP wie 60 min am Stück
        val first = steady(150, 30)
        val second = steady(150, 30, startMs = first.last().timestampMs + 10 * 60_000L)
        assertEquals(156, TrimpCalculator.compute(first + second, maxHr = 190, restingHr = 60))
    }

    @Test
    fun `fallback without resting hr uses percent hrmax`() {
        // 30 × 150/190 × 100 = 2368.4
        assertEquals(2368, TrimpCalculator.compute(steady(150, 30), maxHr = 190, restingHr = null))
    }

    @Test
    fun `bpm at or below resting hr yields zero`() {
        assertEquals(0, TrimpCalculator.compute(steady(55, 30), maxHr = 190, restingHr = 60))
    }

    @Test
    fun `null when fewer than two samples`() {
        assertNull(TrimpCalculator.compute(steady(150, 0), maxHr = 190, restingHr = 60))
    }
}
