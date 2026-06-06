# HR-Tracker App Plan

## Ziel

Native Android-App zur Aufzeichnung, Speicherung, Visualisierung und zum Export von Herzfrequenzdaten eines BLE-Brustgurts.

Hardware:

* moofit HR8
* Standard BLE Heart Rate Service (`0x180D`)
* Heart Rate Measurement (`0x2A37`)
* RR-Intervalle verfügbar
* ANT+ ignorieren

---

## Tech Stack

| Bereich       | Technologie           |
| ------------- | --------------------- |
| Sprache       | Kotlin                |
| UI            | Jetpack Compose       |
| Min SDK       | 26                    |
| Target SDK    | 35                    |
| Async         | Coroutines + Flow     |
| Datenbank     | Room                  |
| BLE           | Nordic Kotlin BLE     |
| Charts        | Vico                  |
| DI            | Hilt                  |
| Serialization | kotlinx.serialization |

---

## Architektur

```text
com.kevin.hrtracker
├── ble/
├── service/
├── data/
│   ├── db/
│   ├── entity/
│   └── repository/
├── domain/
├── export/
└── ui/
    ├── scan/
    ├── live/
    ├── history/
    ├── detail/
    └── settings/
```

MVVM.

---

## Datenmodell

### Session

```kotlin
Session(
    id,
    label,
    startedAt,
    endedAt,
    maxHrUsed,
    restingHr,
    note
)
```

### HrSample

```kotlin
HrSample(
    id,
    sessionId,
    timestampMs,
    bpm,
    rrIntervalsMs
)
```

### SportLabel

```kotlin
SportLabel(
    id,
    name,
    isPredefined
)
```

Vordefinierte Labels:

* Volleyball
* Beach
* Krafttraining
* Cardio

Eigene Labels ergänzbar.

---

## BLE

### Heart Rate Measurement

UUID:

* Service: `0x180D`
* Characteristic: `0x2A37`
* CCCD: `0x2902`

Flags:

```text
bit0 = HR uint8/uint16
bit3 = Energy Expended vorhanden
bit4 = RR-Intervalle vorhanden
```

RR Umrechnung:

```kotlin
rr_ms = raw * 1000 / 1024
```

### Verbindungsablauf

1. Scan mit Filter `0x180D`
2. Connect
3. Service Discovery
4. Notifications aktivieren (CCCD)
5. Notifications parsen
6. Flow<HrSample> emittieren

### Anforderungen

Pflicht:

* CCCD korrekt aktivieren
* RR-Intervalle speichern
* Auto-Reconnect
* Inkrementelles Speichern
* Crash-resistent
* Kein Sample-Verlust

---

## Foreground Service

Pflicht während aktiver Session.

Aufgaben:

* BLE-Verbindung halten
* Samples speichern
* BPM anzeigen
* Laufzeit anzeigen

Foreground Service Type:

```text
connectedDevice
```

---

## Herzfrequenz-Zonen

### HRmax

Default:

```text
HRmax = 208 - 0.7 * Alter
```

Optional:

```text
Manual Override
```

### Zonenmodell

Default:

```text
Karvonen
```

Fallback:

```text
%HRmax
```

### Snapshot pro Session

Speichern:

* maxHrUsed
* restingHr
* zoneModel

---

## Screens

### Scan

* Geräteliste
* Verbindungsstatus
* Auto-Reconnect

### Live

* BPM
* Zone
* Timer
* Live Chart
* Label Auswahl

### History

* Sessionliste

### Detail

* BPM Chart
* Zonen-Banding
* Zonen-Verteilung
* Statistiken
* Export

### Settings

* Alter
* HRmax Override
* Ruhepuls
* Zonenmodell
* Labels
* Gerät

---

## Visualisierung

### Live

* BPM Linie

### Detail

* BPM Linie
* Zonen-Banding
* Zonen-Verteilung

### Statistiken

* Durchschnitt
* Maximum
* Minimum
* Dauer
* Zeit pro Zone

