## Kalorien zu hoch: Pausen mitgezählt, Grundumsatz nicht abgezogen

**Problem:**
Die Kalorien wurden als Keytel(Ø-BPM) × Wanduhr-Dauer (`endedAt − startedAt`) berechnet. Während Pausen (manuell oder Auto-Pause bei BLE-Verlust) werden keine Samples geschrieben, die Pausenzeit wurde aber trotzdem mit dem Trainings-Durchschnittspuls hochgerechnet. Zusätzlich lieferte Keytel den Brutto-Umsatz inkl. Grundumsatz, und der Ø-BPM war nach Sample-Anzahl statt nach Zeit gewichtet.

**Lösung:**
- `CalorieCalculator.estimateActiveKcal` integriert sample-weise über die Intervalle zwischen zwei Samples; Intervalle > 5 s (Pause, Dropout) zählen nicht.
- Pro Intervall wird der Grundumsatz nach Schofield (Gewicht, Alter, Geschlecht) abgezogen und auf ≥ 0 geclampt → Aktivkalorien.
- `MAX_SAMPLE_GAP_MS` in `HrZoneCalculator` auf `internal`, damit Zonen und Kalorien dieselbe Lückenschwelle nutzen.

**Betroffene Dateien:**
- `CalorieCalculator.kt`, `CalorieCalculatorTest.kt`
- `HrZoneCalculator.kt` (Sichtbarkeit `MAX_SAMPLE_GAP_MS`)
- `DetailViewModel.kt` (`calories` aus Samples statt Stats)
- `docs/SPEC.md`
