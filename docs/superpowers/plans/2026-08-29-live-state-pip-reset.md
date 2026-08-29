# Fix: Live-State geht beim Minimieren (PiP) verloren

## Context

**Symptom:** Während einer laufenden Session die App minimieren → PiP-Fenster erscheint → App
wieder öffnen. Zonentimer stehen auf 0, der Live-Graph ist leer. Die Gesamt-Sessiondauer läuft
korrekt weiter (vom User bestätigt).

**Root Cause (statisch belegt, Symptom-Signatur passt exakt):**

`MainActivity.kt:96-102` tauscht beim PiP-Wechsel den kompletten Nav-Baum aus:

```kotlin
if (inPip) PipContent() else Surface { HrTrackerNav() }
```

`rememberNavController()` liegt *innerhalb* `HrTrackerNav()` (`MainActivity.kt:119`). Der
else-Zweig verlässt beim PiP-Eintritt die Composition → NavController wird disposed. Beim
Zurückkehren wird ein **neuer** NavController gebaut → neue `NavBackStackEntry` → neues
`LiveViewModel` (`LiveScreen.kt:73`, `hiltViewModel()` ist entry-scoped).

Der gesamte Live-State lebt rein in-memory in dieser ViewModel-Instanz:

| State | Ort | Verhalten bei Neu-Instanziierung |
| --- | --- | --- |
| `_bpmHistory` (Chart) | `LiveViewModel.kt:50` | `emptyList()` |
| `_timeInZone` (Zonentimer) | `LiveViewModel.kt:60` | `emptyMap()` |
| `_milestones` | `LiveViewModel.kt:102` | leer |
| `pausedAccumMs` | `LiveViewModel.kt:110` | `0` → Elapsed zählt Pausen nicht mehr ab |
| `sessionStartMs` | `LiveViewModel.kt:155-157` | **wird aus der DB neu geseedet** |

`sessionStartMs` ist der einzige Wert mit DB-Reseed — genau deshalb läuft die Gesamtdauer weiter,
während Zonen und Graph bei 0 stehen. Das ist der Beleg, nicht nur eine Vermutung.

Regressionsquelle: `a6d3367 feat(pip): MainActivity geht bei aktiver Session in PiP`.

**Ziel:** Der Live-State überlebt den PiP-Wechsel vollständig — Chart, Zonentimer, Milestones,
Pausen-Verrechnung.

## Fix (Stufe 1 — empfohlen)

NavController über den PiP-Zweig hinaus hochziehen. Damit bleiben BackStack-Entries und die daran
gescopten ViewModels am Leben; nichts muss rekonstruiert werden.

**Datei:** `code/app/src/main/kotlin/com/kevin/hrtracker/MainActivity.kt`

1. In `setContent` (`:93-104`) vor dem `if (inPip)` einfügen:

```kotlin
// ponytail: NavController muss den PiP-Wechsel ueberleben — sonst neuer
// BackStack -> neues LiveViewModel -> Chart und Zonentimer auf 0 (Bug).
val navController = rememberNavController()
```

2. `HrTrackerNav()` (`:100`) → `HrTrackerNav(navController)`.
3. Signatur `:118` → `private fun HrTrackerNav(navController: NavHostController)`, die lokale
   `rememberNavController()`-Zeile `:119` entfernen.
4. Import `androidx.navigation.NavHostController` ergänzen.

Sonst nichts. Kein Refactor angrenzenden Codes, keine Änderung an `LiveViewModel`.

**Bekannte, akzeptierte Restlücke:** Der `SaveableStateHolder` des `NavHost` wird weiterhin
disposed, daher fällt `dynamicScaleOverride` (`LiveScreen.kt:109`) auf `null` zurück und der Chart
nutzt wieder den Einstellungs-Default. Kosmetisch, kein Datenverlust.

## Fallback (Stufe 2 — nur falls Stufe 1 nicht reicht)

Falls die Geräteverifikation zeigt, dass der State trotzdem verloren geht (Process Death bei
Speicherdruck, oder Entry-ViewModelStores werden doch geräumt): Live-State beim VM-Start aus der
persistierten Quelle rekonstruieren — exakt das Muster, das der Resume-Pfad schon nutzt
(`LiveViewModel.kt:145-149`, Bugfix 002).

Im `init`-Block von `LiveViewModel`, im bestehenden `activeSessionId.collect` (`:153-167`):
bei `id != null && sessionStartMs == 0L` zusätzlich

```kotlin
val samples = sessionRepository.getSamplesForSession(id)
val zones = HrZoneCalculator.calculateZones(session.maxHrUsed, session.restingHr)
_timeInZone.value = HrZoneCalculator.aggregateTimeInZone(samples, zones)
_bpmHistory.value = samples.takeLast(120).map { it.bpm }
```

Wiederverwendet vorhandene Funktionen — `SessionRepository.getSamplesForSession`,
`HrZoneCalculator.aggregateTimeInZone`, `HrZoneCalculator.calculateZones`. Kein neuer Code in
`domain/`. Diese Stufe ist unit-testbar (Fake-Repository mit Samples → frische VM → `timeInZone`
und `bpmHistory` befüllt); Stufe 1 ist es nicht.

Stufe 2 nur bauen, wenn Stufe 1 den Bug nachweislich nicht schließt. Nicht auf Verdacht mitliefern.

## Verifikation

Der Bug ist ein Lifecycle-/Composition-Bug — kein Unit-Test kann ihn fangen. Beleg ist die
Gerätereproduktion, in dieser Reihenfolge:

1. **Vorher-Repro auf dem aktuellen Stand** (damit der Fix belegbar etwas ändert):
   `./gradlew :app:installDebug` → Session starten → ~60 s laufen lassen, bis Graph und mindestens
   ein Zonentimer gefüllt sind → Home → PiP erscheint → App aus dem Recents/Launcher wieder öffnen
   → **erwartet: Zonentimer 0, Graph leer.**
2. Fix Stufe 1 anwenden.
3. `./gradlew assembleDebug` → `./gradlew lint` → `./gradlew test` (Ergebnis im Bericht zitieren,
   nicht behaupten).
4. **Nachher-Repro, identische Schritte:** Zonentimer und Graph müssen den Stand von vor dem
   Minimieren zeigen und weiterlaufen. Zusätzlich prüfen: Gesamtdauer weiterhin korrekt,
   gesetzte Milestones noch vorhanden.
5. Session pausieren → minimieren → wieder öffnen → fortsetzen: Elapsed darf die Pause weiterhin
   abziehen (`pausedAccumMs` überlebt jetzt).

## Umsetzung

- Änderung an `MainActivity.kt` an `@implementer` delegieren, harte Scope-Grenze: nur diese Datei,
  nur die vier Punkte oben, kein Refactor der Nav-Struktur.
- Danach `@reviewer` auf den Diff, vor dem Commit.
- Vor dem Commit: `changelog.d/` (neuer Bugfix-Eintrag) und `docs/SPEC.md`-Abschnitt
  Picture-in-Picture aktualisieren.
- Diesen Plan nach `docs/superpowers/plans/2026-08-29-live-state-pip-reset.md` ins Repo kopieren
  (Repo-Kopie ist die verbindliche Ablage).
- **Achtung Working Tree:** `git status` zeigt alle `changelog.d/*`-Dateien als gelöscht sowie eine
  ungestagte Änderung in `ui/settings/SettingsScreen.kt`. Vor dem Commit klären — nicht mit diesem
  Bugfix vermischen (separater Commit).
