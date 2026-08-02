package com.kevin.hrtracker.ui.pip

import com.kevin.hrtracker.domain.HrZoneCalculator
import com.kevin.hrtracker.domain.ZoneBounds
import com.kevin.hrtracker.ui.formatDuration

data class PipUiState(
    val bpm: Int?,
    val zone: Int?,
    val elapsedText: String,
    val paused: Boolean
)

// ponytail: Dauer = Wanduhr seit startedAt, Pausen werden NICHT abgezogen —
// die Pausen-Akkumulation lebt heute nur in LiveViewModel (pausedAccumMs).
// Im PiP steht bei Pause "PAUSE" statt der Zeit, damit die Abweichung nicht auffaellt.
// Upgrade-Pfad: pausedAccumMs nach SessionRepository hochziehen, dann hier abziehen.
fun buildPipUiState(
    bpm: Int?,
    zones: List<ZoneBounds>,
    startedAtMs: Long?,
    nowMs: Long,
    paused: Boolean
): PipUiState {
    val elapsedSeconds = if (startedAtMs == null) 0L
        else ((nowMs - startedAtMs) / 1000).coerceAtLeast(0L)
    return PipUiState(
        bpm = bpm,
        zone = if (bpm == null || zones.isEmpty()) null else HrZoneCalculator.zoneFor(bpm, zones),
        elapsedText = formatDuration(elapsedSeconds),
        paused = paused
    )
}
