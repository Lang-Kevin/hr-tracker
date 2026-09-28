## Zoom und Scrollen in den BPM-Diagrammen

**Problem:**
Das Historien-Diagramm zeigte immer die ganze Session, ohne Möglichkeit, einen Bereich genauer anzusehen. Der Live-Chart zeigte nur ein festes 120-s-Fenster (`takeLast(120)`), ältere Abschnitte der laufenden Session waren nicht erreichbar und die Zeitskala war nicht veränderbar.

**Lösung:**
- Gemeinsames, reines Kotlin-Viewport-Modell (`ChartViewport` für die Historie, `LiveWindow` für Live) mit Unit-Tests; Zoom bleibt am Gestenschwerpunkt verankert, alles auf die Datengrenzen geklemmt.
- `Modifier.chartZoomPan`: Zwei-Finger-Pinch skaliert nur die X-Achse, Ein-Finger-Pannen erst nach horizontalem Touch-Slop (vertikales Scrollen des Detail-Screens bleibt erhalten), Doppeltipp.
- Historie (Detail-Screen): Pinch-Zoom in einen Bereich (bis 20 s sichtbar), Pannen im gezoomten Zustand, Doppeltipp zoomt ×3 bzw. setzt zurück; Zeitbereich-Label und Reset-Button im Chart-Header. Dynamische Skala nutzt den sichtbaren Ausschnitt; Meilensteine und Verbindungslücken folgen dem Zoom.
- Live: Verlauf hält die ganze Session (Sicherheitsgrenze 6 h) und wird bei Session-Start geleert. Wischen scrollt zurück, Pinch skaliert das Zeitfenster (30 s bis ganze Session), „LIVE"-Chip springt zurück an die aktuelle Kante, Doppeltipp setzt auf 120 s zurück. „BPM Ø" bleibt der Durchschnitt der letzten 120 Samples.

**Betroffene Dateien:**
- `ChartViewport.kt`, `ChartGestures.kt` (neu), `ChartViewportTest.kt` (neu)
- `TrainingUi.kt` (`BpmZoneChart` mit Viewport), `DetailScreen.kt`
- `LiveViewModel.kt` (voller Verlauf, Reset), `LiveScreen.kt` (`LiveBpmZoneChart` mit sichtbarem Bereich, Gesten, LIVE-Chip)
