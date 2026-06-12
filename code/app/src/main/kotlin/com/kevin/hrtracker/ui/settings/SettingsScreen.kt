package com.kevin.hrtracker.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import com.kevin.hrtracker.domain.HrSource
import com.kevin.hrtracker.domain.HrZoneCalculator
import com.kevin.hrtracker.ui.theme.ZoneColors

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val healthImportStatus by viewModel.healthImportStatus.collectAsStateWithLifecycle()
    val hcPermissions = remember { setOf(HealthPermission.getReadPermission(HeartRateRecord::class)) }
    val requestHcPermissions = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(hcPermissions)) viewModel.importRestingHrFromHealthConnect()
    }

    var ageText by remember(settings.age) { mutableStateOf(settings.age.toString()) }
    var manualMaxHrText by remember(settings.manualMaxHr) {
        mutableStateOf(settings.manualMaxHr?.toString() ?: "")
    }
    var restingHrText by remember(settings.restingHr) {
        mutableStateOf(settings.restingHr?.toString() ?: "")
    }

    val ageError = ageText.toIntOrNull()?.let { it !in 10..99 } ?: ageText.isNotEmpty()
    val maxHrError = manualMaxHrText.isNotEmpty() &&
        (manualMaxHrText.toIntOrNull()?.let { it !in 100..250 } ?: true)
    val restingHrError = restingHrText.isNotEmpty() &&
        (restingHrText.toIntOrNull()?.let { it !in 20..100 } ?: true)

    val tanakaMaxHr = HrZoneCalculator.tanakaMaxHr(settings.age)
    val effectiveMaxHr = settings.maxHrUsed
    val model = if (settings.restingHr != null) "Karvonen (HRR)" else "%HRmax"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück") }
            Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Herzfrequenz", style = MaterialTheme.typography.titleMedium)

                NumberField(
                    label = "Alter",
                    value = ageText,
                    onValueChange = { ageText = it },
                    onDone = { it.toIntOrNull()?.let { v -> if (v in 10..99) viewModel.setAge(v) } },
                    isError = ageError,
                    supportingText = if (ageError) "Alter muss zwischen 10 und 99 liegen" else null
                )
                Text(
                    "Tanaka HRmax: $tanakaMaxHr  •  Aktiv: $effectiveMaxHr BPM",
                    style = MaterialTheme.typography.bodySmall
                )

                NumberField(
                    label = "Manueller HRmax (leer = Tanaka)",
                    value = manualMaxHrText,
                    onValueChange = { manualMaxHrText = it },
                    onDone = {
                        viewModel.setManualMaxHr(it.toIntOrNull()?.takeIf { v -> v in 100..250 })
                    },
                    isError = maxHrError,
                    supportingText = if (maxHrError) "HRmax muss zwischen 100 und 250 liegen" else null
                )

                NumberField(
                    label = "Ruhepuls (leer = %HRmax-Modell)",
                    value = restingHrText,
                    onValueChange = { restingHrText = it },
                    onDone = {
                        viewModel.setRestingHr(it.toIntOrNull()?.takeIf { v -> v in 20..100 })
                    },
                    isError = restingHrError,
                    supportingText = if (restingHrError) "Ruhepuls muss zwischen 20 und 100 liegen" else null
                )
                if (viewModel.isHealthConnectAvailable) {
                    OutlinedButton(
                        onClick = { requestHcPermissions.launch(hcPermissions) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = healthImportStatus != HealthImportStatus.LOADING
                    ) {
                        if (healthImportStatus == HealthImportStatus.LOADING) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Von Smartwatch importieren")
                    }
                    if (healthImportStatus == HealthImportStatus.NO_DATA) {
                        Text(
                            "Kein Ruhepuls in Health Connect gefunden",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Text("Zonen-Modell: $model", style = MaterialTheme.typography.bodySmall)

                HorizontalDivider()
                Text("Ziel-Zone", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { z ->
                        val selected = settings.targetZone == z
                        val zoneColor = ZoneColors.getOrElse(z - 1) { MaterialTheme.colorScheme.primary }
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setTargetZone(z) },
                            label = { Text("Z$z") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = zoneColor,
                                selectedLabelColor = androidx.compose.ui.graphics.Color.White
                            )
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Zonen-Vorschau", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                val zones = com.kevin.hrtracker.domain.HrZoneCalculator
                    .calculateZones(effectiveMaxHr, settings.restingHr)
                zones.forEach { z ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val zoneColor = ZoneColors.getOrElse(z.zone - 1) { MaterialTheme.colorScheme.primary }
                            Surface(
                                color = zoneColor,
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.size(12.dp)
                            ) {}
                            Text("Z${z.zone}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("${z.lo} – ${z.hi} BPM", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        ZoneErklarungCard()

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("HR-Quelle", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Herzfrequenzquelle für Aufzeichnungen",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.hrSource == HrSource.BLE,
                        onClick = { viewModel.setHrSource(HrSource.BLE) },
                        label = { Text("BLE-Sensor") }
                    )
                    FilterChip(
                        selected = settings.hrSource == HrSource.WATCH,
                        onClick = { viewModel.setHrSource(HrSource.WATCH) },
                        label = { Text("Galaxy Watch") }
                    )
                }
                if (settings.hrSource == HrSource.WATCH) {
                    Text(
                        "Watch-Aufnahmen haben keine RR-Daten — HRV zeigt \"–\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private val ZONE_DESCRIPTIONS = listOf(
    1 to "Regeneration — sehr leichte Belastung, aktive Erholung",
    2 to "Fettverbrennung — lockeres Tempo, aerobe Basis",
    3 to "Aerob — moderates Training, Ausdaueraufbau",
    4 to "Anaerob — intensive Belastung, Laktatschwelle",
    5 to "VO₂max — maximale Intensität, kurze Intervalle"
)

@Composable
private fun ZoneErklarungCard() {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Was bedeuten die Zonen?", style = MaterialTheme.typography.titleMedium)
                Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.bodySmall)
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider()
                    ZONE_DESCRIPTIONS.forEach { (z, desc) ->
                        val zoneColor = ZoneColors.getOrElse(z - 1) { MaterialTheme.colorScheme.primary }
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = zoneColor,
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "Z$z",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = androidx.compose.ui.graphics.Color.White
                                    )
                                }
                            }
                            Text(desc, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDone: (String) -> Unit,
    isError: Boolean = false,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        isError = isError,
        supportingText = supportingText?.let { msg -> { Text(msg, color = MaterialTheme.colorScheme.error) } },
        trailingIcon = {
            TextButton(onClick = { onDone(value) }) { Text("OK") }
        }
    )
}
