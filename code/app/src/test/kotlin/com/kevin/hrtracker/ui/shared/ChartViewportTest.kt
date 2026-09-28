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
}
