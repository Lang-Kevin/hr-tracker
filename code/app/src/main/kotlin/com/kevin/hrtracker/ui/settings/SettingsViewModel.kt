package com.kevin.hrtracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.hrtracker.data.repository.SessionRepository
import com.kevin.hrtracker.data.repository.SettingsRepository
import com.kevin.hrtracker.domain.UserSettings
import com.kevin.hrtracker.domain.WidgetVariant
import com.kevin.hrtracker.domain.ZoneBounds
import com.kevin.hrtracker.domain.ZoneModel
import com.kevin.hrtracker.domain.Sex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.userSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    // ponytail: 90-Tage-Fenster, damit alte Fehlmessungen/veraltete Fitness nicht ewig nachwirken.
    private val observedWindowMs = 90L * 24 * 60 * 60 * 1000
    val observedMaxHr: StateFlow<Int?> =
        sessionRepository.observedMaxBpm(System.currentTimeMillis() - observedWindowMs)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val savedDeviceAddress: StateFlow<String?> = settingsRepository.savedDeviceAddress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val debugMode: StateFlow<Boolean> = settingsRepository.debugMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setDebugMode(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDebugMode(enabled) }

    fun setChartDynamicScale(enabled: Boolean) = viewModelScope.launch { settingsRepository.setChartDynamicScale(enabled) }

    fun setWidgetVariant(variant: WidgetVariant) = viewModelScope.launch { settingsRepository.setWidgetVariant(variant) }

    fun clearSavedDevice() = viewModelScope.launch { settingsRepository.clearSavedDevice() }

    fun setAge(age: Int) = viewModelScope.launch { settingsRepository.setAge(age) }

    fun setManualMaxHr(maxHr: Int?) = viewModelScope.launch {
        settingsRepository.setManualMaxHr(maxHr)
    }

    fun setRestingHr(restingHr: Int?) = viewModelScope.launch {
        settingsRepository.setRestingHr(restingHr)
    }

    fun setWeightKg(weightKg: Int?) = viewModelScope.launch {
        settingsRepository.setWeightKg(weightKg)
    }

    fun setSex(sex: Sex?) = viewModelScope.launch { settingsRepository.setSex(sex) }

    fun setZoneModel(model: ZoneModel) = viewModelScope.launch { settingsRepository.setZoneModel(model) }

    fun setTargetZone(zone: Int) = viewModelScope.launch { settingsRepository.setTargetZone(zone) }

    fun setCustomZones(zones: List<ZoneBounds>?) = viewModelScope.launch {
        settingsRepository.setCustomZones(zones)
    }
}
