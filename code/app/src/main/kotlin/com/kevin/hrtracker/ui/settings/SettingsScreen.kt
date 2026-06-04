package com.kevin.hrtracker.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.hrtracker.domain.HrZoneCalculator

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val savedDeviceAddress by viewModel.savedDeviceAddress.collectAsStateWithLifecycle()

    var ageText by remember(settings.age) { mutableStateOf(settings.age.toString()) }
    var manualMaxHrText by remember(settings.manualMaxHr) {
        mutableStateOf(settings.manualMaxHr?.toString() ?: "")
    }
    var restingHrText by remember(settings.restingHr) {
        mutableStateOf(settings.restingHr?.toString() ?: "")
    }

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
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück") }
            Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Gespeichertes Gerät", style = MaterialTheme.typography.titleMedium)
                if (savedDeviceAddress != null) {
                    Text(savedDeviceAddress!!, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { viewModel.clearSavedDevice() }) {
                        Text("Verbindung löschen")
                    }
                } else {
                    Text("Kein Gerät gespeichert", style = MaterialTheme.typography.bodyMedium)
                }
            }
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
                    onDone = { it.toIntOrNull()?.let { v -> if (v in 10..99) viewModel.setAge(v) } }
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
                    }
                )

                NumberField(
                    label = "Ruhepuls (leer = %HRmax-Modell)",
                    value = restingHrText,
                    onValueChange = { restingHrText = it },
                    onDone = {
                        viewModel.setRestingHr(it.toIntOrNull()?.takeIf { v -> v in 20..100 })
                    }
                )
                Text("Zonen-Modell: $model", style = MaterialTheme.typography.bodySmall)
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
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Z${z.zone}", style = MaterialTheme.typography.bodyMedium)
                        Text("${z.lo} – ${z.hi} BPM", style = MaterialTheme.typography.bodyMedium)
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
    onDone: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            TextButton(onClick = { onDone(value) }) { Text("OK") }
        }
    )
}
