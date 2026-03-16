# OpenClaw HealthConnect Bridge

An Android application that bridges [Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect) data from your smartwatch and phone to the [OpenClaw](https://github.com/davidegarbi) assistant via a custom HTTP plugin.

The app reads health metrics from Health Connect and periodically syncs them to your OpenClaw instance through `POST /health-connect/sync` with Bearer token authentication.

## Supported Health Data

| Category | Data Type |
|---|---|
| Heart | Heart Rate, Blood Pressure, SpO2 |
| Activity | Steps, Distance, Calories, Exercise Sessions |
| Body | Weight, Height, Body Temperature |
| Sleep | Sleep Sessions |
| Respiratory | Respiratory Rate |
| Metabolic | Blood Glucose |

## Quick Start

This guide walks you through the full setup: installing the plugin on your OpenClaw server, then connecting the Android app.

### One-Click Install via OpenClaw

If you already have OpenClaw running, paste this prompt into your OpenClaw chat to have the agent install and configure the plugin for you:

> Install the Health Connect Bridge plugin from https://github.com/DavideGarbi/openclaw-healthconnect-bridge
>
> Steps:
> 1. Clone the repo to /tmp/health-connect-bridge
> 2. Copy the `openclaw-plugin/` folder to `~/.openclaw/extensions/health-connect/`
> 3. Run `npm install` inside that folder
> 4. Generate a secure auth token: `openssl rand -hex 32`
> 5. Edit `~/.openclaw/openclaw.json`:
>    - Find the EXISTING `plugins.entries` object
>    - ADD a new key `"health-connect"` inside it (do NOT create a second `plugins` key)
>    - Value: `{ "enabled": true, "config": { "authToken": "<generated-token>" } }`
> 6. Verify JSON is valid: `python3 -c "import json; json.load(open('<config-path>'))"`
> 7. Restart the gateway process
> 8. Verify the plugin loaded: `openclaw plugins list` — should show "health-connect" as "loaded"
> 9. Print the endpoint URL (http://\<host\>:18790/health-connect/sync) and the auth token
>
> IMPORTANT:
> - The plugin runs its OWN HTTP server on port 18790, NOT on the gateway port
> - Do NOT use `api.registerGatewayHttpHandler` — it doesn't exist in the SDK
> - The plugin uses `api.registerService()` for server lifecycle and `api.registerTool()` for the agent tool
> - The package.json `name` field MUST match the manifest `id` field ("health-connect")

Then skip to [Step 5](#step-5-install-the-android-app).

---

### Manual Setup

### Step 1: Install the OpenClaw Plugin

Copy the `openclaw-plugin/` folder from this repo into your OpenClaw extensions directory:

```bash
# Clone the repo (or download the openclaw-plugin folder)
git clone https://github.com/davidegarbi/openclaw-healthconnect-bridge.git

# Copy the plugin to your OpenClaw extensions
cp -r openclaw-healthconnect-bridge/openclaw-plugin ~/.openclaw/extensions/health-connect

# Install plugin dependencies
cd ~/.openclaw/extensions/health-connect
npm install
```

### Step 2: Generate an Auth Token

The auth token is a shared secret between the plugin and the Android app. Generate one:

```bash
openssl rand -hex 32
```

This will output something like: `a1b2c3d4e5f6...` — save this, you will need it in both the plugin config and the Android app.

### Step 3: Configure the Plugin

Edit `~/.openclaw/openclaw.json` (or wherever your config lives). **IMPORTANT:** Add `"health-connect"` inside the **existing** `plugins.entries` object — do NOT create a second `plugins` key, or you will overwrite your other plugin configs.

```json5
{
  plugins: {
    entries: {
      // ... your existing plugins stay here ...
      "health-connect": {
        enabled: true,
        config: {
          // REQUIRED: paste the token you generated in Step 2
          authToken: "a1b2c3d4e5f6..."
        }
      }
    }
  }
}
```

Optional config fields: `httpPort` (default: 18790), `httpBind` (default: "0.0.0.0"), `httpPath` (default: "/health-connect/sync"), `storagePath`, `retentionDays` (default: 90). See [plugin README](openclaw-plugin/README.md#config-reference) for details.

Then restart the OpenClaw gateway:

```bash
openclaw gateway restart
```

### Step 4: Find Your Endpoint URL

The plugin runs its **own HTTP server** on port 18790 (not the gateway port). The endpoint URL is:

```
http://<your-server-ip>:18790/health-connect/sync
```

**How to find it:**
- Local server, same Wi-Fi: `http://192.168.1.100:18790/health-connect/sync` (use your machine's LAN IP)
- Remote server: `http://your-server.com:18790/health-connect/sync`

You can verify the plugin is running:

```bash
curl http://localhost:18790/health-connect/sync
# Should return: {"ok":true,"plugin":"health-connect","datesAvailable":0,"latestDate":null}
```

**Important:** Your Android phone must be able to reach this URL over the network. If your OpenClaw server runs on your local machine, both devices must be on the same network (Wi-Fi), and you must use your computer's LAN IP address (e.g. `192.168.1.100`), not `localhost`.

### Step 5: Install the Android App

Download the APK from the [Releases page](https://github.com/davidegarbi/openclaw-healthconnect-bridge/releases) and install it on your Android device.

Your device also needs [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) installed (comes preinstalled on most devices running Android 14+; available on Play Store for Android 9+).

### Step 6: Configure the Android App

1. Open the app
2. Tap **Grant Permissions** to allow reading health data from Health Connect
3. In the **Configuration** section:
   - **Endpoint URL**: paste the full URL from Step 4 (e.g. `http://192.168.1.100:3000/health-connect/sync`)
   - **Bearer Token**: paste the same token you generated in Step 2
4. Tap **Save**
5. Choose a **Sync Interval** (how often to auto-sync in the background) or leave it on "Manual only"
6. Tap **Sync Now** to test the connection

If the sync succeeds, you will see "Sync completed successfully" and the "Last sync" timestamp will update. Your OpenClaw agent can now query your health data using the `health_connect` tool.

## Architecture

```
Android App (Health Connect) --> HTTP POST --> Plugin HTTP Server (:18790) --> JSON Storage
                                                        |
                                                Agent Tool (query)
```

## Requirements

- Android 9+ (API 28)
- [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) installed on the device
- A running OpenClaw instance
- Network connectivity between phone and server

## Building from Source

```bash
git clone https://github.com/davidegarbi/openclaw-healthconnect-bridge.git
cd openclaw-healthconnect-bridge

# Build debug APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Run unit tests
./gradlew test
```

Requires JDK 21.

## App Configuration Reference

| Setting | Description | Example |
|---|---|---|
| **Endpoint URL** | Full URL of the plugin's sync endpoint (uses plugin port, not gateway port) | `http://192.168.1.100:18790/health-connect/sync` |
| **Bearer Token** | The same `authToken` you configured in the plugin (generated with `openssl rand -hex 32`) | `a1b2c3d4e5f6...` |
| **Sync Interval** | How often to auto-sync in the background | 15 min / 30 min / 1 hr / 4 hrs / Manual only |

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Networking**: Retrofit + Moshi + OkHttp
- **Background Sync**: WorkManager
- **Preferences**: DataStore + EncryptedSharedPreferences
- **Health Data**: Health Connect Client SDK
- **Plugin**: TypeScript (Node.js)

## Repository Structure

```
openclaw-healthconnect-bridge/
├── app/                    # Android app (Kotlin)
├── openclaw-plugin/        # OpenClaw server-side plugin (TypeScript)
├── ...
```

### OpenClaw Plugin

See [`openclaw-plugin/README.md`](openclaw-plugin/README.md) for full plugin documentation including the sync API specification, supported record types, and agent tool usage.

## Troubleshooting

| Problem | Solution |
|---|---|
| Sync fails with no error | Make sure your phone can reach the server (same Wi-Fi network, correct IP) |
| 401 Unauthorized | Auth token is missing in the app or the plugin has no `authToken` configured |
| 403 Forbidden | The token in the app does not match the token in the plugin config |
| Health Connect not available | Install or update [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) from the Play Store |
| No data after sync | Make sure you granted all Health Connect permissions in the app, and that your watch/phone has actually recorded data |

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a pull request.

## License

This project is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE).

**In short:**
- You **can** use, modify, and distribute this software for **noncommercial purposes**
- You **must** give credit to the original author
- You **cannot** use this software for commercial purposes
- Contributions are accepted under the same license terms

## Disclaimer

THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED. THE AUTHOR ASSUMES NO RESPONSIBILITY OR LIABILITY FOR ANY ERRORS, OMISSIONS, DAMAGES, OR LOSSES OF ANY KIND ARISING FROM THE USE OF THIS SOFTWARE, INCLUDING BUT NOT LIMITED TO LOSS OF DATA, HEALTH DATA INACCURACIES, OR DEVICE MALFUNCTIONS.

**USE THIS SOFTWARE ENTIRELY AT YOUR OWN RISK.**

This application reads sensitive health data from your device. The author is not responsible for how this data is transmitted, stored, or used by third-party servers you configure. You are solely responsible for securing your endpoint and credentials.

## Author

**Davide Garbi** — [GitHub](https://github.com/davidegarbi)
