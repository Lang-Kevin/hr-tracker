package com.kevin.hrtracker.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.hrtracker.ble.ConnectionState
import com.kevin.hrtracker.ble.HrBleManager
import com.kevin.hrtracker.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val bleManager: HrBleManager,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val activeSessionId: StateFlow<Long?> = sessionRepository.activeSessionId

    private val _currentBpm = MutableStateFlow<Int?>(null)
    val currentBpm: StateFlow<Int?> = _currentBpm.asStateFlow()

    private val _bpmHistory = MutableStateFlow<List<Int>>(emptyList())
    val bpmHistory: StateFlow<List<Int>> = _bpmHistory.asStateFlow()

    // elapsed seconds since session start
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private var sessionStartMs: Long = 0L

    init {
        viewModelScope.launch {
            bleManager.hrSamples.collect { parsed ->
                _currentBpm.value = parsed.bpm
                _bpmHistory.value = (_bpmHistory.value + parsed.bpm).takeLast(120)
            }
        }
        viewModelScope.launch {
            while (true) {
                if (activeSessionId.value != null && sessionStartMs > 0) {
                    _elapsedSeconds.value = (System.currentTimeMillis() - sessionStartMs) / 1000
                }
                delay(1_000)
            }
        }
        viewModelScope.launch {
            activeSessionId.collect { id ->
                if (id != null && sessionStartMs == 0L) sessionStartMs = System.currentTimeMillis()
                if (id == null) { sessionStartMs = 0L; _elapsedSeconds.value = 0 }
            }
        }
    }
}
