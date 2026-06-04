package com.kevin.hrtracker.data.db

import androidx.room.*
import com.kevin.hrtracker.data.entity.SportLabel
import kotlinx.coroutines.flow.Flow

@Dao
interface SportLabelDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(label: SportLabel): Long

    @Query("SELECT * FROM SportLabel ORDER BY isPredefined DESC, name ASC")
    fun getAllLabels(): Flow<List<SportLabel>>

    @Delete
    suspend fun delete(label: SportLabel)
}
