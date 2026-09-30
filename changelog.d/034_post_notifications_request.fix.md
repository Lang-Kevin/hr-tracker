## POST_NOTIFICATIONS zur Laufzeit auf API 33+ angefordert

**Problem:**
App deklariert `POST_NOTIFICATIONS` in AndroidManifest.xml (Zeile 28), fordert die Berechtigung aber nie zur Laufzeit an. Auf API 33+ bleibt die Foreground-Service-Benachrichtigung deshalb versteckt.

**Lösung:**
- `POST_NOTIFICATIONS` wird zur Laufzeit angefordert, nachdem BLE-Berechtigungen gewährt wurden.
- Nicht blockierend: Recording funktioniert auch ohne Notification-Berechtigung, nur die UI-Benachrichtigung bleibt versteckt.
- Verwendet `rememberPermissionState` (Accompanist), dasselbe Pattern wie BLE-Berechtigungen.

**Dateien:**
- `code/app/src/main/kotlin/com/kevin/hrtracker/ui/scan/ScanScreen.kt`
- `docs/SPEC.md` (Permissions-Abschnitt aktualisiert)
