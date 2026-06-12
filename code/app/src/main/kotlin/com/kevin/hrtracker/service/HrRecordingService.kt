package com.kevin.hrtracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.Wearable
import com.kevin.hrtracker.MainActivity
import com.kevin.hrtracker.ble.HrBleManager
import com.kevin.hrtracker.ble.ParsedHr
import com.kevin.hrtracker.data.repository.SessionRepository
import com.kevin.hrtracker.domain.HrSource
import com.kevin.hrtracker.wearable.WearableHrSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HrRecordingService : Service() {

    @Inject lateinit var bleManager: HrBleManager
    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var settingsRepository: com.kevin.hrtracker.data.repository.SettingsRepository
    @Inject lateinit var wearableHrSource: WearableHrSource

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var notificationJob: Job? = null
    private var currentHrSource: HrSource = HrSource.BLE

    companion object {
        private const val CHANNEL_ID = "hr_recording"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.kevin.hrtracker.ACTION_START"
        const val ACTION_STOP  = "com.kevin.hrtracker.ACTION_STOP"
        const val EXTRA_LABEL  = "label"

        fun startIntent(context: Context, label: String) =
            Intent(context, HrRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LABEL, label)
            }

        fun stopIntent(context: Context) =
            Intent(context, HrRecordingService::class.java).apply { action = ACTION_STOP }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val label = intent.getStringExtra(EXTRA_LABEL) ?: "Training"
                startForeground(NOTIFICATION_ID, buildNotification("–", "00:00"))
                startSession(label)
            }
            ACTION_STOP -> {
                stopSession()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startSession(label: String) {
        scope.launch {
            val s = settingsRepository.userSettings.first()
            currentHrSource = s.hrSource
            val hrFlow: Flow<ParsedHr> = if (s.hrSource == HrSource.WATCH) wearableHrSource.hrSamples
                                         else bleManager.hrSamples
            sessionRepository.startSession(label, maxHrUsed = s.maxHrUsed, restingHr = s.restingHr, hrSamples = hrFlow)
            if (currentHrSource == HrSource.WATCH) notifyWatch(true)
            var lastBpm = "–"
            val startMs = System.currentTimeMillis()
            notificationJob = launch {
                hrFlow.collect { parsed: ParsedHr ->
                    lastBpm = parsed.bpm.toString()
                    val elapsed = (System.currentTimeMillis() - startMs) / 1000
                    val mm = elapsed / 60
                    val ss = elapsed % 60
                    updateNotification(lastBpm, "%02d:%02d".format(mm, ss))
                }
            }
        }
    }

    private fun stopSession() {
        notificationJob?.cancel()
        if (currentHrSource == HrSource.WATCH) notifyWatch(false)
        scope.launch { sessionRepository.stopSession() }
    }

    private fun notifyWatch(isRecording: Boolean) {
        val data = byteArrayOf(if (isRecording) 1.toByte() else 0.toByte())
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                Wearable.getMessageClient(this).sendMessage(node.id, "/session_state", data)
            }
        }
    }

    private fun updateNotification(bpm: String, elapsed: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(bpm, elapsed))
    }

    private fun buildNotification(bpm: String, elapsed: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("HR Tracker läuft")
            .setContentText("$bpm BPM  •  $elapsed")
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "HR Aufzeichnung", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
