package com.kevin.hrtracker

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kevin.hrtracker.service.HrRecordingService
import com.kevin.hrtracker.ui.detail.DetailScreen
import com.kevin.hrtracker.ui.history.HistoryScreen
import com.kevin.hrtracker.ui.onboarding.OnboardingScreen
import com.kevin.hrtracker.ui.onboarding.OnboardingViewModel
import com.kevin.hrtracker.ui.settings.SettingsScreen
import com.kevin.hrtracker.ui.live.LiveScreen
import com.kevin.hrtracker.ui.scan.ScanScreen
import com.kevin.hrtracker.ui.scan.ScanViewModel
import com.kevin.hrtracker.ui.theme.HRTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

private object Route {
    const val ONBOARDING = "onboarding"
    const val SCAN     = "scan"
    const val LIVE     = "live?hrv={hrv}"
    const val HISTORY  = "history"
    const val DETAIL   = "detail/{sessionId}"
    const val SETTINGS = "settings"
    fun detail(id: Long) = "detail/$id"
    fun live(hrv: Int = 0) = "live?hrv=$hrv"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HRTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    HrTrackerNav()
                }
            }
        }
    }

    @Composable
    private fun HrTrackerNav() {
        val navController = rememberNavController()
        val scanViewModel: ScanViewModel = hiltViewModel()
        val onboardingViewModel: OnboardingViewModel = hiltViewModel()
        val activeSessionId by scanViewModel.activeSessionId.collectAsStateWithLifecycle()
        val onboardingDone by onboardingViewModel.onboardingDone.collectAsStateWithLifecycle()

        LaunchedEffect(activeSessionId) {
            if (activeSessionId != null &&
                navController.currentDestination?.route?.startsWith("live") != true) {
                navController.navigate(Route.live()) { launchSingleTop = true }
            }
        }

        LaunchedEffect(onboardingDone) {
            if (!onboardingDone) {
                navController.navigate(Route.ONBOARDING) { launchSingleTop = true }
            }
        }

        NavHost(navController = navController, startDestination = Route.SCAN) {

            composable(Route.ONBOARDING) {
                OnboardingScreen(
                    onComplete = {
                        navController.navigate(Route.SCAN) {
                            popUpTo(Route.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            composable(Route.SCAN) {
                ScanScreen(
                    viewModel = scanViewModel,
                    onSessionStarted = { label -> startRecordingService(label) },
                    onHrvSessionStarted = { seconds ->
                        startRecordingService("HRV RMSSD")
                        navController.navigate(Route.live(seconds)) { launchSingleTop = true }
                    },
                    onNavigateToHistory = { navController.navigate(Route.HISTORY) },
                    onNavigateToSettings = { navController.navigate(Route.SETTINGS) },
                    onResumeSession = { navController.navigate(Route.live()) { launchSingleTop = true } }
                )
            }

            composable(
                route = Route.LIVE,
                arguments = listOf(navArgument("hrv") { type = NavType.IntType; defaultValue = 0 })
            ) {
                LiveScreen(
                    onStopSession = {
                        scanViewModel.stopSession()
                        stopService(HrRecordingService.stopIntent(this@MainActivity))
                        navController.popBackStack()
                    },
                    onAbortSession = {
                        scanViewModel.discardSession()
                        stopService(HrRecordingService.stopIntent(this@MainActivity))
                        navController.popBackStack()
                    }
                )
            }

            composable(Route.HISTORY) {
                HistoryScreen(
                    onSessionClick = { id -> navController.navigate(Route.detail(id)) }
                )
            }

            composable(
                route = Route.DETAIL,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) {
                DetailScreen()
            }

            composable(Route.SETTINGS) {
                SettingsScreen()
            }
        }
    }

    private fun startRecordingService(label: String) {
        val intent = HrRecordingService.startIntent(this, label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }
}
