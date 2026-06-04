# Planung: Android Herzfrequenz-Tracker App ("HR-Tracker")

**Zweck dieses Dokuments:** Technischer Bauplan / Handoff für den Claude Code Agent.
**Hardware:** moofit HR8 Brustgurt (Bluetooth 5.0 + ANT+, sendet Standard-BLE Heart Rate Profile inkl. RR-Intervalle).
**Design:** Bewusst funktional gehalten — Layout/Styling kommt anschließend von einem separaten UX/UI-Agent. Dieser Plan liefert die Struktur, nicht die Optik.

---

## 1. Wichtigste Erkenntnis zur Hardware

Der moofit HR8 ist ein **Standard-BLE-Sensor**. Er implementiert das offizielle **Heart Rate Service (GATT, UUID `0x180D`)**. Es gibt **kein** moofit-spezifisches Protokoll zu reverse-engineeren.

Relevante Characteristics:

| Characteristic | UUID | Eigenschaft | Inhalt |
|---|---|---|---|
| Heart Rate Measurement | `0x2A37` | Notify | BPM + optional RR-Intervalle |
| Body Sensor Location | `0x2A38` | Read | Sensor-Position (optional) |
| Client Characteristic Config (CCCD) | `0x2902` | Write | Notifications aktivieren |

**Konsequenz:** Die App ist ein generischer BLE-HR-Client und funktioniert damit auch mit Polar, Garmin, Wahoo etc. — nicht nur dem HR8.

> **ANT+ ignorieren.** ANT+ ist auf den meisten Android-Phones nicht ohne weiteres nutzbar. Wir setzen ausschließlich auf BLE.

---

## 2. Empfohlener Tech-Stack

| Bereich | Wahl | Begründung |
|---|---|---|
| Sprache | **Kotlin** | Standard, Coroutines/Flow |
| UI | **Jetpack Compose** | Flexibel, da Design später separat kommt |
| Min SDK | **26** (Android 8) | BLE stabil; deckt praktisch alle aktiven Geräte ab |
| Target SDK | **35** (Android 15) | Aktuelle Permission-/Foreground-Service-Regeln |
| Async | **Coroutines + Flow** | BLE-Notifications als `Flow<HrSample>` |
| Persistenz | **Room** | Lokal, typsicher, FK-Beziehungen |
| BLE | **Nordic Kotlin-BLE-Library** (`no.nordicsemi.android.kotlin.ble`) | Reduziert GATT-Boilerplate massiv; Alternative: nacktes Android-BLE-API |
| Charts | **Vico** (`com.patrykandpatrick.vico`) | Compose-nativ. Alternative: MPAndroidChart (View-basiert, sehr ausgereift) |
| DI | **Hilt** | Saubere Trennung Repository/ViewModel (optional, empfohlen) |
| Serialisierung | **kotlinx.serialization** | Für JSON-Export |

---

## 3. Architektur (MVVM, Clean-ish)

```
com.kevin.hrtracker
├── ble/                # BLE-Scan, Verbindung, GATT, HR-Parsing
│   ├── HrBleManager.kt
│   └── HeartRateParser.kt
├── service/            # ForegroundService für laufende Session
│   └── HrRecordingService.kt
├── data/
│   ├── db/             # Room: Database, DAOs
│   ├── entity/         # Session, HrSample
│   └── repository/     # SessionRepository, SettingsRepository
├── domain/             # Zonen-Berechnung, Session-Statistik
│   ├── HrZoneCalculator.kt
│   └── SessionStats.kt
├── export/             # CSV- und JSON-Export + Share-Intent
│   └── SessionExporter.kt
└── ui/                 # Compose-Screens + ViewModels (Design später)
    ├── scan/
    ├── live/
    ├── history/
    ├── detail/
    └── settings/
```

---

## 4. Datenmodell (Room)

