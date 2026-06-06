package com.kevin.hrtracker.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun readLatestRestingHr(): Int? {
        if (!isAvailable) return null
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val end = Instant.now()
            val start = end.minus(24, ChronoUnit.HOURS)
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            val samples = response.records
                .flatMap { it.samples }
                .map { it.beatsPerMinute.toInt() }
                .filter { it in 20..100 }
            if (samples.isEmpty()) null
            else {
                val sorted = samples.sorted()
                sorted[sorted.size / 2]
            }
        } catch (_: Exception) {
            null
        }
    }
}
