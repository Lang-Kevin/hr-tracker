# No-Contact-Samples verwerfen statt auf 30 klemmen (Bugfix)

**Problem:** Kein-Haut-Kontakt-Readings (bpm = 0) wurden durch `bpm.coerceIn(30, 220)` auf 30 BPM geklemmt statt verworfen. Diese Phantom-30er-Werte erschienen während Aussetzern im Live-Display und wurden in Room persistiert, was den Ø-BPM in Detail-Screen, History und Export nach unten zog.

**Lösung:** Samples außerhalb 30–220 werden jetzt vollständig verworfen.

- `HeartRateParser.kt` → `bpm !in 30..220` gibt `null` zurück (Sample verworfen).
- `WearHrListenerService.kt` → `bpm !in 30..220` bricht `onMessageReceived` ab (`return`).
- `HrSensorManager.kt` (Wear) → `onAccuracyChanged` trackt Genauigkeit; `onSensorChanged` sendet nur bei akzeptabler Genauigkeit und bpm in 30..220.
- Neue Unit-Tests `HeartRateParserTest` decken alle Fälle ab (valid uint8/uint16, bpm=0, bpm=250, RR-Umrechnung, leeres Array).
- `docs/SPEC.md` → gültiger Bereich 30–220 dokumentiert, Verwerfungs-Semantik festgehalten.
