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

    val lastStep = 2

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
    onComplete: () -> Unit
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
    Button(
        onClick = onComplete,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple, contentColor = OnPrimary)
    ) { Text("Loslegen") }
}
