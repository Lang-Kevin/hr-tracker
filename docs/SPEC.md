# HR-Tracker — Spezifikation

Referenzdokument. Wird **nicht automatisch** in jede Claude-Code-Session geladen — gezielt mit `@docs/SPEC.md` referenzieren oder vom `@explorer`-Subagenten lesen lassen.

## Hardware

- moofit HR8 (BLE-Brustgurt)
- Standard BLE Heart Rate Service `0x180D` / Characteristic `0x2A37` / CCCD `0x2902`
- ANT+ ignorieren

> **Hinweis:** Wear-OS-Companion (Samsung Galaxy Watch D227) ist auf Branch
> `feat/wear-os-companion` ausgelagert und auf diesem Branch nicht enthalten.

## Tech-Stack

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

## Datenmodell

```kotlin
Session(id, label, startedAt, endedAt, maxHrUsed, restingHr, note,
        zoneSnapshotJson /* DB v2 */)

HrSample(id, sessionId, timestampMs, bpm, rrIntervalsMs)

SportLabel(id, name, isPredefined)

Milestone(id, sessionId, atSeconds, label /* DB v5 */)

UserSettings(age, hrMaxOverride, restingHr, zoneModel, targetZone, 
             chartDynamicScale, widgetVariant, weightKg, sex /* DataStore */)
```

Vordefinierte Labels: Volleyball, Beach, Krafttraining, Cardio, Trainingbike.

DB-Version 5 (Migration 1→2 `zoneSnapshotJson`; 4→5 Tabelle `milestones` + Index auf `sessionId`). **UserSettings (Alter, Ruhepuls, HRmax-Override, Zonenmodell, Zielzone, Diagramm-Skalierung, Widget-Variante, Körperdaten) sind DataStore-basiert, nicht in Room persistiert. Kalorien werden auf Basis von `weightKg` und `sex` on-read berechnet und nicht persistiert.**

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

Gültiger BPM-Bereich: **30–220**. Samples außerhalb dieses Bereichs (z. B. Kein-Kontakt-Readings mit bpm = 0) werden in `HeartRateParser` **verworfen** (Rückgabe `null`), nicht auf 30 geklemmt. So fließen keine Ausreißer in Live-Anzeige, Durchschnitt oder Export ein.

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

## HR-Quelle

HR-Quelle ist BLE (`HrBleManager`). `HrRecordingService` und `LiveViewModel`/`PipViewModel`
beziehen Samples direkt aus `HrBleManager.hrSamples` bzw. `HrBleManager.lastHr`.

> Der frühere reaktive HR-Quellen-Switch (BLE | WATCH) inkl. `WearableHrSource`,
> `notifyWatch()` und Health-Connect-Import lag am Wear-OS-Companion und ist auf
> Branch `feat/wear-os-companion` ausgelagert.

`hrSamples: SharedFlow<ParsedHr>` bleibt bewusst bei `replay = 0` — würde `replay = 1` gesetzt, bekäme `SessionRepository.launchSampleJob` bei jedem `startSession()`/`resume()` den zwischengespeicherten Vorgänger-Sample erneut geliefert und schriebe ihn als Phantom-Sample in Room. Für prozessweiten Zugriff auf den letzten Messwert (z. B. PiP-Widget) existiert stattdessen zusätzlich `lastHr: StateFlow<ParsedHr?>` auf `HrBleManager`, per `stateIn` aus dem Flow abgeleitet.

## Zonen-Modell

