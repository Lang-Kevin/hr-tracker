# HR-Tracker — Changelog

Chronologische Historie. **Nicht automatisch geladen** — Claude liest gezielt, wenn der Kontext einer historischen Entscheidung gebraucht wird.

## Milestones M1–M7 (Initial-Implementierung)

| Milestone | Inhalt                                  |
| --------- | --------------------------------------- |
| M1        | BLE Scan + Connect + BPM                |
| M2        | Room + Persistenz                       |
| M3        | Foreground Service + Live-Screen        |
| M4        | History + Detail                        |
| M5        | Zonen + Settings                        |
| M6        | JSON-Export                             |
| M7        | Robustheit + Reconnect                  |

Alle abgeschlossen.

---

## M-D1 — Design-System (2026-06-05)

Status: abgeschlossen, Build SUCCESSFUL. Quelle: `.claude/designs/`.

Geänderte Dateien:

- `ui/theme/Color.kt` — neu
- `ui/theme/Theme.kt` — custom darkColorScheme
- `ui/scan/ScanScreen.kt` — "Training starten" (war "Session starten")
- `ui/live/LiveScreen.kt` — Zonenfarben, "Gesamtzeit", zwei Buttons
- `ui/history/HistoryScreen.kt` — Empty-State erweitert
- `ui/settings/SettingsScreen.kt` — farbige Zone-Badges

## Live-Training-Erweiterung (2026-06-05)

- `domain/UserSettings.kt` → `targetZone: Int = 2`
- `data/repository/SettingsRepository.kt` → `TARGET_ZONE` DataStore-Key + `setTargetZone()`
- `data/db/SessionDao.kt` → `getByIdFlow(id)`
- `data/repository/SessionRepository.kt` → `activeSession: Flow<Session?>`
- `ui/live/LiveViewModel.kt` → `sessionLabel`, `averageBpm`, `targetZone`, `timeInZone`, `percentInTargetZone`, `zoneBounds`
- `ui/live/LiveScreen.kt` → Sport-Label im Header, Ø-BPM, Ziel-Zone-Card, Zeit-pro-Zone-Tabelle
- `ui/settings/SettingsScreen.kt` → Ziel-Zonen-FilterChips Z1–Z5

## Bestätigungsdialoge Live-Screen (2026-06-05)

- `ui/live/LiveScreen.kt` → Abbrechen / Abschließen öffnen jetzt `ConfirmDialog` (privater Composable, wiederverwendbar)

## Detail-Screen-Refactor (2026-06-05)

- `data/db/SessionDao.kt` → `updateLabel()`, `deleteByIds()`
- `ui/shared/TrainingUi.kt` → neu; `BpmZoneChart`, `ZeitInZoneSection`, `StatItem` (shared Live+Detail)
- `ui/detail/DetailViewModel.kt` → reaktiv (StateFlow), `timeInZone`, `dominantZone`, `percentInTargetZone`, `zoneBounds`, `updateLabel()`
- `ui/detail/DetailScreen.kt` → Live-Layout, tappable Label, `EditTrainingTypeDialog`

---

## App-Review — Verbesserungsplan (2026-06-05)

Voller Plan: `.claude/plans/werde-kreativ-und-review-dreamy-breeze.md`

### Batch 1 — Bugfixes & Quick Wins (`0e79de1`)
- #2 BPM-Clamp 30–220 in `HeartRateParser`
- #12 Settings-Validierungsfeedback (Inline-Errors)
- #17 Zeit-in-Zone-Bug: Timestamps statt Sample-Count

### Batch 2 — UX-Erweiterungen I (`5f80d54` + Batch-3-Commit)
- #14 Notiz-Feld DetailScreen (`SessionDao.updateNote`, `DetailViewModel.updateNote`, `EditNoteDialog`)
- #6 Label-Dialog beim Training-Start (`StartTrainingDialog`, `TRAINING_TYPES`)
- #1 Double-Start-Race-Condition (`isStarting`-Flag, `LaunchedEffect`)

### Batch 3 — UX-Erweiterungen II (`845efe5`)
- #4 CCCD-Fehler sichtbar (`ConnectionState.Error`, `onDescriptorWrite`, rote Statusfarbe)
- #11 Zonen-Erklärung Settings (`ZoneErklarungCard`, `AnimatedVisibility`)
- #25 Font Space Grotesk (`ui-text-google-fonts`, `Type.kt`, `HrTrackerTypography`)

