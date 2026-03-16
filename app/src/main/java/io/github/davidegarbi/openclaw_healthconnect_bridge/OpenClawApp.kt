package io.github.davidegarbi.openclaw_healthconnect_bridge

import android.app.Application
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.preferences.AppPreferences
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.preferences.SecurePreferences
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OpenClawApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        appScope.launch {
            val appPrefs = AppPreferences(this@OpenClawApp)
            val securePrefs = SecurePreferences(this@OpenClawApp)

            val url = appPrefs.getEndpointUrl()
            val token = securePrefs.bearerToken
            val interval = appPrefs.getSyncIntervalMinutes()

            if (url.isNotBlank() && !token.isNullOrBlank() && interval > 0) {
                SyncScheduler(this@OpenClawApp).schedulePeriodicSync(interval)
            }
        }
    }
}