- HRmax-Formel: Tanaka (`208 - 0.7·Alter`), Manual Override möglich.
- Gemessener HRmax: Settings schlägt den höchsten aufgezeichneten BPM-Wert der letzten 90 Tage vor (`HrSampleDao.getObservedMaxBpm`), sofern er über dem aktiven HRmax liegt. Wird **nie** automatisch übernommen — der Nutzer tippt "Übernehmen", das Ergebnis landet im vorhandenen `manualMaxHr`.
- Zonen-Modell ist eine **explizite Einstellung** (`ZoneModel { HR_MAX, KARVONEN }`, DataStore-Key `zone_model`). Default: **`HR_MAX`** (%HRmax).
- `HR_MAX`: `HRmax · z`. `KARVONEN`: `((HRmax − HRrest) · z) + HRrest`.
- Beide Modelle nutzen die Lehrbuch-Prozentstufen 50/60/70/80/90. Das ist Absicht: die Stufen sind pro Modell standarddefiniert. Weil sich die Bezugsgröße unterscheidet, liegen Karvonen-Grenzen deutlich höher — Alter 30, HRmax 187, Ruhepuls 60: Zone 2 = 112–130 (%HRmax) vs. 136–148 (Karvonen). Früher wurde implizit auf Karvonen umgeschaltet, sobald ein Ruhepuls gesetzt war; das war für den Nutzer unsichtbar.
- `KARVONEN` ohne gesetzten Ruhepuls fällt auf `HR_MAX` zurück (Settings weist darauf hin).
- Pro Session als JSON-Snapshot persistieren (`Session.zoneSnapshotJson`), damit historische Sessions mit ihren Original-Zonengrenzen angezeigt werden, auch wenn HRrest, HRmax oder das Zonen-Modell später geändert werden — ein Default-Wechsel (z. B. `HR_MAX` → `KARVONEN`) bewertet vergangene Sessions dadurch nicht rückwirkend neu. Der Snapshot existiert allerdings nur für Sessions ab DB v2 — `MIGRATION_1_2` hat die Spalte ohne Backfill hinzugefügt, v1-Sessions haben dauerhaft `zoneSnapshotJson == null`.
- `HrZoneCalculator.resolveZones` bewertet genau diese Alt-Sessions nach der **Legacy-Regel** weiter (KARVONEN wenn `restingHr` gesetzt ist, sonst HR_MAX) — nicht nach dem neuen Default. Sonst würden sich die Zonengrenzen alter Sessions rückwirkend verschieben.

Zone-Farben (Z1–Z5): Blau → Hellblau → Lila → Pink-Lila → Pink (siehe Design-System).

Custom-Zonen-Editor: Leere Felder zeigen den berechneten Default als Placeholder. Beim Ändern einer Grenze werden Nachbargrenzen automatisch kaskadiert, sodass die Reihenfolge stets streng aufsteigend (30–220 BPM) bleibt. "Was bedeuten die Zonen?" öffnet sich über ein Fragezeichen-Icon als Dialog in der Zonen-Vorschau (ersetzt den bisherigen Inline-Hinweis).

## Screens

| Screen   | Inhalt                                                                  |
| -------- | ----------------------------------------------------------------------- |
| Scan     | Geräteliste, Verbindungsstatus, Auto-Reconnect, Start-Dialog mit Label, HRV-Messung-Button |
| Live     | BPM, Zone, Timer, Live-Chart, Ø-BPM, Ziel-Zone, Zeit-pro-Zone, Puls-Anim |
| History  | Sessionliste, Summary-Card, Swipe-to-Delete, Label-Filter, Datumsbereich-Filter |
| Detail   | BPM-Chart, Zonen-Banding, Statistiken (Kalorien, HRmax, Ruhepuls), RMSSD, TRIMP, HRR60, Notiz, Label-Edit  |
| Settings | Alter, HRmax-Override, Ruhepuls, Körperdaten (Gewicht, Geschlecht), Zonenmodell, Labels, Ziel-Zone, Diagramm-Standard, HR-Quelle (nur Debug) |
| Onboarding | 3-Step-Dialog (Willkommen, Alter, Ruhepuls) für Pflichtdaten der Zonenberechnung, jederzeit überspringbar |

Tutorial-Overlay: Pro Screen (Scan, Live, History, Settings) ein Spotlight-Overlay (`TutorialOverlay.kt`), das beim ersten Besuch einzelne UI-Elemente nacheinander hervorhebt (dimmt Hintergrund, schneidet per `BlendMode.Clear` ein Loch um das Element, zeigt Erklärkarte mit Weiter/Überspringen). Gesehen-Status pro Screen in DataStore (`tutorial_seen_screens`, `SettingsRepository`). Erklärkarte flippt zwischen oben/unten ausgerichtet (`BoxWithConstraints`), um das hervorgehobene Element nicht zu verdecken. Das Overlay ist **modal** (konsumiert alle Touches, nichts dahinter bedienbar); das Spotlight-Rect wird pro Schritt **eingefroren** statt live gelesen, off-screen-Ziele werden per `BringIntoViewRequester` automatisch in den sichtbaren Bereich gescrollt; das History-Tutorial startet erst bei mindestens einem vorhandenen Training und aktivem Verlauf-Tab.

