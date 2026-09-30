## Detail-Diagramm Zeitbasis: Linie und Meilensteine auf Wall-Clock ausgerichtet

**Problem:**
Das Trainings-Diagramm in DetailScreen hatte ein Koordinatensystem-Mismatch: Die X-Achse, Gap-Marker (Verbindungsverlust) und Scrubber-Zeit waren wall-clock-basiert (absolute Millisekunden-Differenzen), aber die BPM-Linie wurde über den **Sample-Index** positioniert und Meilensteine nutzten **aktive Zeit** (abzüglich Pausen). Nach BLE-Dropouts oder manuellen Pausen drifteten Linie, Achse und Meilensteine auseinander.

**Lösung:**
- `SampleIntervals.activeToWallMs()`: Neue reine Funktion, die einen Offset in aktiver Zeit zu einem Wall-Clock-Timestamp auflöst. Nutzt die gleiche Lückenschwelle (MAX_GAP_MS) wie `activeMs()`.
- `DetailViewModel`:
  - `sampleFractions: StateFlow<List<Float>>` = pro Sample die wall-clock-Fraktion (0f..1f) relativ zur Sitzungsdauer.
  - `chartMilestones: StateFlow<List<Milestone>>` = Meilensteine mit `atSeconds` von aktiver Zeit zu wall-clock-Zeit konvertiert via `activeToWallMs()`.
- `BpmZoneChart`:
  - Optionaler Parameter `sampleFractions: List<Float>? = null` (Standard: null → Verhalten wie heute).
  - Wenn non-null und Größe == `bpmHistory.size`: Linie nutzt `mapX(sampleFractions[i])` statt Index; Scrubber findet Nearest-Sample und zeigt `frac*totalSec` als Zeit an.
- `DetailScreen.kt`: Neue Flows sammeln, `chartMilestones` und `sampleFractions` an BpmZoneChart übergeben. Meilenstein-List-Beschriftung bleibt bei Original-`milestones` (aktive Zeit als Label).
- Test-Coverage für `activeToWallMs()` mit Fällen: (a) kontinuierliche Samples reichen für Ziel, (b) Pause/Gap vor dem Ziel (das Ziel liegt im Intervall nach der Pause), (c) leere Liste.

**Betroffene Dateien:**
- `domain/SampleIntervals.kt` (neue Funktion `activeToWallMs()`)
- `domain/SampleIntervalsTest.kt` (neue Tests)
- `ui/detail/DetailViewModel.kt` (neue Flows `sampleFractions`, `chartMilestones`)
- `ui/shared/TrainingUi.kt` (BpmZoneChart mit neuem Parameter, Linienpositioning)
- `ui/detail/DetailScreen.kt` (neue Flows sammeln, an Chart übergeben)
