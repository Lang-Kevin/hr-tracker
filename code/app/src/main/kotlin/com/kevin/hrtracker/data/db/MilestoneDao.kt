package com.kevin.hrtracker.data.db

import androidx.room.*
import com.kevin.hrtracker.data.entity.Milestone
import kotlinx.coroutines.flow.Flow

@Dao
interface MilestoneDao {
    @Insert
    suspend fun insertAll(milestones: List<Milestone>)

    @Query("SELECT * FROM milestones WHERE sessionId = :sessionId ORDER BY atSeconds")
    fun getBySession(sessionId: Long): Flow<List<Milestone>>

    @Query("UPDATE milestones SET label = :label WHERE id = :id")
    suspend fun updateLabel(id: Long, label: String)

    @Query("DELETE FROM milestones WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)
}
