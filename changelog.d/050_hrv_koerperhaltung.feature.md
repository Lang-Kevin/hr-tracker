## HRV: Körperhaltung im Start-Dialog

**Problem:**
Die Körperhaltung beeinflusst die HRV, wurde aber nicht erfasst. Die Optionen 30 s und 1 min konnten das Qualitäts-Gate (60 s verwerfen, ≥180 s Analyse) nie bestehen.

**Lösung:**
- `HrvDurationDialog` fragt Liegend/Sitzend/Stehend ab (Default Liegend, `rememberSaveable`).
- Haltung läuft über `EXTRA_HRV_POSTURE` in den `HrRecordingService` und wird in `Session.posture` gespeichert.
- Presets 30 s und 1 min entfernt; nur Full (5 min) bleibt.
- Neue Strings in EN und DE.

**Betroffene Dateien:**
- `domain/Readiness.kt`, `ui/scan/ScanScreen.kt`, `MainActivity.kt`
- `service/HrRecordingService.kt`, `data/repository/SessionRepository.kt`
- `res/values*/strings_scan.xml`, `docs/SPEC.md`
