package com.kevin.hrtracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kevin.hrtracker.domain.SavedDevice
import com.kevin.hrtracker.domain.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val AGE              = intPreferencesKey("age")
        val MANUAL_MAX_HR    = intPreferencesKey("manual_max_hr")
        val RESTING_HR       = intPreferencesKey("resting_hr")
        val TARGET_ZONE      = intPreferencesKey("target_zone")
        val SAVED_DEVICE_MAC = stringPreferencesKey("saved_device_mac")
        val SAVED_DEVICES    = stringPreferencesKey("saved_devices")
        val AUTO_CONNECT     = booleanPreferencesKey("auto_connect")
    }

    val userSettings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            age          = prefs[Keys.AGE] ?: 30,
            manualMaxHr  = prefs[Keys.MANUAL_MAX_HR],
            restingHr    = prefs[Keys.RESTING_HR],
            targetZone   = prefs[Keys.TARGET_ZONE] ?: 2
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

    suspend fun setTargetZone(zone: Int) {
        dataStore.edit { it[Keys.TARGET_ZONE] = zone.coerceIn(1, 5) }
    }

    val savedDeviceAddress: Flow<String?> = dataStore.data.map { it[Keys.SAVED_DEVICE_MAC] }

    suspend fun saveDeviceAddress(address: String) {
        dataStore.edit { it[Keys.SAVED_DEVICE_MAC] = address }
    }

    suspend fun clearSavedDevice() {
        dataStore.edit { it.remove(Keys.SAVED_DEVICE_MAC) }
    }

    val savedDevices: Flow<List<SavedDevice>> = dataStore.data.map { prefs ->
        prefs[Keys.SAVED_DEVICES]?.let { json ->
            runCatching { Json.decodeFromString<List<SavedDevice>>(json) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun addSavedDevice(device: SavedDevice) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.SAVED_DEVICES]?.let {
                runCatching { Json.decodeFromString<List<SavedDevice>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = listOf(device) + current.filter { it.address != device.address }
            prefs[Keys.SAVED_DEVICES] = Json.encodeToString(updated)
        }
    }

    suspend fun removeSavedDevice(address: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.SAVED_DEVICES]?.let {
                runCatching { Json.decodeFromString<List<SavedDevice>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[Keys.SAVED_DEVICES] = Json.encodeToString(current.filter { it.address != address })
        }
    }

    val autoConnect: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_CONNECT] ?: false }

    suspend fun setAutoConnect(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_CONNECT] = enabled }
    }
}
