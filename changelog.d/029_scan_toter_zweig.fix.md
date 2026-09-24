## Toter Fortsetzen/Stoppen-Zweig im Scan-Screen entfernt

**Problem:**
`ScanScreen` zeigte bei aktiver Session die Buttons "Fortsetzen" und "Stoppen". Der Zweig war unerreichbar: `LaunchedEffect(activeSessionId)` in `MainActivity` navigiert bei aktiver Session immer sofort zu Live. Gerätetest (Fire-Tablet, 2026-09-23) bestätigt: auch nach `am start --activity-clear-task` landet die App auf Live.

**Lösung:**
- Zweig und die Parameter `onResumeSession`/`onSessionStopped` aus `ScanScreen` entfernt, zugehörige Lambdas in `MainActivity` entfernt.
- Stop/Verwerfen laufen weiterhin ausschließlich über den Live-Screen (`onStopSession`/`onAbortSession`).

**Dateien:**
- `code/app/src/main/kotlin/com/kevin/hrtracker/ui/scan/ScanScreen.kt`
- `code/app/src/main/kotlin/com/kevin/hrtracker/MainActivity.kt`
