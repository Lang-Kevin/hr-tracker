## Night Drive Redesign, Akku-Anzeige, strikter Token-Check

**Problem:**
Die App nutzte ein eigenes Lila-Design und eigene Chart-Kopien; der Akkustand des Pulsgurts war nicht sichtbar, Design-Regeln wurden in der CI nicht erzwungen.

**Lösung:**
- Alle Screens auf das Night-Drive-Design-System der Shared Lib umgestellt (Scan, Live, Detail, History, Statistik, Form, Einstellungen, PiP); hr-Duplikate gelöscht.
- Akkustand per BLE Battery Service (0x180F/0x2A19) einmal pro Verbindung gelesen und in der Scan-Karte angezeigt.
- CI: strikter Design-Token-Check (ohne `--warn`).
- Room v8: additive, nullable Spalte `zoneSeconds` (Mini-Zonenleiste in History); `SessionMetrics.VERSION` 3 berechnet Bestandssessions beim Start nach.
- Neue Strings in EN und DE.

**Betroffene Dateien:**
- `ui/**`, `ble/HrBleManager.kt`, `data/db/*`, `domain/SessionMetrics.kt`, `res/values*/strings_*.xml`, `.github/workflows/*`, `docs/SPEC.md`
