package com.kevin.hrtracker.domain

import com.kevin.hrtracker.data.entity.HrSample

enum class HrrRating {
    NIEDRIG,
    NORMAL,
    GUT,
    SEHR_GUT
}

data class HrrResult(
    val peakBpm: Int,
    val hrAt60s: Int,
    val hrr60: Int,
    val recoveredToTarget: Boolean,
    val secondsToTarget: Int?,
    val rating: HrrRating
)

object HrRecovery {

    fun computeHrRecovery(samples: List<HrSample>, maxHr: Int): HrrResult? {
        // Guard: minimum samples and valid maxHr
        if (samples.size < 2 || maxHr <= 0) return null

        val highThreshold = 0.70 * maxHr
        val recoveryTarget = 0.60 * maxHr

        val lastT = samples.last().timestampMs

        // ponytail: peak = hoechste beobachtete bpm (kein onset-detection). upgrade-pfad: echte exercise-cessation-erkennung falls noetig
        val peakCandidates = samples.filter { it.timestampMs <= lastT - 60_000 }
        if (peakCandidates.isEmpty()) return null

        val peak = peakCandidates.maxByOrNull { it.bpm } ?: return null
        if (peak.bpm < highThreshold) return null

        // fenster [60s, 65s] nach peak — toleriert BLE-luecken, nimmt nie ein sample VOR 60s (das wuerde recovery unterschaetzen)
        val hrAt60Sample = samples.firstOrNull {
            it.timestampMs >= peak.timestampMs + 60_000 &&
            it.timestampMs <= peak.timestampMs + 65_000
        } ?: return null

        val hrAt60 = hrAt60Sample.bpm
        val hrr60 = peak.bpm - hrAt60

        // Check if recovered to target, measure time to recovery
        val recoveryTarget_Int = (recoveryTarget).toInt()
        val samplesAfterPeak = samples.filter { it.timestampMs > peak.timestampMs }
        val recoveryPoint = samplesAfterPeak.firstOrNull { it.bpm <= recoveryTarget_Int }

        val recoveredToTarget = recoveryPoint != null
        val secondsToTarget = if (recoveryPoint != null) {
            ((recoveryPoint.timestampMs - peak.timestampMs) / 1000).toInt()
        } else {
            null
        }

        val rating = when {
            hrr60 < 12 -> HrrRating.NIEDRIG
            hrr60 < 18 -> HrrRating.NORMAL
            hrr60 < 30 -> HrrRating.GUT
            else -> HrrRating.SEHR_GUT
        }

        return HrrResult(
            peakBpm = peak.bpm,
            hrAt60s = hrAt60,
            hrr60 = hrr60,
            recoveredToTarget = recoveredToTarget,
            secondsToTarget = secondsToTarget,
            rating = rating
        )
    }
}
