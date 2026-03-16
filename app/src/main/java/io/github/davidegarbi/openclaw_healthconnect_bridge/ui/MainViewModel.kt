package io.github.davidegarbi.openclaw_healthconnect_bridge.ui

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.healthconnect.HealthConnectReader
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.preferences.AppPreferences
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.preferences.SecurePreferences
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.sync.SyncScheduler
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.sync.SyncWorker
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
    val syncIntervalMinutes: Long = 60L,
    val lastSyncTime: Long = 0L,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val syncError: Boolean = false
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

    fun saveSyncInterval(minutes: Long) {
        _uiState.update { it.copy(syncIntervalMinutes = minutes) }
        viewModelScope.launch {
            appPrefs.setSyncIntervalMinutes(minutes)
            if (minutes > 0) {
                syncScheduler.schedulePeriodicSync(minutes)
            } else {
                syncScheduler.cancelPeriodicSync()
            }
        }
    }

    fun syncNow() {
        _uiState.update { it.copy(isSyncing = true, syncMessage = null, syncError = false) }
        syncScheduler.syncNow()
    }

    fun onSyncResult(success: Boolean, message: String?) {
        _uiState.update {
            it.copy(
                isSyncing = false,
                syncError = !success,
                syncMessage = message ?: if (success) "Sync completed successfully" else "Sync failed"
            )
        }
    }
}
