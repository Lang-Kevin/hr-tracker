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
import com.kevin.hrtracker.domain.CalorieCalculator
import com.kevin.hrtracker.domain.HrRecovery
import com.kevin.hrtracker.domain.HrrResult
import com.kevin.hrtracker.domain.HrZoneCalculator
import com.kevin.hrtracker.domain.HrvCalculator
import com.kevin.hrtracker.domain.SampleIntervals
import com.kevin.hrtracker.domain.TrainingLoad
import com.kevin.hrtracker.domain.TrimpCalculator
import com.kevin.hrtracker.domain.ZoneBounds
import com.kevin.hrtracker.export.SessionExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val db: HrDatabase,
    private val exporter: SessionExporter,
    private val sportLabelDao: SportLabelDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle.get<Long>("sessionId"))

    /** Direkt nach Session-Ende geöffnet → RPE-Abfrage anbieten. */
    val askRpeOnOpen: Boolean = savedStateHandle.get<Boolean>("askRpe") ?: false

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
            avgBpm = SampleIntervals.avgBpm(list) ?: list.map { it.bpm }.average().toInt(),
            maxBpm = list.maxOf { it.bpm },
            minBpm = list.minOf { it.bpm },
            sampleCount = list.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val zoneBounds: StateFlow<List<ZoneBounds>> = session.map { sess ->
        if (sess == null) emptyList()
        else HrZoneCalculator.resolveZones(sess.zoneSnapshotJson, sess.maxHrUsed, sess.restingHr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rmssd: StateFlow<Int?> = samples.map { HrvCalculator.rmssd(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Aktive Zeit ohne Pausen/Dropouts, null solange die Session läuft. */
    val activeSeconds: StateFlow<Long?> = combine(session, samples) { sess, list ->
        if (sess?.endedAt == null) null else SampleIntervals.activeMs(list) / 1000L
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Session-RPE-Last (RPE × aktive Minuten), null ohne Bewertung. */
    val srpeLoad: StateFlow<Int?> = combine(session, activeSeconds) { sess, active ->
        val rpe = sess?.rpe ?: return@combine null
        active?.let { TrainingLoad.srpe(rpe, it * 1000L).roundToInt() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val trimp: StateFlow<Int?> = combine(session, samples) { sess, list ->
        if (sess == null || sess.endedAt == null) null
        else TrimpCalculator.compute(list, sess.maxHrUsed, sess.restingHr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Gewicht oder Geschlecht fehlen → Kalorien nicht berechenbar (Hinweis im Detail-Screen). */
    val bodyDataMissing: StateFlow<Boolean> = settingsRepository.userSettings
        .map { it.weightKg == null || it.sex == null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val calories: StateFlow<Int?> = combine(
        session, samples, settingsRepository.userSettings
    ) { sess, list, settings ->
        if (sess == null || sess.endedAt == null) null
        else CalorieCalculator.estimateActiveKcal(
            samples = list,
            weightKg = settings.weightKg,
            age = settings.age,
            sex = settings.sex
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val timeInZone: StateFlow<Map<Int, Long>> = combine(session, samples) { sess, list ->
        if (sess == null || list.isEmpty()) emptyMap()
        else {
            val zones = HrZoneCalculator.resolveZones(sess.zoneSnapshotJson, sess.maxHrUsed, sess.restingHr)
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

    /** Gap-Lücken (Verbindungsverlust) als Bruchteil der Session-Dauer (0f..1f). */
    val gapFractions: StateFlow<List<Pair<Float, Float>>> = combine(session, samples) { sess, list ->
        if (sess == null || list.size < 2) return@combine emptyList()
        val startedAt = sess.startedAt
        val endedAt = sess.endedAt ?: list.maxOf { it.timestampMs }
        val durationMs = (endedAt - startedAt).toFloat().coerceAtLeast(1f)
        HrZoneCalculator.detectGaps(list).map { range ->
            val startFraction = ((range.first - startedAt).toFloat() / durationMs).coerceIn(0f, 1f)
            val endFraction = ((range.last - startedAt).toFloat() / durationMs).coerceIn(0f, 1f)
            startFraction to endFraction
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateLabel(label: String) {
        viewModelScope.launch { db.sessionDao().updateLabel(sessionId, label) }
    }

    fun updateRpe(rpe: Int?) {
        viewModelScope.launch { db.sessionDao().updateRpe(sessionId, rpe?.coerceIn(0, 10)) }
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
        val durationS = activeSeconds.value ?: 0L
        val zonesJson = zones.entries
            .sortedBy { it.key }
            .joinToString(",") { "\"${it.key}\":${it.value}" }
        return """{"avgHf":${s.avgBpm},"maxHf":${s.maxBpm},"dauerSekunden":$durationS,"zeitInZonen":{$zonesJson}}"""
    }
}
