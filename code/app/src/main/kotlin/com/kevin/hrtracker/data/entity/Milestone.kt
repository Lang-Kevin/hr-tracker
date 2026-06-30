package com.kevin.hrtracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "milestones",
    indices = [Index("sessionId")]
)
data class Milestone(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val atSeconds: Long,
    val label: String = ""
)
