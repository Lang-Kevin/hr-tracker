package com.kevin.hrtracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.hrtracker.data.entity.Session
import com.kevin.hrtracker.data.entity.isHrvMeasurement
import com.kevin.hrtracker.data.entity.toHrvMeasurements
import com.kevin.hrtracker.data.repository.SessionRepository
import com.kevin.hrtracker.data.repository.SettingsRepository
import com.kevin.hrtracker.domain.LoadDay
import com.kevin.hrtracker.domain.LoadMetric
import com.kevin.hrtracker.domain.Readiness
import com.kevin.hrtracker.domain.ReadinessSummary
import com.kevin.hrtracker.domain.TrainingLoad
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val sessions: StateFlow<List<Session>> = sessionRepository.getSessionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedLabels = MutableStateFlow<Set<String>>(emptySet())
    val selectedLabels: StateFlow<Set<String>> = _selectedLabels.asStateFlow()

    val availableLabels: StateFlow<List<String>> = sessions.map { sessionList ->
        sessionList.map { it.label }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _dateRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val dateRange: StateFlow<Pair<Long, Long>?> = _dateRange.asStateFlow()

    val filteredSessions: StateFlow<List<Session>> = combine(
        sessions,
        _selectedLabels,
        _dateRange
    ) { all, selected, range ->
        var result = if (selected.isEmpty()) all else all.filter { it.label in selected }
        if (range != null) {
            val (start, end) = range
            val zone = ZoneId.systemDefault()
            val endExclusive = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
                .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            result = result.filter { it.startedAt in start until endExclusive }
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trashSessions: StateFlow<List<Session>> = sessionRepository.getTrashFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    data class SummaryStats(
        val sessionCount: Int,
        val totalDurationS: Long,
        val longestDurationS: Long,
        val avgBpm: Int?
    )

    val summaryStats: StateFlow<SummaryStats> = filteredSessions.map { sessionList ->
        val completed = sessionList.filter { it.endedAt != null && !it.isHrvMeasurement }
        val durations = completed.map { it.activeSeconds() }
        SummaryStats(
            sessionCount = completed.size,
            totalDurationS = durations.sum(),
            longestDurationS = durations.maxOrNull() ?: 0L,
            avgBpm = weightedAvgBpm(completed)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryStats(0, 0L, 0L, null))

    data class WeekStats(
        val weekLabel: String,
        val sessionCount: Int,
        val totalDurationMin: Long,
        val avgBpm: Int?
    )

    data class SessionTrimpEntry(
        val sessionId: Long,
        val label: String,
        val startedAt: Long,
        val trimp: Int
    )

    private val trainings: StateFlow<List<Session>> = sessions.map { list ->
        list.filter { it.endedAt != null && !it.isHrvMeasurement }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weeklyData: StateFlow<List<WeekStats>> = trainings.map { sessionList ->
        val cal = Calendar.getInstance()
        (5 downTo 0).map { weeksAgo ->
            cal.timeInMillis = System.currentTimeMillis()
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            cal.add(Calendar.WEEK_OF_YEAR, -weeksAgo)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val weekStart = cal.timeInMillis
            val weekEnd = weekStart + 7L * 24 * 60 * 60 * 1000
            val weekSessions = sessionList.filter { it.startedAt >= weekStart && it.startedAt < weekEnd }
            WeekStats(
                weekLabel = SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(weekStart)),
                sessionCount = weekSessions.size,
                totalDurationMin = weekSessions.sumOf { it.activeSeconds() } / 60L,
                avgBpm = weightedAvgBpm(weekSessions)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trimpHistory: StateFlow<List<SessionTrimpEntry>> = trainings.map { list ->
        list.take(15).mapNotNull { sess ->
            SessionTrimpEntry(sess.id, sess.label, sess.startedAt, sess.trimp ?: return@mapNotNull null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Trainingslast (akut/chronisch, ACWR) ---

    private val _loadMetric = MutableStateFlow(LoadMetric.TRIMP)
    val loadMetric: StateFlow<LoadMetric> = _loadMetric.asStateFlow()

    fun setLoadMetric(metric: LoadMetric) { _loadMetric.value = metric }

    data class LoadState(
        val days: List<LoadDay>,
        /** Trainings der letzten 28 Tage ohne RPE (nur relevant für sRPE). */
        val unratedLast28: Int
    )

    val loadState: StateFlow<LoadState> = combine(trainings, _loadMetric) { list, metric ->
        val today = LocalDate.now()
        val loads = list.mapNotNull { sess ->
            val load = when (metric) {
                LoadMetric.TRIMP -> sess.trimp?.toDouble()
                LoadMetric.SRPE -> sess.rpe?.let { TrainingLoad.srpe(it, sess.activeSeconds() * 1000L) }
            } ?: return@mapNotNull null
            sess.startedAt.toLocalDate() to load
        }
        val since = today.minusDays((TrainingLoad.CHRONIC_DAYS - 1).toLong())
        LoadState(
            days = TrainingLoad.series(loads, list.minOfOrNull { it.startedAt }?.toLocalDate(), today, LOAD_CHART_DAYS),
            unratedLast28 = if (metric == LoadMetric.SRPE)
                list.count { it.rpe == null && !it.startedAt.toLocalDate().isBefore(since) } else 0
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState(emptyList(), 0))

    // --- Form: HRV-Bereitschaft, Ruhepuls, HRR60 ---

    val readiness: StateFlow<ReadinessSummary?> = sessions.map { list ->
        val measurements = list.toHrvMeasurements()
        if (measurements.isEmpty()) null else Readiness.summarize(measurements, LocalDate.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    data class HrrTrend(
        /** (Datum, HRR60) der Trainings der letzten 8 Wochen, älteste zuerst. */
        val points: List<Pair<LocalDate, Int>>,
        val avgLast4Weeks: Double?,
        val avgPrev4Weeks: Double?
    )

    val hrrTrend: StateFlow<HrrTrend> = trainings.map { list ->
        val today = LocalDate.now()
        val points = list.mapNotNull { s -> s.hrr60?.let { s.startedAt.toLocalDate() to it } }
            .filter { !it.first.isBefore(today.minusDays(55)) }
            .sortedBy { it.first }
        val (cur, prev) = Readiness.compareWindows(points, today, windowDays = 28)
        HrrTrend(points, cur, prev)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HrrTrend(emptyList(), null, null))

    val currentRestingHr: StateFlow<Int?> = settingsRepository.userSettings.map { it.restingHr }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val autoRestingHr: StateFlow<Boolean> = settingsRepository.userSettings.map { it.autoRestingHr }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setAutoRestingHr(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAutoRestingHr(enabled)
        if (enabled) applyRestingHrFromHrv()
    }

    fun applyRestingHrFromHrv() = viewModelScope.launch {
        val hr = readiness.value?.restingHr7 ?: return@launch
        settingsRepository.setRestingHr(hr.coerceIn(20, 100))
    }

    fun toggleLabelFilter(label: String) {
        val current = _selectedLabels.value
        _selectedLabels.value = if (label in current) current - label else current + label
    }

    fun setDateRange(start: Long?, end: Long?) {
        if (start == null || end == null) {
            _dateRange.value = null
            return
        }
        // DateRangePicker liefert UTC-Mitternacht-Millis; auf geräte-lokale Zeitzone re-ankern,
        // damit das Filterfenster mit der lokalen Tages-Bucketing-Logik übereinstimmt.
        val zone = ZoneId.systemDefault()
        val localStart = Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val localEnd = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        _dateRange.value = localStart to localEnd
    }

    fun clearDateRange() {
        _dateRange.value = null
    }

    fun startSelection(id: Long) {
        _selectedIds.value = setOf(id)
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value
        _selectedIds.value = if (id in current) current - id else current + id
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun moveToTrash(ids: List<Long>) {
        if (ids.isEmpty()) return
        _selectedIds.value = emptySet()
        viewModelScope.launch { sessionRepository.deleteSessionsByIds(ids) }
    }

    fun restoreSessions(ids: List<Long>) {
        viewModelScope.launch { sessionRepository.restoreSessionsByIds(ids) }
    }
}

private const val LOAD_CHART_DAYS = 42

/** Aktive Zeit in s; Fallback Wanduhr-Dauer, solange die Kennzahlen noch nicht berechnet sind. */
private fun Session.activeSeconds(): Long =
    (activeMs ?: endedAt?.let { it - startedAt } ?: 0L) / 1000L

/** Ø-Puls über mehrere Sessions, gewichtet mit der aktiven Zeit. */
private fun weightedAvgBpm(sessions: List<Session>): Int? {
    val withBpm = sessions.filter { it.avgBpm != null && it.activeSeconds() > 0 }
    val total = withBpm.sumOf { it.activeSeconds() }
    if (total == 0L) return null
    return (withBpm.sumOf { it.avgBpm!!.toDouble() * it.activeSeconds() } / total).roundToInt()
}

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
