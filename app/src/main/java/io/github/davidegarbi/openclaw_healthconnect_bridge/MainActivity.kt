package io.github.davidegarbi.openclaw_healthconnect_bridge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import io.github.davidegarbi.openclaw_healthconnect_bridge.data.sync.SyncWorker
import io.github.davidegarbi.openclaw_healthconnect_bridge.ui.MainScreen
import io.github.davidegarbi.openclaw_healthconnect_bridge.ui.MainViewModel
import io.github.davidegarbi.openclaw_healthconnect_bridge.ui.theme.OpenClawHealthConnectBridgeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    private val healthPermissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(BodyTemperatureRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class)
    )

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.onPermissionsResult(granted.containsAll(healthPermissions))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        checkPermissionsIfAvailable()
        observeSyncStatus()

        setContent {
            OpenClawHealthConnectBridgeTheme {
                val state by viewModel.uiState.collectAsState()
                MainScreen(
                    uiState = state,
                    onRequestPermissions = { permissionLauncher.launch(healthPermissions) },
                    onSaveEndpointUrl = viewModel::saveEndpointUrl,
                    onSaveBearerToken = viewModel::saveBearerToken,
                    onSyncIntervalChanged = viewModel::saveSyncInterval,
                    onSyncNow = viewModel::syncNow,
                    onInstallHealthConnect = ::openHealthConnectPlayStore
                )
            }
        }
    }

    private fun checkPermissionsIfAvailable() {
        if (HealthConnectClient.getSdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) return

        lifecycleScope.launch {
            val client = HealthConnectClient.getOrCreate(this@MainActivity)
            val granted = client.permissionController.getGrantedPermissions()
            viewModel.onPermissionsResult(granted.containsAll(healthPermissions))
        }
    }

    private fun observeSyncStatus() {
        viewModel.syncScheduler.getOneShotSyncStatus().observe(this) { workInfos ->
            val info = workInfos?.firstOrNull() ?: return@observe
            when (info.state) {
                WorkInfo.State.SUCCEEDED -> {
                    val msg = info.outputData.getString(SyncWorker.KEY_MESSAGE)
                    viewModel.onSyncResult(success = true, message = msg)
                }
                WorkInfo.State.FAILED -> {
                    val error = info.outputData.getString(SyncWorker.KEY_ERROR)
                    viewModel.onSyncResult(success = false, message = error)
                }
                WorkInfo.State.RUNNING -> { /* already showing spinner */ }
                else -> { /* enqueued, blocked, cancelled */ }
            }
        }
    }

    private fun openHealthConnectPlayStore() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
            setPackage("com.android.vending")
        }
        startActivity(intent)
    }
}
