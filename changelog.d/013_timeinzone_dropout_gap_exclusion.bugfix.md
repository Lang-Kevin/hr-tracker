## BLE-Dropout-Lücken aus Zeit-in-Zone-Statistik ausschließen (Bugfix)

**Problem:** Verbindungsunterbrüche (BLE-Dropout) wurden bislang als zusammenhängende Aufzeichnungszeit gezählt. Eine Lücke zwischen zwei Samples (Δt > 5 000 ms) ließ den letzten bekannten BPM-Wert in die Zonenverweildauer einfließen — obwohl in dieser Zeit kein valides Signal vorlag.

**Lösung:** `HrZoneCalculator.aggregateTimeInZone` überspringt jedes Intervall, dessen Zeitdelta zum Vorgänger-Sample über 5 000 ms liegt. Damit zählen Dropout-Lücken weder in der Detail-Auswertung noch im Export.

- `domain/HrZoneCalculator.kt` → Lücken-Guard (Δt > 5 000 ms) in `aggregateTimeInZone`.
- `ui/detail/DetailViewModel.kt` → nutzt unverändert `aggregateTimeInZone` (profitiert automatisch).
- `export/` → Export-Aggregation auf `HrZoneCalculator.aggregateTimeInZone` entdoppelt; eigene Loop entfernt.
