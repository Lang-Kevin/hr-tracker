package com.kevin.hrtracker.ui.detail

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.hrtracker.data.db.HrDatabase
import com.kevin.hrtracker.data.db.SportLabelDao
import com.kevin.hrtracker.data.entity.HrSample
import com.kevin.hrtracker.data.entity.Milestone
import com.kevin.hrtracker.data.entity.Session
import com.kevin.hrtracker.data.entity.SportLabel
import com.kevin.hrtracker.data.repository.SettingsRepository
import com.kevin.hrtracker.domain.HrRecovery
import com.kevin.hrtracker.domain.HrrResult
import com.kevin.hrtracker.domain.HrZoneCalculator
import com.kevin.hrtracker.domain.ZoneBounds
import com.kevin.hrtracker.export.SessionExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.sqrt

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val db: HrDatabase,
    private val exporter: SessionExporter,
    private val sportLabelDao: SportLabelDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle.get<Long>("sessionId"))

    val chartDynamicScaleDefault: StateFlow<Boolean> =
        settingsRepository.userSettings.map { it.chartDynamicScale }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val trainingLabels: StateFlow<List<SportLabel>> = sportLabelDao.getAllLabels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val session: StateFlow<Session?> = db.sessionDao().getByIdFlow(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val samples: StateFlow<List<HrSample>> = db.hrSampleDao()
        .getSamplesForSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bpmHistory: StateFlow<List<Int>> = samples.map { list -> list.map { it.bpm } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    data class Stats(val avgBpm: Int, val maxBpm: Int, val minBpm: Int, val sampleCount: Int)

    val stats: StateFlow<Stats?> = samples.map { list ->
        if (list.isEmpty()) null
        else Stats(
            avgBpm = list.map { it.bpm }.average().toInt(),
            maxBpm = list.maxOf { it.bpm },
            minBpm = list.minOf { it.bpm },
            sampleCount = list.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val zoneBounds: StateFlow<List<ZoneBounds>> = session.map { sess ->
        if (sess == null) emptyList()
        else sess.zoneSnapshotJson
            ?.let { runCatching { Json.decodeFromString<List<ZoneBounds>>(it) }.getOrNull() }
            ?: HrZoneCalculator.calculateZones(sess.maxHrUsed, sess.restingHr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rmssd: StateFlow<Int?> = samples.map { list ->
        // Collect all RR values in order (each BLE sample = one heartbeat notification,
        // consecutive samples are consecutive beats — no gap between them).
        // 300–2000 ms range filter removes ectopic beats and sensor noise before diffing.
        val allRrs = list.flatMap { sample ->
            sample.rrIntervalsMs
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.filter { it in 300..2000 }
                ?: emptyList()
        }
        if (allRrs.size < 2) null
        else {
            val diffs = allRrs.zipWithNext { a, b -> (b - a).toDouble() }
            sqrt(diffs.sumOf { it * it } / diffs.size).toInt()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val trimp: StateFlow<Int?> = combine(session, stats) { sess, st ->
        if (sess == null || st == null || sess.endedAt == null) null
        else {
            val durationMin = (sess.endedAt - sess.startedAt) / 60000.0
            if (durationMin <= 0) null
            else if (sess.restingHr != null) {
                val hrr = (sess.maxHrUsed - sess.restingHr).toDouble().coerceAtLeast(1.0)
                val hrRatio = ((st.avgBpm - sess.restingHr) / hrr).coerceIn(0.0, 1.0)
                (durationMin * hrRatio * exp(1.92 * hrRatio)).toInt().coerceAtLeast(0)
            } else {
                val hrRatio = (st.avgBpm.toDouble() / sess.maxHrUsed).coerceIn(0.0, 1.0)
                (durationMin * hrRatio * 100).toInt()
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val timeInZone: StateFlow<Map<Int, Long>> = combine(session, samples) { sess, list ->
        if (sess == null || list.isEmpty()) emptyMap()
        else {
            val zones = HrZoneCalculator.calculateZones(sess.maxHrUsed, sess.restingHr)
            HrZoneCalculator.aggregateTimeInZone(list, zones)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val dominantZone: StateFlow<Int> = timeInZone.map { map ->
        map.maxByOrNull { it.value }?.key ?: 3
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 3)

    val percentInTargetZone: StateFlow<Float?> = combine(timeInZone, dominantZone) { map, zone ->
        if (map.isEmpty()) null
        else map.getOrDefault(zone, 0L).toFloat() / map.values.sum().coerceAtLeast(1L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recovery: StateFlow<HrrResult?> = combine(samples, session) { list, sess ->
        if (sess == null) null
        else HrRecovery.computeHrRecovery(list, sess.maxHrUsed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val milestones: StateFlow<List<Milestone>> = db.milestoneDao().getBySession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateLabel(label: String) {
        viewModelScope.launch { db.sessionDao().updateLabel(sessionId, label) }
    }

    fun updateNote(note: String) {
        viewModelScope.launch { db.sessionDao().updateNote(sessionId, note) }
    }

    fun updateMilestoneLabel(id: Long, label: String) {
        viewModelScope.launch { db.milestoneDao().updateLabel(id, label.trim()) }
    }

    suspend fun export(context: Context): Intent? = exporter.buildShareIntent(context, sessionId)

    suspend fun exportCsv(context: Context): Intent? = exporter.buildCsvShareIntent(context, sessionId)

    fun generateReport(): String {
        val s = stats.value ?: return "{\"fehler\":\"Keine Daten verfügbar\"}"
        val sess = session.value ?: return "{\"fehler\":\"Keine Daten verfügbar\"}"
        val zones = timeInZone.value
        val durationS = if (sess.endedAt != null) (sess.endedAt - sess.startedAt) / 1000L else 0L
        val zonesJson = zones.entries
            .sortedBy { it.key }
            .joinToString(",") { "\"${it.key}\":${it.value}" }
        return """{"avgHf":${s.avgBpm},"maxHf":${s.maxBpm},"dauerSekunden":$durationS,"zeitInZonen":{$zonesJson}}"""
    }
}
