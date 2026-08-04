## Widget-Varianten für PiP und Notification (Feature)

**Beschreibung:**
Das PiP-Fenster und die Benachrichtigung während der Aufzeichnung zeigten bisher einen festen Satz an Werten. Wer nur die Zahl sehen will oder primär auf die Zone schaut, kann die Anzeige jetzt in den Einstellungen unter "Widget-Anzeige" umstellen.

- Vier Presets: `MINIMAL` (nur BPM, groß), `STANDARD` (BPM + Zone + Dauer, bisheriges Verhalten), `ZONE` (Zone dominant), `TIMER` (Dauer dominant).
- Eine gemeinsame Einstellung für beide Anzeigeflächen — die Präferenz "nur die Zahl" gilt erfahrungsgemäß für PiP und Notification gleichzeitig.
- Default ist `STANDARD`: das PiP-Fenster bleibt für bestehende Installationen byteidentisch, die Notification zeigt zusätzlich das Zonen-Segment (vorher nicht enthalten).
- Persistenz in DataStore (`widget_variant`), unbekannter Wert fällt auf `STANDARD` zurück. Keine Room-Migration.
- Neue Datei `domain/WidgetVariant.kt` mit dem Enum und der reinen Funktion `widgetNotificationText`, die Service und Test gemeinsam nutzen.
- Pause: `PAUSE` ersetzt die Zeile, die sonst die Dauer zeigt (`STANDARD`, `TIMER`) bzw. kommt als kleine Zeile dazu (`MINIMAL`, `ZONE`).
- Bewusst nicht enthalten: Homescreen-AppWidget, getrennte Einstellungen pro Fläche, neue Metriken wie Ø-BPM oder %HRmax.
