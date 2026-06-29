## Settings-Filter — Material-3 SegmentedButton statt FilterChip (Feature)

**Beschreibung:**
- Ziel-Zone (Z1–Z5) und HR-Quelle (BLE/Watch) im Settings-Screen nutzen jetzt `SingleChoiceSegmentedButtonRow` statt einzelner `FilterChip`s — korrektes Material-3-Idiom für exklusive Einfachauswahl.
- Segment-Form via `SegmentedButtonDefaults.itemShape(index, count)`.
- Kontrast: Inhaltsfarbe per `zoneColor.luminance() > 0.5f` (helle Zonen → onSurface, dunkle → weiß), erfüllt WCAG-Kontrast.
- HR-Quelle nur bei `FeatureFlags.SMARTWATCH_ENABLED`; sonst statischer Text "HR-Quelle: BLE-Sensor" (ein Segment in einem Single-Choice-Row ist ungültig).
- ViewModel-Aufrufe (`setTargetZone`, `setHrSource`) unverändert.
