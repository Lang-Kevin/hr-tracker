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

    // ponytail: cache prevents new StateFlow (and false initial flash) on every recomposition
    private val cache = HashMap<String, StateFlow<Boolean?>>()

    fun seenState(screen: String): StateFlow<Boolean?> =
        cache.getOrPut(screen) {
            settingsRepository.tutorialSeenScreens
                .map { screen in it }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)
        }

    fun markSeen(screen: String) {
        viewModelScope.launch { settingsRepository.markTutorialSeen(screen) }
    }
}
