package io.github.davidegarbi.openclaw_healthconnect_bridge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: UiState,
    onRequestPermissions: () -> Unit,
    onSaveEndpointUrl: (String) -> Unit,
    onSaveBearerToken: (String) -> Unit,
    onAutoSyncToggled: (Boolean) -> Unit,
    onSyncIntervalChanged: (Long) -> Unit,
    onBackgroundSyncRangeChanged: (Long) -> Unit,
    onSyncNowClicked: () -> Unit,
    onSyncRangeSelected: (Long) -> Unit,
    onSyncRangeSheetDismissed: () -> Unit,
    onInstallHealthConnect: () -> Unit
) {
    var editingUrl by remember(uiState.endpointUrl) { mutableStateOf(uiState.endpointUrl) }
    var editingToken by remember(uiState.bearerToken) { mutableStateOf(uiState.bearerToken) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("OpenClaw Health Bridge") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Health Connect Status
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Health Connect", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    when (uiState.healthConnectStatus) {
                        HealthConnectClient.SDK_AVAILABLE -> {
                            Text(
                                if (uiState.permissionsGranted) "Connected - permissions granted"
                                else "Available - permissions needed"
                            )
                            if (!uiState.permissionsGranted) {
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = onRequestPermissions) {
                                    Text("Grant Permissions")
                                }
                            }
                        }
                        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                            Text("Health Connect needs to be updated")
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = onInstallHealthConnect) {
                                Text("Update Health Connect")
                            }
                        }
                        else -> {
                            Text("Health Connect is not available on this device")
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = onInstallHealthConnect) {
                                Text("Install Health Connect")
                            }
                        }
                    }
                }
            }

            // Configuration
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Configuration", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editingUrl,
                        onValueChange = { editingUrl = it },
                        label = { Text("Endpoint URL") },
                        placeholder = { Text("http://192.168.1.100:18790/health-connect/sync") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editingToken,
                        onValueChange = { editingToken = it },
                        label = { Text("Bearer Token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            onSaveEndpointUrl(editingUrl)
                            onSaveBearerToken(editingToken)
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save")
                    }
                }
            }

            // Background Sync
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Background Sync", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto sync in background")
                        Switch(
                            checked = uiState.autoSyncEnabled,
                            onCheckedChange = onAutoSyncToggled
                        )
                    }

                    if (uiState.autoSyncEnabled) {
                        Spacer(Modifier.height(8.dp))
                        SyncIntervalDropdown(
                            selectedMinutes = uiState.syncIntervalMinutes,
                            onSelected = onSyncIntervalChanged
                        )
                        Spacer(Modifier.height(8.dp))
                        BackgroundSyncRangeDropdown(
                            selectedHours = uiState.backgroundSyncRangeHours,
                            onSelected = onBackgroundSyncRangeChanged
                        )
                    }
                }
            }

            // Sync Status
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sync Status", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    if (uiState.lastSyncTime > 0) {
                        val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(Date(uiState.lastSyncTime))
                        Text("Last sync: $formatted")
                    } else {
                        Text("Never synced")
                    }

                    uiState.syncMessage?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = it,
                            color = if (uiState.syncError) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onSyncNowClicked,
                            enabled = !uiState.isSyncing
                                    && uiState.endpointUrl.isNotBlank()
                                    && uiState.bearerToken.isNotBlank()
                        ) {
                            Text("Sync Now")
                        }
                        if (uiState.isSyncing) {
                            CircularProgressIndicator()
                            uiState.syncingRangeLabel?.let {
                                Text(
                                    text = "Syncing $it...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Sync Range Bottom Sheet
    if (uiState.showSyncRangeSheet) {
        SyncRangeBottomSheet(
            lastSelectedHours = uiState.lastManualSyncRangeHours,
            onRangeSelected = onSyncRangeSelected,
            onDismiss = onSyncRangeSheetDismissed
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncRangeBottomSheet(
    lastSelectedHours: Long,
    onRangeSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val options = listOf(
        24L to "Last 24 hours",
        168L to "Last 7 days",
        720L to "Last 30 days",
        2160L to "Last 90 days"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = "Sync time range",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            options.forEach { (hours, label) ->
                ListItem(
                    headlineContent = { Text(label) },
                    supportingContent = if (hours == 2160L) {
                        { Text(
                            "Health Connect may only retain 30 days of data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ) }
                    } else null,
                    leadingContent = {
                        RadioButton(
                            selected = hours == lastSelectedHours,
                            onClick = { onRangeSelected(hours) }
                        )
                    },
                    modifier = Modifier.clickable { onRangeSelected(hours) }
                )
                if (hours != 2160L) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncIntervalDropdown(
    selectedMinutes: Long,
    onSelected: (Long) -> Unit
) {
    val options = listOf(
        15L to "Every 15 minutes",
        30L to "Every 30 minutes",
        60L to "Every 1 hour",
        240L to "Every 4 hours"
    )
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedMinutes }?.second ?: "Every 1 hour"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Sync Interval") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (minutes, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(minutes)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackgroundSyncRangeDropdown(
    selectedHours: Long,
    onSelected: (Long) -> Unit
) {
    val options = listOf(
        6L to "Last 6 hours",
        12L to "Last 12 hours",
        24L to "Last 24 hours",
        48L to "Last 48 hours"
    )
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedHours }?.second ?: "Last 24 hours"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Data Range per Sync") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (hours, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(hours)
                        expanded = false
                    }
                )
            }
        }
    }
}
