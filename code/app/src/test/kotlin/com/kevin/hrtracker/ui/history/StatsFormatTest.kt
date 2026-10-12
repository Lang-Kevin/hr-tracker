package com.kevin.hrtracker.ui.history

import java.time.LocalDate
import java.util.Locale
import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsFormatTest {
    @Test fun `iso week incl 53-week year`() {
        assertEquals(42, isoWeek(LocalDate.of(2026, 10, 12)))
        assertEquals(53, isoWeek(LocalDate.of(2027, 1, 1)))
        assertEquals(1, isoWeek(LocalDate.of(2027, 1, 4)))
    }

    @Test fun `acwr series maps null to NaN`() {
        val s = acwrSeries(listOf(null, 1.25))
        assertTrue(s[0].isNaN())
        assertEquals(1.25f, s[1], 1e-6f)
    }

    @Test fun `baseline is exp of ln-band midpoint, delta relative`() {
        val r = baselineDelta(48.0, ln(40.0), ln(48.4))!!
        assertEquals(44.0, r.first, 1e-9)
        assertEquals(48.0 / 44.0 - 1.0, r.second, 1e-9)
    }

    @Test fun `baseline needs all inputs`() {
        assertNull(baselineDelta(null, 1.0, 2.0))
        assertNull(baselineDelta(40.0, null, 2.0))
    }

    @Test fun `resting delta rounds against 7-day mean`() {
        assertEquals(-2, restingDelta(55, 57.2))
        assertNull(restingDelta(null, 57.0))
        assertNull(restingDelta(55, null))
    }

    @Test fun `signed texts use U+2212 and never clamp`() {
        assertEquals("+9%", signedPercent(0.0909, Locale.US))
        assertEquals("−4%", signedPercent(-0.042, Locale.US))
        assertEquals("0%", signedPercent(-0.004, Locale.US))
        assertEquals("−2", signedInt(-2))
        assertEquals("+3", signedInt(3))
        assertEquals("0", signedInt(0))
    }
}
