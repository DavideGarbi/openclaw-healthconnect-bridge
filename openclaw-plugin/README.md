# Health Connect Bridge - OpenClaw Plugin

OpenClaw plugin that receives health data from Android Health Connect via HTTP and provides agent tools to query it.

## Architecture

```
Android App (Health Connect) --> HTTP POST --> OpenClaw Gateway --> Plugin --> JSON Storage
                                                                      |
                                                              Agent Tool (query)
```

## Setup

1. Install the plugin in `~/.openclaw/extensions/health-connect/`
2. Configure in `openclaw.json`:

```json5
{
  plugins: {
    entries: {
      "health-connect": {
        enabled: true,
        config: {
          authToken: "YOUR_SECRET_TOKEN",  // generate with: openssl rand -hex 32
          storagePath: "~/.openclaw/health-connect-data",  // optional
          httpPath: "/health-connect/sync",  // optional
          retentionDays: 90  // optional
        }
      }
    }
  }
}
```

3. Restart the gateway: `openclaw gateway restart`

## Sync API

### POST /health-connect/sync

Receives health data from the Android app.

```
POST http://<gateway-host>:<port>/health-connect/sync
Authorization: Bearer YOUR_SECRET_TOKEN
Content-Type: application/json
```

**Request body:**

```json
{
  "deviceId": "abc123",
  "syncedAt": "2026-03-16T20:30:00.000Z",
  "records": [
    { "type": "steps", "startTime": "2026-03-16T06:00:00Z", "endTime": "2026-03-16T07:00:00Z", "count": 1250 },
    { "type": "heart_rate", "time": "2026-03-16T10:30:00Z", "bpm": 72 },
    { "type": "sleep", "startTime": "2026-03-15T23:00:00Z", "endTime": "2026-03-16T07:15:00Z" },
    { "type": "calories_burned", "startTime": "2026-03-16T06:00:00Z", "endTime": "2026-03-16T20:00:00Z", "kcal": 2150 },
    { "type": "blood_oxygen", "time": "2026-03-16T10:00:00Z", "percentage": 98 },
    { "type": "blood_pressure", "time": "2026-03-16T08:00:00Z", "systolicMmHg": 120, "diastolicMmHg": 80 },
    { "type": "weight", "time": "2026-03-16T07:30:00Z", "kg": 75.2 }
  ]
}
```

**Response (200 OK):**

```json
{
  "ok": true,
  "added": 7,
  "updated": 0,
  "recordsReceived": 7
}
```

**Errors:**
- `401` - Missing or no auth token configured
- `403` - Invalid auth token
- `400` - Invalid payload (with error message)
- `500` - Server error

### GET /health-connect/sync

Health check endpoint (no auth required):

```json
{
  "ok": true,
  "plugin": "health-connect",
  "datesAvailable": 15,
  "latestDate": "2026-03-16"
}
```

## Supported Record Types

| Type | Key Fields | Source |
|------|-----------|--------|
| `steps` | `startTime`, `endTime`, `count` | StepsRecord |
| `heart_rate` | `time`, `bpm` | HeartRateRecord |
| `sleep` | `startTime`, `endTime`, `title?` | SleepSessionRecord |
| `calories_burned` | `startTime`, `endTime`, `kcal` | TotalCaloriesBurnedRecord |
| `distance` | `startTime`, `endTime`, `meters` | DistanceRecord |
| `weight` | `time`, `kg` | WeightRecord |
| `height` | `time`, `meters` | HeightRecord |
| `exercise` | `startTime`, `endTime`, `exerciseType`, `title?` | ExerciseSessionRecord |
| `blood_oxygen` | `time`, `percentage` | OxygenSaturationRecord |
| `body_temperature` | `time`, `celsius` | BodyTemperatureRecord |
| `blood_pressure` | `time`, `systolicMmHg`, `diastolicMmHg` | BloodPressureRecord |
| `respiratory_rate` | `time`, `rpm` | RespiratoryRateRecord |
| `blood_glucose` | `time`, `mmolPerL` | BloodGlucoseRecord |

## Agent Tool

The plugin registers a `health_connect` tool with three actions:

- **`today`** - Quick daily summary (steps, calories, sleep, heart rate, etc.)
- **`query`** - Query specific data types with date range and limit
- **`dates`** - List all available dates with stored data

## Notes for the Android App

- **Recommended sync interval:** every 15-60 minutes
- **Batching:** send all available records in a single POST (server-side deduplication is automatic)
- **Timestamps:** all in ISO 8601 (preferably UTC)
- **Retry:** on 5xx errors, retry with exponential backoff
- **Offline:** accumulate records locally and send on next successful sync
