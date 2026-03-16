# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

OpenClaw HealthConnect Bridge is an Android application that reads health data from Health Connect and syncs it to an OpenClaw instance via HTTP. Single-screen Compose UI, WorkManager-based background sync, Retrofit networking.

- **Package**: `io.github.davidegarbi.openclaw_healthconnect_bridge`
- **Single module**: `:app`
- **Language**: Kotlin (Java 11 source/target compatibility)
- **Min SDK**: 28 (Android 9), **Target SDK**: 36
- **AGP**: 9.1.0, **Gradle**: 9.3.1, **JDK toolchain**: 21
- **License**: PolyForm Noncommercial 1.0.0

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run a single unit test class
./gradlew testDebugUnitTest --tests "io.github.davidegarbi.openclaw_healthconnect_bridge.ExampleUnitTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

## Architecture

- **No DI framework** — manual construction, app is small enough
- **Two preference stores** — EncryptedSharedPreferences for bearer token, DataStore for everything else
- **Moshi + Retrofit** for networking (no kotlinx.serialization compiler plugin)
- **WorkManager** for periodic and one-shot background sync
- **Independent error handling per health data type** — one failure does not block others

## Package Structure

```
io.github.davidegarbi.openclaw_healthconnect_bridge/
├── MainActivity.kt / OpenClawApp.kt
├── data/healthconnect/   — HealthConnectReader, HealthDataModels
├── data/network/         — OpenClawApi, OpenClawClient, SyncPayload
├── data/preferences/     — SecurePreferences, AppPreferences
├── data/sync/            — SyncWorker, SyncScheduler
└── ui/                   — MainScreen, MainViewModel, theme/
```

## Dependencies

Managed via Gradle version catalog at `gradle/libs.versions.toml`. Key dependencies: Health Connect Client SDK, Compose BOM + Material3, Retrofit + Moshi, WorkManager, DataStore, Security Crypto.
