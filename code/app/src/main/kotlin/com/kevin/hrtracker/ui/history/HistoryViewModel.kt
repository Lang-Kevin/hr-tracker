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
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val db: HrDatabase
) : ViewModel() {

    val sessions: StateFlow<List<Session>> = sessionRepository.getSessionsFlow()
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

    fun deleteSingle(id: Long) {
        viewModelScope.launch { sessionRepository.deleteSessionsByIds(listOf(id)) }
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        _selectedIds.value = emptySet()
        viewModelScope.launch { sessionRepository.deleteSessionsByIds(ids) }
    }
}
