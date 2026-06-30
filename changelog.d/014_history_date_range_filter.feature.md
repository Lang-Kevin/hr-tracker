## Verlauf — Filter nach Tag/Zeitraum (Feature)

**Beschreibung:**
- History-Screen erhält einen Datumsbereich-Filter zusätzlich zum bestehenden Label-Filter (Einzeltag oder Zeitraum via DateRangePicker).
- Beide Filter werden UND-verknüpft: Label-Auswahl und Datumsbereich schränken die Sessionliste gemeinsam ein.
- DateRangePicker liefert UTC-Mitternacht-Millis; diese werden in `HistoryViewModel.setDateRange` auf die geräte-lokale Zeitzone re-ankert, damit das Filterfenster zur lokalen Tages-Bucketing-Logik passt.
