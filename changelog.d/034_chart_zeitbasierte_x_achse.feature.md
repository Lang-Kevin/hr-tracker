## Zeitbasierte X-Achse in den BPM-Diagrammen

**Problem:**
Beide Diagramme positionierten Samples nach Index und gingen von ca. 1 Sample/s aus. In der Historie liefen Zeitbeschriftung, Ticks und Scrubber-Zeit bei Verbindungslücken von der Linie weg (Lücken, Meilensteine und Ticks waren zeitbasiert, die Linie indexbasiert). Live verrutschten Zeitbeschriftung und Meilensteine nach Pausen oder bei unregelmäßiger BLE-Rate.

**Lösung:**
- Historie: `DetailViewModel.sampleFractions` liefert je Sample den Zeitanteil an der Sessiondauer (gleiche Basis wie `gapFractions`). `BpmZoneChart` nutzt ihn für Linie, sichtbaren Ausschnitt (Binärsuche), Downsampling, letzten Punkt und Scrubber; ohne passende Daten bleibt das bisherige Index-Verhalten.
- Live: `LiveViewModel` hält BPM und aktive Sekunden seit Start (ohne Pausen) atomar in einem Zustand (`LiveSeries`). Während einer Pause werden keine Samples an den Chart-Verlauf angehängt (der Chart zeigt aktive Zeit, wie Dauer und Zeit in Zone). `LiveWindow` arbeitet auf Sekunden; Slice, Linie, Ticks, Meilensteine (direkt an ihrer Sekunde) und Scrubber nutzen dieselbe Zeitskala. Die Dezimierung bleibt an absolute Sample-Indizes gebunden.
- Neue reine Kotlin-Helfer mit Unit-Tests (`visibleRangeByFractions`, `nearestIndexByFraction`, Sekunden-Varianten, Chunk-Mittelung).

**Betroffene Dateien:**
- `ChartTime.kt` (neu), `ChartTimeTest.kt` (neu), `ChartViewport.kt` (KDoc `LiveWindow`)
- `TrainingUi.kt` (`BpmZoneChart`), `DetailViewModel.kt`, `DetailScreen.kt`
- `LiveViewModel.kt` (`LiveSeries`, Sekunden je Sample, keine Samples in Pause), `LiveScreen.kt` (`LiveBpmZoneChart` zeitbasiert)
