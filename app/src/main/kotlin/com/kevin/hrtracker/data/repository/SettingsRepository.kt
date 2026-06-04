package com.kevin.hrtracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.kevin.hrtracker.domain.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val AGE           = intPreferencesKey("age")
        val MANUAL_MAX_HR = intPreferencesKey("manual_max_hr")
        val RESTING_HR    = intPreferencesKey("resting_hr")
    }

    val userSettings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            age          = prefs[Keys.AGE] ?: 30,
            manualMaxHr  = prefs[Keys.MANUAL_MAX_HR],
            restingHr    = prefs[Keys.RESTING_HR]
        )
    }

    suspend fun setAge(age: Int) {
        dataStore.edit { it[Keys.AGE] = age }
    }

    suspend fun setManualMaxHr(maxHr: Int?) {
        dataStore.edit {
            if (maxHr != null) it[Keys.MANUAL_MAX_HR] = maxHr else it.remove(Keys.MANUAL_MAX_HR)
        }
    }

    suspend fun setRestingHr(restingHr: Int?) {
        dataStore.edit {
            if (restingHr != null) it[Keys.RESTING_HR] = restingHr else it.remove(Keys.RESTING_HR)
        }
    }
}
