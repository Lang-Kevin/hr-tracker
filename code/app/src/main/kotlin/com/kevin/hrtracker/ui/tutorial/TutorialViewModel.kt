package com.kevin.hrtracker.ui.tutorial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.hrtracker.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TutorialViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    fun seenState(screen: String): StateFlow<Boolean> =
        settingsRepository.tutorialSeenScreens
            .map { screen in it }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun markSeen(screen: String) {
        viewModelScope.launch { settingsRepository.markTutorialSeen(screen) }
    }
}
