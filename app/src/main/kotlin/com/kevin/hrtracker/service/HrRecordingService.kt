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
import com.kevin.hrtracker.MainActivity
import com.kevin.hrtracker.ble.HrBleManager
import com.kevin.hrtracker.ble.ParsedHr
import com.kevin.hrtracker.data.repository.SessionRepository
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var notificationJob: Job? = null

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
            sessionRepository.startSession(label, maxHrUsed = 187, restingHr = null)
            var lastBpm = "–"
            val startMs = System.currentTimeMillis()
            notificationJob = launch {
                bleManager.hrSamples.collect { parsed: ParsedHr ->
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
        scope.launch { sessionRepository.stopSession() }
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
