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
    val rmssdArtefactPct: Double?
) {
    companion object {
        const val VERSION = 2

        fun compute(
                samples: List<HrSample>, maxHr: Int, restingHr: Int?, isHrvMeasurement: Boolean = false
            ): SessionMetrics {
            val hrv = HrvCalculator.analyze(samples, HrvQuality.discardMsFor(isHrvMeasurement))
            return SessionMetrics(
                activeMs = SampleIntervals.activeMs(samples),
                avgBpm = SampleIntervals.avgBpm(samples),
                trimp = TrimpCalculator.compute(samples, maxHr, restingHr),
                hrr60 = HrRecovery.computeHrRecovery(samples.sortedBy { it.timestampMs }, maxHr)?.hrr60,
                rmssd = hrv?.rmssd,
                rmssdValidBeats = hrv?.validBeats,
                rmssdArtefactPct = hrv?.artefactPct
            )
        }
    }
}
