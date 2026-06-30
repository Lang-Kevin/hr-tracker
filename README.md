# HR-Tracker

Native Android app for recording heart rate data from a BLE chest strap, with a Wear OS companion app.

**Hardware:** moofit HR8 (BLE chest strap) · Samsung Galaxy Watch D227 (Wear OS companion)

---

## Features

- **BLE recording** — connects to the moofit HR8 via BLE Heart Rate Service (`0x180D`), auto-reconnects on signal loss
- **Wear OS companion** — records HR from the Galaxy Watch as an alternative source; switchable at runtime
- **Live screen** — real-time BPM, heart rate zone, timer, live chart, average BPM, target zone card, time-per-zone table
- **Session history** — list of all sessions with summary cards and swipe-to-delete
- **Detail analytics** — BPM chart with zone banding, RMSSD (HRV), TRIMP, zone distribution, editable label and notes
- **Heart rate zones** — Karvonen model (default), %HRmax fallback; HRmax via Tanaka formula (`208 − 0.7·age`) or manual override
- **Zone snapshots** — zone boundaries persisted per session so historical data always reflects the settings at recording time
- **Export** — JSON export (CSV available via export flow)
- **RR intervals** — stored for every BLE session (`rr_ms = raw × 1000 / 1024`), required for HRV

## Tech Stack

| Area           | Technology                        |
|----------------|-----------------------------------|
| Language       | Kotlin                            |
| UI             | Jetpack Compose + Material 3      |
| Architecture   | MVVM (strict — no logic in Composables) |
| Async          | Coroutines + Flow                 |
| Database       | Room (v2, migration included)     |
| BLE            | Nordic Kotlin BLE                 |
| Charts         | Vico                              |
| DI             | Hilt                              |
| Serialization  | kotlinx.serialization             |
| Wear IPC       | Wearable Data Layer (`MessageClient`) |
| Min SDK        | 26 (Wear: 30) · Target SDK: 35   |

## Architecture

```
UI (Compose) → ViewModel → Repository → BLE / Room / DataStore
```

```
com.kevin.hrtracker
├── ble/        BLE manager, parser, reconnect logic
├── data/       Room DB, DAOs, repositories, DataStore
├── domain/     Domain models, zone calculations
├── export/     JSON/CSV export
├── service/    Foreground service (connectedDevice type)
├── ui/         Screens: Scan, Live, History, Detail, Settings
└── wearable/   Wear OS message bridge
```

Wear module: `com.kevin.hrtracker.wear` (`sensor/`, `comms/`, `ui/`)

## Build

```bash
./gradlew assembleDebug          # build phone app
./gradlew :wear:assembleDebug    # build Wear module
./gradlew test                   # unit tests
./gradlew lint                   # lint
./gradlew :app:installDebug      # install on connected device
```

## Permissions

| API level | Required permissions |
|-----------|----------------------|
| Android 12+ | `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` |
| Android < 12 | `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION` |
| All | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `POST_NOTIFICATIONS` |
| Wear | `BODY_SENSORS` |

## Data Model

```kotlin
Session(id, label, startedAt, endedAt, maxHrUsed, restingHr, note, zoneSnapshotJson)
HrSample(id, sessionId, timestampMs, bpm, rrIntervalsMs)
SportLabel(id, name, isPredefined)
```

Predefined sport labels: Volleyball, Beach, Krafttraining, Cardio, Trainingbike.

## Design System

Dark theme (Material 3). Font: **Space Grotesk**.

| Token          | Color     | Usage                  |
|----------------|-----------|------------------------|
| PrimaryPurple  | `#B6A6F2` | Buttons, primary       |
| SecondaryBlue  | `#5BA9E6` | Zone 1                 |
| LightPurple    | `#CBBCFF` | Zone 3, onSurface      |
| TertiaryPink   | `#F4738E` | Zone 5                 |
| ErrorRed       | `#EF4444` | Cancel actions, errors |
| BackgroundDark | `#1D1B20` | App background         |
| SurfaceDark    | `#252330` | Cards, surfaces        |
