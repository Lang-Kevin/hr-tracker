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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.kevin.hrtracker.R
import com.kevin.shared.ui.components.PrimaryButton
import com.kevin.shared.ui.components.TextActionButton
import com.kevin.shared.ui.theme.AppTheme

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
            .background(MaterialTheme.colorScheme.background)
            .padding(AppTheme.spacing.xl)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppTheme.spacing.xs),
                horizontalArrangement = Arrangement.Center
            ) {
                (0..lastStep).forEach { i ->
                    val isActive = i == step
                    Box(
                        modifier = Modifier
                            .padding(horizontal = AppTheme.spacing.xs)
                            .size(AppTheme.spacing.sm)
                            .clip(CircleShape)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                }
            }
            if (step < lastStep) {
                TextActionButton(
                    text = stringResource(R.string.onboarding_skip),
                    onClick = {
                        viewModel.skip()
                        onComplete()
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }

        Spacer(Modifier.height(AppTheme.spacing.xxl))

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
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(AppTheme.spacing.sm))
    Text(
        stringResource(R.string.onboarding_welcome_text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.weight(1f))
    PrimaryButton(
        text = stringResource(R.string.onboarding_next),
        onClick = onNext,
        modifier = Modifier.fillMaxWidth()
    )
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
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(AppTheme.spacing.sm))
    Text(
        stringResource(R.string.onboarding_age_text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(AppTheme.spacing.xl))
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
        Spacer(Modifier.height(AppTheme.spacing.md))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(AppTheme.spacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.onboarding_max_hr), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$it BPM", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium) // i18n-ignore: unit/acronym, same in every language
            }
        }
    }
    Spacer(Modifier.weight(1f))
    PrimaryButton(
        text = stringResource(R.string.onboarding_next),
        onClick = onNext,
        enabled = ageValid,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ColumnScope.Step2(
    restingHrText: String,
    onRestingHrChange: (String) -> Unit,
    onComplete: () -> Unit
) {
    Text(
        stringResource(R.string.onboarding_resting_hr_title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(AppTheme.spacing.sm))
    Text(
        stringResource(R.string.onboarding_resting_hr_text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(AppTheme.spacing.xl))
    OutlinedTextField(
        value = restingHrText,
        onValueChange = onRestingHrChange,
        label = { Text(stringResource(R.string.onboarding_resting_hr_label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(Modifier.weight(1f))
    PrimaryButton(
        text = stringResource(R.string.onboarding_get_started),
        onClick = onComplete,
        modifier = Modifier.fillMaxWidth()
    )
}
