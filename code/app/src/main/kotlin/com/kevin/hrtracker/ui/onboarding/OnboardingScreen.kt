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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kevin.hrtracker.R
import com.kevin.shared.ui.theme.BackgroundDark
import com.kevin.shared.ui.theme.OnPrimary
import com.kevin.shared.ui.theme.PrimaryPurple
import com.kevin.shared.ui.theme.SurfaceDark

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
                    val isCurrent = i == step
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCurrent -> PrimaryPurple
                                    isActive  -> PrimaryPurple.copy(alpha = 0.5f)
                                    else      -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                }
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
                ) { Text(stringResource(R.string.onboarding_skip), color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
        stringResource(R.string.onboarding_welcome_title),
        style = MaterialTheme.typography.headlineMedium,
        color = Color.White
    )
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.onboarding_welcome_text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.weight(1f))
    Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple, contentColor = OnPrimary)
    ) { Text(stringResource(R.string.onboarding_next)) }
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
        stringResource(R.string.onboarding_age_title),
        style = MaterialTheme.typography.headlineMedium,
        color = Color.White
    )
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.onboarding_age_text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = ageText,
        onValueChange = onAgeChange,
        label = { Text(stringResource(R.string.onboarding_age_label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = ageText.isNotEmpty() && !ageValid,
        supportingText = if (ageText.isNotEmpty() && !ageValid) {
            { Text(stringResource(R.string.onboarding_age_error)) }
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
                Text(stringResource(R.string.onboarding_max_hr), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$it BPM", color = PrimaryPurple, style = MaterialTheme.typography.titleMedium) // i18n-ignore: unit/acronym, same in every language
            }
        }
    }
    Spacer(Modifier.weight(1f))
    Button(
        onClick = onNext,
        enabled = ageValid,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple, contentColor = OnPrimary)
    ) { Text(stringResource(R.string.onboarding_next)) }
}

@Composable
private fun ColumnScope.Step2(
    restingHrText: String,
    onRestingHrChange: (String) -> Unit,
    onComplete: () -> Unit
) {
    Text(
        stringResource(R.string.onboarding_resting_hr_title),
        style = MaterialTheme.typography.headlineMedium,
        color = Color.White
    )
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.onboarding_resting_hr_text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = restingHrText,
        onValueChange = onRestingHrChange,
        label = { Text(stringResource(R.string.onboarding_resting_hr_label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(Modifier.weight(1f))
    Button(
        onClick = onComplete,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple, contentColor = OnPrimary)
    ) { Text(stringResource(R.string.onboarding_get_started)) }
}
