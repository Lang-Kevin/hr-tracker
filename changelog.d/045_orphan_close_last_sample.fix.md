## Verwaiste Sessions werden mit Zeitstempel des letzten Samples geschlossen

**Problem:**
Sessions, die durch App-Kill oder Absturz offen geblieben sind, wurden beim nächsten Start mit dem Zeitstempel des App-Neustarts geschlossen. Dies führte zu aufgeblasenen Sessiondauern (Bias bis zu mehrere Stunden später).

**Lösung:**
Die `closeOrphanedSessions`-Query verwendet jetzt `COALESCE((SELECT MAX(timestampMs) FROM HrSample WHERE HrSample.sessionId = Session.id), startedAt)` als `endedAt`. Fehlen Samples, wird `startedAt` verwendet (Fallback für Sessionen ohne HR-Daten). Der Cutoff wird jetzt vor dem Start der Init-Coroutine genommen, damit eine direkt nach App-Start begonnene Session nicht fälschlich geschlossen wird. Bereits früher geschlossene Sessions mit aufgeblähter Dauer werden nicht rückwirkend korrigiert.

**Betroffene Dateien:**
- `code/app/src/main/kotlin/com/kevin/hrtracker/data/db/SessionDao.kt`
- `code/app/src/main/kotlin/com/kevin/hrtracker/data/repository/SessionRepository.kt`
- `docs/SPEC.md`
