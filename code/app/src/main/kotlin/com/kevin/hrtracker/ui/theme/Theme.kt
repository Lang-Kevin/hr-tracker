package com.kevin.hrtracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HrTrackerColorScheme = darkColorScheme(
    primary = PrimaryPurple,
    onPrimary = OnPrimary,
    secondary = SecondaryBlue,
    tertiary = TertiaryPink,
    error = ErrorRed,
    background = BackgroundDark,
    surface = SurfaceDark,
    onSurface = LightPurple,
)

@Composable
fun HRTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HrTrackerColorScheme,
        typography = HrTrackerTypography,
        content = content
    )
}
