## Zeitachse und Scrubber in den BPM-Diagrammen

**Problem:**
Beide BPM-Diagramme (Live und Detail) hatten keine Zeitachse, man konnte also nicht ablesen, an welcher Stelle der Session man gerade schaut, und es gab keine Möglichkeit, den genauen BPM-Wert an einem bestimmten Punkt zu ermitteln.

**Lösung:**
- Zeitachse: Unter dem Plot liegt ein Band mit „m:ss"-Beschriftungen (ab einer Stunde „h:mm:ss") und schwachen vertikalen Gitterlinien. Die Tick-Abstände wählen „schöne" Schritte (5 s bis 2 h, höchstens 5 Ticks) und folgen Zoom und Scrollen, in Live wie in der Historie. Die Tick-Berechnung ist reines Kotlin mit Unit-Tests.
- Scrubber: Finger lange auf das Diagramm halten und ziehen zeigt eine vertikale Linie, einen Punkt am nächsten Sample und einen Tooltip „<bpm> bpm · m:ss" mit dem rohen Wert (nicht dezimiert). Beim Auslösen gibt es haptisches Feedback, beim Loslassen verschwindet die Anzeige.
- Gesten: Während des Scrubbens wird nicht gepannt; Pannen (erst nach horizontalem Slop), Pinch-Zoom und Doppeltipp bleiben unverändert. Die Indexauswahl (`nearestIndex`) ist reines Kotlin mit Unit-Tests.
- Tutorial-Text des Live-Charts um den Hinweis „Lange drücken zeigt den genauen Wert." ergänzt.

**Betroffene Dateien:**
- `ChartTicks.kt` (neu), `ChartTicksTest.kt` (neu)
- `ChartViewport.kt` (`nearestIndex`), `ChartViewportTest.kt`
- `ChartGestures.kt` (`onScrub`)
- `TrainingUi.kt` (Zeitachse, `drawScrubber`, `BpmZoneChart` mit `scrubX`), `DetailScreen.kt`
- `LiveScreen.kt` (Zeitachse, `LiveBpmZoneChart` mit `scrubX`, Tutorial-Text)
