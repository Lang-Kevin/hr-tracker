package com.kevin.hrtracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kevin.hrtracker.data.entity.HrSample
import com.kevin.hrtracker.data.entity.Milestone
import com.kevin.hrtracker.data.entity.Session
import com.kevin.hrtracker.data.entity.SportLabel

@Database(
    entities = [Session::class, HrSample::class, SportLabel::class, Milestone::class],
    version = 6,
    exportSchema = false
)
abstract class HrDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun hrSampleDao(): HrSampleDao
    abstract fun sportLabelDao(): SportLabelDao
    abstract fun milestoneDao(): MilestoneDao
}