```kotlin
@Entity
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,            // Sportart, z.B. "Volleyball", "Krafttraining"
    val startedAt: Long,          // epoch millis
    val endedAt: Long?,           // null = läuft noch
    val maxHrUsed: Int,           // HRmax-Wert, mit dem Zonen berechnet wurden (Snapshot!)
    val restingHr: Int?,          // optional, für Karvonen
    val note: String? = null
)

@Entity(
    foreignKeys = [ForeignKey(
        entity = Session::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class HrSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMs: Long,        // absoluter Zeitstempel des Samples
    val bpm: Int,
    val rrIntervalsMs: String? = null  // RR-Intervalle als CSV "812,798,..." (für HRV)
)

@Entity(indices = [Index(value = ["name"], unique = true)])
data class SportLabel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,           // z.B. "Volleyball", "Krafttraining"
    val isPredefined: Boolean   // beim ersten Start geseedet; User-Labels = false
)
```

Vordefinierte Labels werden beim ersten App-Start geseedet (z.B. Volleyball, Beach, Krafttraining, Cardio). Der User kann eigene über das Settings- oder Live-Screen-UI ergänzen.

**Wichtige Design-Entscheidung:** `maxHrUsed` wird **pro Session als Snapshot** gespeichert. Ändert Kevin später seinen HRmax in den Settings, bleiben alte Sessions zonen-korrekt.

**RR-Intervalle:** Der HR8 liefert sie. Unbedingt mitspeichern — sie sind für die spätere HRV-/Performance-Analyse durch den Chatbot wertvoll und lassen sich nachträglich nicht rekonstruieren.

---

## 5. BLE: Das Herzstück

### 5.1 Parsing der Heart Rate Measurement (`0x2A37`)

Das erste Byte ist ein **Flags-Byte**. Danach folgen die Daten variabel. Der Agent muss das korrekt parsen:

```
Byte 0 = Flags:
  Bit 0 : HR-Format    0 = UINT8 (1 Byte), 1 = UINT16 (2 Byte, little-endian)
  Bit 1-2: Sensor-Kontakt-Status
  Bit 3 : Energy Expended vorhanden
  Bit 4 : RR-Intervalle vorhanden

Danach:
  - HR-Wert (1 oder 2 Byte, je nach Bit 0)
  - ggf. Energy Expended (2 Byte) — überspringen
  - ggf. RR-Intervalle (je 2 Byte, UINT16, Einheit = 1/1024 Sekunde)
    → in ms umrechnen: rr_ms = rr_raw * 1000 / 1024
```

Pseudocode:

```kotlin
fun parse(data: ByteArray): ParsedHr {
    val flags = data[0].toInt()
    var offset = 1
    val bpm = if (flags and 0x01 == 0) {
        data[offset].toInt() and 0xFF
    } else {
        val v = (data[offset].toInt() and 0xFF) or ((data[offset+1].toInt() and 0xFF) shl 8)
        offset += 1; v
    }
    offset += 1
    if (flags and 0x08 != 0) offset += 2            // Energy Expended überspringen
    val rr = mutableListOf<Int>()
    if (flags and 0x10 != 0) {                      // RR-Intervalle
        while (offset + 1 < data.size) {
            val raw = (data[offset].toInt() and 0xFF) or ((data[offset+1].toInt() and 0xFF) shl 8)
            rr += (raw * 1000 / 1024)
            offset += 2
        }
    }
    return ParsedHr(bpm, rr)
}
```

### 5.2 Verbindungs-Flow

1. **Scan** nach Geräten, die `0x180D` advertisen (ScanFilter setzen → spart Akku & Rauschen).
2. Nutzer wählt Gerät → `connectGatt`.
3. Service Discovery → `0x180D` → Characteristic `0x2A37`.
4. **Notifications aktivieren** (CCCD `0x2902` mit `ENABLE_NOTIFICATION_VALUE` beschreiben — wird oft vergessen, sonst kommen keine Daten!).
5. Eingehende Notifications → parsen → als `Flow<HrSample>` emittieren.

### 5.3 Robustheit (nicht optional)

- **Reconnect-Logik:** Brustgurt verliert kurz Kontakt → automatisch neu verbinden, ohne die laufende Session zu beenden.
- **Inkrementelles Speichern:** Jedes Sample sofort in Room schreiben, nicht erst beim Stop. So überlebt eine Session auch App-Kill / Crash.
- **Sampling-Rate:** HR-Gurte senden typ. ~1 Hz. Kein Throttling nötig.

