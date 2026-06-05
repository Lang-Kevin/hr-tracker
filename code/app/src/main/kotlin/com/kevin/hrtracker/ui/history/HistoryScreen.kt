package com.kevin.hrtracker.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.hrtracker.data.entity.Session
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onSessionClick: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück") }
            Text("Verlauf", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(8.dp))
        if (sessions.isEmpty()) {
            Text("Noch keine Sessions aufgezeichnet.", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Starte ein Training, um deine Herzfrequenz-Daten hier zu sehen.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(sessions, key = { it.id }) { session ->
                SessionListItem(session = session, onClick = { onSessionClick(session.id) })
            }
        }
    }
}

@Composable
private fun SessionListItem(session: Session, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(session.label, style = MaterialTheme.typography.titleMedium)
            Text(
                session.startedAt.toDateString(),
                style = MaterialTheme.typography.bodySmall
            )
            session.endedAt?.let { end ->
                Text(
                    "Dauer: ${durationString((end - session.startedAt) / 1000)}",
                    style = MaterialTheme.typography.bodySmall
                )
            } ?: Text("läuft noch…", style = MaterialTheme.typography.bodySmall)
        }
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
