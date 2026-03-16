package io.github.davidegarbi.openclaw_healthconnect_bridge.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface OpenClawApi {
    @POST
    suspend fun sync(@Url url: String, @Body payload: SyncPayload): Response<SyncResponse>
}
