## BLE-Verbindungsverlust Auto-Pause (Feature)

**Beschreibung:**
- Eine laufende Aufzeichnung wird automatisch pausiert, sobald der BLE-Verbindungsstatus auf `Disconnected`, `Reconnecting` oder `Error` wechselt.
- Bei Wiederherstellung der Verbindung (Status `Ready`) wird die Aufzeichnung automatisch fortgesetzt.
- Im Live-Screen erscheint während der Auto-Pause ein Banner „Verbindung verloren — Messung pausiert".
- Die Auto-Pause greift **nur im BLE-Modus**. Watch-Sessions sind nicht betroffen.
- Manuelle Benutzer-Pause wird nicht überschrieben: Auto-Resume läuft nur, wenn die Pause durch den Verbindungsverlust ausgelöst wurde (`pausedByConnectionLoss`-Flag). Eine vom Nutzer gestartete Pause bleibt bestehen.
