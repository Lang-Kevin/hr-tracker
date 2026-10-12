package com.kevin.hrtracker.ble

/** Battery Level characteristic 0x2A19: one unsigned byte, percent 0..100. Values above 100 clamp to 100; empty is null. */
object BatteryLevelParser {
    fun parse(value: ByteArray?): Int? {
        if (value == null || value.isEmpty()) return null
        val level = value[0].toInt() and 0xFF
        return level.coerceAtMost(100)
    }
}
