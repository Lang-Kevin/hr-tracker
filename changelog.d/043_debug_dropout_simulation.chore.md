## Pseudo-Sensor durchläuft echte Reconnect-Loop

**Problem:**
Pseudo-Sensor (Fake-Gerät) ging beim Verbinden direkt in Ready-State, ohne echte Reconnect-Loop durchzulaufen. Dropouts waren nicht reproduzierbar.

**Lösung:**
- Pseudo-Sensor nutzt jetzt `fakeAttempt()` in der echten Reconnect-Loop.
- Neue `simulateFakeDropout(ms)` versetzt das Fake-Gerät in Dropout-Zustand.
- Debug-Receiver für `adb shell am broadcast -n com.kevin.hrtracker/.debug.DebugDropoutReceiver --el ms 60000`.
- Jeder Versuch läuft in den 10-s-Timeout, danach Backoff (3/5/10/30 s); Wiederverbindung nach Ablauf (60 s → ca. 78 s).
- `--el` (long) ist Pflicht.
