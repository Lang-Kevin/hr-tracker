## timeInZone bleibt beim Session-Resume erhalten (Bugfix)

**Problem:** Beim Fortsetzen einer pausierten Session wurde `timeInZone` (Verweildauer pro Zone) auf leer zurückgesetzt — rein In-Memory, kein persistierter Quellwert. Vor der Pause gezählte Zonen-Zeiten gingen verloren (Gesamt-Elapsed blieb korrekt).

**Lösung:** Rekonstruktion statt Persistenz — keine Room-Migration, kein neues Entity-Feld.
- `domain/HrZoneCalculator.kt` → neue reine Funktion `aggregateTimeInZone(samples, zones)`, extrahiert aus der bestehenden Detail-Aggregation.
- `ui/detail/DetailViewModel.kt` → nutzt die extrahierte Funktion (Verhalten unverändert).
- `ui/live/LiveViewModel.kt` → seedet `timeInZone` beim Resume aus persistierten `HrSample`s; Live-Loop zählt ab Resume inkrementell weiter. Kein Double-Counting (Loop steht während Pause still).
- `data/repository/SessionRepository.kt` → `getSamplesForSession(sessionId)` ergänzt; Resume-Pfad läuft über Repository (MVVM-Layering eingehalten, kein direkter DB-Zugriff im ViewModel).

**Tests:** 5 Unit-Tests für `aggregateTimeInZone` (leer, einzeln, konsekutiv, Multi-Zone, Resume-Szenario).
