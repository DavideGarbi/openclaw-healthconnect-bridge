package io.github.davidegarbi.openclaw_healthconnect_bridge.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.davidegarbi.openclaw_healthconnect_bridge.R
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.healthconnect.HealthConnectReader
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.network.OpenClawClient
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.network.SyncPayload
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.preferences.AppPreferences
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.preferences.SecurePreferences
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
            return Result.failure(workDataOf(KEY_ERROR to "Endpoint URL or token not configured"))
        }

        // Show foreground notification to prevent the OS from killing mid-sync
        try {
            setForeground(createForegroundInfo())
        } catch (e: Exception) {
            Log.w(TAG, "Could not set foreground: ${e.message}")
        }

        // Manual sync passes a specific range; background sync uses the configured default
        val rangeHours = inputData.getLong(KEY_RANGE_HOURS, 0L)
        val to = Instant.now()
        val from = if (rangeHours > 0) {
            to.minus(java.time.Duration.ofHours(rangeHours))
        } else {
            // Background sync: use configured range (default 24h)
            val bgRange = appPrefs.getBackgroundSyncRangeHours()
            to.minus(java.time.Duration.ofHours(bgRange))
        }

        return try {
            Log.i(TAG, "Starting sync: reading health data from ${from} to ${to}")

            val reader = HealthConnectReader(applicationContext)
            val snapshot = reader.readSnapshot(from, to)

            val deviceId = Settings.Secure.getString(
                applicationContext.contentResolver,
                Settings.Secure.ANDROID_ID
            )

            val payload = SyncPayload.fromSnapshot(
                snapshot = snapshot,
                deviceId = deviceId,
                syncedAt = Instant.now().toString()
            )

            Log.i(TAG, "Sending ${payload.records.size} records to $endpointUrl")

            val api = OpenClawClient.create(bearerToken)
            val response = api.sync(endpointUrl, payload)

            if (response.isSuccessful) {
                val body = response.body()
                appPrefs.setLastSyncTime(to.toEpochMilli())
                val msg = "Sync successful: ${body?.added ?: payload.records.size} records synced"
                Log.i(TAG, msg)
                Result.success(workDataOf(KEY_MESSAGE to msg))
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = when (response.code()) {
                    401 -> "Unauthorized: missing or invalid authentication token"
                    403 -> "Forbidden: authentication token does not match"
                    400 -> "Bad request: ${parseErrorMessage(errorBody) ?: "invalid payload format"}"
                    500 -> "Server error: try again later"
                    else -> "HTTP ${response.code()}: ${parseErrorMessage(errorBody) ?: "unknown error"}"
                }
                Log.w(TAG, "Sync failed: $errorMsg")
                if (response.code() in listOf(401, 403)) {
                    // Auth errors won't be fixed by retrying
                    Result.failure(workDataOf(KEY_ERROR to errorMsg))
                } else if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure(workDataOf(KEY_ERROR to errorMsg))
                }
            }
        } catch (e: SocketTimeoutException) {
            val msg = "Connection timed out. Server may be unreachable."
            Log.e(TAG, msg, e)
            retryOrFail(msg)
        } catch (e: ConnectException) {
            val msg = "Cannot connect to server. Check the URL and port."
            Log.e(TAG, msg, e)
            retryOrFail(msg)
        } catch (e: UnknownHostException) {
            val msg = "Unknown host. Check the endpoint URL."
            Log.e(TAG, msg, e)
            retryOrFail(msg)
        } catch (e: java.io.IOException) {
            val msg = if (e.message?.contains("Cleartext", ignoreCase = true) == true) {
                "Cleartext HTTP blocked. Enable cleartext traffic or use HTTPS."
            } else {
                "Network error: ${e.message}"
            }
            Log.e(TAG, msg, e)
            retryOrFail(msg)
        } catch (e: Exception) {
            val msg = "Sync error: ${e.message ?: e.javaClass.simpleName}"
            Log.e(TAG, msg, e)
            retryOrFail(msg)
        }
    }

    private fun retryOrFail(errorMessage: String): Result {
        return if (runAttemptCount < 3) {
            Result.retry()
        } else {
            Result.failure(workDataOf(KEY_ERROR to errorMessage))
        }
    }

    private fun parseErrorMessage(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        // Try to extract "error" field from JSON like {"error":"message"}
        return try {
            val regex = """"error"\s*:\s*"([^"]+)"""".toRegex()
            regex.find(errorBody)?.groupValues?.get(1)
        } catch (_: Exception) {
            errorBody.take(200)
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val channelId = "sync_channel"
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Health Data Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when health data is being synced"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Syncing health data...")
            .setSmallIcon(R.drawable.ic_sync)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val NOTIFICATION_ID = 1001
        const val WORK_NAME_PERIODIC = "health_connect_periodic_sync"
        const val WORK_NAME_ONE_SHOT = "health_connect_one_shot_sync"
        const val KEY_ERROR = "sync_error"
        const val KEY_MESSAGE = "sync_message"
        const val KEY_RANGE_HOURS = "sync_range_hours"
    }
}
