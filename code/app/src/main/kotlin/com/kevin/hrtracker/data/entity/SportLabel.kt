package com.kevin.hrtracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kevin.shared.domain.LabelItem

@Entity(indices = [Index(value = ["name"], unique = true)])
data class SportLabel(
    @PrimaryKey(autoGenerate = true) override val id: Long = 0,
    override val name: String,
    override val isPredefined: Boolean
) : LabelItem
