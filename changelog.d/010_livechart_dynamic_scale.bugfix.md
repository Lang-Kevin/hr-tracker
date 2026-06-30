## LiveScreen — dynamic-scale im Live-Chart wirksam (Bugfix)

**Beschreibung:**
- Regression aus Commit 437b26a: der `dynamicScale`-Toggle in `LiveScreen` wurde vom privaten `BpmZoneChart`-Composable ignoriert — die Y-Achse war stets statisch (0–220 BPM).
- Fix: `BpmZoneChart` berechnet bei `dynamicScale = true` min/max aus `bpmHistory` (analog zu `TrainingUi`). `bpmToY` nimmt nun `Float` statt `Int` entgegen, um Clamp-Truncation bei fraktionalen Achsengrenzen zu vermeiden.
