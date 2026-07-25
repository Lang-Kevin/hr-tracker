# Live-Chart ignorierte den Diagramm-Standard (Bugfix)

**Problem:** Das Setting „Diagramm: dynamische Skalierung (Standard)" (`UserSettings.chartDynamicScale`) hatte auf dem Live-Screen keinerlei Wirkung. `LiveScreen` initialisierte seinen Skalierungs-State hartkodiert mit `rememberSaveable { mutableStateOf(true) }` und las den DataStore-Wert nie — der Live-Graph nutzte damit immer die Default-Variante, egal was in den Einstellungen stand. Die Kette Settings → `SettingsRepository` → DataStore war intakt; nur der Consumer fehlte. Der Detail-Screen war korrekt.

**Lösung:** Live-Screen folgt jetzt demselben Muster wie der Detail-Screen — Setting als Default, lokaler Toggle nur als Override.

- `ui/live/LiveViewModel.kt` → neuer `chartDynamicScaleDefault: StateFlow<Boolean>` aus `settingsRepository.userSettings`.
- `ui/live/LiveScreen.kt` → `dynamicScaleOverride: Boolean?` (`null` = Setting gilt) statt hartkodiertem `true`; IconToggleButton setzt nur den Override.
- `docs/SPEC.md` → Diagramm-Standard gilt explizit für Live *und* Detail; Feldname korrigiert (`chartDynamicScale`, nicht `chartDynamicScaleDefault`).