---

## 6. Foreground Service (Pflicht!)

Eine Sport-Session läuft mit ausgeschaltetem Display / im Hintergrund weiter. Android killt sonst die BLE-Verbindung.

- `HrRecordingService` als **Foreground Service** mit dauerhafter Notification (zeigt aktuellen BPM + Laufzeit).
- Service-Typ ab Android 14: `connectedDevice` (ggf. `health`).
- Service hält BLE-Verbindung + schreibt Samples in DB. UI bindet sich nur dran an.

---

## 7. Herzfrequenz-Zonen

**5-Zonen-Modell auf Basis %HRmax** (Standard, gut für Visualisierung):

| Zone | % HRmax | Charakter |
|---|---|---|
| Z1 | 50–60 % | Erholung / sehr leicht |
| Z2 | 60–70 % | Grundlage / Fettstoffwechsel |
| Z3 | 70–80 % | Aerob / moderat |
| Z4 | 80–90 % | Schwelle / hart |
| Z5 | 90–100 % | Maximal |

**HRmax bestimmen — ENTSCHIEDEN: aus User-Angaben.**
In den Settings gibt der User seine Daten ein, die App leitet HRmax daraus ab:
1. **Alter** → automatische Berechnung per Tanaka: `HRmax = 208 − 0.7 × Alter`.
2. **Optionaler manueller Override:** Hat der User einen gemessenen HRmax, überschreibt dieser den berechneten Wert.

**Zonen-Modell — ENTSCHIEDEN: beide, Default Karvonen.**
- `HrZoneCalculator` unterstützt **%HRmax** und **Karvonen (HRR)**.
- **Default = Karvonen**, sobald ein Ruhepuls hinterlegt ist → individueller, passt besser zum trainierten Sportler.
- **Automatischer Fallback auf %HRmax**, wenn kein Ruhepuls eingetragen ist.

Formeln:
- %HRmax: `Grenze = HRmax × %`
- Karvonen: `Grenze = (HRmax − Ruhepuls) × % + Ruhepuls`

`HrZoneCalculator` nimmt `bpm` + `maxHrUsed` + optional `restingHr` + `zoneModel` und gibt Zone 1–5 zurück. Der Ruhepuls ist ein User-Profil-Setting und wird wie `maxHrUsed` pro Session als Snapshot gespeichert.

---

## 8. Screens (funktional, Design später)

1. **Scan/Verbindung** — Geräteliste, Verbindungsstatus, "gespeichertes Gerät" auto-reconnect.
2. **Live-Session** — Start/Stop, großer Live-BPM, aktuelle Zone, laufende Zeit, Live-Linienchart, Sportart-Label setzen (Auswahl aus vordefinierter Liste; eigene Labels können ergänzt werden).
3. **History** — Liste gespeicherter Sessions (Datum, Sportart, Dauer, Ø/Max BPM).
4. **Session-Detail** — Linienchart über Zeit mit **Zonen-Banding** (farbige Hintergrundbänder), Zonen-Verteilung (Zeit pro Zone, Balken/Donut), Statistik (Ø/Max/Min, Dauer), Label editieren, **Export-Button**.
5. **Settings** — Alter (→ Tanaka-HRmax), optionaler manueller HRmax-Override, Ruhepuls (für Karvonen), Zonen-Modell (Auto: Karvonen falls Ruhepuls vorhanden, sonst %HRmax), Verwaltung der Sportart-Labels, gespeichertes Gerät.

---

## 9. Visualisierung

- **Primär:** Liniendiagramm BPM (Y) über Zeit (X) — sowohl live als auch im Detail.
- **Zonen-Banding:** farbige horizontale Bänder hinter der Linie (sofort erkennbar, in welcher Zone trainiert wurde).
- **Zonen-Verteilung:** "Zeit pro Zone" als Balken oder Donut → der typische Trainings-Insight.
- **Session-Stats:** Ø-BPM, Max, Min, Dauer, Zeit in jeder Zone.

---

