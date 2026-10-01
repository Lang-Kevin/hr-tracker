## Session-Ownership: Service als Single Command Owner

**Problem:**
Der Session-Zustand und Entscheidungen (Meilensteine, Pause-Clock, HRV-Countdown) lebten in der UI-Schicht (`LiveViewModel`, `LiveScreen`). Beim Schließen des PiP-Fensters gingen diese Informationen verloren und HRV-Auto-Stop feuerte nicht. Die UI rief `stopService()` direkt auf (nicht als Intent), was die Service-Logik umging. Double-START kreierte zwei offene DB-Zeilen und leckte Collector; `START_STICKY` mit `null` Intent führte zu Zombie-Service ohne Shutdown.

**Lösung:**
- Service ist Single Command Owner: Start/Stop/Discard nur via Intent (`ACTION_START`, `ACTION_STOP`, `ACTION_DISCARD`), nie direkt `stopService()` aus der UI.
- `null` Intent in `onStartCommand()` → `stopSelf()` + `START_NOT_STICKY`, `onDestroy()` Safety-Net-Stop.
- `SessionRepository` ist einziger State-Halter (Mutex, idempotente `start()`, `NonCancellable` in `close()`, `activeSessionId=null` nach Close).
- Meilensteine sofort beim Add in DB eingefügt, auf Discard gelöscht.
- `ActiveClock` (domain/ActiveClock.kt, unit-tested) im Repository für Elapsed-Time (Pause ausgeschlossen).
- HRV-Countdown im Service (`EXTRA_HRV_SECONDS`, 1s-Ticker, `hrvRemainingSec` StateFlow).
- `MainActivity` erkennt service-seitige Session-Beendigung via `lifecycleScope`-Collector und navigiert Live→Detail.
- Debug: `DebugSessionReceiver` (START/STOP Repro), Debug-AndroidManifest.
- Keine DB-Migration, keine Shared-Library-Änderung.

**Betroffene Dateien:**
- `code/app/src/main/kotlin/com/kevin/hrtracker/MainActivity.kt`, `service/HrRecordingService.kt`, `data/repository/SessionRepository.kt`, `domain/ActiveClock.kt`, `ui/live/LiveViewModel.kt`, `ui/live/LiveScreen.kt`, `ui/scan/ScanViewModel.kt`
- `code/app/src/test/.../domain/ActiveClockTest.kt`
- `code/app/src/debug/kotlin/.../DebugSessionReceiver.kt`, `debug/AndroidManifest.xml`
- `docs/SPEC.md`
