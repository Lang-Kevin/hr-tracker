package com.kevin.hrtracker.ui.scan

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.hrtracker.ble.ConnectionState
import com.kevin.hrtracker.ble.HrBleManager
import com.kevin.hrtracker.data.repository.SessionRepository
import com.kevin.hrtracker.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val bleManager: HrBleManager,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val scanResults: StateFlow<List<ScanResult>> = bleManager.scanResults
    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val activeSessionId: StateFlow<Long?> = sessionRepository.activeSessionId

    @SuppressLint("MissingPermission")
    fun startScan() = bleManager.startScan()

    fun stopScan() = bleManager.stopScan()

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        bleManager.stopScan()
        bleManager.connect(device)
    }

    fun startSession(label: String = "Training") = viewModelScope.launch {
        val s = settingsRepository.userSettings.first()
        sessionRepository.startSession(label, maxHrUsed = s.maxHrUsed, restingHr = s.restingHr)
    }

    fun stopSession() = viewModelScope.launch { sessionRepository.stopSession() }

    override fun onCleared() {
        super.onCleared()
        bleManager.stopScan()
    }
}
