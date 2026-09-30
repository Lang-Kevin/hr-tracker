package com.kevin.hrtracker.ui.shared

import org.junit.Assert.*
import org.junit.Test

class ChartTimeTest {

    private val f = floatArrayOf(0f, 0.1f, 0.2f, 0.6f, 0.7f, 1f) // Lücke zwischen 0.2 und 0.6

    @Test fun `visible range empty for empty array`() {
        assertTrue(visibleRangeByFractions(FloatArray(0), 0f, 1f).isEmpty())
    }

    @Test fun `visible range full`() {
        assertEquals(0..5, visibleRangeByFractions(f, 0f, 1f))
    }

    @Test fun `visible range includes one neighbour each side`() {
        // 0.3..0.5 enthält kein Sample; Nachbarn 0.2 (idx 2) und 0.6 (idx 3)
        assertEquals(2..3, visibleRangeByFractions(f, 0.3f, 0.5f))
        // 0.6..0.7 enthält idx 3,4 -> 2..5
        assertEquals(2..5, visibleRangeByFractions(f, 0.6f, 0.7f))
    }

    @Test fun `visible range at edges is clamped`() {
        assertEquals(0..1, visibleRangeByFractions(f, 0f, 0.05f))
        assertEquals(4..5, visibleRangeByFractions(f, 0.9f, 1f))
    }

    @Test fun `visible range single sample`() {
        assertEquals(0..0, visibleRangeByFractions(floatArrayOf(0.5f), 0.2f, 0.8f))
    }

    @Test fun `nearest index basics`() {
        assertNull(nearestIndexByFraction(FloatArray(0), 0.5f))
        assertNull(nearestIndexByFraction(f, Float.NaN))
        assertEquals(0, nearestIndexByFraction(f, -1f))
        assertEquals(5, nearestIndexByFraction(f, 2f))
        assertEquals(1, nearestIndexByFraction(f, 0.1f))
        assertEquals(1, nearestIndexByFraction(f, 0.12f))
    }

    @Test fun `nearest index across gap picks closer side`() {
        assertEquals(2, nearestIndexByFraction(f, 0.35f))
        assertEquals(3, nearestIndexByFraction(f, 0.45f))
    }

    @Test fun `nearest tie picks earlier`() {
        assertEquals(0, nearestIndexByFraction(floatArrayOf(0f, 1f), 0.5f))
    }

    @Test fun `seconds variants`() {
        val s = listOf(0f, 1f, 2f, 10f, 11f)
        assertEquals(2..4, visibleRangeBySeconds(s, 3f, 10f))
        assertEquals(2..4, visibleRangeBySeconds(s, 5f, 10.5f))
        assertEquals(2, nearestIndexBySeconds(s, 3f))
        assertEquals(3, nearestIndexBySeconds(s, 8f))
        assertTrue(visibleRangeBySeconds(emptyList(), 0f, 5f).isEmpty())
    }

    @Test fun `live total seconds`() {
        assertEquals(0, liveTotalSeconds(emptyList()))
        assertEquals(1, liveTotalSeconds(listOf(0f)))
        assertEquals(4, liveTotalSeconds(listOf(0f, 2.2f)))
        assertEquals(11, liveTotalSeconds(listOf(0f, 10f)))
    }

    @Test fun `downsample means`() {
        val p = FloatArray(10) { it.toFloat() }
        val v = FloatArray(10) { it * 2f }
        val (dp, dv) = downsampleMeans(p, v, 5)
        assertEquals(5, dp.size)
        assertEquals(0.5f, dp[0], 1e-4f)
        assertEquals(1f, dv[0], 1e-4f)
        assertEquals(8.5f, dp[4], 1e-4f)
        val (sp, _) = downsampleMeans(p, v, 20)
        assertEquals(10, sp.size)
    }
}
