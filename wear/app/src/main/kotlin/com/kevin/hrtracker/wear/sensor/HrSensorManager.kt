package com.kevin.hrtracker.wear.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class HrSensorManager(private val context: Context) {

    fun hrFlow(): Flow<Int> = callbackFlow {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        var lastAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // ponytail: no-contact/unreliable guard — drop invalid readings before forwarding
                if (lastAccuracy == SensorManager.SENSOR_STATUS_NO_CONTACT ||
                    lastAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) return
                if (event.values.isEmpty()) return
                val bpm = event.values[0].toInt()
                if (bpm in 30..220) trySend(bpm)
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                lastAccuracy = accuracy
            }
        }
        if (sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        awaitClose { manager.unregisterListener(listener) }
    }
}
