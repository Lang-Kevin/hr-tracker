# HR-Tracker — Spezifikation

Referenzdokument. Wird **nicht automatisch** in jede Claude-Code-Session geladen — gezielt mit `@docs/SPEC.md` referenzieren oder vom `@explorer`-Subagenten lesen lassen.

## Hardware

- moofit HR8 (BLE-Brustgurt)
- Samsung Galaxy Watch D227 (Wear-OS-Companion)
- Standard BLE Heart Rate Service `0x180D` / Characteristic `0x2A37` / CCCD `0x2902`
- ANT+ ignorieren

## Tech-Stack

| Bereich       | Technologie           |
| ------------- | --------------------- |
| Sprache       | Kotlin                |
| UI            | Jetpack Compose       |
| Min SDK       | 26 (Wear 30)          |
| Target SDK    | 35                    |
| Async         | Coroutines + Flow     |
| Datenbank     | Room                  |
| BLE           | Nordic Kotlin BLE     |
| Charts        | Vico                  |
| DI            | Hilt                  |
| Serialization | kotlinx.serialization |
| Wear-IPC      | Wearable Data Layer (`MessageClient`) |

## Datenmodell

```kotlin
Session(id, label, startedAt, endedAt, maxHrUsed, restingHr, note,
        zoneSnapshotJson /* DB v2 */)

HrSample(id, sessionId, timestampMs, bpm, rrIntervalsMs)

SportLabel(id, name, isPredefined)
```

Vordefinierte Labels: Volleyball, Beach, Krafttraining, Cardio, Trainingbike.

DB-Version 2 (Migration 1→2 für `zoneSnapshotJson`).

## BLE-Flow

```text
Scan (Filter 0x180D) → Connect → Service Discovery
  → CCCD aktivieren → Notifications parsen → Flow<HrSample>
```

### Heart-Rate-Measurement-Format

Flags-Byte:

```text
bit0 = HR uint8/uint16
bit3 = Energy Expended vorhanden
bit4 = RR-Intervalle vorhanden
```

RR-Umrechnung: `rr_ms = raw * 1000 / 1024`

### Device-Adress-Guard

Samsung-BLE-Stack mischt HR-Notifications der Galaxy Watch (über GPS / Google Play Services) in `HrBleManager.onCharacteristicChanged` ein. Daher: in beiden Overrides die MAC-Adresse gegen das erwartete Gerät prüfen, fremde Notifications mit Warn-Log verwerfen. Siehe `docs/CHANGELOG.md` → "BLE Cross-Notification Bugfix".

## Foreground Service

Pflicht während aktiver Session. Type: `connectedDevice`. Aufgaben:

- BLE-Verbindung halten
- Samples inkrementell speichern
- BPM + Laufzeit in der Notification anzeigen (über zentrale `formatDuration`, immer `HH:MM:SS`)

Dauer-Anzeigen (Live-Timer, Zone-Zeit, Notification) rendern einheitlich über `ui.Format.formatDuration(totalSeconds: Long): String` als `HH:MM:SS`, um Overflow über 60 Minuten zu vermeiden.

## HR-Quellen-Switch

`UserSettings.hrSource: HrSource` (BLE | WATCH) im DataStore. `HrRecordingService` injiziert sowohl `HrBleManager` als auch `WearableHrSource` (Singleton `SharedFlow<ParsedHr>`) und wählt die Quelle reaktiv via `flatMapLatest(settingsRepository.userSettings)`.

**Wichtig**: `notifyWatch(true/false)` nur senden, wenn `currentHrSource == WATCH`. Sonst Doppel-Stream.

## Zonen-Modell

- HRmax-Formel: Tanaka (`208 - 0.7·Alter`), Manual Override möglich.
- Default: **Karvonen** (`((HRmax − HRrest) · z) + HRrest`).
- Fallback: **%HRmax**.
- Pro Session als JSON-Snapshot persistieren (`Session.zoneSnapshotJson`), damit historische Sessions mit ihren Original-Zonengrenzen angezeigt werden, auch wenn HRrest später geändert wird.

Zone-Farben (Z1–Z5): Blau → Hellblau → Lila → Pink-Lila → Pink (siehe Design-System).

## Screens

| Screen   | Inhalt                                                                  |
| -------- | ----------------------------------------------------------------------- |
| Scan     | Geräteliste, Verbindungsstatus, Auto-Reconnect, Start-Dialog mit Label, HRV-Messung-Button |
| Live     | BPM, Zone, Timer, Live-Chart, Ø-BPM, Ziel-Zone, Zeit-pro-Zone, Puls-Anim |
| History  | Sessionliste, Summary-Card, Swipe-to-Delete                             |
| Detail   | BPM-Chart, Zonen-Banding, Statistiken, RMSSD, TRIMP, Notiz, Label-Edit  |
| Settings | Alter, HRmax-Override, Ruhepuls, Zonenmodell, Labels, Ziel-Zone, HR-Quelle |
| Onboarding | 3-Step-Dialog (Willkommen, Alter, Ruhepuls) für Pflichtdaten der Zonenberechnung, jederzeit überspringbar |

