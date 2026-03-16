package io.github.davidegarbi.openclaw_healthconnect_bridge.data.sync

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.healthconnect.HealthConnectReader
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.network.OpenClawClient
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.network.SyncMetadata
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.network.SyncPayload
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.preferences.AppPreferences
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.preferences.SecurePreferences
import java.time.Instant

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appPrefs = AppPreferences(applicationContext)
        val securePrefs = SecurePreferences(applicationContext)

        val endpointUrl = appPrefs.getEndpointUrl()
        val bearerToken = securePrefs.bearerToken

        if (endpointUrl.isBlank() || bearerToken.isNullOrBlank()) {
            Log.w(TAG, "Sync skipped: endpoint or token not configured")
            return Result.failure()
        }

        val lastSync = appPrefs.getLastSyncTime()
        val from = if (lastSync > 0) Instant.ofEpochMilli(lastSync) else Instant.now().minusSeconds(86400)
        val to = Instant.now()

        return try {
            val reader = HealthConnectReader(applicationContext)
            val snapshot = reader.readSnapshot(from, to)

            val deviceId = Settings.Secure.getString(
                applicationContext.contentResolver,
                Settings.Secure.ANDROID_ID
            )

            val payload = SyncPayload(
                metadata = SyncMetadata(
                    deviceId = deviceId,
                    syncTimestamp = Instant.now().toString(),
                    dataFrom = from.toString(),
                    dataTo = to.toString()
                ),
                heartRate = snapshot.heartRate,
                steps = snapshot.steps,
                sleep = snapshot.sleep,
                calories = snapshot.calories,
                spo2 = snapshot.spo2,
                distance = snapshot.distance,
                exercise = snapshot.exercise,
                bloodPressure = snapshot.bloodPressure,
                temperature = snapshot.temperature,
                respiratoryRate = snapshot.respiratoryRate,
                bloodGlucose = snapshot.bloodGlucose,
                weight = snapshot.weight,
                height = snapshot.height
            )

            val api = OpenClawClient.create(endpointUrl, bearerToken)
            val response = api.sync(payload)

            if (response.isSuccessful) {
                appPrefs.setLastSyncTime(to.toEpochMilli())
                Log.i(TAG, "Sync successful")
                Result.success()
            } else {
                Log.w(TAG, "Sync failed: HTTP ${response.code()}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        const val WORK_NAME_PERIODIC = "health_connect_periodic_sync"
        const val WORK_NAME_ONE_SHOT = "health_connect_one_shot_sync"
    }
}
