package com.kevin.hrtracker.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.hrtracker.data.entity.Session
import com.kevin.hrtracker.ui.theme.PrimaryPurple
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onSessionClick: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val trashSessions by viewModel.trashSessions.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val summaryStats by viewModel.summaryStats.collectAsStateWithLifecycle()
    val weeklyData by viewModel.weeklyData.collectAsStateWithLifecycle()
    val trimpHistory by viewModel.trimpHistory.collectAsStateWithLifecycle()
    var pendingDeleteIds by remember { mutableStateOf<List<Long>?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    pendingDeleteIds?.let { ids ->
        AlertDialog(
            onDismissRequest = { pendingDeleteIds = null },
            title = { Text("In Papierkorb verschieben?") },
            text = {
                val count = ids.size
                Text(
                    "$count ${if (count == 1) "Eintrag wird" else "Einträge werden"} in den Papierkorb " +
                        "verschoben und beim nächsten App-Start endgültig gelöscht."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.moveToTrash(ids)
                    pendingDeleteIds = null
                }) { Text("Verschieben") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteIds = null }) { Text("Abbrechen") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        if (isSelectionMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { viewModel.clearSelection() }) {
                    Icon(Icons.Default.Close, contentDescription = "Auswahl abbrechen")
                }
                Text(
                    "${selectedIds.size} ausgewählt",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { pendingDeleteIds = selectedIds.toList() },
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Löschen")
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Zurück") }
                Text("Verlauf", style = MaterialTheme.typography.headlineMedium)
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            contentColor = PrimaryPurple
        ) {
            listOf("Verlauf", "Statistik", "Papierkorb").forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when (selectedTab) {
            0 -> {
                if (sessions.isNotEmpty()) {
                    SummaryCard(summaryStats)
                    Spacer(Modifier.height(8.dp))
                }
                if (sessions.isEmpty()) {
                    Text("Noch keine Sessions aufgezeichnet.", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Starte ein Training, um deine Herzfrequenz-Daten hier zu sehen.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sessions, key = { it.id }) { session ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (!isSelectionMode && value == SwipeToDismissBoxValue.EndToStart) {
                                    pendingDeleteIds = listOf(session.id)
                                    false
                                } else false
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CardDefaults.shape)
                                        .background(MaterialTheme.colorScheme.error)
                                        .padding(end = 16.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            },
                            enableDismissFromStartToEnd = false
                        ) {
                            SessionListItem(
                                session = session,
                                isSelected = session.id in selectedIds,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) viewModel.toggleSelection(session.id)
                                    else onSessionClick(session.id)
                                },
                                onLongClick = { viewModel.startSelection(session.id) }
                            )
                        }
                    }
                }
            }
            1 -> StatistikTab(weeklyData, trimpHistory)
            2 -> TrashTab(trashSessions, onRestore = { viewModel.restoreSessions(listOf(it)) })
        }
    }
}

@Composable
private fun SummaryCard(stats: HistoryViewModel.SummaryStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryItem("TRAININGS", stats.sessionCount.toString())
            SummaryItem("GESAMTDAUER", durationString(stats.totalDurationS))
            SummaryItem("Ø BPM", stats.avgBpm?.toString() ?: "—")
            SummaryItem("LÄNGSTE", durationString(stats.longestDurationS))
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionListItem(
    session: Session,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(session.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    session.startedAt.toDateString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                session.endedAt?.let { end ->
                    Text(
                        "Dauer: ${durationString((end - session.startedAt) / 1000)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } ?: Text(
                    "läuft noch…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatistikTab(
    weeklyData: List<HistoryViewModel.WeekStats>,
    trimpHistory: List<HistoryViewModel.SessionTrimpEntry>
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "Letzte 6 Wochen",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("WOCHE", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f))
                        Text("EINH.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.7f))
                        Text("MIN", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.8f))
                        Text("Ø BPM", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.9f))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    weeklyData.forEach { week ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(week.weekLabel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.2f))
                            Text(
                                if (week.sessionCount > 0) "${week.sessionCount}" else "—",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(0.7f),
                                color = if (week.sessionCount > 0) PrimaryPurple else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (week.totalDurationMin > 0) "${week.totalDurationMin}" else "—",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(0.8f)
                            )
                            Text(
                                week.avgBpm?.toString() ?: "—",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(0.9f)
                            )
                        }
                    }
                }
            }
        }

        if (trimpHistory.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "TRIMP-Verlauf",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(trimpHistory, key = { it.sessionId }) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                entry.startedAt.toDateString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "TRIMP ${entry.trimp}",
                            style = MaterialTheme.typography.titleSmall,
                            color = PrimaryPurple,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Noch keine TRIMP-Daten verfügbar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TrashTab(
    trashSessions: List<Session>,
    onRestore: (Long) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            if (trashSessions.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        "Elemente werden beim nächsten App-Start endgültig gelöscht.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (trashSessions.isEmpty()) {
            item {
                Text(
                    "Papierkorb ist leer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(trashSessions, key = { it.id }) { session ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(session.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                session.startedAt.toDateString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onRestore(session.id) }) {
                            Text("Wiederherstellen")
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

private fun Long.toDateString(): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(this))

internal fun durationString(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}
