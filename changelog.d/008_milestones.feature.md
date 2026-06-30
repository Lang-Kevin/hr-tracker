## Trainings-Meilensteine (Feature)

**Beschreibung:**
- Live-Screen: FAB markiert während der Session den aktuellen Zeitpunkt als Meilenstein. Anzeige als vertikale Linien im Live-Chart.
- Persistierung bei Session-Ende mit Default-Label `M{i+1}` in eigener Room-Tabelle `milestones` (id/sessionId/atSeconds/label). Migration v4→v5 (`CREATE TABLE milestones` + Index auf `sessionId`).
- Detail-Screen: Abschnitt "Meilensteine" listet die Marker (Zeit via `formatDuration`), klickbar zum Umbenennen des Labels (`DetailViewModel.updateMilestoneLabel`).
- Persistierung läuft in `NonCancellable` mit try/catch → kein Verlust, wenn die VM bei Session-Ende zerstört wird; reiner Fehler-Pfad wird geloggt.
- Hinweis: Marker werden nur bei regulärem Session-Ende gespeichert, nicht bei Force-Kill (MVP-Scope).
