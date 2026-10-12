package com.kevin.hrtracker.ui.shared

import org.junit.Assert.*
import org.junit.Test

class ChartViewportTest {

    private val eps = 1e-4f

    @Test
    fun `zoom keeps anchor point fixed`() {
        val v = ChartViewport(0.2f, 0.8f)
        val anchor = 0.25f
        val before = v.start + anchor * v.span
        val z = v.zoomBy(2f, anchor, 0.01f)
        assertEquals(0.3f, z.span, eps)
        assertEquals(before, z.start + anchor * z.span, eps)
    }

    @Test
    fun `zoom clamps at left and right edge`() {
        val l = ChartViewport.Full.zoomBy(4f, 0f, 0.01f)
        assertEquals(0f, l.start, eps); assertEquals(0.25f, l.end, eps)
        val r = ChartViewport.Full.zoomBy(4f, 1f, 0.01f)
        assertEquals(0.75f, r.start, eps); assertEquals(1f, r.end, eps)
    }

    @Test
    fun `zoom respects minSpan`() {
        val z = ChartViewport.Full.zoomBy(1000f, 0.5f, 0.1f)
        assertEquals(0.1f, z.span, eps)
        assertEquals(0.45f, z.start, eps)
    }

    @Test
    fun `zoom out returns to full`() {
        val z = ChartViewport(0.4f, 0.6f).zoomBy(0.01f, 0.5f, 0.05f)
        assertTrue(z.isFull)
        assertEquals(ChartViewport(0f, 1f), z)
    }

    @Test
    fun `zoom with invalid factor is no-op`() {
        val v = ChartViewport(0.1f, 0.5f)
        assertEquals(v, v.zoomBy(0f, 0.5f, 0.05f))
    }

    @Test
    fun `pan moves earlier for positive delta and clamps`() {
        val v = ChartViewport(0.4f, 0.6f)
        val p = v.panBy(0.5f) // halbe sichtbare Breite nach rechts
        assertEquals(0.3f, p.start, eps); assertEquals(0.5f, p.end, eps)
        val left = v.panBy(100f)
        assertEquals(0f, left.start, eps); assertEquals(0.2f, left.end, eps)
        val right = v.panBy(-100f)
        assertEquals(0.8f, right.start, eps); assertEquals(1f, right.end, eps)
        assertTrue(ChartViewport.Full.panBy(0.3f).isFull)
    }

    @Test
    fun `mapX maps into visible window`() {
        val v = ChartViewport(0.25f, 0.75f)
        assertEquals(0f, v.mapX(0.25f), eps)
        assertEquals(1f, v.mapX(0.75f), eps)
        assertEquals(0.5f, v.mapX(0.5f), eps)
        assertEquals(-0.5f, v.mapX(0f), eps)
        assertEquals(1.5f, v.mapX(1f), eps)
    }

    @Test
    fun `visibleIndexRange full empty single`() {
        assertEquals(0..99, ChartViewport.Full.visibleIndexRange(100))
        assertTrue(ChartViewport.Full.visibleIndexRange(0).isEmpty())
        assertTrue(ChartViewport.Full.visibleIndexRange(-3).isEmpty())
        assertEquals(0..0, ChartViewport(0.2f, 0.4f).visibleIndexRange(1))
    }

    @Test
    fun `visibleIndexRange includes neighbours`() {
        // size 11 -> Fraktionen 0, 0.1 ... 1.0; [0.3,0.6] -> Indizes 3..6, plus Nachbarn 2..7
        assertEquals(2..7, ChartViewport(0.3f, 0.6f).visibleIndexRange(11))
        // zwischen Samples: [0.32,0.38] -> nur Index 3 und 4 als Nachbarn
        assertEquals(3..4, ChartViewport(0.32f, 0.38f).visibleIndexRange(11))
        // Ränder werden geklemmt
        assertEquals(0..2, ChartViewport(0f, 0.1f).visibleIndexRange(11))
        assertEquals(8..10, ChartViewport(0.9f, 1f).visibleIndexRange(11))
    }

    @Test
    fun `zoom in and out round trip returns full`() {
        val v = ChartViewport.Full.zoomBy(10f, 0.5f, 0.01f)
        val z = v.zoomBy(0.1f, 0.5f, 0.01f)
        assertTrue(z.isFull)
        assertEquals(0f, z.start, eps)
        assertEquals(1f, z.end, eps)
    }

    @Test
    fun `zoom with anchor 0 keeps left edge`() {
        val v = ChartViewport(0.2f, 0.8f)
        val z = v.zoomBy(2f, 0f, 0.01f)
        assertEquals(v.start, z.start, eps)
    }

    @Test
    fun `zoom with anchor 1 keeps right edge`() {
        val v = ChartViewport(0.2f, 0.8f)
        val z = v.zoomBy(2f, 1f, 0.01f)
        assertEquals(v.end, z.end, eps)
    }

    @Test
    fun `minSpan larger than 1 clamps to 1`() {
        val z = ChartViewport.Full.zoomBy(1000f, 0.5f, 1.5f)
        assertEquals(1f, z.span, eps)
    }

    @Test
    fun `minSpan negative or zero becomes TINY`() {
        val z1 = ChartViewport.Full.zoomBy(1000f, 0.5f, 0f)
        assertTrue(z1.span > 0f)
        val z2 = ChartViewport.Full.zoomBy(1000f, 0.5f, -0.5f)
        assertTrue(z2.span > 0f)
    }

