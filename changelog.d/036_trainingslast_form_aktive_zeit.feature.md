## Aktive Zeit, Trainingslast (ACWR, Session-RPE) und Form-Tab (HRV, Ruhepuls, HRR60)

**Problem:**
Pausen zählten weiterhin in Gesamtdauer, Wochenminuten und Detail-Dauer; RMSSD wurde über Pausen hinweg differenziert; Ø-BPM war nach Sample-Anzahl statt Zeit gewichtet. Es gab keinen Blick auf die Belastung über Wochen, keine subjektive Belastung und keine Auswertung der morgendlichen HRV-Messungen über die Zeit.

**Lösung:**
- `SampleIntervals` als gemeinsame Lücken-Regel (> 5 s zählt nicht): aktive Zeit, zeitgewichteter Ø-BPM. `HrvCalculator.rmssd` setzt an Lücken neu an.
- DB v6: Session-Spalten `activeMs`, `avgBpm`, `trimp`, `hrr60`, `rmssd`, `metricsVersion`, `rpe`. `SessionMetrics` berechnet sie nach Session-Ende und einmalig beim App-Start für alle bestehenden Sessions.
- Detail-Screen: Stat „AKTIV“, Karte „Belastung (RPE 0–10)“ mit sRPE-Last; RPE-Abfrage direkt nach dem Speichern einer Session.
- History → Statistik: Karte „Trainingslast“ (akut 7 T, chronisch Ø 4 W, ACWR mit Ampel, 42-Tage-Chart), umschaltbar TRIMP/sRPE. Summary und Wochen mit aktiver Zeit, ohne HRV-Messungen.
- History → neuer Tab „Form“: HRV-Bereitschaft (ln RMSSD, 7-Tage-Ø vs. 60-Tage-Normalbereich), Ruhepuls aus HRV-Messungen (optional automatisch übernehmen), HRR60-Trend 4 vs. 4 Wochen.
- JSON-Export: `active_s`, `rpe`; `avg_bpm` zeitgewichtet.

**Betroffene Dateien:**
- Neu: `SampleIntervals.kt`, `HrvCalculator.kt`, `SessionMetrics.kt`, `TrainingLoad.kt`, `Readiness.kt`, `TrendChart.kt`, `TrendCards.kt` + Tests
- `Session.kt`, `SessionDao.kt`, `HrDatabase.kt`, `DatabaseModule.kt` (Migration 5→6)
- `SessionRepository.kt`, `SettingsRepository.kt`, `UserSettings.kt`
- `DetailViewModel.kt`, `DetailScreen.kt`, `HistoryViewModel.kt`, `HistoryScreen.kt`, `SettingsScreen.kt`, `MainActivity.kt`
- `HrZoneCalculator.kt`, `CalorieCalculator.kt`, `TrimpCalculator.kt`, `SessionExporter.kt`
- `docs/SPEC.md`
