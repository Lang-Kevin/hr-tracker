package com.kevin.hrtracker.ui.scan

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.kevin.hrtracker.ble.ConnectionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(
    viewModel: ScanViewModel = hiltViewModel(),
    onSessionStarted: (label: String) -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val scanResults by viewModel.scanResults.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()

    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permissionState = rememberMultiplePermissionsState(requiredPermissions)

    LaunchedEffect(permissionState.allPermissionsGranted) {
        if (permissionState.allPermissionsGranted) viewModel.startScan()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("HR Tracker", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onNavigateToHistory) { Text("Verlauf") }
            TextButton(onClick = onNavigateToSettings) { Text("⚙") }
        }
        Text("Status: ${connectionState::class.simpleName}", style = MaterialTheme.typography.bodyMedium)

        if (!permissionState.allPermissionsGranted) {
            Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                Text("Bluetooth-Berechtigung gewähren")
            }
        } else {
            if (connectionState is ConnectionState.Ready) {
                Divider()
                if (activeSessionId == null) {
                    Button(
                        onClick = {
                            viewModel.startSession()
                            onSessionStarted("Training")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Session starten")
                    }
                } else {
                    Button(
                        onClick = { viewModel.stopSession() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Session stoppen  (ID: $activeSessionId)")
                    }
                }
                Divider()
            }

            Text("HR-Geräte (0x180D):", style = MaterialTheme.typography.titleSmall)
            if (scanResults.isEmpty()) {
                Text("Scan läuft…", style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(scanResults, key = { it.device.address }) { result ->
                    DeviceItem(result = result) { viewModel.connect(result.device) }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceItem(result: ScanResult, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                result.device.name ?: "Unbekanntes Gerät",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(result.device.address, style = MaterialTheme.typography.bodySmall)
        }
    }
}
