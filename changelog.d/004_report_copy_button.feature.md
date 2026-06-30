## Report-Dialog — JSON-Kopieren via Clipboard (Feature)

**Beschreibung:**
- Detail-Screen Report-Dialog erhält einen "Kopieren"-Button, der den Report-JSON direkt in die Zwischenablage kopiert.
- Nutzt `LocalClipboardManager` (Stdlib), keine neue Abhängigkeit.
- Nach erfolgreichem Kopieren wird ein Toast-Feedback "Report kopiert" angezeigt.
- Ermöglicht einfaches Teilen und Verarbeitung von Session-Reports außerhalb der App.
