# Statistik-Kachel: Wert bricht nicht mehr um (Bugfix)

**Problem:** In der „GESAMTZEIT"-Kachel des Live-Screens brach der Zeitwert bei Werten wie „00:00:22" auf zwei Zeilen um, da der Wert-Text kein `maxLines` und keine Größenanpassung hatte.

**Lösung:** `StatItem` (`ui/shared/TrainingUi.kt`) rendert den Wert jetzt mit `maxLines = 1` und `autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = headlineSmall.fontSize, stepSize = 0.5.sp)`. Der Text schrumpft bei Überlänge, wächst aber nie über die Theme-Größe hinaus — sonst hätten Kacheln nebeneinander ungleiche Schriftgrößen bekommen.

- `ui/shared/TrainingUi.kt` → `StatItem` Wert-Text.
- Betrifft Live-Screen und Detail-Screen (beide nutzen `StatItem`).
