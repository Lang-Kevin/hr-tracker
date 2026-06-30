package com.kevin.hrtracker.ui.live

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.shared.ble.ConnectionState
import com.kevin.hrtracker.ble.HrBleManager
import com.kevin.hrtracker.data.db.MilestoneDao
import com.kevin.hrtracker.data.entity.Milestone
import com.kevin.hrtracker.data.repository.SessionRepository
import com.kevin.hrtracker.data.repository.SettingsRepository
import com.kevin.hrtracker.domain.HrSource
import com.kevin.hrtracker.domain.HrZoneCalculator
import com.kevin.hrtracker.domain.ZoneBounds
import com.kevin.hrtracker.wearable.WearableHrSource
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val bleManager: HrBleManager,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val wearableHrSource: WearableHrSource,
    private val milestoneDao: MilestoneDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _hrvCountdown = MutableStateFlow<Int?>(
        savedStateHandle.get<Int>("hrv")?.takeIf { it > 0 }
    )
    val hrvCountdown: StateFlow<Int?> = _hrvCountdown.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val activeSessionId: StateFlow<Long?> = sessionRepository.activeSessionId

    val sessionLabel: StateFlow<String?> = sessionRepository.activeSession
        .map { it?.label }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _currentBpm = MutableStateFlow<Int?>(null)
    val currentBpm: StateFlow<Int?> = _currentBpm.asStateFlow()

    private val _lastRrMs = MutableStateFlow<Int?>(null)
    val lastRrMs: StateFlow<Int?> = _lastRrMs.asStateFlow()

    private val _bpmHistory = MutableStateFlow<List<Int>>(emptyList())
    val bpmHistory: StateFlow<List<Int>> = _bpmHistory.asStateFlow()

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
        else HrZoneCalculator.zoneFor(bpm, settings.effectiveZones)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val targetZone: StateFlow<Int> = settingsRepository.userSettings
        .map { it.targetZone }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 2)

    val percentInTargetZone: StateFlow<Float?> = combine(
        _timeInZone, targetZone, _elapsedSeconds
    ) { tiz, tz, elapsed ->
        if (elapsed == 0L) null else (tiz[tz] ?: 0L).toFloat() / elapsed.toFloat()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val zoneBounds: StateFlow<List<ZoneBounds>> = settingsRepository.userSettings
        .map { it.effectiveZones }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTargetZone(zone: Int) = viewModelScope.launch { settingsRepository.setTargetZone(zone) }

    val reachedZones: StateFlow<Set<Int>> = _timeInZone.map { tiz ->
        tiz.filterValues { it > 0L }.keys
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val isPaused: StateFlow<Boolean> = sessionRepository.isPaused
    val connectionLost: StateFlow<Boolean> = sessionRepository.pausedByConnectionLoss

    fun togglePause() = viewModelScope.launch {
        if (isPaused.value) sessionRepository.resume() else sessionRepository.pause()
    }

    private val _milestones = MutableStateFlow<List<Long>>(emptyList())
    val milestones: StateFlow<List<Long>> = _milestones.asStateFlow()

    fun addMilestone() {
        _milestones.update { it + _elapsedSeconds.value }
    }

    private var sessionStartMs = 0L
    private var pausedAccumMs = 0L
    private var pauseStartedMs = 0L

    init {
        viewModelScope.launch {
            settingsRepository.userSettings
                .flatMapLatest { s ->
                    if (s.hrSource == HrSource.WATCH) wearableHrSource.hrSamples
                    else bleManager.hrSamples
                }
                .collect { parsed ->
                    _currentBpm.value = parsed.bpm
                    _lastRrMs.value = parsed.rrIntervalsMs.lastOrNull()
                    _bpmHistory.value = (_bpmHistory.value + parsed.bpm).takeLast(120)
                }
        }
        viewModelScope.launch {
            while (true) {
                if (activeSessionId.value != null && sessionStartMs > 0 && !isPaused.value) {
                    _elapsedSeconds.value =
                        (System.currentTimeMillis() - sessionStartMs - pausedAccumMs) / 1000
                    currentZone.value?.let { z ->
                        _timeInZone.update { map -> map + (z to (map.getOrDefault(z, 0L) + 1L)) }
                    }
                }
                delay(1_000)
            }
        }
        viewModelScope.launch {
            combine(activeSessionId, isPaused, sessionRepository.activeSession) { id, paused, session ->
                Triple(id, paused, session)
            }.collect { (id, paused, session) ->
                if (paused) {
                    pauseStartedMs = System.currentTimeMillis()
                } else if (pauseStartedMs > 0) {
                    // Resume event: reload samples and seed timeInZone from persisted data
                    pausedAccumMs += System.currentTimeMillis() - pauseStartedMs
                    pauseStartedMs = 0L

                    if (id != null && session != null) {
                        val samples = sessionRepository.getSamplesForSession(id)
                        val zones = HrZoneCalculator.calculateZones(session.maxHrUsed, session.restingHr)
                        _timeInZone.value = HrZoneCalculator.aggregateTimeInZone(samples, zones)
                    }
                }
            }
        }
        viewModelScope.launch {
            activeSessionId.collect { id ->
                if (id != null && sessionStartMs == 0L) {
                    sessionStartMs = sessionRepository.activeSession.first()?.startedAt
                        ?: System.currentTimeMillis()
                }
                if (id == null) {
                    sessionStartMs = 0L
                    _elapsedSeconds.value = 0
                    _timeInZone.value = emptyMap()
                    pausedAccumMs = 0L
                    pauseStartedMs = 0L
                }
            }
        }
        if (_hrvCountdown.value != null) {
            viewModelScope.launch {
                activeSessionId.first { it != null }
                while ((_hrvCountdown.value ?: 0) > 0) {
                    delay(1_000)
                    if (!isPaused.value) _hrvCountdown.update { it?.minus(1) }
                }
            }
        }
        viewModelScope.launch {
            var previousSessionId: Long? = null
            activeSessionId.collect { id ->
                if (previousSessionId != null && id == null) {
                    // Session wurde gerade beendet — persistiere Milestones
                    val milestonesList = _milestones.value
                    if (milestonesList.isNotEmpty()) {
                        val entities = milestonesList.mapIndexed { idx, seconds ->
                            Milestone(
                                sessionId = previousSessionId!!,
                                atSeconds = seconds,
                                label = "M${idx + 1}"
                            )
                        }
                        // ponytail: NonCancellable, sonst gehen Milestones verloren wenn
                        // die VM während des Inserts bei Session-Ende zerstört wird.
                        withContext(NonCancellable) {
                            try {
                                milestoneDao.insertAll(entities)
                                Log.d("HRTracker", "Milestones persistiert: ${entities.size}")
                            } catch (e: Exception) {
                                Log.e("HRTracker", "Milestone-Persistierung fehlgeschlagen", e)
                            }
                        }
                    }
                    _milestones.value = emptyList()
                }
                previousSessionId = id
            }
        }
    }
}
