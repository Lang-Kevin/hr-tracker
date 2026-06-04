package com.kevin.hrtracker.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    object Ready : ConnectionState()
    object Reconnecting : ConnectionState()
}

@Singleton
class HrBleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "HRTracker"
        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
        val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        // Backoff delays between reconnect attempts: 3s, 5s, 10s, 30s
        private val RECONNECT_DELAYS_MS = listOf(3_000L, 5_000L, 10_000L, 30_000L)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _hrSamples = MutableSharedFlow<ParsedHr>(replay = 0, extraBufferCapacity = 64)
    val hrSamples: SharedFlow<ParsedHr> = _hrSamples.asSharedFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null
    private var lastDevice: BluetoothDevice? = null
    private var reconnectEnabled = false
    private var reconnectJob: Job? = null

    @SuppressLint("MissingPermission")
    fun startScan() {
        _scanResults.value = emptyList()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val scanner = adapter.bluetoothLeScanner ?: run {
            Log.e(TAG, "BLE scanner not available — Bluetooth off?")
            return
        }
        // No service UUID filter: many devices (incl. moofit HR8) only include 0x180D
        // in the scan response, not the primary advertisement — a filter would miss them.
        // We accept all BLE devices and let the user pick; onServicesDiscovered validates.
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val current = _scanResults.value
                if (current.none { it.device.address == result.device.address }) {
                    _scanResults.value = current + result
                }
            }
            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: errorCode=$errorCode")
            }
        }
        scanCallback = cb
        scanner.startScan(emptyList(), settings, cb)
        Log.d(TAG, "BLE scan started (no filter — showing all devices)")
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        scanCallback?.let { adapter.bluetoothLeScanner?.stopScan(it) }
        scanCallback = null
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        // Close any existing GATT before opening a new one —
        // leaving it open causes duplicate onCharacteristicChanged callbacks.
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        lastDevice = device
        reconnectEnabled = true
        reconnectJob?.cancel()
        _connectionState.value = ConnectionState.Connecting
        bluetoothGatt = device.connectGatt(
            context, false, gattCallback, BluetoothDevice.TRANSPORT_LE
        )
        Log.d(TAG, "Connecting to ${device.address}")
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        reconnectEnabled = false
        reconnectJob?.cancel()
        reconnectJob = null
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = ConnectionState.Disconnected
        Log.d(TAG, "Disconnected (user-initiated)")
    }

    @SuppressLint("MissingPermission")
    private fun scheduleReconnect() {
        val device = lastDevice ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            for ((attempt, delayMs) in RECONNECT_DELAYS_MS.withIndex()) {
                if (!reconnectEnabled) break
                Log.d(TAG, "Reconnect attempt ${attempt + 1}/${RECONNECT_DELAYS_MS.size} in ${delayMs}ms")
                _connectionState.value = ConnectionState.Reconnecting
                delay(delayMs)
                if (!reconnectEnabled) break
                bluetoothGatt?.close()
                bluetoothGatt = device.connectGatt(
                    context, false, gattCallback, BluetoothDevice.TRANSPORT_LE
                )
                // Wait up to 8s for the connection to succeed before trying again
                var waited = 0
                while (waited < 8_000 && reconnectEnabled &&
                    _connectionState.value is ConnectionState.Reconnecting
                ) {
                    delay(500)
                    waited += 500
                }
                if (_connectionState.value is ConnectionState.Ready) {
                    Log.d(TAG, "Reconnected successfully after attempt ${attempt + 1}")
                    return@launch
                }
            }
            if (_connectionState.value !is ConnectionState.Ready) {
                Log.w(TAG, "Reconnect failed after all attempts — giving up")
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "GATT connected — discovering services")
                    _connectionState.value = ConnectionState.Connected
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "GATT disconnected (status=$status)")
                    gatt.close()
                    bluetoothGatt = null
                    if (reconnectEnabled) {
                        Log.d(TAG, "Unexpected disconnect — scheduling reconnect")
                        scheduleReconnect()
                    } else {
                        _connectionState.value = ConnectionState.Disconnected
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                return
            }
            val allServices = gatt.services.map { it.uuid.toString().uppercase().take(8) }
            Log.d(TAG, "Services discovered: $allServices")
            val hrChar = gatt.getService(HR_SERVICE_UUID)
                ?.getCharacteristic(HR_MEASUREMENT_UUID)
            if (hrChar == null) {
                Log.e(TAG, "HR Measurement characteristic (0x2A37) not found")
                return
            }
            gatt.setCharacteristicNotification(hrChar, true)

            val cccd = hrChar.getDescriptor(CCCD_UUID)
            if (cccd == null) {
                Log.e(TAG, "CCCD descriptor (0x2902) not found")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
            }
            Log.d(TAG, "CCCD written — HR notifications enabled")
            _connectionState.value = ConnectionState.Ready
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int
        ) {
            Log.d(TAG, "Descriptor write ${descriptor.uuid} status=$status")
        }

        // API 33+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == HR_MEASUREMENT_UUID) handleHrData(value)
        }

        // API < 33
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                && characteristic.uuid == HR_MEASUREMENT_UUID
            ) {
                handleHrData(characteristic.value ?: return)
            }
        }
    }

    private fun handleHrData(value: ByteArray) {
        val parsed = HeartRateParser.parse(value) ?: return
        Log.d(TAG, "BPM=${parsed.bpm}  RR=${parsed.rrIntervalsMs}")
        scope.launch { _hrSamples.emit(parsed) }
    }
}
