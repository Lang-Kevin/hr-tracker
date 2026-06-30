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

        // ponytail: max-drop candidate selection — wähle Peak mit grösstem Abfall zu hrAt60, nicht globales BPM-Max. O(n·window); onset-detection if false positives.
        val candidates = samples.filter {
            it.timestampMs <= lastT - 60_000 && it.bpm >= highThreshold
        }
        if (candidates.isEmpty()) return null

        var bestPeak: HrSample? = null
        var bestDrop = -1
        var bestHrAt60 = -1

        for (candidate in candidates) {
            val hrAt60Sample = samples.firstOrNull {
                it.timestampMs >= candidate.timestampMs + 60_000 &&
                it.timestampMs <= candidate.timestampMs + 65_000
            } ?: continue

            val drop = candidate.bpm - hrAt60Sample.bpm
            // ponytail: Tie → frühester (höchster) Peak gewinnt
            if (drop > bestDrop) {
                bestDrop = drop
                bestPeak = candidate
                bestHrAt60 = hrAt60Sample.bpm
            }
        }

        if (bestPeak == null || bestDrop <= 0) return null

        val peak = bestPeak
        val hrAt60 = bestHrAt60
        val hrr60 = bestDrop

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
