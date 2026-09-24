## Kalorien und erweiterte Report-Statistiken

**Problem:**
Die App hatte keine Unterstützung für Kalorienschätzung basierend auf der Herzfrequenz und es fehlten Eingabefelder für Körperdaten (Gewicht, Geschlecht) zur Berechnung erweiterter Analysen. Die HRR60-Recovery-Card verschwand bei fehlenden Peak-Bedingungen komplett, statt einen aussagekräftigen Fehlerzustand zu zeigen. Beim Beenden einer Session war die Navigation unklar und der Foreground Service wurde nicht immer korrekt beendet.

**Lösung:**
- Keytel-Formel (Keytel et al., 2005) für Kalorienschätzung, sex-dependent, on-read aus Durchschnitts-BPM und Session-Dauer berechnet. Inputs: Gewicht, Geschlecht, Alter, Durchschnitts-BPM, Session-Dauer. Rückgabe: `Int?` (null ohne Gewicht/Geschlecht), clamped auf `>= 0`. Implementiert in `CalorieCalculator.kt` mit Unit-Tests.
- `UserSettings` um `weightKg: Int?` und `sex: Sex?` ergänzt (DataStore-Keys, keine Room-Migration; DB bleibt Version 5).
- Settings-Screen: neue "Körperdaten"-Rubrik mit Gewicht (kg) und Geschlecht (m/w)-Selector.
- Detail-Screen: dritte Statistik-Zeile mit Kalorien, HRmax und Ruhepuls; Hint-Text bei unvollständigen Körperdaten, wenn Kalorien auf einer beendeten Session null sind.
- HRR60-Recovery-Card rendert nun immer mit "—"-Zustand und Begründungstext, statt komplett auszublenden.
- Session-Ende (beide Stop-Buttons im Live- und Scan-Screen): navigiert direkt zum Detail-Screen. Foreground Service wird zuverlässig beendet, Backstack-Navigation mit `popUpTo(Route.SCAN)`.

**Betroffene Dateien:**
- `CalorieCalculator.kt` (Utility, Keytel-Formeln)
- `UserSettings.kt`, `SettingsRepository.kt` (weightKg, sex, DataStore-Keys)
- `SettingsScreen.kt` (Körperdaten-UI)
- `DetailViewModel.kt` (calories StateFlow via combine)
- `DetailScreen.kt` (3 Stat-Zeilen, HRR-Card Always-Render, Hint)
- `MainActivity.kt`, `SessionRepository.kt`, `ScanViewModel.kt`, `ScanScreen.kt` (Stop → Detail-Navigation)
