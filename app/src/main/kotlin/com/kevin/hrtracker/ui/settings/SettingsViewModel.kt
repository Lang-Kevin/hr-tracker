package com.kevin.hrtracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.hrtracker.data.repository.SettingsRepository
import com.kevin.hrtracker.domain.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.userSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun setAge(age: Int) = viewModelScope.launch { settingsRepository.setAge(age) }

    fun setManualMaxHr(maxHr: Int?) = viewModelScope.launch {
        settingsRepository.setManualMaxHr(maxHr)
    }

    fun setRestingHr(restingHr: Int?) = viewModelScope.launch {
        settingsRepository.setRestingHr(restingHr)
    }
}
