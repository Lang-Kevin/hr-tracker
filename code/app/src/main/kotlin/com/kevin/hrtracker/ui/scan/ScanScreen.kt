package com.kevin.hrtracker.ui.scan

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.kevin.hrtracker.R
import com.kevin.hrtracker.domain.HrvPosture
import com.kevin.hrtracker.ui.tutorial.TutorialViewModel
import com.kevin.shared.ble.ConnectionState
import com.kevin.shared.domain.DeviceType
import com.kevin.shared.domain.DiscoveredDevice
import com.kevin.shared.ui.LabelPickerDialog
import com.kevin.shared.ui.components.PrimaryButton
import com.kevin.shared.ui.components.SecondaryButton
import com.kevin.shared.ui.components.SegmentedControl
import com.kevin.shared.ui.components.TextActionButton
import com.kevin.shared.ui.dialog.AppDialog
import com.kevin.shared.ui.overlay.PermissionGate
import com.kevin.shared.ui.overlay.PermissionGateMode
import com.kevin.shared.ui.overlay.TutorialSpotlight
import com.kevin.shared.ui.overlay.TutorialStep
import com.kevin.shared.ui.overlay.openAppSettings
import com.kevin.shared.ui.overlay.permissionGateMode
import com.kevin.shared.ui.overlay.rememberTutorialAnchors
import com.kevin.shared.ui.overlay.tutorialAnchor
import com.kevin.shared.ui.scan.DiscoveredDeviceItem
import com.kevin.shared.ui.scan.SavedDeviceItem
import com.kevin.shared.ui.theme.AppTheme
import java.util.Locale
import com.kevin.shared.R as SharedR

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(
    viewModel: ScanViewModel = hiltViewModel(),
    onSessionStarted: (label: String) -> Unit = {},
    onHrvSessionStarted: (seconds: Int, posture: HrvPosture) -> Unit = { _, _ -> },
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val savedDevices by viewModel.savedDevices.collectAsStateWithLifecycle()
    val autoConnect by viewModel.autoConnect.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val trainingLabels by viewModel.trainingLabels.collectAsStateWithLifecycle()
    val deviceName by viewModel.connectedDeviceName.collectAsStateWithLifecycle()
    val batteryLevel by viewModel.batteryLevel.collectAsStateWithLifecycle()

    var showStartDialog by remember { mutableStateOf(false) }
    var showHrvDialog by remember { mutableStateOf(false) }
    var isStarting by remember { mutableStateOf(false) }
    // Reset isStarting once session is confirmed active
    LaunchedEffect(activeSessionId) { if (activeSessionId != null) isStarting = false }

    if (showStartDialog) {
        LabelPickerDialog(
            title = stringResource(R.string.scan_label_picker_title),
            items = trainingLabels,
            initialSelection = null,
            confirmText = stringResource(R.string.scan_label_picker_confirm),
            onConfirm = { label ->
                isStarting = true
                onSessionStarted(label)
                showStartDialog = false
            },
            onDismiss = { showStartDialog = false },
            dismissText = null,
            addFieldLabel = stringResource(R.string.scan_label_picker_add),
            onAdd = viewModel::addTrainingLabel,
            onDelete = null
        )
    }
    if (showHrvDialog) {
        HrvDurationDialog(
            onSelect = { seconds, posture ->
                showHrvDialog = false
                isStarting = true
                onHrvSessionStarted(seconds, posture)
            },
            onDismiss = { showHrvDialog = false }
        )
    }
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    var requestedOnce by rememberSaveable { mutableStateOf(false) }
    val permissionState = rememberMultiplePermissionsState(requiredPermissions) { requestedOnce = true }
    val context = LocalContext.current
    val locale = Locale.getDefault()

    LaunchedEffect(permissionState.allPermissionsGranted) {
        if (permissionState.allPermissionsGranted) viewModel.startScan()
    }

    // POST_NOTIFICATIONS: requested after BLE is granted (no double dialog). Asked once; after a denial
    // not again (user can enable in system settings). Not gating — the recording works without it,
    // only the FGS notification stays hidden.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notifPermission = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        LaunchedEffect(permissionState.allPermissionsGranted) {
            if (permissionState.allPermissionsGranted && notifPermission.status != PermissionStatus.Granted && !notifPermission.status.shouldShowRationale) {
                notifPermission.launchPermissionRequest()
            }
        }
    }

    val tutorialViewModel: TutorialViewModel = hiltViewModel()
    val tutorialAnchors = rememberTutorialAnchors()
    val tutorialSeen by tutorialViewModel.seenState("scan").collectAsStateWithLifecycle()

    val state = connectionState
    val statusText = when (state) {
        is ConnectionState.Disconnected -> stringResource(SharedR.string.shared_ble_status_disconnected)
        is ConnectionState.Connecting -> stringResource(SharedR.string.shared_ble_status_connecting)
        is ConnectionState.Connected -> stringResource(SharedR.string.shared_ble_status_connected)
        is ConnectionState.Ready -> stringResource(SharedR.string.shared_ble_status_ready)
        is ConnectionState.Reconnecting -> stringResource(SharedR.string.shared_ble_status_reconnecting)
        is ConnectionState.Error -> stringResource(SharedR.string.shared_ble_status_error, state.reason)
    }
    val mode = permissionGateMode(permissionState.allPermissionsGranted, permissionState.shouldShowRationale, requestedOnce)

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextActionButton(text = stringResource(R.string.scan_history), onClick = onNavigateToHistory)
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.tutorialAnchor(tutorialAnchors, "scan_settings")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.scan_settings_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Box(Modifier.tutorialAnchor(tutorialAnchors, "scan_status")) {
                DeviceStatusCard(
                    statusText = statusText,
                    ready = state is ConnectionState.Ready,
                    connected = state !is ConnectionState.Disconnected,
                    deviceName = deviceName,
                    batteryLevel = batteryLevel,
                    autoConnect = autoConnect,
                    onDisconnect = { viewModel.disconnect() },
                    onToggleAutoConnect = { viewModel.toggleAutoConnect() }
                )
            }

            PermissionGate(
                granted = mode == PermissionGateMode.Granted,
                title = stringResource(R.string.scan_permission_title),
                message = stringResource(R.string.scan_permission_message),
                actionLabel = stringResource(
                    if (mode == PermissionGateMode.OpenSettings) R.string.scan_permission_settings else R.string.scan_permission_grant
                ),
                onAction = {
                    if (mode == PermissionGateMode.OpenSettings) openAppSettings(context)
                    else { requestedOnce = true; permissionState.launchMultiplePermissionRequest() }
                },
                modifier = Modifier.weight(1f),
            ) {
                if (state is ConnectionState.Ready) {
                    if (activeSessionId == null) {
                        PrimaryButton(
                            text = if (isStarting) stringResource(R.string.scan_starting) else stringResource(R.string.scan_start_workout),
                            onClick = { if (!isStarting) showStartDialog = true },
                            enabled = !isStarting,
                            modifier = Modifier.fillMaxWidth()
                        )
                        SecondaryButton(
                            text = stringResource(R.string.scan_measure_hrv),
                            onClick = { if (!isStarting) showHrvDialog = true },
                            enabled = !isStarting,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                if (savedDevices.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.scan_saved_devices), locale)
                    savedDevices.forEach { device ->
                        SavedDeviceItem(
                            device = device,
                            onClick = { viewModel.connectToSaved(device) },
                            onForget = { viewModel.forgetDevice(device) }
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                SectionLabel(stringResource(R.string.scan_available_devices), locale)
                SecondaryButton(
                    text = if (isScanning) stringResource(R.string.scan_searching) else stringResource(R.string.scan_start_search),
                    onClick = { viewModel.startScan() },
                    enabled = !isScanning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tutorialAnchor(tutorialAnchors, "scan_start")
                )
                if (isScanning && discoveredDevices.none { it is DiscoveredDevice.Real }) {
                    Text(
                        stringResource(R.string.scan_scan_running),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val simulatedDeviceText = stringResource(R.string.scan_device_simulated)
                val chestStrapText = stringResource(R.string.scan_device_chest_strap)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
                    items(discoveredDevices, key = { it.address }) { device ->
                        DiscoveredDeviceItem(
                            device = device,
                            onClick = { viewModel.connectToDiscovered(device) },
                            iconAndSubtitle = { d ->
                                when {
                                    d is DiscoveredDevice.Fake -> Icons.Default.Bluetooth to simulatedDeviceText
                                    d.deviceType == DeviceType.CHEST_STRAP -> Icons.Default.Favorite to chestStrapText
                                    else -> Icons.Default.Bluetooth to d.address
                                }
                            }
                        )
                    }
                }
            }
        }

        TutorialSpotlight(
            steps = listOf(
                TutorialStep("scan_status", stringResource(R.string.scan_tutorial_status_title), stringResource(R.string.scan_tutorial_status_text)),
                TutorialStep("scan_start", stringResource(R.string.scan_tutorial_search_title), stringResource(R.string.scan_tutorial_search_text)),
                TutorialStep("scan_settings", stringResource(R.string.scan_tutorial_settings_title), stringResource(R.string.scan_tutorial_settings_text))
            ),
            anchors = tutorialAnchors,
            visible = tutorialSeen == false,
            onFinish = { tutorialViewModel.markSeen("scan") },
            skipLabel = stringResource(R.string.common_tutorial_skip),
            nextLabel = { n, m -> stringResource(R.string.common_tutorial_next_progress, n, m) },
            doneLabel = stringResource(R.string.common_tutorial_done),
        )
    }
}

@Composable
private fun SectionLabel(text: String, locale: Locale) {
    Text(
        text.uppercase(locale),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = AppTheme.spacing.xs)
    )
}

@Composable
private fun HrvDurationDialog(onSelect: (Int, HrvPosture) -> Unit, onDismiss: () -> Unit) {
    val postures = listOf(
        HrvPosture.LYING to R.string.scan_hrv_posture_lying,
        HrvPosture.SITTING to R.string.scan_hrv_posture_sitting,
        HrvPosture.STANDING to R.string.scan_hrv_posture_standing,
    )
    var selected by rememberSaveable { mutableIntStateOf(0) }
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.scan_hrv_dialog_title),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
                Text(stringResource(R.string.scan_hrv_posture_label), style = MaterialTheme.typography.bodyMedium)
                SegmentedControl(
                    options = postures.map { stringResource(it.second) },
                    selectedIndex = selected,
                    onSelect = { selected = it },
                )
                Text(
                    stringResource(R.string.scan_hrv_posture_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            PrimaryButton(
                text = stringResource(R.string.scan_hrv_option, stringResource(R.string.scan_hrv_full), stringResource(R.string.scan_hrv_minutes, 5)),
                onClick = { onSelect(300, postures[selected].first) },
                modifier = Modifier.fillMaxWidth()
            )
            TextActionButton(text = stringResource(R.string.scan_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    )
}
