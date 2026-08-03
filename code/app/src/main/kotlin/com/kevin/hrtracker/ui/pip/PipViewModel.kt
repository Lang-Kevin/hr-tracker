package com.kevin.hrtracker.ui.pip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.hrtracker.ble.HrBleManager
import com.kevin.hrtracker.data.repository.SessionRepository
import com.kevin.hrtracker.data.repository.SettingsRepository
import com.kevin.hrtracker.domain.HrSource
import com.kevin.hrtracker.domain.WidgetVariant
import com.kevin.hrtracker.domain.ZoneBounds
import com.kevin.hrtracker.wearable.WearableHrSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import javax.inject.Inject

private data class PipSettings(
    val zones: List<ZoneBounds>,
    val variant: WidgetVariant
)

@HiltViewModel
class PipViewModel @Inject constructor(
    bleManager: HrBleManager,
    sessionRepository: SessionRepository,
    settingsRepository: SettingsRepository,
    wearableHrSource: WearableHrSource
) : ViewModel() {

    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    private val bpm: Flow<Int?> = settingsRepository.userSettings
        .flatMapLatest { s ->
            if (s.hrSource == HrSource.WATCH) wearableHrSource.lastHr else bleManager.lastHr
        }
        .map { it?.bpm }

    private val pipSettings: Flow<PipSettings> = settingsRepository.userSettings
        .map { PipSettings(it.effectiveZones, it.widgetVariant) }
        .distinctUntilChanged()

    val uiState: StateFlow<PipUiState> = combine(
        bpm,
        pipSettings,
        sessionRepository.activeSession.map { it?.startedAt },
        sessionRepository.isPaused,
        ticker
    ) { currentBpm, settings, startedAt, paused, now ->
        buildPipUiState(currentBpm, settings.zones, startedAt, now, paused, settings.variant)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PipUiState(
            bpm = null,
            zone = null,
            elapsedText = "00:00:00",
            paused = false,
            variant = WidgetVariant.STANDARD
        )
    )
}