---

## Export

V1:

```text
JSON
```

Export enthält:

### session

* Metadaten
* HRmax
* Ruhepuls
* ZoneModel
* Zonengrenzen

### summary

* avg_bpm
* max_bpm
* min_bpm
* time_in_zone

### samples

* timestamp
* elapsed
* bpm
* rr_ms
* zone

CSV ist V2.

---

## Permissions

Android 12+:

* BLUETOOTH_SCAN
* BLUETOOTH_CONNECT

Android <12:

* BLUETOOTH
* BLUETOOTH_ADMIN
* ACCESS_FINE_LOCATION

Zusätzlich:

* FOREGROUND_SERVICE
* FOREGROUND_SERVICE_CONNECTED_DEVICE
* POST_NOTIFICATIONS

---

## Milestones

### M1

BLE Scan + Connect + BPM

### M2

Room + Persistenz

### M3

Foreground Service + Live Screen

### M4

History + Detail

### M5

Zonen + Settings

### M6

JSON Export

### M7

Robustheit + Reconnect

---

## Entscheidungen

1. HRmax = Tanaka + optional Override
2. Karvonen Default, %HRmax Fallback
3. Vordefinierte Labels + eigene Labels
4. JSON-only für V1
5. Ein gespeicherter Brustgurt
6. Auto-Reconnect
7. RR-Intervalle verpflichtend speichern

---

## Design-System (M-D1, 2026-06-05)

Status: **ABGESCHLOSSEN** — Build SUCCESSFUL

Quelle: `.claude/designs/` (Home, Live-Training, Verlauf, Einstellungen HTML-Mockups)

### Farbpalette

| Token         | Hex         | Verwendung              |
| ------------- | ----------- | ----------------------- |
| PrimaryPurple | `#B6A6F2`   | Primary, Buttons        |
| SecondaryBlue | `#5BA9E6`   | Zone 1, Secondary       |
| TertiaryPink  | `#F4738E`   | Zone 5, Tertiary        |
| ErrorRed      | `#EF4444`   | Abbrechen, Error        |
| BackgroundDark| `#1D1B20`   | App-Hintergrund         |
| SurfaceDark   | `#252330`   | Cards, Surfaces         |
| LightPurple   | `#CBBCFF`   | Zone 3, onSurface       |

Zone-Farben (Z1–Z5): Blau → Hellblau → Lila → Pink-Lila → Pink

### Geänderte Dateien

* `ui/theme/Color.kt` — neu erstellt
* `ui/theme/Theme.kt` — custom darkColorScheme
* `ui/scan/ScanScreen.kt` — "Training starten" (war: "Session starten")
* `ui/live/LiveScreen.kt` — neue Zonenfarben, "Gesamtzeit"-Label, zwei Buttons (Abbrechen / Abschließen)
* `ui/history/HistoryScreen.kt` — erweiterter Empty-State-Text
* `ui/settings/SettingsScreen.kt` — farbige Zone-Badges in Vorschau

### Offen / Nächste Schritte

* **Font Space Grotesk**: TTF-Dateien unter `res/font/` ablegen + `Type.kt` erstellen (aktuell: Material3 Default)

---

## Live-Training Erweiterung (2026-06-05)

Status: **ABGESCHLOSSEN** — Build SUCCESSFUL

Quelle: `.claude/designs/Live-Training.html`

### Hinzugefügt

* `domain/UserSettings.kt` — `targetZone: Int = 2`
* `data/repository/SettingsRepository.kt` — `TARGET_ZONE` DataStore-Key + `setTargetZone()`
* `data/db/SessionDao.kt` — `getByIdFlow(id)` für reaktives Session-Label
* `data/repository/SessionRepository.kt` — `activeSession: Flow<Session?>`
* `ui/live/LiveViewModel.kt` — `sessionLabel`, `averageBpm`, `targetZone`, `timeInZone`, `percentInTargetZone`, `zoneBounds`; Ticker trackt Zeit pro Zone
* `ui/live/LiveScreen.kt` — Sport-Label im Header, BPM Ø, Ziel-Zone-Card (Zeit + Fortschrittsbalken + %), Zeit-in-Zone-Tabelle (alle 5 Zonen mit BPM-Grenzen)
* `ui/settings/SettingsScreen.kt` — Ziel-Zonen-Auswahl Z1–Z5 (FilterChips)
* `ui/settings/SettingsViewModel.kt` — `setTargetZone()`

