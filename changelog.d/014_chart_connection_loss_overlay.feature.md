## Verbindungsverlust-Overlay im Session-Detail-Chart (Feature)

**Beschreibung:**
BLE-Dropout-Lücken (Δt > 5 000 ms zwischen zwei aufeinanderfolgenden Samples) werden im Session-Detail-Chart visuell hervorgehoben: Eine rote gestrichelte vertikale Markierung erscheint auf Höhe des Sitzungsdurchschnitts (avg BPM) und zeigt so auf einen Blick, wo Verbindungsunterbrüche stattfanden.

- Die Overlay-Linien sind **rein visuell** — Zonen- und Zeitaggregation zählen während der Lücke weiterhin nicht hoch (bereits seit M1 via `HrZoneCalculator.aggregateTimeInZone`).
- Lücken-Erkennung über neue Funktion `HrZoneCalculator.detectGaps`, die eine geordnete Liste der Lücken-Zeitpunkte zurückgibt.
- `ui/detail/DetailViewModel.kt` → reicht erkannte Lücken an den Chart weiter.
- `ui/detail/BpmZoneChart.kt` → zeichnet pro Lücke eine rote gestrichelte Linie auf avg-BPM-Höhe.
