package com.kevin.hrtracker.ble

/** Battery Level characteristic 0x2A19: one unsigned byte, percent 0..100. 0xFF = unknown sentinel -> null; 101..254 clamp to 100; empty is null. */
object BatteryLevelParser {
    fun parse(value: ByteArray?): Int? {
        if (value == null || value.isEmpty()) return null
        val level = value[0].toInt() and 0xFF
        if (level == 0xFF) return null
        return level.coerceAtMost(100)
    }
}
