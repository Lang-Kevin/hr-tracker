package com.kevin.hrtracker.ble

/** Battery Level characteristic 0x2A19: one unsigned byte, percent 0..100. Anything else is null. */
object BatteryLevelParser {
    fun parse(value: ByteArray?): Int? {
        if (value == null || value.isEmpty()) return null
        val level = value[0].toInt() and 0xFF
        return if (level in 0..100) level else null
    }
}
