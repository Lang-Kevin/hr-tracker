## PiP-Navigation: Zustand von LiveViewModel und Zonentimer erhalten (Bugfix)

**Problem:**
Beim Minimieren während einer aktiven Session wechselt die App in Picture-in-Picture. Die Implementierung in `MainActivity.kt` (Zeile 96–102) tauscht dafür den kompletten NavHost-Baum aus (`if (inPip) PipContent() else Surface { HrTrackerNav() }`). Der `NavController` wurde mit `rememberNavController()` *innerhalb* von `HrTrackerNav()` erzeugt und wird bei jedem Wechsel disposed. Das führt zu:
- Neuer NavController beim Zurück-Kommen
- Neue NavBackStackEntry für den LIVE-Screen
- Neues, isolation-loses `LiveViewModel` für die Entry
- `_bpmHistory` und `_timeInZone` starten leer
- **Symptom:** Zonentimer zeigt 0, Live-Chart ist leer

**Lösung:**
- `MainActivity.kt:96-102`: `rememberNavController()` in `setContent` nach oben ziehen, *vor* die PiP-Bedingung
- `HrTrackerNav()` erhält den Controller als Parameter statt ihn lokal zu erzeugen
- NavBackStackEntry und die Entry-Scopes der ViewModels überleben den PiP-Wechsel

**Betroffene Dateien:**
- `MainActivity.kt` → NavController-Erzeugung + Signatur + Übergabe
- Import `androidx.navigation.NavHostController` hinzufügen

**Bekannte Restlücke:**
Der `SaveableStateHolder` des NavHost wird weiterhin disposed, daher fällt `dynamicScaleOverride` (`ui/live/LiveScreen.kt:109`) auf den Einstellungs-Default zurück — kosmetisch, kein Datenverlust.