## 10. Export (für externe Chatbot-Analyse)

Da die Auswertung **außerhalb** der App passiert, muss der Export selbsterklärend und maschinenlesbar sein. **ENTSCHIEDEN: JSON-only für v1** (trägt Kontext + Metadaten, ideal für Chatbot). CSV ist bewusst zurückgestellt und kann später ergänzt werden.

**JSON-Struktur:**
```json
{
  "session": {
    "id": 12,
    "label": "Volleyball",
    "started_at": "2026-06-03T19:30:00+02:00",
    "ended_at": "2026-06-03T22:00:00+02:00",
    "duration_s": 9000,
    "max_hr_used": 187,
    "resting_hr": 52,
    "zone_model": "percent_hrmax",
    "zones": {"z1":[94,112],"z2":[112,131],"z3":[131,150],"z4":[150,168],"z5":[168,187]}
  },
  "summary": {
    "avg_bpm": 138, "max_bpm": 179, "min_bpm": 71,
    "time_in_zone_s": {"z1":600,"z2":1800,"z3":3600,"z4":2400,"z5":600}
  },
  "samples": [
    {"t":"2026-06-03T19:30:01+02:00","elapsed_s":1,"bpm":78,"rr_ms":[770,765],"zone":1}
  ]
}
```

**CSV (zurückgestellt, v2):** falls später gewünscht, eine Zeile pro Sample mit `timestamp_iso,elapsed_s,bpm,rr_ms,zone`. Für v1 nicht implementieren.

- Export schreibt Datei (App-spezifischer Storage oder via Storage Access Framework) und teilt sie via `ACTION_SEND` / Share-Sheet.
- Metadaten (Sportart, HRmax, Zonen-Definitionen) **mit exportieren** → der Chatbot hat sofort Kontext für die Analyse.

---

## 11. Permissions (Manifest)

```xml
<!-- Android 12+ (API 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" tools:targetApi="s"/>
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" tools:targetApi="s"/>

<!-- Android < 12 -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30"/>

<!-- Foreground Service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"/>

<!-- Notification (Android 13+) für Service-Notification -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
```

Runtime-Permissions müssen zur Laufzeit abgefragt werden (BLUETOOTH_SCAN/CONNECT, ggf. Location <12, POST_NOTIFICATIONS).

---

## 12. Empfohlene Build-Reihenfolge (Milestones für den Agent)

| # | Milestone | Definition of Done |
|---|---|---|
| **M1** | Setup + BLE | Scan, Verbindung, Live-BPM erscheint in Logcat |
| **M2** | Persistenz | Room + Session Start/Stop, Samples werden inkrementell gespeichert |
| **M3** | Live + Service | Live-Screen mit Chart + Timer, läuft als Foreground Service weiter bei Screen-off |
| **M4** | History + Detail | Session-Liste + Detail mit Chart |
| **M5** | Zonen + Settings | HRmax-Konfiguration, Zonen-Berechnung, Zonen-Banding + Verteilung |
| **M6** | Export | JSON + CSV inkl. Share-Intent |
| **M7** | Robustheit | Reconnect bei Verbindungsabbruch, Edge Cases, App-Kill-Safe |

Jeder Milestone ist eigenständig testbar → guter Schnitt für einen agentischen Workflow.

---

## 13. Getroffene Entscheidungen

1. **HRmax:** aus User-Angaben — Alter (→ Tanaka-Berechnung) mit optionalem manuellem Override.
2. **Zonen-Modell:** beide implementieren; **Default Karvonen** wenn Ruhepuls vorhanden, sonst Fallback **%HRmax**.
3. **Sportart-Label:** vordefinierte Liste (z.B. Volleyball, Beach, Krafttraining, Cardio) **+ eigene Labels ergänzbar**. → eigene kleine Room-Tabelle `SportLabel` (vordefinierte werden beim ersten Start geseedet, User kann eigene hinzufügen).
4. **Export:** **nur JSON** für v1. CSV zurückgestellt.
5. **Geräte:** genau **ein** gespeicherter Gurt mit Auto-Reconnect. Keine Multi-Device-Verwaltung in v1.
