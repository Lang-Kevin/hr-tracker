package com.kevin.hrtracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index(value = ["name"], unique = true)])
data class SportLabel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isPredefined: Boolean
)