### Batch 4 — History & Visuals (`0d7a469`)
- #9 Swipe-to-Delete History (`SwipeToDismissBox EndToStart`)
- #27 Puls-Animation Live (`Animatable` Herz-Icon, `lastRrMs` StateFlow)
- #8 History-Summary-Card (`SummaryCard`, `getGlobalAvgBpm` DAO-Query)

### Batch 5 — Analytics (`21cc3c4`)
- #15 RMSSD/HRV DetailScreen (RR-CSV → RMSSD-Formel)
- #16 TRIMP (Bannister Karvonen-Ratio, Fallback %HRmax × Dauer)
- #21 Zonengrenzen-Snapshot (`Session.zoneSnapshotJson`, DB v2, Migration 1→2)

### Batch 6 — Statistik & Export (`baa5fed`)
- #19 Statistik-Tab in History
- #23 CSV-Export
- #28 Onboarding-Flow

Alle 6 Batches abgeschlossen.

---

## Smartwatch Companion (2026-06-06)

Voller Plan: `.claude/plans/read-the-project-md-from-glittery-dawn.md`

Neues `:wear`-Modul (Wear OS 3+) für Samsung Galaxy Watch D227.

IPC: Wearable Data Layer API (`MessageClient`), Pfad `/hr_sample`, ~1 Hz.

### Architektur

```
Watch (:wear)                        Phone (:app)
SensorManager (TYPE_HEART_RATE)  →   WearHrListenerService
HrSensorManager (Flow<ParsedHr>) →   WearableHrSource (SharedFlow<ParsedHr>)
PhoneMessenger (MessageClient)   →   HrRecordingService (wählt HR-Quelle)
WatchCommandListener             ←   Session-State-Signal (/session_state)
HomeScreen (BPM, Status)
```

### Neue Dateien `:wear`
- `wear/build.gradle.kts` (minSdk 30, Wear Compose, wearable)
- `wear/AndroidManifest.xml` (BODY_SENSORS, WearableListenerService)
- `wear/WearApplication.kt`, `wear/MainActivity.kt`
- `wear/ui/HomeScreen.kt`
- `wear/sensor/HrSensorManager.kt`
- `wear/comms/PhoneMessenger.kt`
- `wear/comms/WatchCommandListener.kt`

### Neue Dateien `:app`
- `wearable/WearableHrSource.kt` (@Singleton SharedFlow)
- `wearable/WearHrListenerService.kt` (WearableListenerService)

### Geänderte Dateien `:app`
- `settings.gradle.kts` (`:wear` include)
- `libs.versions.toml` (play-services-wearable 18.2.0, wear-compose 1.3.1)
- `app/build.gradle.kts` (play-services-wearable)
- `AndroidManifest.xml` (WearHrListenerService)
- `SettingsRepository.kt` + `UserSettings.kt` (`HrSource` enum)
- `HrRecordingService.kt` (WearableHrSource injizieren)
- `SettingsScreen.kt` + `SettingsViewModel.kt` (HR-Quelle FilterChips)

### Bekannte Einschränkung

Samsung Galaxy Watch liefert keine RR-Intervalle über SensorManager → Watch-Sessions haben leere `rrIntervalsMs` → RMSSD zeigt "–".

---

## BLE Cross-Notification Bugfix (2026-06-06)

**Problem**: BPM-Wert wechselte im BLE-Modus zwischen zwei Werten (2-stellig HR8, 3-stellig Watch). Trat erst nach Smartwatch-Feature auf.

---

## Resume-Session + Back-Button-Warnung (2026-06-17)

In-app-Resume: solange der Prozess lebt, führt ein neuer "Fortsetzen"-Button auf `ScanScreen` zurück zur laufenden `LiveScreen`. Kein Process-Death-Recovery, kein BLE-Auto-Reconnect-on-Cold-Start.

System-Back auf `LiveScreen` öffnet bei aktiver Session einen 3-Wege-Dialog (Speichern / Verwerfen / Weiter messen) statt die Session stillschweigend zu verlassen.

**Bugfix**: bestehender "Abbrechen"-Button in `LiveScreen` verwarf die Session nicht wirklich — `MainActivity` verdrahtete kein eigenes `onAbortSession`, fiel auf `onStopSession` zurück (speicherte statt zu verwerfen). Jetzt korrekt verdrahtet, nutzt denselben Discard-Pfad wie der Back-Dialog (Hard-Delete inkl. `HrSample`-Cascade).

