package com.kevin.hrtracker.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kevin.hrtracker.ble.HrBleManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugDropoutEntryPoint {
    fun hrBleManager(): HrBleManager
}

class DebugDropoutReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "DebugDropout"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val ms = intent.getLongExtra("ms", 60_000L)
        val entryPoint = EntryPointAccessors.fromApplication(context, DebugDropoutEntryPoint::class.java)
        entryPoint.hrBleManager().simulateFakeDropout(ms)
        Log.d(TAG, "Dropout simulated: ${ms}ms")
    }
}
