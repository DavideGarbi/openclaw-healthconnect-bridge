# Health Connect Bridge - OpenClaw Plugin

OpenClaw plugin that receives health data from the Android app via HTTP and makes it available to the OpenClaw agent through a query tool.

## How It Works

```
Android App (Health Connect) --> HTTP POST --> OpenClaw Gateway --> Plugin --> JSON Storage
                                                                      |
                                                              Agent Tool (query)
```

1. The Android app reads health data from Health Connect (steps, heart rate, sleep, etc.)
2. It sends the data as JSON to the plugin's HTTP endpoint (`POST /health-connect/sync`)
3. The plugin validates the request (Bearer token auth), deduplicates, and stores the data as daily JSON files
4. The OpenClaw agent can then query this data using the `health_connect` tool

## Installation

```bash
# Copy the plugin folder to your OpenClaw extensions directory
cp -r openclaw-plugin ~/.openclaw/extensions/health-connect

# Install dependencies
cd ~/.openclaw/extensions/health-connect
npm install
```

## Configuration

### 1. Generate an auth token

This token is a shared secret between the plugin and the Android app. Both sides must use the same value.

```bash
openssl rand -hex 32
```

### 2. Add to your OpenClaw config

Edit `~/.openclaw/openclaw.json` (or wherever your config lives):

```json5
{
  plugins: {
    entries: {
      "health-connect": {
        enabled: true,
        config: {
          // REQUIRED - paste the token you generated above
          authToken: "your-generated-token-here",

          // OPTIONAL - path to store health data files
          // Default: ~/.openclaw/health-connect-data
          storagePath: "~/.openclaw/health-connect-data",

          // OPTIONAL - HTTP endpoint path
          // Default: /health-connect/sync
          httpPath: "/health-connect/sync",

          // OPTIONAL - how many days of data to keep
          // Default: 90
          retentionDays: 90
        }
      }
    }
  }
}
```

### 3. Restart the gateway

```bash
openclaw gateway restart
```

### 4. Verify the plugin is running

```bash
curl http://localhost:<port>/health-connect/sync
```

Expected response:

```json
{"ok": true, "plugin": "health-connect", "datesAvailable": 0, "latestDate": null}
```

### 5. Find your endpoint URL for the Android app

The URL you enter in the Android app is your gateway address plus the HTTP path:

```
http://<your-server-ip-or-domain>:<gateway-port>/health-connect/sync
```

Examples:
- Local server, same Wi-Fi: `http://192.168.1.100:3000/health-connect/sync`
- Remote server with HTTPS: `https://myserver.example.com/health-connect/sync`

**Note:** `localhost` will not work from a phone. Use your machine's LAN IP address if running locally.

## Config Reference

| Property | Required | Default | Description |
|---|---|---|---|
| `authToken` | Yes | - | Bearer token shared with the Android app |
| `storagePath` | No | `~/.openclaw/health-connect-data` | Directory for daily JSON data files |
| `httpPath` | No | `/health-connect/sync` | HTTP endpoint path |
| `retentionDays` | No | `90` | Automatically delete data older than this |

## Sync API

### POST /health-connect/sync

Receives health data from the Android app. Requires Bearer token auth.

```
POST /health-connect/sync
Authorization: Bearer <authToken>
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
    { "type": "weight", "time": "2026-03-16T07:30:00Z", "kg": 75.2 }
  ]
}
```

**Response (200):**

```json
{ "ok": true, "added": 3, "updated": 0, "recordsReceived": 3 }
```

**Error codes:**
- `401` - No auth token in request, or plugin has no `authToken` configured
- `403` - Token does not match
- `400` - Invalid payload format
- `500` - Server error

### GET /health-connect/sync

Health check (no auth required). Returns plugin status and data availability.

## Supported Record Types

| Type | Key Fields | Android Source |
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

The plugin registers a `health_connect` tool that the OpenClaw agent can use:

### `action: "today"`
Returns a summary of today's health data (total steps, calories burned, sleep hours, heart rate min/max/avg, weight, exercises, etc.).

### `action: "query"`
Query specific data types with optional date range and limit:
- `type` - one of the record types above, `"summary"`, or `"all"`
- `from` - start date, YYYY-MM-DD (default: 7 days ago)
- `to` - end date, YYYY-MM-DD (default: today)
- `limit` - max records to return

### `action: "dates"`
Lists all dates that have stored health data.

## Data Storage

Health data is stored as one JSON file per day in the `storagePath` directory:

```
~/.openclaw/health-connect-data/
  2026-03-14.json
  2026-03-15.json
  2026-03-16.json
```

Each file contains all records for that day plus sync metadata. Duplicate records (same data sent twice) are automatically deduplicated. Files older than `retentionDays` are automatically deleted on each sync.
