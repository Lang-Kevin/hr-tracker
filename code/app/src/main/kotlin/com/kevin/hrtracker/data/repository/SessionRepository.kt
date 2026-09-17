package com.kevin.hrtracker.data.repository

import android.util.Log
import com.kevin.hrtracker.ble.HrBleManager
import com.kevin.hrtracker.ble.ParsedHr
import com.kevin.hrtracker.data.db.HrDatabase
import com.kevin.hrtracker.data.entity.HrSample
import com.kevin.hrtracker.data.entity.Session
import com.kevin.hrtracker.domain.HrZoneCalculator
import com.kevin.hrtracker.domain.ZoneBounds
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val db: HrDatabase,
    private val bleManager: HrBleManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    val activeSession = activeSessionId.flatMapLatest { id ->
        if (id == null) flowOf(null) else db.sessionDao().getByIdFlow(id)
    }

    private var sampleJob: Job? = null
    private var activeHrFlow: Flow<ParsedHr>? = null

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _pausedByConnectionLoss = MutableStateFlow(false)
    val pausedByConnectionLoss: StateFlow<Boolean> = _pausedByConnectionLoss.asStateFlow()

    init {
        scope.launch {
            db.sessionDao().closeOrphanedSessions(
                cutoff = System.currentTimeMillis(),
                endedAt = System.currentTimeMillis()
            )
            db.sessionDao().permanentlyDeleteTrashed()
            Log.d("HRTracker", "Orphaned sessions closed, trash purged")
        }
    }

    suspend fun startSession(
        label: String,
        maxHrUsed: Int,
        restingHr: Int?,
        hrSamples: Flow<ParsedHr> = bleManager.hrSamples,
        customZones: List<ZoneBounds>? = null
    ): Long {
        val zones = customZones ?: HrZoneCalculator.calculateZones(maxHrUsed, restingHr)
        val zoneJson = Json.encodeToString<List<ZoneBounds>>(zones)
        val id = db.sessionDao().insert(
            Session(
                label = label,
                startedAt = System.currentTimeMillis(),
                endedAt = null,
                maxHrUsed = maxHrUsed,
                restingHr = restingHr,
                zoneSnapshotJson = zoneJson
            )
        )
        _activeSessionId.value = id
        activeHrFlow = hrSamples
        sampleJob = launchSampleJob(id, hrSamples)
        Log.d("HRTracker", "Session $id started: $label")
        return id
    }

    private fun launchSampleJob(id: Long, hrSamples: Flow<ParsedHr>): Job = scope.launch {
        hrSamples.collect { parsed ->
            db.hrSampleDao().insert(
                HrSample(
                    sessionId = id,
                    timestampMs = System.currentTimeMillis(),
                    bpm = parsed.bpm,
                    rrIntervalsMs = if (parsed.rrIntervalsMs.isEmpty()) null
                                    else parsed.rrIntervalsMs.joinToString(",")
                )
            )
            Log.d("HRTracker", "DB: BPM=${parsed.bpm} RR=${parsed.rrIntervalsMs} → session $id")
        }
    }

    fun pause() {
        sampleJob?.cancel()
        sampleJob = null
        _isPaused.value = true
    }

    fun resume() {
        val id = _activeSessionId.value ?: return
        val flow = activeHrFlow ?: return
        sampleJob?.cancel()
        sampleJob = null
        _pausedByConnectionLoss.value = false
        sampleJob = launchSampleJob(id, flow)
        _isPaused.value = false
    }

    /** Called by service on BLE disconnect — auto-pause, does not interfere with user-pause. */
    fun autoPause() {
        if (_isPaused.value) return // already paused (user or auto)
        _pausedByConnectionLoss.value = true
        pause()
    }

    /** Called by service on BLE reconnect — only resumes if WE caused the pause. */
    fun autoResume() {
        if (!_pausedByConnectionLoss.value) return
        _pausedByConnectionLoss.value = false
        resume()
    }

    suspend fun stopSession(): Long? {
        val id = _activeSessionId.value ?: return null
        sampleJob?.cancel()
        sampleJob = null
        _activeSessionId.value = null
        _isPaused.value = false
        _pausedByConnectionLoss.value = false
        activeHrFlow = null
        db.sessionDao().closeSession(id, System.currentTimeMillis())
        Log.d("HRTracker", "Session $id stopped")
        return id
    }

    suspend fun discardSession() {
        val id = _activeSessionId.value ?: return
        sampleJob?.cancel()
        sampleJob = null
        _activeSessionId.value = null
        _isPaused.value = false
        _pausedByConnectionLoss.value = false
        activeHrFlow = null
        db.sessionDao().deleteById(id)
        Log.d("HRTracker", "Session $id discarded")
    }

    suspend fun getSamplesForSession(sessionId: Long) =
        db.hrSampleDao().getSamplesForSession(sessionId).first()

    fun getSessionsFlow() = db.sessionDao().getAllSessions()

    fun getTrashFlow() = db.sessionDao().getTrashFlow()

    suspend fun deleteSessionsByIds(ids: List<Long>) =
        db.sessionDao().softDeleteByIds(ids, System.currentTimeMillis())

    suspend fun restoreSessionsByIds(ids: List<Long>) = db.sessionDao().restoreByIds(ids)
}
