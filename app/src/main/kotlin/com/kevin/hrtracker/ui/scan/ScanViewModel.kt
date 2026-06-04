package com.kevin.hrtracker.ui.scan

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.lifecycle.ViewModel
import com.kevin.hrtracker.ble.ConnectionState
import com.kevin.hrtracker.ble.HrBleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val bleManager: HrBleManager
) : ViewModel() {

    val scanResults: StateFlow<List<ScanResult>> = bleManager.scanResults
    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState

    @SuppressLint("MissingPermission")
    fun startScan() = bleManager.startScan()

    fun stopScan() = bleManager.stopScan()

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        bleManager.stopScan()
        bleManager.connect(device)
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.stopScan()
    }
}
