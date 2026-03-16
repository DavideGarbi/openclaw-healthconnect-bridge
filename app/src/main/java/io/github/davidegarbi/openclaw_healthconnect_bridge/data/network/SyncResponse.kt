package io.github.davidegarbi.openclaw_healthconnect_bridge.data.network

data class SyncResponse(
    val ok: Boolean?,
    val added: Int?,
    val updated: Int?,
    val recordsReceived: Int?,
    val error: String?
)
