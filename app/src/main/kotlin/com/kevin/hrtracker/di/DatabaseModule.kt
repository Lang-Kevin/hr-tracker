package com.kevin.hrtracker.di

import android.content.Context
import androidx.room.Room
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

    private val predefinedLabels = listOf(
        "Volleyball", "Beach-Volleyball", "Krafttraining",
        "Cardio", "Laufen", "Radfahren", "Schwimmen", "Yoga"
    )

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HrDatabase =
        Room.databaseBuilder(context, HrDatabase::class.java, "hr_tracker.db")
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
