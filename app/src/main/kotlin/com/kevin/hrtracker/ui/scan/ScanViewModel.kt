package com.kevin.hrtracker.ui.scan

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.hrtracker.ble.ConnectionState
import com.kevin.hrtracker.ble.HrBleManager
import com.kevin.hrtracker.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val bleManager: HrBleManager,
    private val sessionRepository: SessionRepository
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

    // Placeholder values for M2 — real settings in M5
    fun startSession() = viewModelScope.launch {
        sessionRepository.startSession(label = "Training", maxHrUsed = 187, restingHr = null)
    }

    fun stopSession() = viewModelScope.launch {
        sessionRepository.stopSession()
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.stopScan()
    }
}