    @Test
    fun `panBy huge positive delta clamps to left edge`() {
        val v = ChartViewport(0.4f, 0.6f)
        val p = v.panBy(1_000_000f)
        assertEquals(0f, p.start, eps)
        assertEquals(v.span, p.end, eps)
    }

    @Test
    fun `panBy huge negative delta clamps to right edge`() {
        val v = ChartViewport(0.4f, 0.6f)
        val p = v.panBy(-1_000_000f)
        assertEquals(1f - v.span, p.start, eps)
        assertEquals(1f, p.end, eps)
    }

    @Test
    fun `mapX at start of viewport is 0`() {
        val v = ChartViewport(0.3f, 0.7f)
        assertEquals(0f, v.mapX(0.3f), eps)
    }

    @Test
    fun `mapX at end of viewport is 1`() {
        val v = ChartViewport(0.3f, 0.7f)
        assertEquals(1f, v.mapX(0.7f), eps)
    }

    @Test
    fun `visibleIndexRange with 3601 samples zoomed to middle`() {
        // Zoom into the middle portion of 3601 samples (fractions from 0 to 1)
        val v = ChartViewport(0.4f, 0.6f)
        val range = v.visibleIndexRange(3601)
        // At start fraction 0.4 with 3600 steps between 3601 samples
        // first sample is at index ~1440, with neighbor at 1439
        // at end fraction 0.6, last sample is at index ~2160, with neighbor at 2161
        assertTrue(range.first < range.last)
        assertTrue(range.first >= 0)
        assertTrue(range.last < 3601)
        // Verify samples at start and end fractions are included
        val startFraction = range.first.toFloat() / 3600f
        val endFraction = range.last.toFloat() / 3600f
        assertTrue(startFraction <= 0.4f + 0.01f)  // with some tolerance
        assertTrue(endFraction >= 0.6f - 0.01f)
    }

    @Test
    fun `zoom by NaN is no-op`() {
        val v = ChartViewport(0.1f, 0.5f)
        assertEquals(v, v.zoomBy(Float.NaN, 0.5f, 0.05f))
    }

    @Test
    fun `zoom by Infinity is no-op`() {
        val v = ChartViewport(0.1f, 0.5f)
        assertEquals(v, v.zoomBy(Float.POSITIVE_INFINITY, 0.5f, 0.05f))
    }

    @Test
    fun `zoom and pan snap to exact edges`() {
        val z = ChartViewport(0.5f, 1f).zoomBy(2f, 1f, 0.01f)
        assertEquals(1f, z.end, 0f)
        val p = ChartViewport(0.2f, 0.6f).panBy(-100f)
        assertEquals(1f, p.end, 0f)
        assertEquals(0.6f, p.start, eps)
        val q = ChartViewport(0.3f, 0.7f).panBy(100f)
        assertEquals(0f, q.start, 0f)
    }

    @Test
    fun `pan by huge positive value clamps correctly`() {
        val v = ChartViewport(0.2f, 0.8f)
        val p = v.panBy(1e9f)
        assertEquals(0f, p.start, eps)
        assertEquals(0.6f, p.end, eps)
    }

    @Test
    fun `visibleIndexRange with single sample and zoomed viewport`() {
        val v = ChartViewport(0.3f, 0.7f)
        assertEquals(0..0, v.visibleIndexRange(1))
    }

    @Test
    fun `zoom preserves anchor point through multiple zooms`() {
        var v = ChartViewport.Full
        val anchor = 0.5f
        val anchorValue1 = v.start + anchor * v.span

        v = v.zoomBy(2f, anchor, 0.01f)
        val anchorValue2 = v.start + anchor * v.span

        v = v.zoomBy(2f, anchor, 0.01f)
        val anchorValue3 = v.start + anchor * v.span

        assertEquals(anchorValue1, anchorValue2, eps)
        assertEquals(anchorValue1, anchorValue3, eps)
    }

    @Test
    fun `visibleIndexRange at exact sample boundaries`() {
        // 11 samples at fractions 0, 0.1, 0.2, ..., 1.0
        val v = ChartViewport(0f, 0.2f)
        val range = v.visibleIndexRange(11)
        // Should include samples 0 and 2, plus neighbors -1 (clamped to 0) and 3
        assertTrue(range.contains(0))
        assertTrue(range.contains(2))
    }

    @Test
    fun `zoom with minSpan = span keeps viewport unchanged`() {
        val v = ChartViewport(0.3f, 0.6f)
        val span = v.span
        val z = v.zoomBy(2f, 0.5f, span)
        // Should not zoom because minSpan equals current span
        assertEquals(v, z)
    }

    @Test
    fun `nearestIndex maps fraction linearly and rounds`() {
        assertEquals(10, nearestIndex(0f, 10..20))
        assertEquals(20, nearestIndex(1f, 10..20))
        assertEquals(15, nearestIndex(0.5f, 10..20))
        assertEquals(13, nearestIndex(0.26f, 10..20))
        assertEquals(50, nearestIndex(0.5f, 0..100))
    }

    @Test
    fun `nearestIndex clamps out-of-range fractions`() {
        assertEquals(10, nearestIndex(-0.5f, 10..20))
        assertEquals(20, nearestIndex(1.7f, 10..20))
    }

    @Test
    fun `nearestIndex handles empty and single ranges`() {
        assertNull(nearestIndex(0.5f, IntRange.EMPTY))
        assertEquals(7, nearestIndex(0.9f, 7..7))
        assertNull(nearestIndex(Float.NaN, 0..5))
    }
}
