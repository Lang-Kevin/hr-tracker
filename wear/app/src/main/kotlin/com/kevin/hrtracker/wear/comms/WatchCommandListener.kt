package com.kevin.hrtracker.wear.comms

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.kevin.hrtracker.wear.WatchState

class WatchCommandListener : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/session_state" && event.data.isNotEmpty()) {
            WatchState.setRecording(event.data[0] == 1.toByte())
        }
    }
}
