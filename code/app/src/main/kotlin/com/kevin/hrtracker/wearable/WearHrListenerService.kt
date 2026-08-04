package com.kevin.hrtracker.wearable

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.kevin.hrtracker.ble.ParsedHr
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.nio.ByteBuffer
import java.nio.ByteOrder

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WearHrEntryPoint {
    fun wearableHrSource(): WearableHrSource
}

class WearHrListenerService : WearableListenerService() {

    private val wearableHrSource: WearableHrSource by lazy {
        EntryPointAccessors.fromApplication(applicationContext, WearHrEntryPoint::class.java)
            .wearableHrSource()
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != "/hr_sample") return
        val buf = ByteBuffer.wrap(event.data).order(ByteOrder.LITTLE_ENDIAN)
        if (buf.remaining() < 3) return
        val bpm = buf.short.toInt() and 0xFFFF
        val rrCount = buf.get().toInt() and 0xFF
        val rrList = mutableListOf<Int>()
        repeat(rrCount) {
            if (buf.remaining() >= 2) rrList.add(buf.short.toInt() and 0xFFFF)
        }
        if (bpm !in 30..220) return
        wearableHrSource.tryEmit(ParsedHr(bpm, rrList))
    }
}