Session-Beenden: Sowohl der Stop-Button im Live-Screen als auch die System-Back-Geste öffnen bei aktiver Session **denselben** 3-Wege-Dialog `LeaveSessionDialog` (Speichern / Verwerfen / Weiter messen). Bei Speichern wird die Session beendet, der Foreground Service gestoppt und direkt zum Detail-Screen der Session navigiert (`popUpTo(Route.SCAN)`, `launchSingleTop`); Back-Geste vom Report landet auf Scan-Screen. Scan-Screen hat keinen eigenen Session-Zweig: bei aktiver Session navigiert `LaunchedEffect(activeSessionId)` in `MainActivity` immer zu Live; Stoppen/Verwerfen nur über Live.

HRV-Messung: "HRV messen"-Button im Scan-Screen öffnet `HrvDurationDialog` (Super Short 30s / Short 1min / Full 5min). Startet Session mit Label `"HRV RMSSD"`, navigiert zu LiveScreen mit `hrv`-Nav-Arg. LiveScreen zeigt rosa "VERBLEIBEND"-Countdown statt "GESAMTZEIT" und stoppt Session automatisch bei 0. RMSSD erscheint dann im DetailScreen.

Live-Screen Ziel-Zone/Chart: Klick auf **ZIEL-ZONE**-Stat öffnet Zonen-Picker (Z1–Z5), setzt `targetZone`. Ziel-Band + "ZIEL"-Badge im Chart bleiben immer sichtbar.

History-Filter: Label-Filter (Mehrfachauswahl) und Datumsbereich-Filter (Einzeltag oder Zeitraum via `DateRangePicker`) werden UND-verknüpft. Der Label-Filter ist hinter einem FilterChip-Button (FilterList-Icon) versteckt; Klick öffnet ein `ModalBottomSheet` mit der Chip-Auswahl. Der Datumsbereich wird TZ-korrekt behandelt: UTC-Mitternacht-Millis aus dem Picker werden in `HistoryViewModel.setDateRange` auf die geräte-lokale Zeitzone re-ankert. Die Summary-Card (Ø-BPM, Gesamtdauer, Sessionanzahl) reagiert auf beide Filter: Kennzahlen beziehen sich immer nur auf die aktuell gefilterten Sessions; Ø-BPM ist sample-gewichtet (DAO-Query über HrSample).

## Picture-in-Picture (PiP-BPM-Widget)

Beim Minimieren während einer aktiven Session wechselt die App automatisch in natives Android-Picture-in-Picture — **kein** `SYSTEM_ALERT_WINDOW`-Overlay, keine zusätzliche Berechtigung nötig. Ohne aktive Session minimiert die App normal.

- **Trigger:** gegated auf `SessionRepository.activeSessionId != null`. API 31+: `PictureInPictureParams.Builder().setAutoEnterEnabled(true)`, nahtlos beim Home-Swipe. API 26–30: kein Auto-Enter verfügbar, Fallback über `onUserLeaveHint()` — bei Gesten-Navigation nicht immer zuverlässig (Best Effort).
- **Aspect Ratio:** 4:3.
- **Inhalt (`PipContent`), in `STANDARD`:** Herz-Icon + BPM (34sp), "Zone N" in Zonenfarbe (`ZoneColors[zone - 1]`), Session-Dauer `HH:MM:SS`. Bei pausierter Session steht "PAUSE" statt der Zeit.
- **Datenquelle:** `PipViewModel` kombiniert `HrBleManager.lastHr`, `SessionRepository.activeSession`, `SessionRepository.isPaused` und einen 1-Sekunden-Ticker.
- **Bekannte Grenze:** Die angezeigte Dauer zieht Pausen nicht ab (die Pausen-Akkumulation `pausedAccumMs` lebt bisher nur im nav-scoped `LiveViewModel`, nicht prozessweit) — deshalb "PAUSE" statt einer falschen Zeit während der Pause.
- **Nicht enthalten:** keine PiP-Actions (Pause/Stop-Buttons), kein Chart im PiP-Fenster, kein automatisches Schließen des Fensters bei Session-Ende.
- **State-Erhalt bei PiP-Wechsel:** Der `NavController` wird in `MainActivity.onCreate` (`setContent`) geholt und über beide Zweige (PiP und normale UI) hinweg geteilt. So bleibt der BackStack und die daran gebundenen Entry-Scopes der ViewModels (Live-Chart, Zonentimer) beim PiP-Wechsel erhalten.