---

## Bestätigungsdialoge Live-Screen (2026-06-05)

Status: **ABGESCHLOSSEN** — Build SUCCESSFUL

### Änderungen

* `ui/live/LiveScreen.kt` — Beide Buttons (Abbrechen / Abschließen) öffnen jetzt einen `AlertDialog` bevor die Aktion ausgeführt wird
  * **Abbrechen**: "Training abbrechen?" → Bestätigen verwirft Daten, "Weiter messen" schließt Dialog
  * **Abschließen**: "Training abschließen?" → Bestätigen speichert Daten, "Weiter messen" schließt Dialog
  * Neuer privater Composable `ConfirmDialog` (wiederverwendbar, kein ViewModel-Eingriff)

---

## Detail-Screen: Live-Layout + Trainingstyp editieren (2026-06-05)

Status: **ABGESCHLOSSEN** — Build SUCCESSFUL

### Änderungen

* `data/db/SessionDao.kt` — `updateLabel()`, `deleteByIds()` hinzugefügt
* `ui/shared/TrainingUi.kt` — neu; enthält `BpmZoneChart`, `ZeitInZoneSection`, `StatItem` (shared zwischen Live + Detail)
* `ui/detail/DetailViewModel.kt` — reaktiv umgeschrieben (StateFlow statt one-shot); `timeInZone`, `dominantZone`, `percentInTargetZone`, `zoneBounds`, `updateLabel()`
* `ui/detail/DetailScreen.kt` — komplett neu: Live-Layout, tappable Label, `EditTrainingTypeDialog` mit RadioButtons (Allgemeines Training / Beachvolleyball / Trainingbike / Volleyball, alphabetisch)

---

## App-Review & Verbesserungsplan (2026-06-05)

Status: **IN ARBEIT**

Siehe vollständigen Plan: `.claude/plans/werde-kreativ-und-review-dreamy-breeze.md`

### Batch 1 — Bugfixes & Quick Wins ✅ (commit 0e79de1)
- [x] #2 BPM-Clamp (HeartRateParser: 30–220)
- [x] #12 Settings Validierungsfeedback (Inline-Fehlermeldungen)
- [x] #17 Zeit-in-Zone Bug Fix (Timestamps statt Sample-Count)

### Batch 2 — UX-Erweiterungen I ✅ (commit 5f80d54 + Batch-3-Commit)
- [x] #14 Notiz-Feld in DetailScreen (SessionDao.updateNote, DetailViewModel.updateNote, EditNoteDialog)
- [x] #6 Label-Dialog beim Training starten (StartTrainingDialog, TRAINING_TYPES, RadioButtons)
- [x] #1 Double-Start Race Condition (isStarting-Flag, enabled=!isStarting, LaunchedEffect)

### Batch 3 — UX-Erweiterungen II ✅ (commit 845efe5)
- [x] #4 CCCD-Fehler sichtbar machen (ConnectionState.Error, onDescriptorWrite, rote Statusfarbe)
- [x] #11 Zonen-Erklärung in Settings (ZoneErklarungCard, AnimatedVisibility, Z1–Z5)
- [x] #25 Font Space Grotesk (ui-text-google-fonts, Type.kt, HrTrackerTypography, Theme.kt)

