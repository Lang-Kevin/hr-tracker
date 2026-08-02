## PiP-BPM-Widget beim Minimieren während aktiver Session (Feature)

**Beschreibung:**
Wer während einer laufenden Aufzeichnung die App verlässt, verliert den BPM-Wert bisher komplett aus dem Blick — sichtbar war er nur im Notification-Text, wenn man die Shade aufzieht. Beim Minimieren während einer aktiven Session wechselt die App jetzt automatisch in den nativen Android-Picture-in-Picture-Modus.

- Natives PiP, **kein** `SYSTEM_ALERT_WINDOW`-Overlay, keine Extra-Permission nötig.
- Gegated auf aktive Session: ohne Session minimiert die App normal.
- Inhalt: Herz-Icon + BPM, "Zone N" in Zonenfarbe, Session-Dauer `HH:MM:SS` (bei Pause: "PAUSE" statt Zeit).
- `supportsPictureInPicture` im Manifest; API 31+ nutzt `setAutoEnterEnabled`, API 26–30 fällt auf `onUserLeaveHint()` zurück (bei Gesten-Navigation nicht 100% zuverlässig).
- Neue Dateien `ui/pip/PipUiState.kt`, `ui/pip/PipViewModel.kt`, `ui/pip/PipContent.kt`.
- `HrBleManager` und `WearableHrSource` bekommen zusätzlich `lastHr: StateFlow<ParsedHr?>` für prozessweiten Zugriff auf den letzten Messwert; `hrSamples` bleibt bewusst bei `replay = 0`, um keine Phantom-Samples in `SessionRepository.launchSampleJob` zu erzeugen.
- Bekannte Grenze: Die PiP-Dauer zieht Pausen nicht ab, da `pausedAccumMs` bisher nur im `LiveViewModel` lebt.
