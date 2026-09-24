# Widget-Varianten (PiP + Notification)

**Datum:** 2026-08-03
**Status:** Design freigegeben

## Ziel

Der User kann in den Einstellungen wählen, welche Informationen die Live-Anzeigen
zeigen. Betroffen sind zwei Flächen: das PiP-Fenster und die Foreground-Service-
Notification. Beide folgen derselben Einstellung — wer nur die BPM-Zahl sehen will,
will das an beiden Stellen.

## Scope

**Drin:** vier Presets für PiP und Notification, gesteuert von einer gemeinsamen
Einstellung.

**Draußen:**

- Homescreen-AppWidget (eigener Milestone, falls je gewünscht)
- Getrennte Einstellungen für PiP und Notification
- Freie Feldauswahl per Checkbox statt Presets
- Neue Metriken (Ø-BPM, %HRmax, Zeit in Zielzone) — die Varianten kombinieren nur,
  was `PipViewModel` heute schon liefert: BPM, Zone, Dauer, Pause-Zustand
- `PAUSE` in der Notification (steht dort heute auch nicht)

## Varianten

| Variante   | PiP                                     | Notification-Text            |
| ---------- | --------------------------------------- | ---------------------------- |
| `MINIMAL`  | nur BPM, groß, in Zonenfarbe            | `142 BPM`                    |
| `STANDARD` | BPM + Zone + Dauer (heutiges Layout)    | `142 BPM • Zone 3 • 00:12:04` |
| `ZONE`     | Zone dominant (große `3`), BPM klein    | `Zone 3 • 142 BPM`           |
| `TIMER`    | Dauer dominant, BPM klein               | `00:12:04 • 142 BPM`         |

Default ist `STANDARD` — das entspricht dem heutigen Verhalten, bestehende
Installationen sehen also keine Änderung, bis sie selbst umstellen.

## Komponenten

### 1. `domain/WidgetVariant.kt` (neu)

```kotlin
enum class WidgetVariant { MINIMAL, STANDARD, ZONE, TIMER }

fun widgetNotificationText(
    variant: WidgetVariant,
    bpm: Int?,
    zone: Int?,
    elapsed: String
): String
```

Reine Funktion, keine Android-Abhängigkeit, dadurch direkt unit-testbar. Wird vom
Service genutzt; liegt deshalb in `domain/` und nicht in `ui/pip/`.

Fehlende Werte: `bpm == null` → `--` an Stelle der Zahl, `zone == null` → das
Zonen-Segment entfällt ersatzlos.

### 2. `domain/UserSettings.kt`

Neues Feld `widgetVariant: WidgetVariant = WidgetVariant.STANDARD`, analog zu
`chartDynamicScale` (`UserSettings.kt:10`).

### 3. `data/repository/SettingsRepository.kt`

Neuer DataStore-Key `widget_variant`, gespeichert als Enum-Name. Beim Lesen fällt
ein unbekannter oder fehlender Wert auf `STANDARD` zurück. Setter
`setWidgetVariant(variant: WidgetVariant)`. Kein Room, keine Migration.

### 4. `ui/pip/PipUiState.kt`

`PipUiState` bekommt das Feld `variant: WidgetVariant`, `buildPipUiState` einen
entsprechenden Parameter. Der bestehende `ponytail:`-Kompromiss (Dauer =
Wanduhr seit `startedAt`, Pausen werden nicht abgezogen, `PipUiState.kt:14`)
bleibt unverändert.

### 5. `ui/pip/PipViewModel.kt`

`settingsRepository.userSettings` wird auf ein Paar aus `effectiveZones` und
`widgetVariant` gemappt, damit `combine` bei fünf Argumenten bleibt. Sonst
unverändert.

### 6. `ui/pip/PipContent.kt`

`when (state.variant)` verzweigt auf vier private Composables. Geteilt bleiben die
Akzentfarbe aus `ZoneColors` und das Herz-Icon.

**Pause-Regel:** bei `paused` ersetzt `PAUSE` die Zeile, die sonst die Dauer zeigt —
in `STANDARD` die untere Info-Zeile, in `TIMER` die große Zahl. In `MINIMAL` und
`ZONE`, die keine Dauer zeigen, kommt `PAUSE` als kleine Zeile unten dazu. So steht
nie eine weiterlaufende Dauer neben einer pausierten Session.

### 7. `service/HrRecordingService.kt`

`buildNotification` (`HrRecordingService.kt:47`) baut seinen Text über
`widgetNotificationText`. Dafür braucht der Service Variante und Zonen: ein
zusätzlicher `serviceScope.launch { settingsRepository.userSettings.collect { … } }`
in `onRecordingStart` hält beide in Feldern aktuell. Damit greift eine Umstellung
sofort, auch mitten in der Session — konsistent zum PiP, das live reagiert. Die
Zone wird per `HrZoneCalculator.zoneFor(bpm, zones)` bestimmt.

Der Job wird in `onRecordingStop` genauso abgebrochen wie `notificationJob` und
`connectionStateJob`.

### 8. `ui/settings/SettingsScreen.kt`

Eine `SingleChoiceSegmentedButtonRow` „Widget-Anzeige" mit den vier Varianten,
platziert bei den bestehenden Anzeige-Einstellungen. Segmented Buttons statt
Dropdown, weil Ziel-Zone (`SettingsScreen.kt:211`) und HR-Quelle
(`SettingsScreen.kt:413`) dieses Muster schon nutzen.

## Datenfluss

```text
DataStore ─┬─→ SettingsRepository.userSettings ─┬─→ PipViewModel   → PipUiState → PipContent
           │                                    └─→ HrRecordingService → widgetNotificationText
BLE/Watch ─┴─→ lastHr ─────────────────────────────┘
```

## Fehlerfälle

| Fall                             | Verhalten                                  |
| -------------------------------- | ------------------------------------------ |
| Unbekannter Enum-Name im Store   | Fallback `STANDARD`                        |
| Kein BPM (noch keine Messung)    | `--` statt der Zahl, Zonensegment entfällt |
| Keine Zonen konfiguriert         | Zonensegment entfällt                      |
| Session pausiert                 | `PAUSE` im PiP, Notification unverändert   |

## Tests

- Neues Testfile für `widgetNotificationText`: vier Varianten, jeweils mit BPM
  vorhanden und `bpm == null`.
- `PipUiStateTest.kt` um das Variant-Feld erweitert.
- Composables bleiben ungetestet, wie im Projekt üblich.

## Hard Rules

Keine der Regeln aus `CLAUDE.md` wird berührt: kein Eingriff in CCCD, den
Device-Adress-Guard, `notifyWatch()`, die RR-Persistenz oder die Room-Migration.
Der Foreground-Service-Typ bleibt `connectedDevice`.
