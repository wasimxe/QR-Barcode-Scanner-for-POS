package com.scanner.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Manages app settings using DataStore.
 * Stores scan mode, server configuration, and other preferences.
 */
class AppSettings(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scanner_settings")

        private val SCAN_MODE_KEY = intPreferencesKey("scan_mode")
        private val SERVER_PORT_KEY = intPreferencesKey("server_port")
        private val LAST_SERVER_URL_KEY = stringPreferencesKey("last_server_url")
    }

    /**
     * Get current scan mode
     */
    val scanMode: Flow<ScanMode> = context.dataStore.data.map { preferences ->
        val modeIndex = preferences[SCAN_MODE_KEY] ?: 0
        ScanMode.values().getOrNull(modeIndex) ?: ScanMode.WIFI_WEBSOCKET
    }

    /**
     * Get server port
     */
    val serverPort: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SERVER_PORT_KEY] ?: 8080
    }

    /**
     * Get last server URL (for display purposes)
     */
    val lastServerUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LAST_SERVER_URL_KEY] ?: ""
    }

    /**
     * Update scan mode
     */
    suspend fun setScanMode(mode: ScanMode) {
        context.dataStore.edit { preferences ->
            preferences[SCAN_MODE_KEY] = mode.ordinal
        }
    }

    /**
     * Update server port
     */
    suspend fun setServerPort(port: Int) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_PORT_KEY] = port
        }
    }

    /**
     * Save last server URL
     */
    suspend fun setLastServerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SERVER_URL_KEY] = url
        }
    }
}
