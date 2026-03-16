package io.github.davidegarbi.openclaw_healthconnect_bridge.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

class AppPreferences(private val context: Context) {

    val endpointUrl: Flow<String>
        get() = context.dataStore.data.map { it[KEY_ENDPOINT_URL] ?: "" }

    val lastSyncTime: Flow<Long>
        get() = context.dataStore.data.map { it[KEY_LAST_SYNC_TIME] ?: 0L }

    val syncIntervalMinutes: Flow<Long>
        get() = context.dataStore.data.map { it[KEY_SYNC_INTERVAL] ?: 60L }

    suspend fun getEndpointUrl(): String =
        context.dataStore.data.first()[KEY_ENDPOINT_URL] ?: ""

    suspend fun getLastSyncTime(): Long =
        context.dataStore.data.first()[KEY_LAST_SYNC_TIME] ?: 0L

    suspend fun getSyncIntervalMinutes(): Long =
        context.dataStore.data.first()[KEY_SYNC_INTERVAL] ?: 60L

    suspend fun setEndpointUrl(url: String) {
        context.dataStore.edit { it[KEY_ENDPOINT_URL] = url }
    }

    suspend fun setLastSyncTime(time: Long) {
        context.dataStore.edit { it[KEY_LAST_SYNC_TIME] = time }
    }

    suspend fun setSyncIntervalMinutes(minutes: Long) {
        context.dataStore.edit { it[KEY_SYNC_INTERVAL] = minutes }
    }

    companion object {
        private val KEY_ENDPOINT_URL = stringPreferencesKey("endpoint_url")
        private val KEY_LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        private val KEY_SYNC_INTERVAL = longPreferencesKey("sync_interval_minutes")
    }
}
