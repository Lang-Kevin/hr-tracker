package com.kevin.hrtracker.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.kevin.hrtracker.MainActivity
import com.kevin.hrtracker.R
import com.kevin.hrtracker.ble.HrBleManager
import com.kevin.hrtracker.ui.formatDuration
import com.kevin.hrtracker.ble.ParsedHr
import com.kevin.hrtracker.data.repository.SessionRepository
import com.kevin.hrtracker.data.repository.SettingsRepository
import com.kevin.hrtracker.domain.HrZoneCalculator
import com.kevin.hrtracker.domain.WidgetVariant
import com.kevin.hrtracker.domain.ZoneBounds
import com.kevin.hrtracker.domain.widgetNotificationText
import com.kevin.shared.ble.ConnectionState
import com.kevin.shared.service.BaseRecordingService
import com.kevin.shared.service.RecordingServiceContract
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@AndroidEntryPoint
class HrRecordingService : BaseRecordingService() {

    @Inject lateinit var bleManager: HrBleManager
    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private val startMutex = Mutex()
    private var notificationJob: Job? = null
    private var connectionStateJob: Job? = null
    private var settingsJob: Job? = null
    private var lastBpmValue: Int? = null
    @Volatile private var currentVariant: WidgetVariant = WidgetVariant.STANDARD
    @Volatile private var currentZones: List<ZoneBounds> = emptyList()
    private var startMs: Long = 0L

    override val notificationChannelId = "hr_recording"
    override val notificationChannelName: String
        get() = getString(R.string.service_notification_channel_name)
    override val notificationId = 1
    @Suppress("InlinedApi")
    override val fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE

    override fun buildNotification(label: String, elapsed: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val zone = lastBpmValue
            ?.takeIf { currentZones.isNotEmpty() }
            ?.let { HrZoneCalculator.zoneFor(it, currentZones) }
        return NotificationCompat.Builder(this, notificationChannelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(
                widgetNotificationText(
                    currentVariant, lastBpmValue, zone, elapsed,
                    bpmFormat = getString(R.string.service_notification_bpm),
                    zoneFormat = getString(R.string.service_notification_zone)
                )
            )
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    override suspend fun onRecordingStart(label: String) {
        startMutex.withLock {
            if (notificationJob?.isActive == true) return@withLock

            val s = settingsRepository.userSettings.first()
            val hrFlow: Flow<ParsedHr> = bleManager.hrSamples
            sessionRepository.startSession(
                label, maxHrUsed = s.maxHrUsed, restingHr = s.restingHr, zoneModel = s.zoneModel,
                hrSamples = hrFlow, customZones = s.customZones
            )
            currentVariant = s.widgetVariant
            currentZones = s.effectiveZones
            startMs = System.currentTimeMillis()
            settingsJob = serviceScope.launch {
                settingsRepository.userSettings.collect {
                    currentVariant = it.widgetVariant
                    currentZones = it.effectiveZones
                    val elapsed = (System.currentTimeMillis() - startMs) / 1000
                    updateNotification(lastBpmValue?.toString() ?: "–", formatDuration(elapsed))
                }
            }
            connectionStateJob = serviceScope.launch {
                bleManager.connectionState.collect { state ->
                    if (sessionRepository.activeSessionId.value == null) return@collect
                    when (state) {
                        is ConnectionState.Disconnected,
                        is ConnectionState.Reconnecting,
                        is ConnectionState.Connecting,
                        is ConnectionState.Error -> sessionRepository.autoPause()
                        is ConnectionState.Ready -> sessionRepository.autoResume()
                        else -> Unit
                    }
                }
            }
            notificationJob = serviceScope.launch {
                hrFlow.collect { parsed: ParsedHr ->
                    lastBpmValue = parsed.bpm
                    val elapsed = (System.currentTimeMillis() - startMs) / 1000
                    updateNotification(lastBpmValue.toString(), formatDuration(elapsed))
                }
            }
        }
    }

    override suspend fun onRecordingStop() {
        notificationJob?.cancel()
        notificationJob = null
        connectionStateJob?.cancel()
        connectionStateJob = null
        settingsJob?.cancel()
        settingsJob = null
        sessionRepository.stopSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when {
            intent == null -> {
                // process-death sticky restart: no session to resume (H3), don't run as zombie
                stopSelf(startId)
                START_NOT_STICKY
            }
            intent.action == ACTION_DISCARD -> {
                serviceScope.launch {
                    try {
                        sessionRepository.discardSession()
                    } finally {
                        stopSelf(startId)
                    }
                }
                START_NOT_STICKY
            }
            else -> super.onStartCommand(intent, flags, startId)
        }
    }

    override fun onDestroy() {
        if (sessionRepository.activeSessionId.value != null) {
            sessionRepository.stopSessionDetached()
        }
        super.onDestroy()
    }

    companion object {
        private const val ACTION_DISCARD = "com.kevin.hrtracker.ACTION_DISCARD"

        fun startIntent(context: Context, label: String) =
            RecordingServiceContract.startIntent<HrRecordingService>(context, label)

        fun stopIntent(context: Context) =
            RecordingServiceContract.stopIntent<HrRecordingService>(context)

        fun discardIntent(context: Context) =
            Intent(context, HrRecordingService::class.java).apply { action = ACTION_DISCARD }
    }
}
