package io.github.davidegarbi.openclaw_healthconnect_bridge.data.healthconnect

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
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
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.ZoneOffset

class HealthConnectReader(context: Context) {

    private val client = HealthConnectClient.getOrCreate(context)

    val healthConnectClient: HealthConnectClient get() = client

    suspend fun readSnapshot(from: Instant, to: Instant): HealthSnapshot = coroutineScope {
        val timeRange = TimeRangeFilter.between(from, to)

        val heartRate = async { readHeartRate(timeRange) }
        val steps = async { readSteps(timeRange) }
        val sleep = async { readSleep(timeRange) }
        val calories = async { readCalories(timeRange) }
        val spo2 = async { readSpO2(timeRange) }
        val distance = async { readDistance(timeRange) }
        val exercise = async { readExercise(timeRange) }
        val bloodPressure = async { readBloodPressure(timeRange) }
        val temperature = async { readTemperature(timeRange) }
        val respiratoryRate = async { readRespiratoryRate(timeRange) }
        val bloodGlucose = async { readBloodGlucose(timeRange) }
        val weight = async { readWeight(timeRange) }
        val height = async { readHeight(timeRange) }

        HealthSnapshot(
            heartRate = heartRate.await(),
            steps = steps.await(),
            sleep = sleep.await(),
            calories = calories.await(),
            spo2 = spo2.await(),
            distance = distance.await(),
            exercise = exercise.await(),
            bloodPressure = bloodPressure.await(),
            temperature = temperature.await(),
            respiratoryRate = respiratoryRate.await(),
            bloodGlucose = bloodGlucose.await(),
            weight = weight.await(),
            height = height.await()
        )
    }

    private suspend fun readHeartRate(timeRange: TimeRangeFilter): List<HeartRateSample>? = tryRead {
        client.readRecords(ReadRecordsRequest(HeartRateRecord::class, timeRange))
            .records.flatMap { record ->
                record.samples.map { sample ->
                    HeartRateSample(
                        time = sample.time.toString(),
                        bpm = sample.beatsPerMinute
                    )
                }
            }.ifEmpty { null }
    }

    private suspend fun readSteps(timeRange: TimeRangeFilter): List<StepsSample>? = tryRead {
        client.readRecords(ReadRecordsRequest(StepsRecord::class, timeRange))
            .records.map { record ->
                StepsSample(
                    startTime = record.startTime.toString(),
                    endTime = record.endTime.toString(),
                    count = record.count
                )
            }.ifEmpty { null }
    }

    private suspend fun readSleep(timeRange: TimeRangeFilter): List<SleepSession>? = tryRead {
        client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, timeRange))
            .records.map { record ->
                SleepSession(
                    startTime = record.startTime.toString(),
                    endTime = record.endTime.toString(),
                    title = record.title
                )
            }.ifEmpty { null }
    }

    private suspend fun readCalories(timeRange: TimeRangeFilter): List<CaloriesSample>? = tryRead {
        client.readRecords(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, timeRange))
            .records.map { record ->
                CaloriesSample(
                    startTime = record.startTime.toString(),
                    endTime = record.endTime.toString(),
                    kcal = record.energy.inKilocalories
                )
            }.ifEmpty { null }
    }

    private suspend fun readSpO2(timeRange: TimeRangeFilter): List<SpO2Sample>? = tryRead {
        client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, timeRange))
            .records.map { record ->
                SpO2Sample(
                    time = record.time.toString(),
                    percentage = record.percentage.value
                )
            }.ifEmpty { null }
    }

    private suspend fun readDistance(timeRange: TimeRangeFilter): List<DistanceSample>? = tryRead {
        client.readRecords(ReadRecordsRequest(DistanceRecord::class, timeRange))
            .records.map { record ->
                DistanceSample(
                    startTime = record.startTime.toString(),
                    endTime = record.endTime.toString(),
                    meters = record.distance.inMeters
                )
            }.ifEmpty { null }
    }

    private suspend fun readExercise(timeRange: TimeRangeFilter): List<ExerciseSession>? = tryRead {
        client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, timeRange))
            .records.map { record ->
                ExerciseSession(
                    startTime = record.startTime.toString(),
                    endTime = record.endTime.toString(),
                    type = record.exerciseType,
                    title = record.title
                )
            }.ifEmpty { null }
    }

    private suspend fun readBloodPressure(timeRange: TimeRangeFilter): List<BloodPressureSample>? = tryRead {
        client.readRecords(ReadRecordsRequest(BloodPressureRecord::class, timeRange))
            .records.map { record ->
                BloodPressureSample(
                    time = record.time.toString(),
                    systolic = record.systolic.inMillimetersOfMercury,
                    diastolic = record.diastolic.inMillimetersOfMercury
                )
            }.ifEmpty { null }
    }

    private suspend fun readTemperature(timeRange: TimeRangeFilter): List<TemperatureSample>? = tryRead {
        client.readRecords(ReadRecordsRequest(BodyTemperatureRecord::class, timeRange))
            .records.map { record ->
                TemperatureSample(
                    time = record.time.toString(),
                    celsius = record.temperature.inCelsius
                )
            }.ifEmpty { null }
    }

    private suspend fun readRespiratoryRate(timeRange: TimeRangeFilter): List<RespiratoryRateSample>? = tryRead {
        client.readRecords(ReadRecordsRequest(RespiratoryRateRecord::class, timeRange))
            .records.map { record ->
                RespiratoryRateSample(
                    time = record.time.toString(),
                    rpm = record.rate
                )
            }.ifEmpty { null }
    }

    private suspend fun readBloodGlucose(timeRange: TimeRangeFilter): List<BloodGlucoseSample>? = tryRead {
        client.readRecords(ReadRecordsRequest(BloodGlucoseRecord::class, timeRange))
            .records.map { record ->
                BloodGlucoseSample(
                    time = record.time.toString(),
                    mmolPerL = record.level.inMillimolesPerLiter
                )
            }.ifEmpty { null }
    }

    private suspend fun readWeight(timeRange: TimeRangeFilter): List<WeightSample>? = tryRead {
        client.readRecords(ReadRecordsRequest(WeightRecord::class, timeRange))
            .records.map { record ->
                WeightSample(
                    time = record.time.toString(),
                    kg = record.weight.inKilograms
                )
            }.ifEmpty { null }
    }

    private suspend fun readHeight(timeRange: TimeRangeFilter): List<HeightSample>? = tryRead {
        client.readRecords(ReadRecordsRequest(HeightRecord::class, timeRange))
            .records.map { record ->
                HeightSample(
                    time = record.time.toString(),
                    meters = record.height.inMeters
                )
            }.ifEmpty { null }
    }

    private suspend fun <T> tryRead(block: suspend () -> T?): T? {
        return try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read health data: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "HealthConnectReader"

        fun isAvailable(context: Context): Int =
            HealthConnectClient.getSdkStatus(context)
    }
}
