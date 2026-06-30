package com.kevin.hrtracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.hrtracker.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val onboardingDone: StateFlow<Boolean> = settingsRepository.onboardingDone
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun complete(age: Int, restingHr: Int?) {
        viewModelScope.launch {
            settingsRepository.setAge(age)
            if (restingHr != null) settingsRepository.setRestingHr(restingHr)
            settingsRepository.setOnboardingDone(true)
        }
    }

    fun skip() {
        viewModelScope.launch {
            settingsRepository.setOnboardingDone(true)
        }
    }
}
