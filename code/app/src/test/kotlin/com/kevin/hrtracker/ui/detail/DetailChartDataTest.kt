package com.kevin.hrtracker.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailChartDataTest {
    private val bpm = listOf(100, 110, 120, 130)
    private val fr = listOf(0f, 0.1f, 0.6f, 0.7f)

    @Test fun `no gaps maps fractions to seconds`() {
        val s = detailChartSeries(bpm, fr, emptyList(), totalSeconds = 1000f)
        assertEquals(listOf(0f, 100f, 600f, 700f), s.xs)
        assertEquals(listOf(100f, 110f, 120f, 130f), s.ys)
    }

    @Test fun `gap inserts one NaN between its bounding samples`() {
        val s = detailChartSeries(bpm, fr, listOf(0.1f..0.6f), totalSeconds = 1000f)
        assertEquals(5, s.ys.size)
        assertEquals(s.ys.size, s.xs!!.size)
        assertTrue(s.ys[2].isNaN())
        assertEquals(350f, s.xs!![2], 0.001f)
        assertEquals(s.xs!!.sorted(), s.xs)
    }

    @Test fun `empty and mismatched inputs`() {
        assertEquals(0, detailChartSeries(emptyList(), emptyList(), emptyList(), 0f).size)
        assertEquals(2, detailChartSeries(bpm, fr.take(2), emptyList(), 10f).size)
    }
}
