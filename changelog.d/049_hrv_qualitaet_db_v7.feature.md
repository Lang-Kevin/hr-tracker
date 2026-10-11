## HRV-Qualität: DB v7 und SessionMetrics VERSION 2

**Problem:**
Die HRV-Qualitätskennzahlen (gültige Schläge, Artefaktanteil) und die Körperhaltung hatten keinen Speicherplatz; gecachtes und Detail-RMSSD wichen für HRV-Messungen ab.

**Lösung:**
- Room-Migration 6→7: nullable Spalten `rmssdArtefactPct REAL`, `rmssdValidBeats INTEGER`, `posture TEXT` auf `Session` (Migration 1→2 unverändert).
- Bestehende HRV-Messungen erhalten bei der Migration die Haltung `SITTING`.
- `SessionMetrics.VERSION` 1→2: HRV-Sessions verwerfen die ersten 60 s, speichern `rmssdValidBeats` und `rmssdArtefactPct`; Backfill beim App-Start über `getWithStaleMetrics`.
- Detail-Screen nutzt dieselbe Berechnung wie der Cache (Discard je nach HRV-Label).

**Betroffene Dateien:**
- `data/entity/Session.kt`, `data/db/HrDatabase.kt`, `data/db/SessionDao.kt`, `di/DatabaseModule.kt`
- `domain/SessionMetrics.kt`, `data/repository/SessionRepository.kt`, `ui/detail/DetailViewModel.kt`
- `docs/SPEC.md`