Geänderte/neue Dateien:
- `data/db/SessionDao.kt` → `deleteById(id)`
- `data/repository/SessionRepository.kt` → `discardSession()`
- `ui/scan/ScanViewModel.kt` → `discardSession()`-Wrapper
- `ui/scan/ScanScreen.kt` → `onResumeSession`-Parameter, "Fortsetzen"-Button
- `ui/live/LiveScreen.kt` → `BackHandler`, `LeaveSessionDialog`
- `ui/live/LiveViewModel.kt` → `sessionStartMs` aus persistiertem `Session.startedAt` statt Wanduhr-Zeit (korrekte Elapsed-Time nach Re-Entry)
- `MainActivity.kt` → Navigation für Resume + `onAbortSession`-Verdrahtung

Bekannte Restlücke: `timeInZone` (Sekunden pro Zone) wird beim Re-Entry auf leer zurückgesetzt — nur In-Memory, kein persistierter Quellwert. Gesamt-Elapsed-Zeit ist korrekt.

**Ursache**: Samsung BLE-Stack-Bug. Google Play Services verbindet sich über BLE mit der Galaxy Watch (die ebenfalls `0x2A37` exponiert). Samsungs Routing-Tabelle nutzt das Characteristic-UUID als Key statt das `(device, handle)`-Tupel — Watch-HR landete in `HrBleManager.gattCallback` und mischte sich mit HR8-Daten.

### Fixes

- `ble/HrBleManager.kt` — Device-Adress-Guard in beiden `onCharacteristicChanged`-Overrides. Fremde Notifications → Warn-Log + verwerfen. `connect()` stoppt Fake-Emission vor echtem Connect.
- `service/HrRecordingService.kt` — `notifyWatch(true/false)` nur noch im WATCH-Modus (`currentHrSource`-Feld).
- `ui/live/LiveViewModel.kt` — `flatMapLatest` auf `settingsRepository.userSettings` wählt reaktiv die richtige HR-Quelle.

---

## Toolchain-Upgrade Android Studio 2026.1.1 (2026-06-15)

Aligns both `:app` and `:wear` with the versions already in use in `shared-android-lib`.

### Versionen

| Artefakt | Alt | Neu |
| --- | --- | --- |
| Gradle wrapper | 8.13 | 9.4.1 |
| AGP | 8.13.2 | 9.2.1 |
| Kotlin | 2.0.20 | 2.1.0 |
| KSP | 2.0.20-1.0.25 | 2.1.0-1.0.29 |
| Compose BOM | 2024.11.00 | 2026.05.01 |
| compileSdk | 35 | 37 (beide Module) |
| Hilt | 2.53 | 2.57.1 |
| Room | 2.6.1 | 2.8.3 |
| Lifecycle | 2.8.7 | 2.9.2 |
| Navigation | 2.8.5 | 2.9.7 |
| Coroutines | 1.9.0 | 1.11.0 |
| Vico | 2.0.1 | 3.0.3 |
| Health Connect | 1.1.0-alpha06 | 1.1.0 |
| Wear Compose | 1.3.1 | 1.6.2 |

### AGP 9.x Kompatibilität

AGP 9.x registriert die `kotlin`-Extension automatisch (built-in Kotlin support). Da Hilt 2.57.1 noch `BaseExtension` aus dem alten AGP-Pfad referenziert, wird der neue Modus über `gradle.properties` deaktiviert:

```properties
android.builtInKotlin=false
android.newDsl=false
```

Gilt für beide `gradle.properties` (`:app` und `:wear`). Kann entfernt werden, sobald Hilt AGP 9.x nativ unterstützt.

### Vico 3.x API-Migration

`vico:compose:3.x` ist ein KMP-Artefakt. Die Klassen `CartesianChartModelProducer` und `lineSeries` sind von `com.patrykandpatrick.vico.core.cartesian.data.*` nach `com.patrykandpatrick.vico.compose.cartesian.data.*` gewandert.

Geändert: `ui/shared/BpmLineChart.kt` (2 Imports).

### Sonstige Fixes

- `ui/detail/DetailScreen.kt` — `Locale.getDefault()` in Composable durch `LocalConfiguration.current.locales[0]` ersetzt (Lint `NonObservableLocale`).

---

## Offen

- Eventuell: Wear-Modul-Watch-Komplikation (BPM auf Watchface).
- Optional: Heartrate-Stream über `WatchCommandListener` für Watch-only-Sessions ohne Phone-Foreground-Service.
