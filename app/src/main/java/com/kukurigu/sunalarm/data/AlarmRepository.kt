package com.kukurigu.sunalarm.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistent store for the user's alarm configuration and chosen location.
 *
 * The alarms stream always emits a complete list with one entry per dawn phase,
 * falling back to [DefaultAlarms] when nothing has been persisted yet (or when the
 * stored data cannot be decoded).
 */
interface AlarmRepository {
    val alarms: Flow<List<AlarmConfig>>
    val location: Flow<AppLocation?>

    /** Replaces the stored config for the alarm with the same [AlarmConfig.phase]. */
    suspend fun updateAlarm(config: AlarmConfig)

    suspend fun setLocation(location: AppLocation)

    suspend fun getAlarmsOnce(): List<AlarmConfig>

    suspend fun getLocationOnce(): AppLocation?
}

/** Top-level Preferences DataStore for the app, named "kukurigu". */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("kukurigu")

/**
 * [AlarmRepository] backed by a Preferences DataStore. The whole alarm list and the
 * location are serialized to JSON strings via kotlinx.serialization and stored under
 * single keys.
 */
class DataStoreAlarmRepository(context: Context) : AlarmRepository {

    private val dataStore: DataStore<Preferences> = context.applicationContext.dataStore

    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val alarmsKey = stringPreferencesKey("alarms_json")
    private val locationKey = stringPreferencesKey("location_json")

    override val alarms: Flow<List<AlarmConfig>> = dataStore.data.map { prefs ->
        decodeAlarms(prefs[alarmsKey])
    }

    override val location: Flow<AppLocation?> = dataStore.data.map { prefs ->
        decodeLocation(prefs[locationKey])
    }

    override suspend fun updateAlarm(config: AlarmConfig) {
        dataStore.edit { prefs ->
            val current = decodeAlarms(prefs[alarmsKey])
            val updated = if (current.any { it.phase == config.phase }) {
                current.map { if (it.phase == config.phase) config else it }
            } else {
                current + config
            }
            prefs[alarmsKey] = json.encodeToString(updated)
        }
    }

    override suspend fun setLocation(location: AppLocation) {
        dataStore.edit { prefs ->
            prefs[locationKey] = json.encodeToString(location)
        }
    }

    override suspend fun getAlarmsOnce(): List<AlarmConfig> = alarms.first()

    override suspend fun getLocationOnce(): AppLocation? = location.first()

    /** Decodes the stored alarm list, falling back to defaults on absence or error. */
    private fun decodeAlarms(raw: String?): List<AlarmConfig> {
        if (raw.isNullOrEmpty()) return DefaultAlarms.list()
        return try {
            val decoded = json.decodeFromString<List<AlarmConfig>>(raw)
            if (decoded.isEmpty()) DefaultAlarms.list() else decoded
        } catch (e: Exception) {
            DefaultAlarms.list()
        }
    }

    /** Decodes the stored location, returning null on absence or error. */
    private fun decodeLocation(raw: String?): AppLocation? {
        if (raw.isNullOrEmpty()) return null
        return try {
            json.decodeFromString<AppLocation>(raw)
        } catch (e: Exception) {
            null
        }
    }
}
