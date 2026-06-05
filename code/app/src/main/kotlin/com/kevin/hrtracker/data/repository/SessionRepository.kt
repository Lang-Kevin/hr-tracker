package com.kevin.hrtracker.data.repository

import android.util.Log
import com.kevin.hrtracker.ble.HrBleManager
import com.kevin.hrtracker.data.db.HrDatabase
import com.kevin.hrtracker.data.entity.HrSample
import com.kevin.hrtracker.data.entity.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        // On startup SessionRepository has no active session — close any DB sessions
        // left open by a previous crash, kill, or the double-start bug.
        scope.launch {
            db.sessionDao().closeOrphanedSessions(
                cutoff = System.currentTimeMillis(), // all open sessions
                endedAt = System.currentTimeMillis()
            )
            Log.d("HRTracker", "Orphaned sessions cleaned up")
        }
    }

    suspend fun startSession(label: String, maxHrUsed: Int, restingHr: Int?): Long {
        val id = db.sessionDao().insert(
            Session(
                label = label,
                startedAt = System.currentTimeMillis(),
                endedAt = null,
                maxHrUsed = maxHrUsed,
                restingHr = restingHr
            )
        )
        _activeSessionId.value = id
        sampleJob = scope.launch {
            bleManager.hrSamples.collect { parsed ->
                db.hrSampleDao().insert(
                    HrSample(
                        sessionId = id,
                        timestampMs = System.currentTimeMillis(),
                        bpm = parsed.bpm,
                        rrIntervalsMs = if (parsed.rrIntervalsMs.isEmpty()) null
                                        else parsed.rrIntervalsMs.joinToString(",")
                    )
                )
                Log.d("HRTracker", "DB: BPM=${parsed.bpm} → session $id")
            }
        }
        Log.d("HRTracker", "Session $id started: $label")
        return id
    }

    suspend fun stopSession() {
        val id = _activeSessionId.value ?: return
        sampleJob?.cancel()
        sampleJob = null
        _activeSessionId.value = null
        db.sessionDao().closeSession(id, System.currentTimeMillis())
        Log.d("HRTracker", "Session $id stopped")
    }

    fun getSessionsFlow() = db.sessionDao().getAllSessions()

    suspend fun deleteSessionsByIds(ids: List<Long>) = db.sessionDao().deleteByIds(ids)
}
