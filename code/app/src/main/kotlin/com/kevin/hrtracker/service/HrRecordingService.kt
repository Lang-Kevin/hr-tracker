package com.kevin.hrtracker.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.Wearable
import com.kevin.hrtracker.MainActivity
import com.kevin.hrtracker.ble.HrBleManager
import com.kevin.hrtracker.ble.ParsedHr
import com.kevin.hrtracker.data.repository.SessionRepository
import com.kevin.hrtracker.data.repository.SettingsRepository
import com.kevin.hrtracker.domain.HrSource
import com.kevin.hrtracker.wearable.WearableHrSource
import com.kevin.shared.service.BaseRecordingService
import com.kevin.shared.service.RecordingServiceContract
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HrRecordingService : BaseRecordingService() {

    @Inject lateinit var bleManager: HrBleManager
    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var wearableHrSource: WearableHrSource

    private var notificationJob: Job? = null
    private var currentHrSource: HrSource = HrSource.BLE
    private var lastBpm = "–"

    override val notificationChannelId = "hr_recording"
    override val notificationChannelName = "HR Aufzeichnung"
    override val notificationId = 1
    @Suppress("InlinedApi")
    override val fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE

    override fun buildNotification(label: String, elapsed: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, notificationChannelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("HR Tracker läuft")
            .setContentText("$lastBpm BPM  •  $elapsed")
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    override suspend fun onRecordingStart(label: String) {
        val s = settingsRepository.userSettings.first()
        currentHrSource = s.hrSource
        val hrFlow: Flow<ParsedHr> = if (s.hrSource == HrSource.WATCH) wearableHrSource.hrSamples
                                     else bleManager.hrSamples
        sessionRepository.startSession(label, maxHrUsed = s.maxHrUsed, restingHr = s.restingHr, hrSamples = hrFlow)
        if (currentHrSource == HrSource.WATCH) notifyWatch(true)
        val startMs = System.currentTimeMillis()
        notificationJob = serviceScope.launch {
            hrFlow.collect { parsed: ParsedHr ->
                lastBpm = parsed.bpm.toString()
                val elapsed = (System.currentTimeMillis() - startMs) / 1000
                updateNotification(lastBpm, "%02d:%02d".format(elapsed / 60, elapsed % 60))
            }
        }
    }

    override suspend fun onRecordingStop() {
        notificationJob?.cancel()
        if (currentHrSource == HrSource.WATCH) notifyWatch(false)
        sessionRepository.stopSession()
    }

    // notifyWatch stays in this subclass — Hard Rule: only in WATCH mode
    private fun notifyWatch(isRecording: Boolean) {
        val data = byteArrayOf(if (isRecording) 1.toByte() else 0.toByte())
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                Wearable.getMessageClient(this).sendMessage(node.id, "/session_state", data)
            }
        }
    }

    companion object {
        fun startIntent(context: Context, label: String) =
            RecordingServiceContract.startIntent<HrRecordingService>(context, label)

        fun stopIntent(context: Context) =
            RecordingServiceContract.stopIntent<HrRecordingService>(context)
    }
}
