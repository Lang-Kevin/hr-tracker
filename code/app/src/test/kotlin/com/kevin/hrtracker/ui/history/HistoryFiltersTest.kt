package com.kevin.hrtracker.ui.history

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryFiltersTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val la = ZoneId.of("America/Los_Angeles")
    private val d10 = LocalDate.of(2026, 10, 10)

    @Test fun `null range keeps everything`() = assertTrue(inDateRange(0L, null, berlin))

    @Test fun `local midnight in Berlin belongs to the local day`() {
        val t = Instant.parse("2026-10-09T22:30:00Z").toEpochMilli()      // 10.10. 00:30 CEST
        assertTrue(inDateRange(t, d10 to d10, berlin))
        assertFalse(inDateRange(t, d10.minusDays(1) to d10.minusDays(1), berlin))
    }

    @Test fun `late evening in Los Angeles stays on the local day`() {
        val t = Instant.parse("2026-10-11T06:30:00Z").toEpochMilli()      // 10.10. 23:30 PDT
        assertTrue(inDateRange(t, d10 to d10, la))
        assertFalse(inDateRange(t, d10.plusDays(1) to d10.plusDays(1), la))
    }

    @Test fun `end day is inclusive`() {
        val t = LocalDateTime.of(2026, 10, 12, 23, 59).atZone(berlin).toInstant().toEpochMilli()
        assertTrue(inDateRange(t, d10 to LocalDate.of(2026, 10, 12), berlin))
    }

    @Test fun `groups by ISO week, newest first, skipping empty weeks`() {
        val z = ZoneId.of("UTC")
        fun at(y: Int, m: Int, d: Int, h: Int) = LocalDateTime.of(y, m, d, h, 0).atZone(z).toInstant().toEpochMilli()
        val items = listOf(at(2026, 10, 12, 8), at(2026, 10, 11, 20), at(2026, 10, 5, 7), at(2026, 9, 27, 9))
        val g = groupByWeek(items, { it }, today = LocalDate.of(2026, 10, 12), zone = z)
        assertEquals(listOf(0, 1, 3), g.map { it.weeksAgo })
        assertEquals(LocalDate.of(2026, 10, 5), g[1].weekStart)
        assertEquals(listOf(items[1], items[2]), g[1].items)              // input order kept
        assertEquals(LocalDate.of(2026, 9, 21), g[2].weekStart)
    }
}
