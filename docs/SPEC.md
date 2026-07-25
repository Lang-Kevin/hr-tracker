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

Milestone(id, sessionId, atSeconds, label /* DB v5 */)
```

Vordefinierte Labels: Volleyball, Beach, Krafttraining, Cardio, Trainingbike.

DB-Version 5 (Migration 1→2 `zoneSnapshotJson`; 4→5 Tabelle `milestones` + Index auf `sessionId`).

### Meilensteine

Live-FAB markiert den aktuellen Sekundenstand als Meilenstein (vertikale Linie im Live-Chart). Persistierung bei Session-Ende mit Default-Label `M{i+1}` in `NonCancellable` (kein Verlust bei VM-Zerstörung). Detail-Screen listet die Marker (`formatDuration`), Label klickbar editierbar. Nur reguläres Session-Ende persistiert, nicht Force-Kill.

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

### BLE Auto-Pause / Auto-Resume

Gilt ausschließlich im BLE-Modus (`hrSource == BLE`). Watch-Sessions sind nicht betroffen.

- **Auto-Pause**: Wechselt der BLE-Verbindungsstatus auf `Disconnected`, `Reconnecting` oder `Error`, pausiert `HrRecordingService` die laufende Aufzeichnung automatisch und setzt das interne Flag `pausedByConnectionLoss = true`.
- **Auto-Resume**: Erreicht der Status wieder `Ready`, wird die Aufzeichnung automatisch fortgesetzt — jedoch **nur**, wenn `pausedByConnectionLoss == true`. Eine vom Nutzer manuell gestartete Pause bleibt unberührt.
- **Live-Screen**: Zeigt während der Auto-Pause ein Banner „Verbindung verloren — Messung pausiert".
- **Cleanup**: Der `connectionStateJob` (Coroutine, die den BLE-Status beobachtet) wird in `onRecordingStop()` des Service gecancelt.

## HR-Quellen-Switch

`UserSettings.hrSource: HrSource` (BLE | WATCH) im DataStore. `HrRecordingService` injiziert sowohl `HrBleManager` als auch `WearableHrSource` (Singleton `SharedFlow<ParsedHr>`) und wählt die Quelle reaktiv via `flatMapLatest(settingsRepository.userSettings)`. Die HR-Quelle-Card im Settings-Screen ist nur bei `BuildConfig.DEBUG == true` sichtbar (Development-Feature, nicht für Produktions-Nutzer gedacht).

**Wichtig**: `notifyWatch(true/false)` nur senden, wenn `currentHrSource == WATCH`. Sonst Doppel-Stream.

## Zonen-Modell

- HRmax-Formel: Tanaka (`208 - 0.7·Alter`), Manual Override möglich.
- Default: **Karvonen** (`((HRmax − HRrest) · z) + HRrest`).
- Fallback: **%HRmax**.
- Pro Session als JSON-Snapshot persistieren (`Session.zoneSnapshotJson`), damit historische Sessions mit ihren Original-Zonengrenzen angezeigt werden, auch wenn HRrest später geändert wird.

Zone-Farben (Z1–Z5): Blau → Hellblau → Lila → Pink-Lila → Pink (siehe Design-System).

Custom-Zonen-Editor: Leere Felder zeigen den berechneten Default als Placeholder. Beim Ändern einer Grenze werden Nachbargrenzen automatisch kaskadiert, sodass die Reihenfolge stets streng aufsteigend (30–220 BPM) bleibt. "Was bedeuten die Zonen?" öffnet sich über ein Fragezeichen-Icon als Dialog in der Zonen-Vorschau (ersetzt den bisherigen Inline-Hinweis).

## Screens

| Screen   | Inhalt                                                                  |
| -------- | ----------------------------------------------------------------------- |
| Scan     | Geräteliste, Verbindungsstatus, Auto-Reconnect, Start-Dialog mit Label, HRV-Messung-Button |
| Live     | BPM, Zone, Timer, Live-Chart, Ø-BPM, Ziel-Zone, Zeit-pro-Zone, Puls-Anim |
| History  | Sessionliste, Summary-Card, Swipe-to-Delete, Label-Filter, Datumsbereich-Filter |
| Detail   | BPM-Chart, Zonen-Banding, Statistiken, RMSSD, TRIMP, Notiz, Label-Edit  |
| Settings | Alter, HRmax-Override, Ruhepuls, Zonenmodell, Labels, Ziel-Zone, Diagramm-Standard, HR-Quelle (nur Debug) |
| Onboarding | 3-Step-Dialog (Willkommen, Alter, Ruhepuls) für Pflichtdaten der Zonenberechnung, jederzeit überspringbar |

Tutorial-Overlay: Pro Screen (Scan, Live, History, Settings) ein Spotlight-Overlay (`TutorialOverlay.kt`), das beim ersten Besuch einzelne UI-Elemente nacheinander hervorhebt (dimmt Hintergrund, schneidet per `BlendMode.Clear` ein Loch um das Element, zeigt Erklärkarte mit Weiter/Überspringen). Gesehen-Status pro Screen in DataStore (`tutorial_seen_screens`, `SettingsRepository`). Erklärkarte flippt zwischen oben/unten ausgerichtet (`BoxWithConstraints`), um das hervorgehobene Element nicht zu verdecken. Das Overlay ist **modal** (konsumiert alle Touches, nichts dahinter bedienbar); das Spotlight-Rect wird pro Schritt **eingefroren** statt live gelesen, off-screen-Ziele werden per `BringIntoViewRequester` automatisch in den sichtbaren Bereich gescrollt; das History-Tutorial startet erst bei mindestens einem vorhandenen Training und aktivem Verlauf-Tab.

Session-Beenden: Sowohl der Stop-Button im Live-Screen als auch die System-Back-Geste öffnen bei aktiver Session **denselben** 3-Wege-Dialog `LeaveSessionDialog` (Speichern / Verwerfen / Weiter messen). Scan-Screen zeigt bei aktiver Session einen **Fortsetzen**-Button zurück zur laufenden Live-Session (in-app only, kein Process-Death-Recovery).

HRV-Messung: "HRV messen"-Button im Scan-Screen öffnet `HrvDurationDialog` (Super Short 30s / Short 1min / Full 5min). Startet Session mit Label `"HRV RMSSD"`, navigiert zu LiveScreen mit `hrv`-Nav-Arg. LiveScreen zeigt rosa "VERBLEIBEND"-Countdown statt "GESAMTZEIT" und stoppt Session automatisch bei 0. RMSSD erscheint dann im DetailScreen.

Live-Screen Ziel-Zone/Chart: Klick auf **ZIEL-ZONE**-Stat öffnet Zonen-Picker (Z1–Z5), setzt `targetZone`. Ziel-Band + "ZIEL"-Badge im Chart bleiben immer sichtbar.

History-Filter: Label-Filter (Mehrfachauswahl) und Datumsbereich-Filter (Einzeltag oder Zeitraum via `DateRangePicker`) werden UND-verknüpft. Der Label-Filter ist hinter einem FilterChip-Button (FilterList-Icon) versteckt; Klick öffnet ein `ModalBottomSheet` mit der Chip-Auswahl. Der Datumsbereich wird TZ-korrekt behandelt: UTC-Mitternacht-Millis aus dem Picker werden in `HistoryViewModel.setDateRange` auf die geräte-lokale Zeitzone re-ankert. Die Summary-Card (Ø-BPM, Gesamtdauer, Sessionanzahl) reagiert auf beide Filter: Kennzahlen beziehen sich immer nur auf die aktuell gefilterten Sessions; Ø-BPM ist sample-gewichtet (DAO-Query über HrSample).

## Analytics (DetailScreen)

- **RMSSD** aus RR-Intervallen (Watch-Sessions haben keine RR → "–").
- **TRIMP** (Bannister, Karvonen-Ratio; Fallback %HRmax × Dauer).
- Zonenverteilung über Snapshot-Grenzen via `HrZoneCalculator.resolveZones(zoneSnapshotJson, maxHr, restingHr)`: Snapshot bevorzugt, Fallback auf Neuberechnung bei fehlendem oder ungültigem JSON — stellt Custom-Zonen-Konsistenz in der Statistik sicher.
- **Lücken-Ausschluss:** Intervalle mit Δt > 5 000 ms zwischen zwei aufeinanderfolgenden Samples (BLE-Dropout) fließen nicht in die Zonenverweildauer ein (`HrZoneCalculator.aggregateTimeInZone`).
- **Millisekunden-genaue Akkumulation:** `aggregateTimeInZone` summiert pro Zone Millisekunden und rundet erst am Ende auf Sekunden — kein Sub-Sekunden-Verlust pro Intervall bei kurzen/unregelmäßigen BLE-Samples.
- **Lücken-Visualisierung:** Der Detail-Chart markiert erkannte Dropout-Lücken als rote gestrichelte Linie auf avg-BPM-Höhe (display-only). Lücken-Quelle: `HrZoneCalculator.detectGaps`.

## Charts / Visualisierung (BpmZoneChart)

BpmZoneChart auf Live- und Detail-Screen unterstützt zwei Anzeigemodi, umschaltbar via IconToggleButton:

- **Dynamic (Standard):** Y-Achse auto-scaled zu gemessenen Min/Max-BPM ±10% Padding; nur Zonen mit `timeInZone>0` werden gezeichnet. BPM-Serien >300 Punkte werden via shared-android-lib `aggregateByChunks` downgesampled.
- **Static:** Legacy-Verhalten — Y-Range fest auf `[min-8, max+8]`, alle Zonen sichtbar.

**Diagramm-Standard (Setting):** `UserSettings.chartDynamicScaleDefault: Boolean` (DataStore) steuert den initialen Modus im Detail-Screen. Der Nutzer kann den Modus pro Session über den IconToggleButton überschreiben; der Override gilt nur für die aktuelle Screen-Instanz.

## Erholung / Heart Rate Recovery (HRR)

**HRR60-Kennzahl:** Automatisch nach Session-Ende berechnet, zeigt die Herzfrequenz-Erholungsrate an.

- **Definition:** Peak-HF (höchste beobachtete BPM während Session) minus Herzfrequenz im Fenster [60–65 Sekunden] nach dem Peak.
- **Peak-Kriterium:** Muss ≥ 70 % HRmax erreichen mit ≥ 60 Sekunden Nachlauf; toleriert kurze BLE-Lücken in der HF-Messung.
- **Post-Workout-Zielzone:** 60 % HRmax — die App zeigt an, ob und wann dieser Erholungsbereich erreicht wurde.
- **Rating-Kategorien (sportwissenschaftlicher Standard):**
  - < 12 bpm: Niedrig
  - 12–17 bpm: Normal
  - 18–29 bpm: Gut
  - ≥ 30 bpm: Sehr gut
- **Berechnung:** On-read aus vorhandenen `HrSample`-Daten (timestampMs, bpm) — **keine Room-Migration erforderlich.** HRR-Card wird im Detail-Screen nur angezeigt, wenn Peak-Bedingungen erfüllt sind und HRR60 berechenbar ist.

## Export / Report

V1: **JSON**. CSV ist V2 (Batch 6 erledigt).

Zonengrenzen in JSON und CSV werden über `HrZoneCalculator.resolveZones` aus dem persistierten `zoneSnapshotJson` gelesen (Custom-Zonen-konsistent); Fallback auf Neuberechnung nur bei fehlendem oder ungültigem Snapshot.

Struktur:

```text
session    : Metadaten, HRmax, Ruhepuls, ZoneModel, Zonengrenzen
summary    : avg_bpm, max_bpm, min_bpm, time_in_zone
samples[]  : timestamp, elapsed, bpm, rr_ms, zone
```

Der Detail-Screen bietet einen Report-Dialog mit einem "Kopieren"-Button, der den Report-JSON direkt via `LocalClipboardManager` in die Zwischenablage kopiert; Toast-Feedback "Report kopiert" bestätigt die Aktion.

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
