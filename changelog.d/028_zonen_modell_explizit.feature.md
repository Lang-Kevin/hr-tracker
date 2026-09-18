## Zonen-Modell als explizite Einstellung

**Problem:**
Zonengrenzen wirkten zu hoch. Ursache: eine Prozenttabelle (50/60/70/80/90) wurde auf zwei verschiedene Bezugsgrößen angewendet, und das Modell wechselte still von %HRmax auf Karvonen, sobald ein Ruhepuls gespeichert war. Alter 30 / HRmax 187 / Ruhepuls 60: Zone 2 sprang von 112–130 auf 136–148, ohne sichtbaren Hinweis. Zusätzlich: Tanaka streut ±7–11 bpm, jede Grenze trägt diesen Fehler.

**Lösung:**
- `ZoneModel { HR_MAX, KARVONEN }` als explizite Einstellung mit Segmented-Button in Settings, Default `HR_MAX`.
- Lehrbuch-Prozentstufen bleiben für beide Modelle.
- `KARVONEN` ohne Ruhepuls → Fallback `HR_MAX` mit Hinweis im UI.
- Neu: Vorschlag des gemessenen HRmax aus den letzten 90 Tagen, opt-in per "Übernehmen".
- Review-Fund: `resolveZones` hält snapshot-lose v1-Sessions (kein Backfill in `MIGRATION_1_2`) auf der Legacy-Regel (KARVONEN wenn `restingHr` gesetzt, sonst HR_MAX), damit der neue Default `HR_MAX` sie nicht rückwirkend neu bewertet. Dadurch mussten 3 bestehende Tests korrigiert werden — sie hatten gegen den neuen `HR_MAX`-Default assertet und wurden auf `calculateZones(..., ZoneModel.KARVONEN)` umgestellt.

**Nebenbefund behoben:**
`LiveViewModel` hat nach Pause/Resume die Zonen neu berechnet statt den Session-Snapshot zu lesen — dadurch konnte die Zeit-in-Zone gegen andere Grenzen gezählt werden als die, mit denen die Session gestartet wurde. Nutzt jetzt `resolveZones`.

**Betroffene Dateien:**
- `HrZoneCalculator.kt`, `UserSettings.kt`, `SettingsRepository.kt`, `SessionRepository.kt`, `ScanViewModel.kt`, `HrRecordingService.kt`, `LiveViewModel.kt`, `HrSampleDao.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`, `HrZoneCalculatorTest.kt`

**Keine Room-Migration:**
`Session.zoneSnapshotJson` hält historische Sessions auf ihren Originalgrenzen; nur der DataStore bekommt den neuen Key `zone_model`.

**Verworfen:**
Karvonen-Prozentstufen über die Swain-Regression (`%HRmax ≈ 0.64·%HRR + 37`) umzurechnen — das hätte ein drittes, nicht-standardisiertes Modell erfunden und die Regression außerhalb ihres validierten Bereichs extrapoliert.

**Offen / später:**
LTHR-verankerte Zonen (Friel, %LTHR aus 30-min-Test) als einziges Modell, das sich am gemessenen Fitnesslevel statt an einer Altersformel orientiert.
