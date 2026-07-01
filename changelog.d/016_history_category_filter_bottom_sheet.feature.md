## History — Trainingseinheiten-Filter in Button + Overlay (Feature)

**Beschreibung:**
- Der Kategorie-/Label-Filter im History-Screen wurde von einer inline Chip-Reihe zu einem AssistChip/FilterChip-Button mit FilterList-Icon umgebaut.
- Ein Klick öffnet ein `ModalBottomSheet` mit der bekannten Mehrfachauswahl-Chip-Liste.
- Neuer lokaler State `showCategoryFilter` steuert die Sichtbarkeit des Sheets.
- Der Datumsbereich-Filter sowie die UND-Verknüpfung beider Filter bleiben unverändert.
