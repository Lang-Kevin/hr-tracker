package com.kevin.hrtracker.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.hrtracker.ble.ConnectionState
import com.kevin.hrtracker.ble.HrBleManager
import com.kevin.hrtracker.data.repository.SessionRepository
import com.kevin.hrtracker.data.repository.SettingsRepository
import com.kevin.hrtracker.domain.HrZoneCalculator
import com.kevin.hrtracker.domain.ZoneBounds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

private const val BPM_CHART_THRESHOLD = 2

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val bleManager: HrBleManager,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val activeSessionId: StateFlow<Long?> = sessionRepository.activeSessionId

    val sessionLabel: StateFlow<String?> = sessionRepository.activeSession
        .map { it?.label }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _currentBpm = MutableStateFlow<Int?>(null)
    val currentBpm: StateFlow<Int?> = _currentBpm.asStateFlow()

    private val _bpmHistory = MutableStateFlow<List<Int>>(emptyList())
    val bpmHistory: StateFlow<List<Int>> = _bpmHistory.asStateFlow()

    private val _maxBpm = MutableStateFlow<Int?>(null)
    val maxBpm: StateFlow<Int?> = _maxBpm.asStateFlow()

    val averageBpm: StateFlow<Int?> = _bpmHistory.map { history ->
        if (history.isEmpty()) null else history.average().toInt()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _timeInZone = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val timeInZone: StateFlow<Map<Int, Long>> = _timeInZone.asStateFlow()

    val currentZone: StateFlow<Int?> = combine(
        _currentBpm,
        settingsRepository.userSettings
    ) { bpm, settings ->
        if (bpm == null) null
        else {
            val zones = HrZoneCalculator.calculateZones(settings.maxHrUsed, settings.restingHr)
            HrZoneCalculator.zoneFor(bpm, zones)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val targetZone: StateFlow<Int> = settingsRepository.userSettings
        .map { it.targetZone }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 2)

    val percentInTargetZone: StateFlow<Float?> = combine(
        _timeInZone, targetZone, _elapsedSeconds
    ) { tiz, tz, elapsed ->
        if (elapsed == 0L) null else (tiz[tz] ?: 0L).toFloat() / elapsed.toFloat()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val zoneBounds: StateFlow<List<ZoneBounds>> = settingsRepository.userSettings.map { settings ->
        HrZoneCalculator.calculateZones(settings.maxHrUsed, settings.restingHr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTargetZone(zone: Int) = viewModelScope.launch { settingsRepository.setTargetZone(zone) }

    private var sessionStartMs = 0L

    init {
        viewModelScope.launch {
            bleManager.hrSamples.collect { parsed ->
                _currentBpm.value = parsed.bpm
                if (parsed.bpm > (_maxBpm.value ?: 0)) _maxBpm.value = parsed.bpm
                val lastChartBpm = _bpmHistory.value.lastOrNull()
                if (lastChartBpm == null || abs(parsed.bpm - lastChartBpm) >= BPM_CHART_THRESHOLD) {
                    _bpmHistory.value = (_bpmHistory.value + parsed.bpm).takeLast(120)
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                if (activeSessionId.value != null && sessionStartMs > 0) {
                    _elapsedSeconds.value = (System.currentTimeMillis() - sessionStartMs) / 1000
                    currentZone.value?.let { z ->
                        _timeInZone.update { map -> map + (z to (map.getOrDefault(z, 0L) + 1L)) }
                    }
                }
                delay(1_000)
            }
        }
        viewModelScope.launch {
            activeSessionId.collect { id ->
                if (id != null && sessionStartMs == 0L) sessionStartMs = System.currentTimeMillis()
                if (id == null) {
                    sessionStartMs = 0L
                    _elapsedSeconds.value = 0
                    _timeInZone.value = emptyMap()
                    _maxBpm.value = null
                    _bpmHistory.value = emptyList()
                }
            }
        }
    }
}
