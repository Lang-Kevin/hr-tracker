package com.kevin.hrtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kevin.shared.domain.SoftDeletable

@Entity
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val startedAt: Long,
    val endedAt: Long?,
    val maxHrUsed: Int,
    val restingHr: Int?,
    val note: String? = null,
    val zoneSnapshotJson: String? = null,
    override val deletedAt: Long? = null
) : SoftDeletable
