package com.kevin.hrtracker

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kevin.hrtracker.data.repository.SessionRepository
import com.kevin.hrtracker.service.HrRecordingService
import com.kevin.hrtracker.ui.detail.DetailScreen
import com.kevin.hrtracker.ui.history.HistoryScreen
import com.kevin.hrtracker.ui.onboarding.OnboardingScreen
import com.kevin.hrtracker.ui.onboarding.OnboardingViewModel
import com.kevin.hrtracker.ui.pip.PipContent
import com.kevin.hrtracker.ui.settings.SettingsScreen
import com.kevin.hrtracker.ui.live.LiveScreen
import com.kevin.hrtracker.ui.scan.ScanScreen
import com.kevin.hrtracker.ui.scan.ScanViewModel
import com.kevin.hrtracker.ui.theme.HRTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    @Inject lateinit var sessionRepository: SessionRepository

    private val inPipMode = MutableStateFlow(false)

    private val pipSupported: Boolean
        get() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun pipParams(autoEnter: Boolean): PictureInPictureParams =
        PictureInPictureParams.Builder()
            .setAspectRatio(Rational(4, 3))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(autoEnter)
            }
            .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        addOnPictureInPictureModeChangedListener { info ->
            inPipMode.value = info.isInPictureInPictureMode
        }

        // API 31+: nahtloses Auto-Enter beim Home-Swipe. Wird bei jeder
        // Session-Zustandsaenderung neu gesetzt, damit ohne Session nichts passiert.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && pipSupported) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    sessionRepository.activeSessionId.collect { id ->
                        setPictureInPictureParams(pipParams(autoEnter = id != null))
                    }
                }
            }
        }

        setContent {
            HRTrackerTheme {
                // ponytail: NavController muss den PiP-Wechsel ueberleben — sonst neuer
                // BackStack -> neues LiveViewModel -> Chart und Zonentimer auf 0 (Bug).
                val navController = rememberNavController()
                val inPip by inPipMode.collectAsStateWithLifecycle()
                if (inPip) {
                    PipContent()
                } else {
                    Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                        HrTrackerNav(navController)
                    }
                }
            }
        }
    }

    // API 26-30: kein Auto-Enter verfuegbar, daher manuell beim Verlassen.
    // ponytail: onUserLeaveHint feuert bei Gesten-Navigation nicht immer —
    // genau dafuer gibt es ab API 31 setAutoEnterEnabled oben.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        if (!pipSupported || sessionRepository.activeSessionId.value == null) return
        runCatching { enterPictureInPictureMode(pipParams(autoEnter = false)) }
    }

    @Composable
    private fun HrTrackerNav(navController: NavHostController) {
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
                    onNavigateToSettings = { navController.navigate(Route.SETTINGS) }
                )
            }

            composable(
                route = Route.LIVE,
                arguments = listOf(navArgument("hrv") { type = NavType.IntType; defaultValue = 0 })
            ) {
                LiveScreen(
                    onStopSession = {
                        scanViewModel.stopSession { finishedId ->
                            if (finishedId != null) {
                                navController.navigate(Route.detail(finishedId)) {
                                    popUpTo(Route.SCAN)
                                    launchSingleTop = true
                                }
                            } else {
                                navController.popBackStack()
                            }
                        }
                        stopService(HrRecordingService.stopIntent(this@MainActivity))
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
