## HR-Erholung — Peak-Auswahl per max-Drop statt globalem BPM-Max (Bugfix)

**Beschreibung:**
- `HrRecovery.computeHrRecovery` ankerte den Peak bisher am globalen BPM-Maximum unter allen Samples ≤ `lastT − 60s`. Bei Dauertraining mit Mid-Workout-Spitze lieferte das hrAt60 im falschen Fenster → HRR ≈ 0 → fälschlich "NIEDRIG".
- Fix: über ALLE Kandidaten-Anker iterieren (`bpm ≥ 0.70·maxHr` mit Folge-Sample in `[c+60s, c+65s]`) und den Peak mit dem größten positiven Drop (`peakBpm − hrAt60`) wählen. Tie → frühester (höchster) Peak. Kein Kandidat / Drop ≤ 0 → `null`.
- Signatur, `HrrResult` und `HrrRating`-Schwellen (NIEDRIG<12, NORMAL 12–17, GUT 18–29, SEHR_GUT≥30) unverändert. On-read, keine Migration.
- Tests: neuer `globalMaxMidTraining_selectsRealRecoveryAtEnd`-Fall + Kurven der Rating-/Recovery-Tests so geformt, dass der Ziel-Peak eindeutiger max-Drop-Kandidat ist.
