package io.github.davidegarbi.openclaw_healthconnect_bridge.data.network

import io.github.davidegarbi.openclaw_healthconnect_bridge.data.healthconnect.*

data class SyncPayload(
    val deviceId: String,
    val syncedAt: String,
    val records: List<Map<String, Any?>>
) {
    companion object {
        fun fromSnapshot(
            snapshot: HealthSnapshot,
            deviceId: String,
            syncedAt: String
        ): SyncPayload {
            val records = mutableListOf<Map<String, Any?>>()

            snapshot.heartRate?.forEach {
                records.add(mapOf("type" to "heart_rate", "time" to it.time, "bpm" to it.bpm))
            }
            snapshot.steps?.forEach {
                records.add(mapOf("type" to "steps", "startTime" to it.startTime, "endTime" to it.endTime, "count" to it.count))
            }
            snapshot.sleep?.forEach {
                records.add(mapOf("type" to "sleep", "startTime" to it.startTime, "endTime" to it.endTime, "title" to it.title))
            }
            snapshot.calories?.forEach {
                records.add(mapOf("type" to "calories_burned", "startTime" to it.startTime, "endTime" to it.endTime, "kcal" to it.kcal))
            }
            snapshot.spo2?.forEach {
                records.add(mapOf("type" to "blood_oxygen", "time" to it.time, "percentage" to it.percentage))
            }
            snapshot.distance?.forEach {
                records.add(mapOf("type" to "distance", "startTime" to it.startTime, "endTime" to it.endTime, "meters" to it.meters))
            }
            snapshot.exercise?.forEach {
                records.add(mapOf("type" to "exercise", "startTime" to it.startTime, "endTime" to it.endTime, "exerciseType" to it.type.toString(), "title" to it.title))
            }
            snapshot.bloodPressure?.forEach {
                records.add(mapOf("type" to "blood_pressure", "time" to it.time, "systolicMmHg" to it.systolic, "diastolicMmHg" to it.diastolic))
            }
            snapshot.temperature?.forEach {
                records.add(mapOf("type" to "body_temperature", "time" to it.time, "celsius" to it.celsius))
            }
            snapshot.respiratoryRate?.forEach {
                records.add(mapOf("type" to "respiratory_rate", "time" to it.time, "rpm" to it.rpm))
            }
            snapshot.bloodGlucose?.forEach {
                records.add(mapOf("type" to "blood_glucose", "time" to it.time, "mmolPerL" to it.mmolPerL))
            }
            snapshot.weight?.forEach {
                records.add(mapOf("type" to "weight", "time" to it.time, "kg" to it.kg))
            }
            snapshot.height?.forEach {
                records.add(mapOf("type" to "height", "time" to it.time, "meters" to it.meters))
            }

            return SyncPayload(
                deviceId = deviceId,
                syncedAt = syncedAt,
                records = records
            )
        }
    }
}
