package io.github.davidegarbi.openclaw_healthconnect_bridge.data.network

import io.github.davidegarbi.openclaw_healthconnect_bridge.data.healthconnect.*
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class SyncPayload(
    val metadata: SyncMetadata,
    val heartRate: List<HeartRateSample>? = null,
    val steps: List<StepsSample>? = null,
    val sleep: List<SleepSession>? = null,
    val calories: List<CaloriesSample>? = null,
    val spo2: List<SpO2Sample>? = null,
    val distance: List<DistanceSample>? = null,
    val exercise: List<ExerciseSession>? = null,
    val bloodPressure: List<BloodPressureSample>? = null,
    val temperature: List<TemperatureSample>? = null,
    val respiratoryRate: List<RespiratoryRateSample>? = null,
    val bloodGlucose: List<BloodGlucoseSample>? = null,
    val weight: List<WeightSample>? = null,
    val height: List<HeightSample>? = null
)

@JsonClass(generateAdapter = false)
data class SyncMetadata(
    val deviceId: String,
    val syncTimestamp: String,
    val dataFrom: String,
    val dataTo: String
)
