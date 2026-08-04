# Wear-OS-Companion aus dem App-Branch ausgelagert (Techdebt)

**Kontext:** Das Wear-OS/Smartwatch-Feature ist noch nicht fertig und blähte Code und Kontext des App-Branches unnötig auf. Der gesamte Stand ist im Branch `feat/wear-os-companion` gesichert (gepusht); dieser Branch enthält keine Wear-OS-Thematik mehr.

**Entfernt:**
- `wear/`-Modul (komplettes Wear-OS-App-Projekt).
- `wearable/` (`WearHrListenerService`, `WearableHrSource`) inkl. Manifest-`<service>`.
- `HrSource`-Enum (BLE | WATCH) → HR-Quelle ist immer BLE. `hrSource` aus `UserSettings`/`SettingsRepository` (DataStore-Key `hr_source`) und `setHrSource` entfernt.
- `notifyWatch()` + `Wearable`-Aufrufe in `HrRecordingService`; WATCH-Zweige in `HrRecordingService`, `LiveViewModel`, `PipViewModel`.
- `FeatureFlags.SMARTWATCH_ENABLED` (+ Datei); Smartwatch-UI in `SettingsScreen`/`ScanScreen` (HR-Quelle-Card, SmartWatch-Hilfe-Dialog, `DeviceType.SMARTWATCH`-Icon).
- Health-Connect-Import „Von Smartwatch importieren": `HealthConnectManager`, `HealthImportStatus`, zugehörige UI/Launcher, Manifest-Permission `health.READ_HEART_RATE` + Rationale-Intent-Filter.
- Gradle: `play-services-wearable`, `health-connect-client` (Dependencies + Version-Catalog-Einträge).

**Doku:** `README.md`, `CLAUDE.md`, `docs/SPEC.md` auf BLE-only aktualisiert; Verweis auf `feat/wear-os-companion` ergänzt.

**Verifikation:** `:app:assembleDebug`, `:app:testDebugUnitTest`, `lintDebug` grün.
