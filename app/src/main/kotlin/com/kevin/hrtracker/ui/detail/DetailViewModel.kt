package com.kevin.hrtracker.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.hrtracker.data.db.HrDatabase
import com.kevin.hrtracker.data.entity.HrSample
import com.kevin.hrtracker.data.entity.Session
import com.kevin.hrtracker.domain.HrZoneCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val db: HrDatabase
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle.get<Long>("sessionId"))

    val session: StateFlow<Session?> = flow { emit(db.sessionDao().getById(sessionId)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val samples: StateFlow<List<HrSample>> = db.hrSampleDao()
        .getSamplesForSession(sessionId)
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

    // zone → seconds (each sample ≈ 1 s at ~1 Hz)
    val zoneDistribution: StateFlow<Map<Int, Int>> = combine(session, samples) { sess, list ->
        if (sess == null || list.isEmpty()) emptyMap()
        else {
            val zones = HrZoneCalculator.calculateZones(sess.maxHrUsed, sess.restingHr)
            list.groupBy { HrZoneCalculator.zoneFor(it.bpm, zones) }
                .mapValues { (_, samples) -> samples.size }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}