### Widget-Varianten

PiP-Fenster und Notification teilen sich eine Einstellung ("Widget-Anzeige" im Settings-Screen, Segmented Buttons), gespeichert als `UserSettings.widgetVariant: WidgetVariant` (DataStore-Key `widget_variant`, Enum-Name; unbekannter/fehlender Wert fällt auf `STANDARD` zurück; keine Room-Migration). Default `STANDARD` — bestehende Installationen sehen keine Änderung. Die reine Funktion `widgetNotificationText` in `domain/WidgetVariant.kt` baut den Notification-Text; `PipContent.kt` verzweigt auf vier Layout-Composables.

| Variante   | PiP                                     | Notification-Text            |
| ---------- | ---------------------------------------- | ----------------------------- |
| `MINIMAL`  | nur BPM, groß, in Zonenfarbe             | `142 BPM`                     |
| `STANDARD` | BPM + Zone + Dauer (heutiges Layout)     | `142 BPM  •  Zone 3  •  00:12:04` |
| `ZONE`     | Zone dominant (große Zahl), BPM klein    | `Zone 3  •  142 BPM`          |
| `TIMER`    | Dauer dominant, BPM klein                | `00:12:04  •  142 BPM`        |

- **Fehlende Werte:** `bpm == null` → `--` an Stelle der Zahl; `zone == null` → das Zonen-Segment entfällt ersatzlos.
- **Pause-Regel:** `PAUSE` ersetzt die Zeile, die sonst die Dauer zeigt — in `STANDARD` die untere Info-Zeile, in `TIMER` die große Zahl. In `MINIMAL` und `ZONE`, die keine Dauer zeigen, kommt `PAUSE` als zusätzliche kleine Zeile dazu. Die Notification zeigt `PAUSE` nicht — dort bleibt der Text unverändert.

## Analytics (DetailScreen)

- **RMSSD** aus RR-Intervallen (Watch-Sessions haben keine RR → "–").
- **TRIMP** (Bannister, Karvonen-Ratio; Fallback %HRmax × Dauer).
- **Kalorien** (Aktivkalorien, Keytel minus Grundumsatz): On-read aus den HR-Samples, Gewicht, Alter und Geschlecht berechnet (`CalorieCalculator.estimateActiveKcal`).
  - **Brutto (Keytel, kcal/min):** Männer `(-55.0969 + 0.6309 × BPM + 0.1988 × Gewicht + 0.2017 × Alter) / 4.184`, Frauen `(-20.4022 + 0.4472 × BPM − 0.1263 × Gewicht + 0.074 × Alter) / 4.184` (Formel liefert kJ/min, daher `/ 4.184`).
  - **Grundumsatz (Schofield, WHO/FAO 1985, kcal/Tag ÷ 1440):** nach Geschlecht und Altersband (<18, 18–29, 30–59, ≥60) linear im Gewicht; braucht keine Körpergröße.
  - **Integration:** Pro Intervall zwischen zwei aufeinanderfolgenden Samples `max(0, Brutto(BPM) − Grundumsatz) × Δt`. Intervalle mit Δt > `MAX_SAMPLE_GAP_MS` (5 000 ms; Pause, BLE-Dropout) zählen nicht — Pausen gehen weder mit Trainingspuls noch mit Grundumsatz ein.
  - **Bedingungen:** `null`, wenn Gewicht oder Geschlecht fehlen oder weniger als 2 Samples vorliegen. Keine Persistierung, keine Migration.
