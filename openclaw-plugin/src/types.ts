/**
 * Health Connect data types that map to Android Health Connect records.
 * The Android app sends these via HTTP POST to the sync endpoint.
 */

export interface HealthConnectConfig {
  enabled: boolean;
  authToken: string;
  storagePath: string;
  httpPath: string;
  retentionDays: number;
}

// --- Incoming data from the Android app ---

export interface StepsRecord {
  type: "steps";
  startTime: string; // ISO 8601
  endTime: string;
  count: number;
}

export interface HeartRateRecord {
  type: "heart_rate";
  time: string; // ISO 8601
  bpm: number;
}

export interface SleepRecord {
  type: "sleep";
  startTime: string;
  endTime: string;
  title?: string | null;
}

export interface CaloriesRecord {
  type: "calories_burned";
  startTime: string;
  endTime: string;
  kcal: number;
}

export interface DistanceRecord {
  type: "distance";
  startTime: string;
  endTime: string;
  meters: number;
}

export interface WeightRecord {
  type: "weight";
  time: string;
  kg: number;
}

export interface ExerciseRecord {
  type: "exercise";
  startTime: string;
  endTime: string;
  exerciseType: string;
  title?: string | null;
}

export interface BloodOxygenRecord {
  type: "blood_oxygen";
  time: string;
  percentage: number;
}

export interface BodyTemperatureRecord {
  type: "body_temperature";
  time: string;
  celsius: number;
}

export interface BloodPressureRecord {
  type: "blood_pressure";
  time: string;
  systolicMmHg: number;
  diastolicMmHg: number;
}

export interface RespiratoryRateRecord {
  type: "respiratory_rate";
  time: string;
  rpm: number;
}

export interface BloodGlucoseRecord {
  type: "blood_glucose";
  time: string;
  mmolPerL: number;
}

export interface HeightRecord {
  type: "height";
  time: string;
  meters: number;
}

export type HealthRecord =
  | StepsRecord
  | HeartRateRecord
  | SleepRecord
  | CaloriesRecord
  | DistanceRecord
  | WeightRecord
  | ExerciseRecord
  | BloodOxygenRecord
  | BodyTemperatureRecord
  | BloodPressureRecord
  | RespiratoryRateRecord
  | BloodGlucoseRecord
  | HeightRecord;

// --- Sync payload ---

export interface SyncPayload {
  /** Device identifier (Android ID) */
  deviceId?: string;
  /** Batch of health records */
  records: HealthRecord[];
  /** ISO 8601 timestamp of when the sync was initiated */
  syncedAt: string;
}

// --- Stored data format ---

export interface StoredDay {
  date: string; // YYYY-MM-DD
  lastSync: string; // ISO 8601
  records: HealthRecord[];
}

// --- Query types ---

export type QueryType =
  | "steps"
  | "heart_rate"
  | "sleep"
  | "calories_burned"
  | "distance"
  | "weight"
  | "exercise"
  | "blood_oxygen"
  | "body_temperature"
  | "blood_pressure"
  | "respiratory_rate"
  | "blood_glucose"
  | "height"
  | "summary"
  | "all";
