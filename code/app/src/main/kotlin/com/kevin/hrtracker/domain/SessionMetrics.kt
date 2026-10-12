package com.kevin.hrtracker.domain

import com.kevin.hrtracker.data.entity.HrSample

/**
 * Pro Session persistierte Kennzahlen. Hängen nur an den Samples und am Zonen-Snapshot der
 * Session (maxHrUsed, restingHr), nicht an aktuellen Settings — daher sicher cachebar.
 * Kalorien bleiben on-read, weil sie vom aktuellen Gewicht abhängen.
 *
 * VERSION erhöhen, wenn sich eine Formel ändert: Sessions mit kleinerer metricsVersion
 * werden beim App-Start neu berechnet.
 */
data class SessionMetrics(
    val activeMs: Long,
    val avgBpm: Int?,
    val trimp: Int?,
    val hrr60: Int?,
    val rmssd: Int?,
    val rmssdValidBeats: Int?,
    val rmssdArtefactPct: Double?,
    val zoneSeconds: String? = null
) {
    companion object {
        const val VERSION = 2

        fun compute(
            samples: List<HrSample>, maxHr: Int, restingHr: Int?, isHrvMeasurement: Boolean = false,
            zones: List<ZoneBounds>? = null
        ): SessionMetrics {
            val hrv = HrvCalculator.analyze(samples, HrvQuality.discardMsFor(isHrvMeasurement))
            return SessionMetrics(
                activeMs = SampleIntervals.activeMs(samples),
                avgBpm = SampleIntervals.avgBpm(samples),
                trimp = TrimpCalculator.compute(samples, maxHr, restingHr),
                hrr60 = HrRecovery.computeHrRecovery(samples.sortedBy { it.timestampMs }, maxHr)?.hrr60,
                rmssd = hrv?.rmssd,
                rmssdValidBeats = hrv?.validBeats,
                rmssdArtefactPct = hrv?.artefactPct,
                zoneSeconds = zones?.let { encodeZoneSeconds(HrZoneCalculator.aggregateTimeInZone(samples, it), it.size) }
            )
        }

        /** "s1,…,sN" in whole seconds, zone 1 first; missing zones are 0. */
        fun encodeZoneSeconds(timeInZone: Map<Int, Long>, zoneCount: Int): String =
            (1..zoneCount).joinToString(",") { z -> (timeInZone[z] ?: 0L).toString() }

        /** Inverse of [encodeZoneSeconds]; null for null/blank/malformed/negative input (UI then hides the bar). */
        fun decodeZoneSeconds(csv: String?): List<Long>? {
            if (csv.isNullOrBlank()) return null
            val values = csv.split(',').map { it.trim().toLongOrNull() ?: return null }
            return values.takeIf { v -> v.isNotEmpty() && v.all { it >= 0 } }
        }
    }
}
