## Chart-Default, Auto-Zonen, Zonen-Hilfe-Dialog, HR-Quelle Debug-Only (Feature)

**Beschreibung:**
- Settings-Screen: neuer Schalter "Diagramm-Standard (dynamische Skalierung)" persistiert die bevorzugte Standardansicht des BPM-Charts in DataStore. Der Detail-Screen nutzt diesen Wert als Initialwert und erlaubt weiterhin per-Session-Override über den IconToggleButton.
- Custom-Zonen-Editor: leere Felder zeigen den berechneten Default-Wert als Placeholder. Beim Ändern einer Grenze werden Nachbargrenzen automatisch angepasst, sodass die Reihenfolge stets streng aufsteigend bleibt (Bereich 30–220 BPM).
- "Was bedeuten die Zonen?"-Hilfe wurde aus dem separaten Hinweis-Text in ein Fragezeichen-Icon verlagert, das einen erklärenden Dialog in der Zonen-Vorschau öffnet.
- HR-Quelle-Card im Settings-Screen ist nur noch sichtbar, wenn die App im Debug-Modus (`BuildConfig.DEBUG`) läuft.
