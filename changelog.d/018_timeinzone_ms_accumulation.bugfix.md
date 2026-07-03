# Zeit-in-Zone: Millisekunden-genaue Akkumulation (Bugfix)

**Problem:** `HrZoneCalculator.aggregateTimeInZone` teilte pro Intervall ganzzahlig `gapMs / 1000` und summierte die dabei entstehenden Sekundenwerte. Bei kurzen, unregelmäßigen Intervallen (BLE-Jitter) ging so pro Intervall der Rest unter einer Sekunde verloren — bei langen Sessions kumulierte sich das zu einem systematischen Zeit-in-Zone-Verlust von bis zu ~50 %. Betraf auch `generateReport` im Export.

**Lösung:** Millisekunden werden pro Zone akkumuliert; erst am Ende einmalig durch 1000 geteilt. Kein Sub-Sekunden-Verlust mehr pro Intervall.

- `domain/HrZoneCalculator.kt` → `aggregateTimeInZone` akkumuliert ms statt Sekunden pro Intervall.
- Export (`generateReport`) profitiert automatisch, da dieselbe Funktion genutzt wird.
