package com.kevin.hrtracker.ui.detail

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ZoomInMap
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.kevin.hrtracker.data.entity.Milestone
import com.kevin.hrtracker.ui.formatDuration
import com.kevin.hrtracker.ui.shared.BpmZoneChart
import com.kevin.shared.ui.StatItem
import com.kevin.shared.ui.chart.ChartToggleButton
import com.kevin.hrtracker.ui.shared.ZeitInZoneSection
import com.kevin.hrtracker.ui.theme.BackgroundDark
import com.kevin.hrtracker.ui.theme.OnPrimary
import com.kevin.hrtracker.ui.theme.PrimaryPurple
import com.kevin.shared.ui.session.durationString
import com.kevin.shared.ui.LabelPickerDialog
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun DetailScreen(
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
    val calories by viewModel.calories.collectAsStateWithLifecycle()
    val bodyDataMissing by viewModel.bodyDataMissing.collectAsStateWithLifecycle()
    val recovery by viewModel.recovery.collectAsStateWithLifecycle()
    val trainingLabels by viewModel.trainingLabels.collectAsStateWithLifecycle()
    val milestones by viewModel.milestones.collectAsStateWithLifecycle()
    val chartDynamicScaleDefault by viewModel.chartDynamicScaleDefault.collectAsStateWithLifecycle()
    val gapFractions by viewModel.gapFractions.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var reportJson by remember { mutableStateOf<String?>(null) }
    var dynamicScaleOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val dynamicScale = dynamicScaleOverride ?: chartDynamicScaleDefault
    var editingMilestoneId by remember { mutableStateOf<Long?>(null) }
    var editingMilestoneLabel by remember { mutableStateOf("") }

    // ponytail: compute reachedZones from existing timeInZone map (zones with duration > 0)
    val reachedZones = timeInZone.filter { it.value > 0 }.keys

    if (showEditDialog) {
        LabelPickerDialog(
            title = "Trainingstyp ändern",
            items = trainingLabels,
            initialSelection = session?.label ?: trainingLabels.firstOrNull()?.name,
            confirmText = "Speichern",
            onConfirm = { label ->
                viewModel.updateLabel(label)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
            dismissText = null,
            addFieldLabel = null,
            onAdd = null,
            onDelete = null
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

    if (reportJson != null) {
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { reportJson = null },
            title = { Text("Report") },
            text = {
                SelectionContainer {
                    Text(reportJson!!)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(reportJson!!))
                    Toast.makeText(context, "Report kopiert", Toast.LENGTH_SHORT).show()
                }) { Text("Kopieren") }
            },
            confirmButton = {
                TextButton(onClick = { reportJson = null }) { Text("Schließen") }
            }
        )
    }

    if (editingMilestoneId != null) {
        AlertDialog(
            onDismissRequest = { editingMilestoneId = null },
            title = { Text("Meilenstein-Name") },
            text = {
                OutlinedTextField(
                    value = editingMilestoneLabel,
                    onValueChange = { editingMilestoneLabel = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateMilestoneLabel(editingMilestoneId!!, editingMilestoneLabel)
                        editingMilestoneId = null
                    },
                    enabled = editingMilestoneLabel.trim().isNotEmpty()
                ) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { editingMilestoneId = null }) { Text("Abbrechen") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
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
                        SimpleDateFormat("dd.MM.yy", LocalConfiguration.current.locales[0]).format(Date(s.startedAt)),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    s.endedAt?.let { end ->
                        Text(
                            durationString((end - s.startedAt) / 1000),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // BPM Zone Chart Header with Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Trainingszone",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ChartToggleButton(
                checked = dynamicScale,
                onCheckedChange = { dynamicScaleOverride = it },
                icon = if (dynamicScale) Icons.Default.ZoomInMap else Icons.Default.ZoomOutMap,
                contentDescription = if (dynamicScale)
                    "Dynamische Skalierung" else
                    "Statische Skalierung",
                contentColor = PrimaryPurple
            )
        }

        // BPM Zone Chart
        val totalSessionSeconds = session?.endedAt?.let { end ->
            ((end - (session?.startedAt ?: end)) / 1000L)
        }
        BpmZoneChart(
            bpmHistory = bpmHistory,
            currentBpm = bpmHistory.lastOrNull(),
            zoneBounds = zoneBounds,
            targetZone = dominantZone,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            dynamicScale = dynamicScale,
            reachedZones = reachedZones,
            detailMilestones = milestones,
            totalSessionSeconds = totalSessionSeconds,
            gaps = gapFractions,
            meanBpm = stats?.avgBpm
        )

        Spacer(Modifier.height(12.dp))

        // Meilensteine
        if (milestones.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = "Meilensteine",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    milestones.forEach { milestone ->
                        val timeStr = formatDuration(milestone.atSeconds)
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editingMilestoneId = milestone.id
                                    editingMilestoneLabel = milestone.label
                                },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = milestone.label.ifBlank { "Meilenstein" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                    Text(
                                        text = timeStr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Bearbeiten",
                                    tint = PrimaryPurple,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

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

        Spacer(Modifier.height(8.dp))

        // Kalorien + Zonen-Basis
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatItem(
                "KALORIEN",
                calories?.let { "$it kcal" } ?: "—",
                Modifier.weight(1f),
                valueColor = PrimaryPurple
            )
            StatItem("HRMAX", session?.maxHrUsed?.toString() ?: "—", Modifier.weight(1f))
            StatItem("RUHEPULS", session?.restingHr?.toString() ?: "—", Modifier.weight(1f))
        }

        if (calories == null && session?.endedAt != null) {
            Text(
                if (bodyDataMissing) "Kalorien: Gewicht und Geschlecht in den Einstellungen hinterlegen."
                else "Kalorien: Zu wenig Messdaten in dieser Session.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        // Heart Rate Recovery Card
        recovery?.let { hrr ->
            val ratingColor = when (hrr.rating) {
                com.kevin.hrtracker.domain.HrrRating.NIEDRIG -> MaterialTheme.colorScheme.errorContainer
                com.kevin.hrtracker.domain.HrrRating.NORMAL -> MaterialTheme.colorScheme.tertiary
                com.kevin.hrtracker.domain.HrrRating.GUT -> MaterialTheme.colorScheme.primary
                com.kevin.hrtracker.domain.HrrRating.SEHR_GUT -> MaterialTheme.colorScheme.primary
            }
            val ratingText = when (hrr.rating) {
                com.kevin.hrtracker.domain.HrrRating.NIEDRIG -> "Niedrig"
                com.kevin.hrtracker.domain.HrrRating.NORMAL -> "Normal"
                com.kevin.hrtracker.domain.HrrRating.GUT -> "Gut"
                com.kevin.hrtracker.domain.HrrRating.SEHR_GUT -> "Sehr gut"
            }
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Herzfrequenz-Erholung (1 min)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${hrr.hrr60} bpm",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Surface(
                            color = ratingColor,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                ratingText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (hrr.recoveredToTarget && hrr.secondsToTarget != null) {
                        Text(
                            "Erholt auf Ruhebereich nach ${hrr.secondsToTarget}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        "Peak ${hrr.peakBpm} → ${hrr.hrAt60s} bpm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } ?: OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Herzfrequenz-Erholung (1 min)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text("—", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Zu wenig Daten oder kein Peak ≥ 70 % HRmax mit 60 s Nachlauf.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Notiz bearbeiten",
                    tint = PrimaryPurple,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Action buttons
        Button(
            onClick = {
                scope.launch {
                    val intent = viewModel.export(context) ?: return@launch
                    context.startActivity(Intent.createChooser(intent, "JSON exportieren"))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryPurple,
                contentColor = OnPrimary
            )
        ) { Text("JSON exportieren") }
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
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = {
                val json = viewModel.generateReport()
                reportJson = json
            },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, PrimaryPurple),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryPurple)
        ) { Text("Report erzeugen") }
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
        }
    )
}