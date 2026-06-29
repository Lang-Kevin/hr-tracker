## Heart Rate Recovery (HRR) — Automatische Erholungs-Kennzahl (Feature)

**Beschreibung:**
- Nach jeder Session wird automatisch die Herzfrequenz-Erholung (HRR60) berechnet und am Detail-Screen als Card angezeigt.
- **HRR60-Definition:** Peak-HF minus gemessene HF im Fenster [60s–65s] nach dem Peak; benötigt Peak ≥ 70 % HRmax mit ≥60 Sekunden Nachlauf; toleriert kurze BLE-Lücken.
- **Post-Workout Zielzone:** 60 % HRmax; zeigt an, ob und wann dieser Erholungsbereich erreicht wurde.
- **Rating-Kategorien (sportwissenschaftlicher Standard):**
  - < 12: Niedrig
  - 12–17: Normal
  - 18–29: Gut
  - ≥ 30: Sehr gut
- Berechnung erfolgt on-read aus vorhandenen `HrSample`-Daten (timestampMs, bpm) — **keine Room-Migration erforderlich.**
- Card wird nur angezeigt, wenn HRR60 berechenbar ist (Peak-Bedingungen erfüllt).
