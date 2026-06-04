package com.kevin.hrtracker.ble

data class ParsedHr(val bpm: Int, val rrIntervalsMs: List<Int>)

object HeartRateParser {
    fun parse(data: ByteArray): ParsedHr? {
        if (data.isEmpty()) return null
        val flags = data[0].toInt()
        var offset = 1

        val bpm = if (flags and 0x01 == 0) {
            if (offset >= data.size) return null
            data[offset++].toInt() and 0xFF
        } else {
            if (offset + 1 >= data.size) return null
            val v = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
            offset += 2
            v
        }

        if (flags and 0x08 != 0) offset += 2  // skip Energy Expended

        val rr = mutableListOf<Int>()
        if (flags and 0x10 != 0) {
            while (offset + 1 < data.size) {
                val raw = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
                rr += raw * 1000 / 1024  // 1/1024 s → ms
                offset += 2
            }
        }

        return ParsedHr(bpm, rr)
    }
}
