package com.kevin.hrtracker.ui.pip

import com.kevin.hrtracker.domain.WidgetVariant
import com.kevin.hrtracker.domain.ZoneBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PipUiStateTest {

    private val zones = listOf(
        ZoneBounds(1, 100, 119),
        ZoneBounds(2, 120, 139),
        ZoneBounds(3, 140, 159),
        ZoneBounds(4, 160, 179),
        ZoneBounds(5, 180, 200)
    )

    @Test
    fun `bpm wird in zone uebersetzt und dauer formatiert`() {
        val state = buildPipUiState(
            bpm = 142,
            zones = zones,
            startedAtMs = 1_000_000L,
            nowMs = 1_000_000L + 24 * 60_000L + 13_000L,
            paused = false
        )
        assertEquals(142, state.bpm)
        assertEquals(3, state.zone)
        assertEquals("00:24:13", state.elapsedText)
        assertEquals(false, state.paused)
    }

    @Test
    fun `ohne bpm keine zone`() {
        val state = buildPipUiState(null, zones, 1_000_000L, 1_000_000L, false)
        assertNull(state.bpm)
        assertNull(state.zone)
    }

    @Test
    fun `ohne zonen keine zone trotz bpm`() {
        val state = buildPipUiState(142, emptyList(), 1_000_000L, 1_000_000L, false)
        assertEquals(142, state.bpm)
        assertNull(state.zone)
    }

    @Test
    fun `ohne aktive session dauer null`() {
        val state = buildPipUiState(142, zones, null, 9_999_999L, false)
        assertEquals("00:00:00", state.elapsedText)
    }

    @Test
    fun `negative dauer wird auf null geklemmt`() {
        val state = buildPipUiState(142, zones, 2_000_000L, 1_000_000L, false)
        assertEquals("00:00:00", state.elapsedText)
    }

    @Test
    fun `paused wird durchgereicht`() {
        val state = buildPipUiState(142, zones, 1_000_000L, 1_060_000L, true)
        assertEquals(true, state.paused)
        assertEquals("00:01:00", state.elapsedText)
    }

    @Test
    fun `variante wird durchgereicht`() {
        val state = buildPipUiState(
            bpm = 142,
            zones = zones,
            startedAtMs = 1_000_000L,
            nowMs = 1_060_000L,
            paused = false,
            variant = WidgetVariant.ZONE
        )
        assertEquals(WidgetVariant.ZONE, state.variant)
    }

    @Test
    fun `ohne angabe ist die variante standard`() {
        val state = buildPipUiState(142, zones, 1_000_000L, 1_060_000L, false)
        assertEquals(WidgetVariant.STANDARD, state.variant)
    }
}
