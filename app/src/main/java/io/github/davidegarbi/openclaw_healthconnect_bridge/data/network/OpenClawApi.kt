package io.github.davidegarbi.openclaw_healthconnect_bridge.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface OpenClawApi {
    @POST("health-connect/sync")
    suspend fun sync(@Body payload: SyncPayload): Response<Unit>
}
