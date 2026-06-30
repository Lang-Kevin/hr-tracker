## BpmZoneChart — Dynamic vs. Static Scaling (Feature)

**Beschreibung:**
- BpmZoneChart auf Live- und Detail-Screen erhält zwei Anzeigemodi, umschaltbar via IconToggleButton oben rechts auf dem Chart.
  - **Dynamic (Standard):** Y-Achse auto-scaled auf gemessene Min/Max-BPM ±10% Padding (shared-lib-Muster); nur Zonen mit `timeInZone>0` werden gezeichnet; BPM-Serien >300 Punkte downgesampled via shared-android-lib `aggregateByChunks`.
  - **Static ("raus-gezoomt"):** Legacy-Verhalten — Y-Range fest `[min-8, max+8]`, alle Zonen sichtbar.
- Altes manuelles Per-Zone-Visibility-Toggle (`visibleZones`/`toggleZoneVisibility`) wurde entfernt und durch diesen Mode-Toggle ersetzt.
- Touch-Targets 48dp.
- Charts in BpmZoneChart (beide Screens) profitieren von Downsampling für Perf — nur im Dynamic-Modus aktiv.
