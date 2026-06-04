package com.kevin.hrtracker.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.kevin.hrtracker.data.db.HrDatabase
import com.kevin.hrtracker.data.entity.HrSample
import com.kevin.hrtracker.data.entity.Session
import com.kevin.hrtracker.domain.HrZoneCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionExporter @Inject constructor(
    private val db: HrDatabase
) {
    private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        .withZone(ZoneId.systemDefault())

    suspend fun buildShareIntent(context: Context, sessionId: Long): Intent? =
        withContext(Dispatchers.IO) {
            val session = db.sessionDao().getById(sessionId) ?: return@withContext null
            val samples = db.hrSampleDao().getSamplesOnce(sessionId)
            val json = buildJson(session, samples)
            val file = writeToCache(context, sessionId, json)
            shareIntent(context, file)
        }

    private fun buildJson(session: Session, samples: List<HrSample>): String {
        val zones = HrZoneCalculator.calculateZones(session.maxHrUsed, session.restingHr)
        val zoneModel = if (session.restingHr != null) "karvonen" else "percent_hrmax"
        val durationS = session.endedAt?.let { (it - session.startedAt) / 1000 } ?: 0L
        val bpms = samples.map { it.bpm }
        val timeInZone = samples.groupBy { HrZoneCalculator.zoneFor(it.bpm, zones) }
            .mapValues { (_, s) -> s.size }

        val root = buildJsonObject {
            putJsonObject("session") {
                put("id", session.id)
                put("label", session.label)
                put("started_at", ts(session.startedAt))
                put("ended_at", session.endedAt?.let { JsonPrimitive(ts(it)) } ?: JsonNull)
                put("duration_s", durationS)
                put("max_hr_used", session.maxHrUsed)
                put("resting_hr", session.restingHr?.let { JsonPrimitive(it) } ?: JsonNull)
                put("zone_model", zoneModel)
                putJsonObject("zones") {
                    zones.forEach { z ->
                        put("z${z.zone}", JsonArray(listOf(JsonPrimitive(z.lo), JsonPrimitive(z.hi))))
                    }
                }
            }
            putJsonObject("summary") {
                put("avg_bpm", if (bpms.isEmpty()) 0 else bpms.average().toInt())
                put("max_bpm", bpms.maxOrNull() ?: 0)
                put("min_bpm", bpms.minOrNull() ?: 0)
                putJsonObject("time_in_zone_s") {
                    for (z in 1..5) put("z$z", timeInZone[z] ?: 0)
                }
            }
            putJsonArray("samples") {
                samples.forEach { s ->
                    addJsonObject {
                        put("t", ts(s.timestampMs))
                        put("elapsed_s", (s.timestampMs - session.startedAt) / 1000)
                        put("bpm", s.bpm)
                        val rr = s.rrIntervalsMs
                            ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
                            ?: emptyList()
                        put("rr_ms", JsonArray(rr.map { JsonPrimitive(it) }))
                        put("zone", HrZoneCalculator.zoneFor(s.bpm, zones))
                    }
                }
            }
        }
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), root)
    }

    private fun ts(epochMs: Long): String =
        formatter.format(Instant.ofEpochMilli(epochMs))

    private fun writeToCache(context: Context, sessionId: Long, json: String): File {
        val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
        return File(dir, "session_$sessionId.json").also { it.writeText(json) }
    }

    private fun shareIntent(context: Context, file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.provider", file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
