package io.github.davidegarbi.openclaw_healthconnect_bridge.ui

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.healthconnect.HealthConnectReader
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.preferences.AppPreferences
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.preferences.SecurePreferences
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val healthConnectStatus: Int = HealthConnectClient.SDK_UNAVAILABLE,
    val permissionsGranted: Boolean = false,
    val endpointUrl: String = "",
    val bearerToken: String = "",
    val autoSyncEnabled: Boolean = false,
    val syncIntervalMinutes: Long = 60L,
    val backgroundSyncRangeHours: Long = 24L,
    val lastSyncTime: Long = 0L,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val syncError: Boolean = false,
    val showSyncRangeSheet: Boolean = false,
    val lastManualSyncRangeHours: Long = 24L,
    val syncingRangeLabel: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appPrefs = AppPreferences(application)
    private val securePrefs = SecurePreferences(application)
    val syncScheduler = SyncScheduler(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        val status = HealthConnectReader.isAvailable(application)
        _uiState.update { it.copy(healthConnectStatus = status) }

        viewModelScope.launch {
            appPrefs.endpointUrl.collect { url ->
                _uiState.update { it.copy(endpointUrl = url) }
            }
        }
        viewModelScope.launch {
            appPrefs.lastSyncTime.collect { time ->
                _uiState.update { it.copy(lastSyncTime = time) }
            }
        }
        viewModelScope.launch {
            appPrefs.syncIntervalMinutes.collect { interval ->
                _uiState.update { it.copy(syncIntervalMinutes = interval) }
            }
        }
        viewModelScope.launch {
            appPrefs.autoSyncEnabled.collect { enabled ->
                _uiState.update { it.copy(autoSyncEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            appPrefs.backgroundSyncRangeHours.collect { hours ->
                _uiState.update { it.copy(backgroundSyncRangeHours = hours) }
            }
        }
        viewModelScope.launch {
            appPrefs.lastManualSyncRangeHours.collect { hours ->
                _uiState.update { it.copy(lastManualSyncRangeHours = hours) }
            }
        }
        _uiState.update { it.copy(bearerToken = securePrefs.bearerToken ?: "") }
    }

    fun onPermissionsResult(granted: Boolean) {
        _uiState.update { it.copy(permissionsGranted = granted) }
    }

    fun saveEndpointUrl(url: String) {
        _uiState.update { it.copy(endpointUrl = url) }
        viewModelScope.launch { appPrefs.setEndpointUrl(url) }
    }

    fun saveBearerToken(token: String) {
        _uiState.update { it.copy(bearerToken = token) }
        securePrefs.bearerToken = token
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        _uiState.update { it.copy(autoSyncEnabled = enabled) }
        viewModelScope.launch {
            appPrefs.setAutoSyncEnabled(enabled)
            if (enabled) {
                val interval = appPrefs.getSyncIntervalMinutes()
                syncScheduler.schedulePeriodicSync(interval)
            } else {
                syncScheduler.cancelPeriodicSync()
            }
        }
    }

    fun saveSyncInterval(minutes: Long) {
        _uiState.update { it.copy(syncIntervalMinutes = minutes) }
        viewModelScope.launch {
            appPrefs.setSyncIntervalMinutes(minutes)
            if (_uiState.value.autoSyncEnabled) {
                syncScheduler.schedulePeriodicSync(minutes)
            }
        }
    }

    fun saveBackgroundSyncRange(hours: Long) {
        _uiState.update { it.copy(backgroundSyncRangeHours = hours) }
        viewModelScope.launch { appPrefs.setBackgroundSyncRangeHours(hours) }
    }

    fun showSyncRangeSheet() {
        _uiState.update { it.copy(showSyncRangeSheet = true) }
    }

    fun dismissSyncRangeSheet() {
        _uiState.update { it.copy(showSyncRangeSheet = false) }
    }

    fun syncNowWithRange(rangeHours: Long) {
        val label = when (rangeHours) {
            24L -> "last 24 hours"
            168L -> "last 7 days"
            720L -> "last 30 days"
            2160L -> "last 90 days"
            else -> "last ${rangeHours}h"
        }
        _uiState.update {
            it.copy(
                showSyncRangeSheet = false,
                isSyncing = true,
                syncMessage = null,
                syncError = false,
                syncingRangeLabel = label
            )
        }
        viewModelScope.launch {
            appPrefs.setLastManualSyncRangeHours(rangeHours)
        }
        syncScheduler.syncNow(rangeHours)
    }

    fun onSyncResult(success: Boolean, message: String?) {
        _uiState.update {
            it.copy(
                isSyncing = false,
                syncError = !success,
                syncMessage = message ?: if (success) "Sync completed successfully" else "Sync failed",
                syncingRangeLabel = null
            )
        }
    }
}
