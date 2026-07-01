package com.kevin.hrtracker.data.db

import androidx.room.*
import com.kevin.hrtracker.data.entity.HrSample
import kotlinx.coroutines.flow.Flow

data class SessionAvgBpm(val sessionId: Long, val avgBpm: Int)

@Dao
interface HrSampleDao {
    @Insert
    suspend fun insert(sample: HrSample): Long

    @Query("SELECT * FROM HrSample WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun getSamplesForSession(sessionId: Long): Flow<List<HrSample>>

    @Query("SELECT COUNT(*) FROM HrSample WHERE sessionId = :sessionId")
    fun getSampleCount(sessionId: Long): Flow<Int>

    @Query("SELECT * FROM HrSample WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getSamplesOnce(sessionId: Long): List<HrSample>

    @Query("SELECT CAST(AVG(bpm) AS INTEGER) FROM HrSample")
    fun getGlobalAvgBpm(): Flow<Int?>

    @Query("SELECT sessionId, CAST(AVG(bpm) AS INTEGER) AS avgBpm FROM HrSample GROUP BY sessionId")
    fun getSessionAvgBpms(): Flow<List<SessionAvgBpm>>

    @Query("SELECT AVG(bpm) FROM HrSample WHERE sessionId IN (:sessionIds)")
    suspend fun getAvgBpmForSessions(sessionIds: List<Long>): Double?
}
