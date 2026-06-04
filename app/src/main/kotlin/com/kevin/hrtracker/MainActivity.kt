package com.kevin.hrtracker

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
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
import com.kevin.hrtracker.ui.live.LiveScreen
import com.kevin.hrtracker.ui.scan.ScanScreen
import com.kevin.hrtracker.ui.scan.ScanViewModel
import com.kevin.hrtracker.ui.theme.HRTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

private object Route {
    const val SCAN    = "scan"
    const val LIVE    = "live"
    const val HISTORY = "history"
    const val DETAIL  = "detail/{sessionId}"
    fun detail(id: Long) = "detail/$id"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HRTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HrTrackerNav()
                }
            }
        }
    }

    @Composable
    private fun HrTrackerNav() {
        val navController = rememberNavController()
        val scanViewModel: ScanViewModel = hiltViewModel()
        val activeSessionId by scanViewModel.activeSessionId.collectAsStateWithLifecycle()

        LaunchedEffect(activeSessionId) {
            if (activeSessionId != null) {
                navController.navigate(Route.LIVE) { launchSingleTop = true }
            }
        }

        NavHost(navController = navController, startDestination = Route.SCAN) {

            composable(Route.SCAN) {
                ScanScreen(
                    viewModel = scanViewModel,
                    onSessionStarted = { label -> startRecordingService(label) },
                    onNavigateToHistory = { navController.navigate(Route.HISTORY) }
                )
            }

            composable(Route.LIVE) {
                LiveScreen(onStopSession = {
                    scanViewModel.stopSession()
                    stopService(HrRecordingService.stopIntent(this@MainActivity))
                    navController.popBackStack()
                })
            }

            composable(Route.HISTORY) {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onSessionClick = { id -> navController.navigate(Route.detail(id)) }
                )
            }

            composable(
                route = Route.DETAIL,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) {
                DetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    private fun startRecordingService(label: String) {
        val intent = HrRecordingService.startIntent(this, label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }
}
