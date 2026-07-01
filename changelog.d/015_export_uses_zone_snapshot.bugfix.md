## Export & Detail-Statistik nutzen persistierten Zonen-Snapshot (Bugfix)

**Problem:** JSON/CSV-Export und die Zeit-in-Zone-Statistik im Detail-Screen berechneten Zonengrenzen stets neu via `calculateZones` und ignorierten den pro Session persistierten `zoneSnapshotJson`. Custom-Zonen wurden dadurch im Export und in der Detail-Auswertung falsch dargestellt.

**Lösung:** Neuer zentraler Einstiegspunkt `HrZoneCalculator.resolveZones(zoneSnapshotJson, maxHr, restingHr)`: Snapshot bevorzugt, Fallback auf Neuberechnung bei fehlendem oder ungültigem JSON.

- `domain/HrZoneCalculator.kt` → `resolveZones` hinzugefügt.
- `ui/detail/DetailViewModel.kt` → `zoneBounds` und `timeInZone` nutzen `resolveZones` statt direktem `calculateZones`.
- `export/SessionExporter.kt` → `buildJson` und `buildCsv` nutzen `resolveZones` statt direktem `calculateZones`.
