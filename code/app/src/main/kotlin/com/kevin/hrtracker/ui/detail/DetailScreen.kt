package com.kevin.hrtracker.ui.detail

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.kevin.hrtracker.ui.shared.BpmZoneChart
import com.kevin.hrtracker.ui.shared.StatItem
import com.kevin.hrtracker.ui.shared.ZeitInZoneSection
import com.kevin.hrtracker.ui.theme.BackgroundDark
import com.kevin.hrtracker.ui.theme.OnPrimary
import com.kevin.hrtracker.ui.theme.PrimaryPurple
import com.kevin.hrtracker.ui.history.durationString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TRAINING_TYPES = listOf(
    "Allgemeines Training",
    "Beachvolleyball",
    "Trainingbike",
    "Volleyball"
)

@Composable
fun DetailScreen(
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val bpmHistory by viewModel.bpmHistory.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val zoneBounds by viewModel.zoneBounds.collectAsStateWithLifecycle()
    val timeInZone by viewModel.timeInZone.collectAsStateWithLifecycle()
    val dominantZone by viewModel.dominantZone.collectAsStateWithLifecycle()
    val percentInTargetZone by viewModel.percentInTargetZone.collectAsStateWithLifecycle()
    val rmssd by viewModel.rmssd.collectAsStateWithLifecycle()
    val trimp by viewModel.trimp.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        EditTrainingTypeDialog(
            current = session?.label ?: TRAINING_TYPES[0],
            onSave = { label ->
                viewModel.updateLabel(label)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }

    if (showNoteDialog) {
        EditNoteDialog(
            current = session?.note ?: "",
            onSave = { note ->
                viewModel.updateNote(note)
                showNoteDialog = false
            },
            onDismiss = { showNoteDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Header: tappable label + date
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.clickable { showEditDialog = true }) {
                Text(
                    text = session?.label?.uppercase() ?: "TRAINING",
                    color = PrimaryPurple,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            session?.let { s ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(s.startedAt)),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    s.endedAt?.let { end ->
                        Text(
                            durationString((end - s.startedAt) / 1000),
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // BPM Zone Chart
        BpmZoneChart(
            bpmHistory = bpmHistory,
            currentBpm = bpmHistory.lastOrNull(),
            zoneBounds = zoneBounds,
            targetZone = dominantZone,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(Modifier.height(12.dp))

        // Zeit in Zone
        ZeitInZoneSection(
            timeInZone = timeInZone,
            percentInTargetZone = percentInTargetZone,
            targetZone = dominantZone
        )

        Spacer(Modifier.height(12.dp))

        // Stats Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatItem("BPM Ø", stats?.avgBpm?.toString() ?: "—", Modifier.weight(1f))
            StatItem(
                "DAUER",
                session?.endedAt?.let { end ->
                    durationString((end - (session?.startedAt ?: end)) / 1000)
                } ?: "—",
                Modifier.weight(1f)
            )
            StatItem("MAX BPM", stats?.maxBpm?.toString() ?: "—", Modifier.weight(1f), valueColor = PrimaryPurple)
        }

        Spacer(Modifier.height(8.dp))

        // Analytics Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatItem("RMSSD", rmssd?.let { "${it}ms" } ?: "—", Modifier.weight(1f))
            StatItem("TRIMP", trimp?.toString() ?: "—", Modifier.weight(1f), valueColor = PrimaryPurple)
            StatItem("MIN BPM", stats?.minBpm?.toString() ?: "—", Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        // Note field
        val noteText = session?.note
        OutlinedCard(
            modifier = Modifier.fillMaxWidth().clickable { showNoteDialog = true },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Notiz",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (noteText.isNullOrBlank()) "Notiz hinzufügen…" else noteText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (noteText.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                    )
                }
                Text(
                    "✎",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PrimaryPurple
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) { Text("← Zurück") }
            Button(
                onClick = {
                    scope.launch {
                        val intent = viewModel.export(context) ?: return@launch
                        context.startActivity(Intent.createChooser(intent, "JSON exportieren"))
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryPurple,
                    contentColor = OnPrimary
                )
            ) { Text("JSON") }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = {
                scope.launch {
                    val intent = viewModel.exportCsv(context) ?: return@launch
                    context.startActivity(Intent.createChooser(intent, "CSV exportieren"))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, PrimaryPurple),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryPurple)
        ) { Text("CSV Export") }
    }
}

@Composable
private fun EditNoteDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notiz") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("z.B. Beine sehr müde, neues PB…") },
                maxLines = 4
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun EditTrainingTypeDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trainingstyp ändern") },
        text = {
            Column {
                TRAINING_TYPES.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = type }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == type,
                            onClick = { selected = type }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(type, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected) }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
