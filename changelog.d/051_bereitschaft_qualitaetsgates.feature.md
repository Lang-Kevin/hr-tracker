## Bereitschaft: Qualitäts-Gates und Qualitätsanzeige

**Problem:**
Die Bereitschaft nutzte auch unzuverlässige Messungen und mischte Körperhaltungen. Schon 3 bzw. 7 Messungen reichten für eine Einordnung.

**Lösung:**
- Nur verlässliche Messungen gleicher Haltung wie die letzte Messung zählen.
- Rolling ≥ 5 Messungen in 7 Tagen, Baseline ≥ 14 Messungen in 60 Tagen; sonst Grund „Basis im Aufbau (x/14)“ / „zu wenige aktuelle Messungen (x/5)“. Auto-Ruhepuls bleibt bei ≥ 3 (`MIN_RESTING_HR`).
- Detail-Screen zeigt Qualitätszeile oder „Nicht verlässlich: <Grund>“ (RMSSD ausgegraut).
- Form-Karte: Haltungs-Hinweis und Disclaimer (kein medizinischer Hinweis). ±0,5-SD-Band unverändert.
- Neue Strings in EN und DE.

**Betroffene Dateien:**
- `domain/Readiness.kt`, `data/entity/Session.kt`, `ui/detail/DetailViewModel.kt`, `ui/detail/DetailScreen.kt`, `ui/history/TrendCards.kt`
- `res/values*/strings_detail.xml`, `res/values*/strings_history.xml`, `docs/SPEC.md`
