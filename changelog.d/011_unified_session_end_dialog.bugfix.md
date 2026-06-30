## Einheitlicher Session-Beenden-Dialog (Bugfix/UX)

**Beschreibung:**
- Eine aktive Trainingssession kann auf zwei Wegen beendet werden: Android-Zurück-Geste und Stop-Button in der App.
- Bisher zeigten beide Wege unterschiedliche Dialoge (Back → `LeaveSessionDialog` mit Speichern/Verwerfen, Stop-Button → lokaler `ConfirmDialog` mit nur Speichern/Weiter).
- Beide Wege zeigen jetzt dasselbe Element: den gemeinsamen `LeaveSessionDialog` (Speichern / Verwerfen / Weiter messen).
- Der lokale `ConfirmDialog` und der `showStopDialog`-State in `LiveScreen` wurden entfernt.