- Zonenverteilung über Snapshot-Grenzen via `HrZoneCalculator.resolveZones(zoneSnapshotJson, maxHr, restingHr)`: Snapshot bevorzugt, Fallback auf Neuberechnung bei fehlendem oder ungültigem JSON — stellt Custom-Zonen-Konsistenz in der Statistik sicher.
- **Lücken-Ausschluss:** Intervalle mit Δt > 5 000 ms zwischen zwei aufeinanderfolgenden Samples (BLE-Dropout) fließen nicht in die Zonenverweildauer ein (`HrZoneCalculator.aggregateTimeInZone`).
- **Millisekunden-genaue Akkumulation:** `aggregateTimeInZone` summiert pro Zone Millisekunden und rundet erst am Ende auf Sekunden — kein Sub-Sekunden-Verlust pro Intervall bei kurzen/unregelmäßigen BLE-Samples.
- **Lücken-Visualisierung:** Der Detail-Chart markiert erkannte Dropout-Lücken als rote gestrichelte Linie auf avg-BPM-Höhe (display-only). Lücken-Quelle: `HrZoneCalculator.detectGaps`.

## Charts / Visualisierung (BpmZoneChart)

BpmZoneChart auf Live- und Detail-Screen unterstützt zwei Anzeigemodi, umschaltbar via IconToggleButton:

- **Dynamic (Standard):** Y-Achse auto-scaled zu gemessenen Min/Max-BPM ±10% Padding; nur Zonen mit `timeInZone>0` werden gezeichnet. BPM-Serien >300 Punkte werden via shared-android-lib `aggregateByChunks` downgesampled.
- **Static:** Legacy-Verhalten — Y-Range fest auf `[min-8, max+8]`, alle Zonen sichtbar.

**Diagramm-Standard (Setting):** `UserSettings.chartDynamicScale: Boolean` (DataStore, Default `true`) steuert den initialen Modus auf **Live- und Detail-Screen**. Beide Screens halten einen lokalen `dynamicScaleOverride: Boolean?` (`null` = Setting gilt); der IconToggleButton setzt nur diesen Override, gültig für die aktuelle Screen-Instanz. Screens dürfen den Modus nicht hartkodiert initialisieren.

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
- **Berechnung:** On-read aus vorhandenen `HrSample`-Daten (timestampMs, bpm) — **keine Room-Migration erforderlich.** HRR-Card wird im Detail-Screen **immer** gerendert: mit numerischem Wert und Rating-Kategorie, wenn Peak-Bedingungen erfüllt sind; sonst mit „—" und Begründung „Zu wenig Daten oder kein Peak ≥ 70 % HRmax mit 60 s Nachlauf."

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

Statistik-Kachel-Werte (`StatItem`, `ui/shared/TrainingUi.kt`): immer einzeilig (`maxLines = 1`), `autoSize = TextAutoSize.StepBased` schrumpft bis 14sp bei Überlänge, wächst aber nie über `headlineSmall` hinaus.

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
8. HR-Quelle: BLE (Wear-OS-Companion ausgelagert nach `feat/wear-os-companion`)

## Bewusst nicht gebaut (Kalorien & Report Stats)

Diese Features sind **bewusst nicht implementiert**, um MVP-Scope zu halten. Nachrüstbar, wenn die Nutzungsmetriken es rechtfertigen:

- **Keine Kalorien-Persistenz / Migration:** Kalorien werden on-read aus aktuellem Gewicht/Geschlecht berechnet. Alte Sessions profitieren rückwirkend von Gewichtsupdates. Persistierung wird nötig, wenn `UserSettings` historisiert wird (z. B. Gewichtsverlauf-Tracking zur Vermeidung von Report-Drift).
- **Kein separater Post-Session-Summary-Screen:** Der Detail-Screen ist bereits eine vollständige Report-View mit allen Kennzahlen. Ein zusätzlicher Modal/Screen würde Komplexität ohne Mehrwert bringen.
- **Kein Onboarding-Schritt für Körperdaten:** Die Settings-Rubrik "Körperdaten" reicht. Ein zusätzlicher Onboarding-Dialog wird nötig, wenn Nutzer die Kalorien-Kachel dauerhaft leer lassen (Monitoring via Telemetry).
- **Kein strukturiertes HRR-Failure-Result:** Ein einheitlicher Begründungstext („Zu wenig Daten oder kein Peak ≥ 70 % HRmax mit 60 s Nachlauf") deckt beide Null-Ursachen ab. Separate Fehlerkategorien bringen keinen UX-Vorteil bei heute noch niedriger HRR-Häufigkeit.