### Batch 4 — History & Visuals ✅ (commit 0d7a469)
- [x] #9 Swipe-to-Delete in History (SwipeToDismissBox EndToStart, deleteSingle in HistoryViewModel)
- [x] #27 Puls-Animation Live-Screen (Animatable Herz-Icon, lastRrMs StateFlow, LaunchedEffect)
- [x] #8 History Summary Card (SummaryCard, getGlobalAvgBpm DAO-Query, HistoryViewModel.SummaryStats)

### Batch 5 — Analytics ✅ (commit 21cc3c4)
- [x] #15 RMSSD/HRV in DetailScreen (RR-CSV → RMSSD-Formel, Analytics-Row)
- [x] #16 TRIMP-Score (Bannister Karvonen-Ratio, Fallback %HRmax×Dauer, DetailScreen)
- [x] #21 Zonengrenzen-Snapshot (Session.zoneSnapshotJson, DB v2, Migration 1→2, DetailViewModel Snapshot-Prio)

### Batch 6 — Statistik & Export ✅ (commit baa5fed)
- [x] #19 Statistik-Tab in History
- [x] #23 CSV-Export
- [x] #28 Onboarding-Flow

Status: **ABGESCHLOSSEN** — alle 6 Batches fertiggestellt

---

## Smartwatch Companion App (2026-06-06)

Status: **IN ARBEIT**

Ziel: Neues `:wear`-Modul (Wear OS 3+) für Samsung Galaxy Watch D227, das Herzfrequenzdaten live an die Haupt-App überträgt.

Vollständiger Plan: `.claude/plans/read-the-project-md-from-glittery-dawn.md`

### Architektur

```
Watch (:wear)                        Phone (:app)
─────────────────────────────────    ───────────────────────────────────
SensorManager (TYPE_HEART_RATE)  →   WearHrListenerService
HrSensorManager (Flow<ParsedHr>) →   WearableHrSource (SharedFlow<ParsedHr>)
PhoneMessenger (MessageClient)   →   HrRecordingService (wählt HR-Quelle)
WatchCommandListener ←────────── ←  Session-State-Signal (/session_state)
HomeScreen (BPM, Status)
```

IPC: Wearable Data Layer API (`MessageClient`), Pfad `/hr_sample`, ~1 Hz.

### Neue Dateien (:wear)
- `wear/build.gradle.kts` — minSdk 30 (Wear OS 3), Wear Compose, wearable
- `wear/AndroidManifest.xml` — BODY_SENSORS, WearableListenerService
- `wear/WearApplication.kt`, `wear/MainActivity.kt`
- `wear/ui/HomeScreen.kt` — BPM-Anzeige, Herz-Animation, Verbindungsstatus
- `wear/sensor/HrSensorManager.kt` — SensorManager → `Flow<ParsedHr>`
- `wear/comms/PhoneMessenger.kt` — sendet HR via MessageClient
- `wear/comms/WatchCommandListener.kt` — empfängt Session-Status vom Telefon

### Neue Dateien (:app)
- `app/.../wearable/WearableHrSource.kt` — `@Singleton` SharedFlow<ParsedHr>
- `app/.../wearable/WearHrListenerService.kt` — WearableListenerService, emittiert in WearableHrSource

### Geänderte Dateien (:app)
- `settings.gradle.kts` — `:wear` include
- `libs.versions.toml` — play-services-wearable 18.2.0, wear-compose 1.3.1
- `app/build.gradle.kts` — play-services-wearable dependency
- `AndroidManifest.xml` — WearHrListenerService registrieren
- `SettingsRepository.kt` + `UserSettings.kt` — `HrSource` enum (BLE/WATCH), DataStore-Key
- `HrRecordingService.kt` — WearableHrSource injizieren, HR-Flow nach Quelle wählen
- `SettingsScreen.kt` + `SettingsViewModel.kt` — HR-Quelle FilterChips (BLE / Galaxy Watch)

### Hinweis RR-Intervalle
Samsung Galaxy Watch stellt keine RR-Intervalle über SensorManager bereit. Watch-Sitzungen haben leere `rrIntervalsMs` → RMSSD/HRV zeigt "–".
