package com.kevin.hrtracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kevin.hrtracker.domain.HrvMeasurement
import com.kevin.hrtracker.domain.Readiness
import com.kevin.shared.domain.SoftDeletable
import java.time.Instant
import java.time.ZoneId

@Entity
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val startedAt: Long,
    val endedAt: Long?,
    val maxHrUsed: Int,
    val restingHr: Int?,
    val note: String? = null,
    val zoneSnapshotJson: String? = null,
    override val deletedAt: Long? = null,
    // Gecachte Kennzahlen aus SessionMetrics (null bis berechnet, siehe metricsVersion)
    val activeMs: Long? = null,
    val avgBpm: Int? = null,
    val trimp: Int? = null,
    val hrr60: Int? = null,
    val rmssd: Int? = null,
    @ColumnInfo(defaultValue = "0") val metricsVersion: Int = 0,
    /** Subjektive Belastung (CR-10, 0–10), null = nicht bewertet. */
    val rpe: Int? = null
) : SoftDeletable

/** Ruhe-HRV-Messung statt Training — zählt nicht in Trainings-Statistiken und Last. */
val Session.isHrvMeasurement: Boolean get() = label == Readiness.HRV_LABEL

/** HRV-Sessions mit berechneter RMSSD als Messpunkte; Ø-Puls der Messung = Ruhepuls. */
fun List<Session>.toHrvMeasurements(zone: ZoneId = ZoneId.systemDefault()): List<HrvMeasurement> =
    filter { it.isHrvMeasurement && it.rmssd != null && it.deletedAt == null }.map {
        HrvMeasurement(
            date = Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate(),
            timestampMs = it.startedAt,
            rmssd = it.rmssd!!,
            restingHr = it.avgBpm
        )
    }
