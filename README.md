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

## Architecture

```
MainActivity (Compose)  ──▶  MainViewModel
                                  │
                    ┌─────────────┼─────────────┐
                    ▼             ▼              ▼
           HealthConnectReader  AppPreferences  SyncScheduler
                    │            SecurePrefs        │
                    └────────┐                ┌─────┘
                             ▼                ▼
                          SyncWorker (WorkManager)
                             │
                             ▼
                     OpenClawClient (Retrofit)
                             │
                             ▼
                    POST /health-connect/sync
```

## Requirements

- Android 9+ (API 28)
- [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) installed on the device
- A running OpenClaw instance with the health-connect plugin enabled
- JDK 21 for building

## Building

```bash
# Clone the repository
git clone https://github.com/davidegarbi/openclaw-healthconnect-bridge.git
cd openclaw-healthconnect-bridge

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Build release APK
./gradlew assembleRelease
```

## Setup

1. Install the app on your Android device
2. Open the app and grant Health Connect permissions
3. Enter your OpenClaw server endpoint URL (e.g. `https://your-server.com/api/`)
4. Enter your Bearer token
5. Choose a sync interval or use manual sync
6. Tap **Sync Now** to verify the connection

## Configuration

| Setting | Description |
|---|---|
| **Endpoint URL** | Base URL of your OpenClaw instance |
| **Bearer Token** | Authentication token (stored encrypted on device) |
| **Sync Interval** | 15 min / 30 min / 1 hour / 4 hours / Manual only |

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Networking**: Retrofit + Moshi + OkHttp
- **Background Sync**: WorkManager
- **Preferences**: DataStore + EncryptedSharedPreferences
- **Health Data**: Health Connect Client SDK

## Repository Structure

This repo contains both the Android app and the OpenClaw server-side plugin:

```
openclaw-healthconnect-bridge/
├── app/                    # Android app (Kotlin)
├── openclaw-plugin/        # OpenClaw server-side plugin (TypeScript)
├── ...
```

### Android App

```
app/src/main/java/io/github/davidegarbi/openclaw_healthconnect_bridge/
├── MainActivity.kt              # Entry point, permission handling
├── OpenClawApp.kt               # Application class, auto-starts sync
├── data/
│   ├── healthconnect/
│   │   ├── HealthConnectReader.kt   # Reads all 13 record types
│   │   └── HealthDataModels.kt      # Data classes for health samples
│   ├── network/
│   │   ├── OpenClawApi.kt           # Retrofit API interface
│   │   ├── OpenClawClient.kt        # Retrofit client factory
│   │   └── SyncPayload.kt           # JSON payload model
│   ├── preferences/
│   │   ├── AppPreferences.kt        # DataStore for app settings
│   │   └── SecurePreferences.kt     # Encrypted storage for token
│   └── sync/
│       ├── SyncScheduler.kt         # WorkManager scheduling
│       └── SyncWorker.kt            # Background sync worker
└── ui/
    ├── MainScreen.kt                # Compose UI
    ├── MainViewModel.kt             # UI state management
    └── theme/
        ├── Color.kt
        └── Theme.kt
```

### OpenClaw Plugin

See [`openclaw-plugin/README.md`](openclaw-plugin/README.md) for full plugin documentation including the sync API, supported record types, and agent tool usage.

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
