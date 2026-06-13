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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
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

    val summaryStats: StateFlow<SummaryStats> = combine(
        sessions,
        db.hrSampleDao().getGlobalAvgBpm()
    ) { sessionList, avgBpm ->
        val durations = sessionList.filter { it.endedAt != null }
            .map { (it.endedAt!! - it.startedAt) / 1000L }
        SummaryStats(
            sessionCount = sessionList.size,
            totalDurationS = durations.sum(),
            longestDurationS = durations.maxOrNull() ?: 0L,
            avgBpm = avgBpm
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

    private val sessionAvgBpms = db.hrSampleDao().getSessionAvgBpms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
