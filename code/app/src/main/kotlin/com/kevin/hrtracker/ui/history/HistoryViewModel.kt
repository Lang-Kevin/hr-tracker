package com.kevin.hrtracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.hrtracker.data.db.HrDatabase
import com.kevin.hrtracker.data.entity.Session
import com.kevin.hrtracker.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.exp

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val db: HrDatabase
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

    private val sessionAvgBpms = db.hrSampleDao().getSessionAvgBpms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    data class SummaryStats(
        val sessionCount: Int,
        val totalDurationS: Long,
        val longestDurationS: Long,
        val avgBpm: Int?
    )

    val summaryStats: StateFlow<SummaryStats> = filteredSessions.flatMapLatest { sessionList ->
        val completed = sessionList.filter { it.endedAt != null }
        val durations = completed.map { (it.endedAt!! - it.startedAt) / 1000L }
        val ids = completed.map { it.id }
        flow {
            val avgBpm = if (ids.isEmpty()) null
                         else db.hrSampleDao().getAvgBpmForSessions(ids)?.toInt()
            emit(
                SummaryStats(
                    sessionCount = sessionList.size,
                    totalDurationS = durations.sum(),
                    longestDurationS = durations.maxOrNull() ?: 0L,
                    avgBpm = avgBpm
                )
            )
        }
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

    val weeklyData: StateFlow<List<WeekStats>> = combine(sessions, sessionAvgBpms) { sessionList, bpmStats ->
        val bpmMap = bpmStats.associateBy { it.sessionId }
        val cal = Calendar.getInstance()
        (5 downTo 0).map { weeksAgo ->
            cal.timeInMillis = System.currentTimeMillis()
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            cal.add(Calendar.WEEK_OF_YEAR, -weeksAgo)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val weekStart = cal.timeInMillis
            val weekEnd = weekStart + 7L * 24 * 60 * 60 * 1000
            val weekSessions = sessionList.filter {
                it.startedAt >= weekStart && it.startedAt < weekEnd && it.endedAt != null
            }
            val durations = weekSessions.map { (it.endedAt!! - it.startedAt) / 60000L }
            val bpms = weekSessions.mapNotNull { bpmMap[it.id]?.avgBpm }
            WeekStats(
                weekLabel = SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(weekStart)),
                sessionCount = weekSessions.size,
                totalDurationMin = durations.sum(),
                avgBpm = if (bpms.isEmpty()) null else bpms.average().toInt()
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trimpHistory: StateFlow<List<SessionTrimpEntry>> = combine(sessions, sessionAvgBpms) { sessionList, bpmStats ->
        val bpmMap = bpmStats.associateBy { it.sessionId }
        sessionList.filter { it.endedAt != null }.take(15).mapNotNull { sess ->
            val avgBpm = bpmMap[sess.id]?.avgBpm ?: return@mapNotNull null
            val durationMin = (sess.endedAt!! - sess.startedAt) / 60000.0
            if (durationMin <= 0) return@mapNotNull null
            val trimp = if (sess.restingHr != null) {
                val hrr = (sess.maxHrUsed - sess.restingHr).toDouble().coerceAtLeast(1.0)
                val hrRatio = ((avgBpm - sess.restingHr) / hrr).coerceIn(0.0, 1.0)
                (durationMin * hrRatio * exp(1.92 * hrRatio)).toInt().coerceAtLeast(0)
            } else {
                val hrRatio = (avgBpm.toDouble() / sess.maxHrUsed).coerceIn(0.0, 1.0)
                (durationMin * hrRatio * 100).toInt()
            }
            SessionTrimpEntry(sess.id, sess.label, sess.startedAt, trimp)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
