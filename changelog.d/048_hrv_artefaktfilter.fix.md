## HRV: Median-Artefaktfilter, Rundung, Qualitäts-Gates

**Problem:**
Eine Doppeldetektion des Brustgurts (z. B. 799, 401, 280 ms) blieb im 300–2000-ms-Bereich und blähte den RMSSD auf (55 statt ~41–46). Zusätzlich wurde der Wert abgeschnitten statt gerundet.

**Lösung:**
- `HrvCalculator.analyze` flaggt Schläge außerhalb 300–2000 ms oder mit > 30 % Abweichung vom Median des 11er-Fensters; jede Differenz, die einen geflaggten Schlag berührt, entfällt (keine Interpolation).
- Ergebnisobjekt `HrvResult` (rmssd, validBeats, artefactPct, analysedSeconds), RMSSD wird gerundet.
- Optional: erste 60 s (aktive Zeit) verwerfen; kürzere Aufnahmen werden komplett ausgewertet.
- `HrvQuality`: Gates ≥ 180 s, ≥ 180 gültige Schläge, ≤ 5 % Artefakte, sonst `HrvUnreliableReason`.
- `rmssd()` bleibt als Wrapper. Gecachtes `Session.rmssd` bleibt bis zur Schema-/Metrics-Version-Anhebung (Folge-Milestone) alt.
- Test mit synthetischem Fixture (kein echtes Session-Datum).

**Betroffene Dateien:**
- `code/app/src/main/kotlin/com/kevin/hrtracker/domain/HrvCalculator.kt`, `domain/HrvQuality.kt`
- `code/app/src/test/.../domain/HrvCalculatorTest.kt`
- `docs/SPEC.md`
