package com.kevin.hrtracker.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.kevin.hrtracker.service.HrRecordingService

class DebugSessionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "DebugSession"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "START" -> startServiceTwice(context)
            "STOP" -> stopService(context)
            else -> Log.d(TAG, "Unknown action: ${intent.action}")
        }
    }

    private fun startServiceTwice(context: Context) {
        Log.d(TAG, "Starting service twice")
        val startIntent = HrRecordingService.startIntent(context, "Debug Session")

        // First START
        ContextCompat.startForegroundService(context, startIntent)

        // Second START
        ContextCompat.startForegroundService(context, startIntent)

        Log.d(TAG, "Service started twice")
    }

    private fun stopService(context: Context) {
        Log.d(TAG, "Sending ACTION_STOP via startService")
        val stopIntent = HrRecordingService.stopIntent(context)
        // startService, not stopService: stopService skips onStartCommand, so ACTION_STOP would never fire
        context.startService(stopIntent)
        Log.d(TAG, "ACTION_STOP sent")
    }
}