Tutorial-Overlay: Pro Screen (Scan, Live, History, Settings) ein Spotlight-Overlay (`TutorialOverlay.kt`), das beim ersten Besuch einzelne UI-Elemente nacheinander hervorhebt (dimmt Hintergrund, schneidet per `BlendMode.Clear` ein Loch um das Element, zeigt Erklärkarte mit Weiter/Überspringen). Gesehen-Status pro Screen in DataStore (`tutorial_seen_screens`, `SettingsRepository`). Erklärkarte flippt zwischen oben/unten ausgerichtet (`BoxWithConstraints`), um das hervorgehobene Element nicht zu verdecken.

Live-Screen-Buttons: **Abbrechen** und **Abschließen** öffnen jeweils einen `ConfirmDialog` vor Aktion. System-Back öffnet bei aktiver Session zusätzlich einen 3-Wege-Dialog (Speichern / Verwerfen / Weiter messen). Scan-Screen zeigt bei aktiver Session einen **Fortsetzen**-Button zurück zur laufenden Live-Session (in-app only, kein Process-Death-Recovery).

HRV-Messung: "HRV messen"-Button im Scan-Screen öffnet `HrvDurationDialog` (Super Short 30s / Short 1min / Full 5min). Startet Session mit Label `"HRV RMSSD"`, navigiert zu LiveScreen mit `hrv`-Nav-Arg. LiveScreen zeigt rosa "VERBLEIBEND"-Countdown statt "GESAMTZEIT" und stoppt Session automatisch bei 0. RMSSD erscheint dann im DetailScreen.

Live-Screen Ziel-Zone/Chart: Klick auf **ZIEL-ZONE**-Stat öffnet Zonen-Picker (Z1–Z5), setzt `targetZone`. Klick auf eine **Zeit-in-Zone**-Spalte blendet diese Zone im Live-Chart ein/aus (`visibleZones`, rein lokaler State, kein Persistenz, Reset bei Sessionstart). Ziel-Band + "ZIEL"-Badge im Chart bleiben unabhängig davon immer sichtbar.

## Analytics (DetailScreen)

- **RMSSD** aus RR-Intervallen (Watch-Sessions haben keine RR → "–").
- **TRIMP** (Bannister, Karvonen-Ratio; Fallback %HRmax × Dauer).
- Zonenverteilung über Snapshot-Grenzen (Prio: `Session.zoneSnapshotJson`, sonst aktuelle Settings).

## Export

V1: **JSON**. CSV ist V2 (Batch 6 erledigt).

Struktur:

```text
session    : Metadaten, HRmax, Ruhepuls, ZoneModel, Zonengrenzen
summary    : avg_bpm, max_bpm, min_bpm, time_in_zone
samples[]  : timestamp, elapsed, bpm, rr_ms, zone
```

## Permissions

Android 12+: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`
< 12: `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION`
Immer: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `POST_NOTIFICATIONS`
Wear: `BODY_SENSORS`

## Design-System

Quelle: `.claude/designs/` (Home, Live-Training, Verlauf, Einstellungen — HTML-Mockups).

| Token         | Hex         | Verwendung              |
| ------------- | ----------- | ----------------------- |
| PrimaryPurple | `#B6A6F2`   | Primary, Buttons        |
| SecondaryBlue | `#5BA9E6`   | Zone 1, Secondary       |
| TertiaryPink  | `#F4738E`   | Zone 5, Tertiary        |
| ErrorRed      | `#EF4444`   | Abbrechen, Error        |
| BackgroundDark| `#1D1B20`   | App-Hintergrund         |
| SurfaceDark   | `#252330`   | Cards, Surfaces         |
| LightPurple   | `#CBBCFF`   | Zone 3, onSurface       |

Font: **Space Grotesk** (via `ui-text-google-fonts`, `Type.kt`, `HrTrackerTypography`). Material3 darkColorScheme.

## Shared Library (shared-android-lib)

Gradle Composite Build, eingebunden via `includeBuild("../../shared-android-lib")` in `settings.gradle.kts`. Repo getrennt unter `C:\Code\Android\shared-android-lib`, ebenfalls eingebunden in ArmSwing (`C:\Code\Arduino\ArmSwingProject`). Kein Maven/AAR-Publishing — Solo-Dev, manuelles Deployment.

Geteilter Code (`com.kevin.shared.*`):
- `ble`: `BleConstants`, `ConnectionState`
- `domain`: `SavedDevice`, `DeviceType`, `SoftDeletable`, `DiscoveredDevice`
- `settings`: `BleDevicePrefKeys`, `BleDevicePreferences`
- `service`: `RecordingServiceContract`, `BaseRecordingService`

Genutzt u. a. in `HrBleManager`, `HrRecordingService`, `SettingsRepository`, `LiveViewModel`, `ScanViewModel`, `ScanScreen`, `DetailScreen`, `HistoryScreen`, `Session`.

## Architektur-Entscheidungen

1. HRmax = Tanaka + optional Override
2. Karvonen Default, %HRmax Fallback
3. Vordefinierte Labels + eigene Labels
4. JSON-only V1 (CSV in Batch 6 nachgezogen)
5. Ein gespeicherter Brustgurt
6. Auto-Reconnect zwingend
7. RR-Intervalle verpflichtend speichern
8. HR-Quelle als User-Setting (BLE / WATCH), reaktiv gewählt
9. Watch-Sessions ohne RR — RMSSD darf nicht crashen, sondern "–" anzeigen
