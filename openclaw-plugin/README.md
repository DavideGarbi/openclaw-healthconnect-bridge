# Health Connect Bridge - OpenClaw Plugin

OpenClaw plugin that receives health data from the Android app via HTTP and makes it available to the OpenClaw agent through a query tool.

## How It Works

```
Android App (Health Connect) --> HTTP POST --> Plugin HTTP Server (:18790) --> JSON Storage
                                                        |
                                                Agent Tool (query)
```

The plugin runs its **own HTTP server** on a dedicated port (default: 18790), separate from the OpenClaw gateway. It uses `api.registerService()` for lifecycle management and `api.registerTool()` for the agent tool.

1. The Android app reads health data from Health Connect (steps, heart rate, sleep, etc.)
2. It sends the data as JSON to the plugin's HTTP endpoint (`POST http://<host>:18790/health-connect/sync`)
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

Edit `~/.openclaw/openclaw.json` (or wherever your config lives).

**IMPORTANT:** Add the `"health-connect"` entry inside the **existing** `plugins.entries` object. Do NOT create a second `plugins` key — this will silently overwrite your other plugin configs.

```json5
{
  plugins: {
    entries: {
      // ... your existing plugins (telegram, voice-call, etc.) ...
      "health-connect": {
        enabled: true,
        config: {
          // REQUIRED - paste the token you generated above
          authToken: "your-generated-token-here",

          // OPTIONAL - port for the plugin's HTTP server (default: 18790)
          httpPort: 18790,

          // OPTIONAL - bind address (default: "0.0.0.0")
          httpBind: "0.0.0.0",

          // OPTIONAL - HTTP endpoint path (default: "/health-connect/sync")
          httpPath: "/health-connect/sync",

          // OPTIONAL - path to store health data files
          storagePath: "~/.openclaw/health-connect-data",

          // OPTIONAL - how many days of data to keep (default: 90)
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
# Check plugin loaded
openclaw plugins list
# Should show "health-connect" as "loaded"

# Check HTTP server is responding
curl http://localhost:18790/health-connect/sync
```

Expected response:

```json
{"ok": true, "plugin": "health-connect", "datesAvailable": 0, "latestDate": null}
```

### 5. Find your endpoint URL for the Android app

The URL uses the **plugin's own port** (default 18790), NOT the gateway port:

```
http://<your-server-ip>:18790/health-connect/sync
```

Examples:
- Local server, same Wi-Fi: `http://192.168.1.100:18790/health-connect/sync`
- Remote server: `https://myserver.example.com:18790/health-connect/sync`

**Note:** `localhost` will not work from a phone. Use your machine's LAN IP address if running locally.

## Config Reference

| Property | Required | Default | Description |
|---|---|---|---|
| `authToken` | Yes | - | Bearer token shared with the Android app |
| `httpPort` | No | `18790` | Port for the plugin's own HTTP server |
| `httpBind` | No | `0.0.0.0` | Bind address for the HTTP server |
| `httpPath` | No | `/health-connect/sync` | HTTP endpoint path |
| `storagePath` | No | `~/.openclaw/health-connect-data` | Directory for daily JSON data files |
| `retentionDays` | No | `90` | Automatically delete data older than this |

## Sync API

### POST /health-connect/sync

Receives health data from the Android app. Requires Bearer token auth.

```
POST http://<host>:18790/health-connect/sync
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

## Technical Notes

- The plugin creates its own `http.Server` (Node.js `http.createServer`) — it does NOT use `api.registerGatewayHttpHandler()` (which does not exist in the SDK)
- Server lifecycle is managed via `api.registerService({ id, start, stop })`
- The `package.json` `name` field MUST match the `openclaw.plugin.json` `id` field (`"health-connect"`)
