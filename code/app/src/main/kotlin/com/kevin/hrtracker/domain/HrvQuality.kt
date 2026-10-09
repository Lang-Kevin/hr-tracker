package com.kevin.hrtracker.domain

enum class HrvUnreliableReason { TOO_SHORT, TOO_FEW_BEATS, TOO_MANY_ARTEFACTS }

object HrvQuality {
    const val STABILISATION_MS = 60_000L
    const val MIN_ANALYSED_SECONDS = 180
    const val MIN_VALID_BEATS = 180
    const val MAX_ARTEFACT_PCT = 5.0

    fun discardMsFor(isHrvMeasurement: Boolean): Long = if (isHrvMeasurement) STABILISATION_MS else 0L

    /** Gleiche Regel wie HrvCalculator.analyze: Discard nur, wenn die Aufnahme länger ist. */
    fun analysedSeconds(activeMs: Long, discardMs: Long): Int =
        ((if (activeMs > discardMs) activeMs - discardMs else activeMs) / 1000).toInt()

    /** Erstes verletztes Gate in Reihenfolge TOO_SHORT, TOO_FEW_BEATS, TOO_MANY_ARTEFACTS; null = zuverlässig. */
    fun reason(analysedSeconds: Int, validBeats: Int, artefactPct: Double): HrvUnreliableReason? = when {
        analysedSeconds < MIN_ANALYSED_SECONDS -> HrvUnreliableReason.TOO_SHORT
        validBeats < MIN_VALID_BEATS -> HrvUnreliableReason.TOO_FEW_BEATS
        artefactPct > MAX_ARTEFACT_PCT -> HrvUnreliableReason.TOO_MANY_ARTEFACTS
        else -> null
    }

    fun reason(r: HrvResult): HrvUnreliableReason? = reason(r.analysedSeconds, r.validBeats, r.artefactPct)
}
