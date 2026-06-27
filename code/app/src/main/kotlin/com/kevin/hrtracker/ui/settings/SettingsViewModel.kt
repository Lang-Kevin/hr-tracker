package com.kevin.hrtracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.hrtracker.data.repository.SettingsRepository
import com.kevin.hrtracker.domain.HrSource
import com.kevin.hrtracker.domain.UserSettings
import com.kevin.hrtracker.domain.ZoneBounds
import com.kevin.hrtracker.health.HealthConnectManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HealthImportStatus { IDLE, LOADING, SUCCESS, NO_DATA }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.userSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    val savedDeviceAddress: StateFlow<String?> = settingsRepository.savedDeviceAddress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _healthImportStatus = MutableStateFlow(HealthImportStatus.IDLE)
    val healthImportStatus: StateFlow<HealthImportStatus> = _healthImportStatus.asStateFlow()

    val isHealthConnectAvailable: Boolean = healthConnectManager.isAvailable

    val debugMode: StateFlow<Boolean> = settingsRepository.debugMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setDebugMode(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDebugMode(enabled) }

    fun clearSavedDevice() = viewModelScope.launch { settingsRepository.clearSavedDevice() }

    fun setAge(age: Int) = viewModelScope.launch { settingsRepository.setAge(age) }

    fun setManualMaxHr(maxHr: Int?) = viewModelScope.launch {
        settingsRepository.setManualMaxHr(maxHr)
    }

    fun setRestingHr(restingHr: Int?) = viewModelScope.launch {
        settingsRepository.setRestingHr(restingHr)
    }

    fun setTargetZone(zone: Int) = viewModelScope.launch { settingsRepository.setTargetZone(zone) }

    fun setHrSource(source: HrSource) = viewModelScope.launch { settingsRepository.setHrSource(source) }

    fun setCustomZones(zones: List<ZoneBounds>?) = viewModelScope.launch {
        settingsRepository.setCustomZones(zones)
    }

    fun importRestingHrFromHealthConnect() = viewModelScope.launch {
        _healthImportStatus.value = HealthImportStatus.LOADING
        val hr = healthConnectManager.readLatestRestingHr()
        if (hr != null) {
            settingsRepository.setRestingHr(hr)
            _healthImportStatus.value = HealthImportStatus.SUCCESS
        } else {
            _healthImportStatus.value = HealthImportStatus.NO_DATA
        }
    }
}
