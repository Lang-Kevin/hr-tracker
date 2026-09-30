package com.kevin.hrtracker.data.db

import androidx.room.*
import com.kevin.hrtracker.data.entity.Session
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: Session): Long

    @Query("UPDATE Session SET endedAt = :endedAt WHERE id = :id")
    suspend fun closeSession(id: Long, endedAt: Long)

    @Query("SELECT * FROM Session WHERE deletedAt IS NULL ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Query("SELECT * FROM Session WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getTrashFlow(): Flow<List<Session>>

    @Query("SELECT * FROM Session WHERE id = :id")
    suspend fun getById(id: Long): Session?

    @Query("SELECT * FROM Session WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<Session?>

    // Closes sessions left open by a crash or app-kill (startedAt older than cutoff, endedAt null)
    @Query("UPDATE Session SET endedAt = :endedAt WHERE endedAt IS NULL AND startedAt < :cutoff AND deletedAt IS NULL")
    suspend fun closeOrphanedSessions(cutoff: Long, endedAt: Long)

    @Query("UPDATE Session SET label = :label WHERE id = :id")
    suspend fun updateLabel(id: Long, label: String)

    @Query("UPDATE Session SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String)

    @Query("""
        UPDATE Session SET activeMs = :activeMs, avgBpm = :avgBpm, trimp = :trimp, hrr60 = :hrr60,
            rmssd = :rmssd, metricsVersion = :version
        WHERE id = :id
    """)
    suspend fun updateMetrics(
        id: Long, activeMs: Long, avgBpm: Int?, trimp: Int?, hrr60: Int?, rmssd: Int?, version: Int
    )

    /** Beendete Sessions (inkl. Papierkorb), deren Kennzahlen fehlen oder veraltet sind. */
    @Query("SELECT * FROM Session WHERE endedAt IS NOT NULL AND metricsVersion < :version")
    suspend fun getWithStaleMetrics(version: Int): List<Session>

    @Query("UPDATE Session SET rpe = :rpe WHERE id = :id")
    suspend fun updateRpe(id: Long, rpe: Int?)

    @Query("SELECT * FROM Session WHERE deletedAt IS NULL AND label = :label AND endedAt IS NOT NULL ORDER BY startedAt DESC")
    fun getByLabelFlow(label: String): Flow<List<Session>>

    @Query("UPDATE Session SET deletedAt = :now WHERE id IN (:ids)")
    suspend fun softDeleteByIds(ids: List<Long>, now: Long)

    @Query("UPDATE Session SET deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restoreByIds(ids: List<Long>)

    @Query("DELETE FROM Session WHERE deletedAt IS NOT NULL")
    suspend fun permanentlyDeleteTrashed()

    @Query("DELETE FROM Session WHERE id = :id")
    suspend fun deleteById(id: Long)
}
