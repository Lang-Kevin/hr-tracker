## POST_NOTIFICATIONS: nur einmal angefordert

**Problem:**
Die Berechtigung für `POST_NOTIFICATIONS` wurde jedes Mal angefordert, wenn der Benutzer zum Scan-Screen zurückkehrte, nachdem eine Ablehnung erfolgt war. Dies führte zu wiederholtem Prompt-Spam.

**Lösung:**
Die Anfrage wird übersprungen, wenn `shouldShowRationale` wahr ist (nach der ersten Ablehnung) → kein erneuter Dialog. Bei dauerhafter Ablehnung ist es wieder falsch; die Anfrage läuft dann zwar, das System zeigt aber keinen Dialog. Aktivieren über die Systemeinstellungen.

**Betroffene Dateien:**
- `code/app/src/main/kotlin/com/kevin/hrtracker/ui/scan/ScanScreen.kt`
