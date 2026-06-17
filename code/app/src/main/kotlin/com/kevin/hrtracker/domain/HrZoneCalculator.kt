package com.kevin.hrtracker.domain

import kotlinx.serialization.Serializable

@Serializable
data class ZoneBounds(val zone: Int, val lo: Int, val hi: Int) {
    val label: String get() = "Z$zone"
}

object HrZoneCalculator {

    private val zonePercentages = listOf(
        1 to (0.50 to 0.60),
        2 to (0.60 to 0.70),
        3 to (0.70 to 0.80),
        4 to (0.80 to 0.90),
        5 to (0.90 to 1.00)
    )

    fun tanakaMaxHr(age: Int): Int = (208 - 0.7 * age).toInt()

    // Karvonen if restingHr is provided, %HRmax otherwise
    fun calculateZones(maxHr: Int, restingHr: Int?): List<ZoneBounds> =
        zonePercentages.map { (zone, pcts) ->
            val (lo, hi) = if (restingHr != null) {
                val hrr = maxHr - restingHr
                Pair(
                    (hrr * pcts.first + restingHr).toInt(),
                    (hrr * pcts.second + restingHr).toInt()
                )
            } else {
                Pair(
                    (maxHr * pcts.first).toInt(),
                    (maxHr * pcts.second).toInt()
                )
            }
            ZoneBounds(zone, lo, hi)
        }

    fun zoneFor(bpm: Int, zones: List<ZoneBounds>): Int {
        for (z in zones) { if (bpm <= z.hi) return z.zone }
        return 5
    }

    // Per-zone error, or null if valid. Checks lo < hi and no overlap with the previous zone.
    fun validateZoneTexts(zoneTexts: List<Pair<String, String>>): List<String?> =
        zoneTexts.mapIndexed { i, (loText, hiText) ->
            val lo = loText.toIntOrNull()
            val hi = hiText.toIntOrNull()
            val prevHi = if (i > 0) zoneTexts[i - 1].second.toIntOrNull() else null
            when {
                lo == null || hi == null -> "Ungültige Zahl"
                lo >= hi -> "Min muss kleiner als Max sein"
                prevHi != null && lo < prevHi -> "Überlappt mit Z$i"
                else -> null
            }
        }
}
