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

    @Query("SELECT * FROM Session ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Query("SELECT * FROM Session WHERE id = :id")
    suspend fun getById(id: Long): Session?
}
