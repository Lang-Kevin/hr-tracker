package com.kevin.hrtracker.wear.comms

import android.content.Context
import com.google.android.gms.wearable.Wearable
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PhoneMessenger(private val context: Context) {

    fun sendHrSample(bpm: Int) {
        val buf = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(bpm.toShort())
        buf.put(0.toByte())  // RR count = 0 (Galaxy Watch SensorManager does not expose RR)
        val payload = buf.array()
        Wearable.getNodeClient(context).connectedNodes.addOnSuccessListener { nodes ->
            val phone = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull() ?: return@addOnSuccessListener
            Wearable.getMessageClient(context).sendMessage(phone.id, "/hr_sample", payload)
        }
    }
}
