package com.kevin.hrtracker.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kevin.hrtracker.data.db.HrDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE Session ADD COLUMN zoneSnapshotJson TEXT")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DELETE FROM SportLabel WHERE isPredefined = 1")
            listOf("Allg. Training", "Beachvolleyball", "Trainingsbike", "Volleyball").forEach { name ->
                database.execSQL("INSERT INTO SportLabel (name, isPredefined) VALUES (?, 1)", arrayOf(name))
            }
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE Session ADD COLUMN deletedAt INTEGER DEFAULT NULL")
        }
    }

    private val predefinedLabels = listOf("Allg. Training", "Beachvolleyball", "Trainingsbike", "Volleyball")

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HrDatabase =
        Room.databaseBuilder(context, HrDatabase::class.java, "hr_tracker.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    predefinedLabels.forEach { name ->
                        db.execSQL(
                            "INSERT INTO SportLabel (name, isPredefined) VALUES (?, 1)",
                            arrayOf(name)
                        )
                    }
                }
            })
            .build()

    @Provides
    fun provideSessionDao(db: HrDatabase) = db.sessionDao()

    @Provides
    fun provideHrSampleDao(db: HrDatabase) = db.hrSampleDao()

    @Provides
    fun provideSportLabelDao(db: HrDatabase) = db.sportLabelDao()
}
