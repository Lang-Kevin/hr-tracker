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

    // ---- LiveWindow ----

    @Test
    fun `live visibleRange following`() {
        assertEquals(380..499, LiveWindow().visibleRange(500))
        assertTrue(LiveWindow().isFollowing)
    }

    @Test
    fun `live visibleRange with offset`() {
        assertEquals(330..449, LiveWindow(120, 50).visibleRange(500))
        assertFalse(LiveWindow(120, 50).isFollowing)
    }

    @Test
    fun `live visibleRange total smaller than window and empty`() {
        assertEquals(0..49, LiveWindow().visibleRange(50))
        assertTrue(LiveWindow().visibleRange(0).isEmpty())
        assertEquals(0..119, LiveWindow(120, 380).visibleRange(500))
        assertEquals(0..0, LiveWindow(120, 9999).visibleRange(500))
    }

    @Test
    fun `live zoom in keeps anchor sample and clamps`() {
        val w = LiveWindow(120, 100).zoomBy(2f, 0.5f, 1000)
        assertEquals(60, w.windowSeconds)
        // Fenster vorher: 780..899, Mitte 840; nachher 810..869 -> Offset 130
        val r = w.visibleRange(1000)
        assertEquals(810..869, r)
    }

    @Test
    fun `live zoom clamps to MIN and total`() {
        assertEquals(LiveWindow.MIN, LiveWindow(120, 0).zoomBy(100f, 0.5f, 1000).windowSeconds)
        assertEquals(300, LiveWindow(120, 0).zoomBy(0.01f, 0.5f, 300).windowSeconds)
        // total < MIN: Fenster bleibt MIN, Offset 0
        val small = LiveWindow(120, 0).zoomBy(10f, 0.5f, 20)
        assertEquals(LiveWindow.MIN, small.windowSeconds)
        assertEquals(0, small.offsetFromEnd)
    }

    @Test
    fun `live zoom out while following stays clamped at zero offset`() {
        val w = LiveWindow(60, 0).zoomBy(0.5f, 0.5f, 1000)
        assertEquals(120, w.windowSeconds)
        assertTrue(w.offsetFromEnd >= 0)
        assertEquals(1000 - 1 - w.offsetFromEnd, w.visibleRange(1000).last)
    }

    @Test
    fun `live zoom with anchor at right edge keeps following`() {
        val w = LiveWindow(120, 0).zoomBy(2f, 1f, 1000)
        assertEquals(60, w.windowSeconds)
        assertTrue(w.isFollowing)
    }

    @Test
    fun `live pan clamps`() {
        val w = LiveWindow(120, 0)
        assertEquals(30, w.panBy(30f, 500).offsetFromEnd)
        assertEquals(0, w.panBy(-30f, 500).offsetFromEnd)
        assertEquals(380, w.panBy(10_000f, 500).offsetFromEnd)
        assertEquals(0, w.panBy(10f, 100).offsetFromEnd) // total < window
        assertEquals(0, LiveWindow(120, 0).panBy(10f, 0).offsetFromEnd)
    }

    @Test
    fun `live onSampleAdded`() {
        assertEquals(LiveWindow(120, 0), LiveWindow(120, 0).onSampleAdded())
        assertEquals(LiveWindow(120, 6), LiveWindow(120, 5).onSampleAdded())
    }

    // ---- Edge cases: ChartViewport ----

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
    fun `zoom by Infinity zooms to minSpan`() {
        // NOTE: This exposes a potential bug - Infinity should be treated as invalid
        // Currently, Float.POSITIVE_INFINITY > 0 so it passes the validation,
        // and (span / Infinity) ≈ 0, resulting in minSpan being applied
        val v = ChartViewport(0.1f, 0.5f)
        val z = v.zoomBy(Float.POSITIVE_INFINITY, 0.5f, 0.05f)
        assertEquals(0.05f, z.span, eps)
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

    // ---- Edge cases: LiveWindow ----

    @Test
    fun `live with total=0 returns empty range`() {
        assertTrue(LiveWindow(120, 0).visibleRange(0).isEmpty())
        assertTrue(LiveWindow(120, 50).visibleRange(0).isEmpty())
    }

    @Test
    fun `live with total=1 returns single sample`() {
        assertEquals(0..0, LiveWindow(120, 0).visibleRange(1))
        assertEquals(0..0, LiveWindow(120, 50).visibleRange(1))
    }

    @Test
    fun `live with offset larger than total returns single latest`() {
        val w = LiveWindow(120, 9999)
        val range = w.visibleRange(500)
        assertEquals(0..0, range)
    }

    @Test
    fun `live zoom out beyond total while scrolled back`() {
        val w = LiveWindow(100, 200).zoomBy(0.1f, 0.5f, 300)
        // window expands from 100 to 1000, but clamped to total (300)
        assertEquals(300, w.windowSeconds)
        // offset should be clamped appropriately
        assertTrue(w.offsetFromEnd >= 0)
    }

    @Test
    fun `live panBy negative past 0 clamps to 0 and becomes following`() {
        val w = LiveWindow(120, 100)
        val p = w.panBy(-200f, 500)
        assertEquals(0, p.offsetFromEnd)
        assertTrue(p.isFollowing)
    }

    @Test
    fun `live onSampleAdded while following stays following`() {
        val w = LiveWindow(120, 0)
        assertTrue(w.isFollowing)
        val after = w.onSampleAdded()
        assertTrue(after.isFollowing)
        assertEquals(0, after.offsetFromEnd)
    }

    @Test
    fun `live scroll back then add samples maintains offset`() {
        val initial = LiveWindow(120, 0)
        val range1 = initial.visibleRange(500)
        // range = 380..499

        // Scroll back by 100 seconds (increases offsetFromEnd)
        val scrolledBack = initial.panBy(100f, 500)
        // panBy moves forward in the past, so offset becomes 100
        assertEquals(100, scrolledBack.offsetFromEnd)
        val range2 = scrolledBack.visibleRange(500)
        // end = 500 - 1 - 100 = 399, start = 280
        assertEquals(280..399, range2)

        // Add 10 samples while scrolled back (offset increases via onSampleAdded)
        var w = scrolledBack
        repeat(10) { w = w.onSampleAdded() }
        assertEquals(110, w.offsetFromEnd)
        val range3 = w.visibleRange(510)
        // end = 510 - 1 - 110 = 399, start = 280
        assertEquals(280..399, range3)
    }

    @Test
    fun `live zoom with total smaller than window`() {
        val w = LiveWindow(120, 0).zoomBy(2f, 0.5f, 80)
        assertEquals(60, w.windowSeconds)
        // With newStart=10, newEnd=69, offset becomes 10
        val range = w.visibleRange(80)
        assertEquals(10..69, range)
    }

    @Test
    fun `live with very large total and small offset`() {
        val w = LiveWindow(120, 1)
        val range = w.visibleRange(1_000_000)
        // end = 1_000_000 - 1 - 1 = 999_998
        // start = 999_998 - 120 + 1 = 999_879
        assertEquals(999_879, range.first)
        assertEquals(999_998, range.last)
    }

    @Test
    fun `live panBy zero is no-op`() {
        val w = LiveWindow(120, 50)
        assertEquals(w, w.panBy(0f, 500))
    }

    @Test
    fun `live panBy negative with small total`() {
        val w = LiveWindow(120, 10)
        val p = w.panBy(-50f, 80)
        assertTrue(p.offsetFromEnd >= 0)
        assertEquals(0, p.offsetFromEnd)
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
    fun `live zoom preserves following state with anchor 1f`() {
        val w = LiveWindow(120, 0)
        val z = w.zoomBy(2f, 1f, 1000)
        assertTrue(z.isFollowing)
    }

    @Test
    fun `live offset exactly equals total minus 1`() {
        val w = LiveWindow(120, 499)
        val range = w.visibleRange(500)
        assertEquals(0..0, range)
    }

    @Test
    fun `zoom with minSpan = span keeps viewport unchanged`() {
        val v = ChartViewport(0.3f, 0.6f)
        val span = v.span
        val z = v.zoomBy(2f, 0.5f, span)
        // Should not zoom because minSpan equals current span
        assertEquals(v, z)
    }
}
