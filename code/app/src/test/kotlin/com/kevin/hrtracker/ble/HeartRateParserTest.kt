package com.kevin.hrtracker.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateParserTest {

    @Test
    fun parse_uint8_valid_returns_bpm() {
        val data = byteArrayOf(0x00, 150.toByte())
        val result = HeartRateParser.parse(data)
        assertEquals(150, result?.bpm)
        assertEquals(emptyList<Int>(), result?.rrIntervalsMs)
    }

    @Test
    fun parse_uint16_valid_returns_bpm() {
        // flags=0x01 → uint16 LE; 0xC8 0x00 = 200
        val data = byteArrayOf(0x01, 0xC8.toByte(), 0x00)
        val result = HeartRateParser.parse(data)
        assertEquals(200, result?.bpm)
    }

    @Test
    fun parse_no_contact_bpm_zero_returns_null() {
        val data = byteArrayOf(0x00, 0x00)
        assertNull(HeartRateParser.parse(data))
    }

    @Test
    fun parse_bpm_too_high_returns_null() {
        val data = byteArrayOf(0x00, 0xFA.toByte()) // 250
        assertNull(HeartRateParser.parse(data))
    }

    @Test
    fun parse_rr_present_correct_conversion() {
        // flags=0x10 (RR present), bpm uint8=150, RR raw=1024 (0x00,0x04 LE)
        val data = byteArrayOf(0x10, 150.toByte(), 0x00.toByte(), 0x04.toByte())
        val result = HeartRateParser.parse(data)
        assertEquals(150, result?.bpm)
        assertEquals(listOf(1000), result?.rrIntervalsMs) // 1024*1000/1024 = 1000
    }

    @Test
    fun parse_empty_array_returns_null() {
        assertNull(HeartRateParser.parse(byteArrayOf()))
    }
}
