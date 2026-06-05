package com.kevin.hrtracker.ui.detail

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.hrtracker.data.db.HrDatabase
import com.kevin.hrtracker.data.entity.HrSample
import com.kevin.hrtracker.data.entity.Session
import com.kevin.hrtracker.domain.HrZoneCalculator
import com.kevin.hrtracker.domain.ZoneBounds
import com.kevin.hrtracker.export.SessionExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val db: HrDatabase,
    private val exporter: SessionExporter
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle.get<Long>("sessionId"))

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
        else HrZoneCalculator.calculateZones(sess.maxHrUsed, sess.restingHr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val timeInZone: StateFlow<Map<Int, Long>> = combine(session, samples) { sess, list ->
        if (sess == null || list.isEmpty()) emptyMap()
        else {
            val zones = HrZoneCalculator.calculateZones(sess.maxHrUsed, sess.restingHr)
            val sorted = list.sortedBy { it.timestampMs }
            val result = mutableMapOf<Int, Long>()
            for (i in 0 until sorted.size - 1) {
                val zone = HrZoneCalculator.zoneFor(sorted[i].bpm, zones)
                val durS = (sorted[i + 1].timestampMs - sorted[i].timestampMs) / 1000L
                result[zone] = (result[zone] ?: 0L) + durS.coerceAtLeast(0L)
            }
            val lastZone = HrZoneCalculator.zoneFor(sorted.last().bpm, zones)
            result[lastZone] = (result[lastZone] ?: 0L) + 1L
            result
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val dominantZone: StateFlow<Int> = timeInZone.map { map ->
        map.maxByOrNull { it.value }?.key ?: 3
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 3)

    val percentInTargetZone: StateFlow<Float?> = combine(timeInZone, dominantZone) { map, zone ->
        if (map.isEmpty()) null
        else map.getOrDefault(zone, 0L).toFloat() / map.values.sum().coerceAtLeast(1L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updateLabel(label: String) {
        viewModelScope.launch { db.sessionDao().updateLabel(sessionId, label) }
    }

    fun updateNote(note: String) {
        viewModelScope.launch { db.sessionDao().updateNote(sessionId, note) }
    }

    suspend fun export(context: Context): Intent? = exporter.buildShareIntent(context, sessionId)
}
