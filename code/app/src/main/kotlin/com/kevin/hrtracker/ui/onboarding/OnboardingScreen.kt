package com.kevin.hrtracker.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kevin.hrtracker.ui.theme.BackgroundDark
import com.kevin.hrtracker.ui.theme.OnPrimary
import com.kevin.hrtracker.ui.theme.PrimaryPurple
import com.kevin.hrtracker.ui.theme.SurfaceDark

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    var step by remember { mutableIntStateOf(0) }
    var ageText by remember { mutableStateOf("") }
    var restingHrText by remember { mutableStateOf("") }

    val age = ageText.toIntOrNull()
    val computedMaxHr = age?.let { (208 - 0.7 * it).toInt() }
    val restingHr = restingHrText.toIntOrNull()
    val ageValid = age != null && age in 10..99

    val lastStep = 6

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                (0..lastStep).forEach { i ->
                    val isActive = i <= step
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (i == step) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) PrimaryPurple
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                    )
                }
            }
            if (step < lastStep) {
                TextButton(
                    onClick = {
                        viewModel.skip()
                        onComplete()
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) { Text("Überspringen", color = Color.White.copy(alpha = 0.7f)) }
            }
        }

        Spacer(Modifier.height(32.dp))

        when (step) {
            0 -> StepWelcome(onNext = { step = 1 })
            1 -> Step1(
                ageText = ageText,
                onAgeChange = { ageText = it.filter(Char::isDigit).take(3) },
                computedMaxHr = computedMaxHr,
                ageValid = ageValid,
                onNext = { step = 2 }
            )
            2 -> Step2(
                restingHrText = restingHrText,
                onRestingHrChange = { restingHrText = it.filter(Char::isDigit).take(3) },
                onSkip = { step = 3 },
                onNext = { step = 3 }
            )
            3 -> StepBleInfo(onNext = { step = 4 })
            4 -> StepLiveInfo(onNext = { step = 5 })
            5 -> StepHistoryInfo(onNext = { step = 6 })
            6 -> Step3(
                age = age,
                computedMaxHr = computedMaxHr,
                restingHr = restingHr,
                onComplete = {
                    if (age != null) {
                        viewModel.complete(age, restingHr)
                    } else {
                        viewModel.skip()
                    }
                    onComplete()
                }
            )
        }
    }
}

@Composable
private fun ColumnScope.StepWelcome(onNext: () -> Unit) {
    Text(
        "Willkommen bei HR-Tracker",
        style = MaterialTheme.typography.headlineMedium,
        color = Color.White
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Zeichne deine Herzfrequenz mit deinem BLE-Brustgurt auf und behalte deine Trainingszonen im Blick.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.7f)
    )
    Spacer(Modifier.weight(1f))
    Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple, contentColor = OnPrimary)
    ) { Text("Weiter") }
}

@Composable
private fun ColumnScope.StepBleInfo(onNext: () -> Unit) {
    Text(
        "Brustgurt verbinden",
        style = MaterialTheme.typography.headlineMedium,
        color = Color.White
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Auf dem Scan-Screen findest du deinen Gurt per Bluetooth. Einmal verbunden, merkt sich die App das Gerät für automatisches Reconnect.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.7f)
    )
    Spacer(Modifier.weight(1f))
    Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple, contentColor = OnPrimary)
    ) { Text("Weiter") }
}

@Composable
private fun ColumnScope.StepLiveInfo(onNext: () -> Unit) {
    Text(
        "Live-Tracking & Zonen",
        style = MaterialTheme.typography.headlineMedium,
        color = Color.White
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Während der Aufzeichnung siehst du deinen aktuellen Puls, ein Zonen-Chart (Z1–Z5) und kannst die Session pausieren/fortsetzen.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.7f)
    )
    Spacer(Modifier.weight(1f))
    Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple, contentColor = OnPrimary)
    ) { Text("Weiter") }
}

@Composable
private fun ColumnScope.StepHistoryInfo(onNext: () -> Unit) {
    Text(
        "Verlauf & Export",
        style = MaterialTheme.typography.headlineMedium,
        color = Color.White
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Abgeschlossene Sessions findest du im Verlauf, filterbar nach Label. Details lassen sich exportieren.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.7f)
    )
    Spacer(Modifier.weight(1f))
    Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple, contentColor = OnPrimary)
    ) { Text("Weiter") }
}

@Composable
private fun ColumnScope.Step1(
    ageText: String,
    onAgeChange: (String) -> Unit,
    computedMaxHr: Int?,
    ageValid: Boolean,
    onNext: () -> Unit
) {
    Text(
        "Wie alt bist du?",
        style = MaterialTheme.typography.headlineMedium,
        color = Color.White
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Wir berechnen damit deine maximale Herzfrequenz nach der Tanaka-Formel.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.7f)
    )
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = ageText,
        onValueChange = onAgeChange,
        label = { Text("Alter (Jahre)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = ageText.isNotEmpty() && !ageValid,
        supportingText = if (ageText.isNotEmpty() && !ageValid) {
            { Text("Alter muss zwischen 10 und 99 liegen") }
        } else null
    )
    computedMaxHr?.let {
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Maximale Herzfrequenz", color = Color.White.copy(alpha = 0.7f))
                Text("$it BPM", color = PrimaryPurple, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    Spacer(Modifier.weight(1f))
    Button(
        onClick = onNext,
        enabled = ageValid,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple, contentColor = OnPrimary)
    ) { Text("Weiter") }
}

@Composable
private fun ColumnScope.Step2(
    restingHrText: String,
    onRestingHrChange: (String) -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit
) {
    Text(
        "Ruhepuls (optional)",
        style = MaterialTheme.typography.headlineMedium,
        color = Color.White
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Für genauere Zonen-Berechnungen nach der Karvonen-Formel. Typisch: 50–70 BPM morgens nach dem Aufwachen.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.7f)
    )
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = restingHrText,
        onValueChange = onRestingHrChange,
        label = { Text("Ruhepuls (BPM)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(Modifier.weight(1f))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.weight(1f)
        ) { Text("Überspringen") }
        Button(
            onClick = onNext,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple, contentColor = OnPrimary)
        ) { Text("Weiter") }
    }
}

@Composable
private fun ColumnScope.Step3(
    age: Int?,
    computedMaxHr: Int?,
    restingHr: Int?,
    onComplete: () -> Unit
) {
    Text(
        "Bereit zum Starten!",
        style = MaterialTheme.typography.headlineMedium,
        color = Color.White
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Nach dem Onboarding kannst du deinen BLE Brustgurt über den Scan-Screen verbinden.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.7f)
    )
    Spacer(Modifier.height(24.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            age?.let {
                SummaryRow("Alter", "$it Jahre")
            }
            computedMaxHr?.let {
                SummaryRow("Max. Herzfrequenz", "$it BPM")
            }
            SummaryRow(
                "Ruhepuls",
                restingHr?.let { "$it BPM" } ?: "Nicht angegeben"
            )
        }
    }
    Spacer(Modifier.weight(1f))
    Button(
        onClick = onComplete,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple, contentColor = OnPrimary)
    ) { Text("Loslegen") }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}
