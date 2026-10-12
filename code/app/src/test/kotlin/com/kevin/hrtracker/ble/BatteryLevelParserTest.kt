package com.kevin.hrtracker.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryLevelParserTest {
    @Test fun `null or empty is null`() {
        assertNull(BatteryLevelParser.parse(null))
        assertNull(BatteryLevelParser.parse(byteArrayOf()))
    }

    @Test fun `first byte is percent`() {
        assertEquals(0, BatteryLevelParser.parse(byteArrayOf(0)))
        assertEquals(80, BatteryLevelParser.parse(byteArrayOf(80)))
        assertEquals(100, BatteryLevelParser.parse(byteArrayOf(100)))
    }

    @Test fun `out of range clamps to 100`() {
        assertEquals(100, BatteryLevelParser.parse(byteArrayOf(101)))
        assertEquals(100, BatteryLevelParser.parse(byteArrayOf(0xFE.toByte())))
    }

    @Test fun `0xFF is unknown sentinel`() {
        assertNull(BatteryLevelParser.parse(byteArrayOf(0xFF.toByte())))
    }

    @Test fun `trailing bytes ignored`() {
        assertEquals(55, BatteryLevelParser.parse(byteArrayOf(55, 1, 2)))
    }
}
