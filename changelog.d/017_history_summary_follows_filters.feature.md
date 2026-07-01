## History — Zusammenfassungs-Karte folgt aktiven Filtern (Feature)

**Beschreibung:**
- Die Summary-Card im History-Tab (Ø-BPM, Gesamtdauer, Sessionanzahl) reagiert jetzt auf beide Filter (Trainingseinheiten-Label und Zeitraum).
- Kennzahlen beziehen sich ausschließlich auf die aktuell sichtbaren, gefilterten Sessions.
- Ø-BPM wird sample-gewichtet berechnet: neue DAO-Query `getAvgBpmForSessions` aggregiert über `HrSample`-Einträge aller gefilterten Session-IDs.
- `HistoryViewModel.summaryStats` leitet sich via `flatMapLatest` aus `filteredSessions` ab — kein separater Filter-State nötig.
