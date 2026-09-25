package com.kevin.hrtracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.kevin.hrtracker.domain.WidgetVariant
import com.kevin.shared.domain.SavedDevice
import com.kevin.hrtracker.domain.UserSettings
import com.kevin.hrtracker.domain.ZoneBounds
import com.kevin.hrtracker.domain.ZoneModel
import com.kevin.hrtracker.domain.Sex
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
        val ONBOARDING_DONE  = booleanPreferencesKey("onboarding_done")
        val CUSTOM_ZONES     = stringPreferencesKey("custom_zones")
        val TUTORIAL_SEEN    = stringSetPreferencesKey("tutorial_seen_screens")
        val DEBUG_MODE       = booleanPreferencesKey("debug_mode")
        val CHART_DYNAMIC_SCALE = booleanPreferencesKey("chart_dynamic_scale")
        val WIDGET_VARIANT   = stringPreferencesKey("widget_variant")
        val WEIGHT_KG        = intPreferencesKey("weight_kg")
        val SEX              = stringPreferencesKey("sex")
        val ZONE_MODEL       = stringPreferencesKey("zone_model")
        val AUTO_RESTING_HR  = booleanPreferencesKey("auto_resting_hr")
    }

    val userSettings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            age          = prefs[Keys.AGE] ?: 30,
            manualMaxHr  = prefs[Keys.MANUAL_MAX_HR],
            restingHr    = prefs[Keys.RESTING_HR],
            targetZone   = prefs[Keys.TARGET_ZONE] ?: 2,
            customZones  = prefs[Keys.CUSTOM_ZONES]?.let {
                runCatching { Json.decodeFromString<List<ZoneBounds>>(it) }.getOrNull()
            },
            chartDynamicScale = prefs[Keys.CHART_DYNAMIC_SCALE] ?: true,
            widgetVariant = prefs[Keys.WIDGET_VARIANT]?.let {
                runCatching { WidgetVariant.valueOf(it) }.getOrDefault(WidgetVariant.STANDARD)
            } ?: WidgetVariant.STANDARD,
            weightKg = prefs[Keys.WEIGHT_KG],
            sex      = prefs[Keys.SEX]?.let { runCatching { Sex.valueOf(it) }.getOrNull() },
            zoneModel = prefs[Keys.ZONE_MODEL]?.let { runCatching { ZoneModel.valueOf(it) }.getOrNull() } ?: ZoneModel.HR_MAX,
            autoRestingHr = prefs[Keys.AUTO_RESTING_HR] ?: false
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

    suspend fun setWeightKg(weightKg: Int?) {
        dataStore.edit {
            if (weightKg != null) it[Keys.WEIGHT_KG] = weightKg else it.remove(Keys.WEIGHT_KG)
        }
    }

    suspend fun setSex(sex: Sex?) {
        dataStore.edit {
            if (sex != null) it[Keys.SEX] = sex.name else it.remove(Keys.SEX)
        }
    }

    suspend fun setAutoRestingHr(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_RESTING_HR] = enabled }
    }

    suspend fun setTargetZone(zone: Int) {
        dataStore.edit { it[Keys.TARGET_ZONE] = zone.coerceIn(1, 5) }
    }

    suspend fun setZoneModel(model: ZoneModel) {
        dataStore.edit { it[Keys.ZONE_MODEL] = model.name }
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

    val onboardingDone: Flow<Boolean> = dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun setCustomZones(zones: List<ZoneBounds>?) {
        dataStore.edit {
            if (zones != null) it[Keys.CUSTOM_ZONES] = Json.encodeToString(zones)
            else it.remove(Keys.CUSTOM_ZONES)
        }
    }

    val tutorialSeenScreens: Flow<Set<String>> = dataStore.data.map { it[Keys.TUTORIAL_SEEN] ?: emptySet() }

    suspend fun markTutorialSeen(screenKey: String) {
        dataStore.edit { prefs ->
            prefs[Keys.TUTORIAL_SEEN] = (prefs[Keys.TUTORIAL_SEEN] ?: emptySet()) + screenKey
        }
    }

    val debugMode: Flow<Boolean> = dataStore.data.map { it[Keys.DEBUG_MODE] ?: false }

    suspend fun setDebugMode(enabled: Boolean) {
        dataStore.edit { it[Keys.DEBUG_MODE] = enabled }
    }

    suspend fun setChartDynamicScale(enabled: Boolean) {
        dataStore.edit { it[Keys.CHART_DYNAMIC_SCALE] = enabled }
    }

    suspend fun setWidgetVariant(variant: WidgetVariant) {
        dataStore.edit { it[Keys.WIDGET_VARIANT] = variant.name }
    }
}
