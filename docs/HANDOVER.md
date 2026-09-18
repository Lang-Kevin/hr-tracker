# Handover — Kalorien + Report-Stats

Stand: 2026-09-18. Plan: `docs/superpowers/plans/2026-09-17-calories-and-report-stats.md` (vollständig abgeschlossen).

## Branch state

Branch `fix/bpm-no-contact-drop`, 26 ahead of origin/master. **Branch-Entscheidung**: Name wird beim PR verändert (User-Entscheidung), vorerst keine Umbenennung.

| SHA | Was |
| --- | --- |
| `6b80ee9` | chore(changelog): changelog.d konsolidiert (Vorarbeit) |
| `92a4bdd` | fix(ui): widget-variant labels ohne umbruch, rr im sample-log (Vorarbeit) |
| `92dd666` | **Task 1 ✓** feat(domain): keytel kalorienschaetzung — `CalorieCalculator.kt` + Unit-Tests |
| `980174e` | **Task 2 ✓** feat(settings): gewicht und geschlecht fuer kalorien |
| `4ccd63a` | **Task 3 ✓** feat(detail): kalorien, zonen-basis, hrr immer sichtbar |
| `3bb1518` | **Task 4 ✓** feat(nav): session-ende oeffnet report direkt |
| `2f23253` | **Task 5 ✓** docs: kalorien und report-stats |

Uncommitted (bewusst, **nicht** anfassen):
`.claude/settings.json`, `CLAUDE.md`, `code/build_output.txt`, `code/test_output.txt`, `fail.png`, `session_45.json`, `session_48.json`, `graphify-out/`, `code/app/src/test/.../ui/detail/`.

## Abgeschlossen (Branch-Commit-Übersicht)

**Alle 5 Tasks sind code-complete.** Review-Findings vor Commit behoben, alle Einheits-Tests PASS:

- **Task 1**: `CalorieCalculator.kt` — Keytel-Formel (Keytel et al., 2005) über Ø-HF, geschlechtsspezifisch, Inputs Gewicht/Alter/Ø-BPM/Dauer, Rückgabe `Int?`, clamped `>= 0`; 7 Unit-Tests (`com.kevin.hrtracker.domain.CalorieCalculatorTest`).
- **Task 2**: `UserSettings` + `SettingsRepository.kt` — `weightKg: Int?`, `sex: Sex?`; Settings-UI mit Picker.
- **Task 3**: `DetailViewModel` (calories Flow, `combine` mit Settings), `DetailScreen` (Stat-Row mit Kalorien/HRmax/Ruhepuls, HRR-Card immer sichtbar). **Bonus-Findings (vor Commit behoben)**:
  - Null-State HRR-Card nutzte `Card` statt `OutlinedCard` (visueller Jump) → gefixt.
  - `if (recovery != null) { recovery!!.let { … } }` war eine unnötige Umschreibung des Elvis-Patterns → auf `?.let { } ?: OutlinedCard { }` vereinfacht (40 Zeilen Einrückung weg).
- **Task 4**: `MainActivity.kt:165-177` — ScanScreen "Stoppen" + Live "Stop" → `stopSession(onStopped)` → navigiert zu Report. **Scope-Überschreitung (absichtlich, Hard-Rule-Violation sichtbar gemacht)**: ScanScreen-Stop-Pfad rief `stopService(HrRecordingService.stopIntent(...))` nie auf, Foreground Service, BLE-Verbindung und Notification blieben ohne Session am Leben. Fix: `stopService(...)`-Aufruf zur `onSessionStopped`-Routine in `MainActivity.kt:165` hinzugefügt (analog LiveScreen-Pfad). `onDestroy()` wurde nicht angefasst.
- **Task 5**: `changelog.d/` + `docs/SPEC.md` — **4 Factual Errors (vor Commit behoben)**:
  - Kalorien-Formel: `× (Dauer/60)` war falsch → `× Dauer [min]` (keine Division mehr).
  - Fabrizierte Citation: „Knuth & Kern" → „Keytel et al., 2005".
  - Type-Fehler: `weightKg: Double?` sollte `Int?` sein.
  - Dateiliste: zwei unveränderte Dateien gelistet.

## Blockers

Keine. Nichts hängt, nichts läuft im Hintergrund.

## Offene Follow-ups

Keine Bugs, keine Blockaden — lediglich zwei nachgelagerte Cleanup-Tickets (explizit außerhalb dieses Features):

1. **shared-android-lib** (`C:\Code\Android\shared-android-lib`, **separates Repo**): 
   `BaseRecordingService.kt:32-58` — `ACTION_STOP` / suspend-Zweig in `onRecordingStop()` ist Dead Code für diese App. 
   `HrRecordingService.stopIntent()` wird nur an `stopService()` übergeben, nie an `startService()`, und `Context.stopService()` springt zu `onDestroy()` — `onStartCommand()` wird nicht durchlaufen. 
   → Cleanup-Ticket für später, anderes Repo.

2. **Build-Logs im Repo**: `code/build_output.txt` und `code/test_output.txt` — untracked Artefakte. Löschbar oder `.gitignore` erweitern.

## Next 3 steps

1. **Manual-Verification durch User** — Testliste unten durchlaufen (alle 6 Punkte, inkl. neuer Punkte 5+6: Stoppen vom Live- und vom Scan-Screen).
2. **Push + PR** — Branch wird beim PR umbenannt (User-Entscheidung).
3. Optional: die zwei Follow-ups oben abarbeiten (Dead Code in `shared-android-lib`, Build-Logs aufräumen).

## QA-Gate

**`@qa-verifier`: PASS** (2026-09-18, Commit-Range `6b80ee9..2f23253`). Unabhängig nachgeprüft:

| Prüfung | Ergebnis |
| --- | --- |
| `assembleDebug test lint` | BUILD SUCCESSFUL |
| Lint | 48 Warnings, **0 Errors** |
| Unit-Tests | 63/63 PASS (7 Klassen), `CalorieCalculatorTest` 7/7 |
| Hard Rules | unberührt — Diff über `ble/` + `data/db/` in der Range ist leer |
| DB-Schema | `HrDatabase.kt:12` weiterhin `version = 5`, keine neue Migration, Kalorien on-read |
| SPEC ↔ Code | Keytel-Formeln term-by-term identisch (inkl. `/ 4.184`, `× Dauer [min]`) |
| `changelog.d/027` | Dateiliste deckt sich mit `git show --stat` aller Commits |

Offen bleibt nur die Geräte-Verifikation durch den User (Liste unten) — die deckt der QA-Gate nicht ab.

## Verification steps für den User

**Alle nachfolgenden Punkte sind jetzt end-to-end testbar.** Build + Install:

```bash
cd code && ./gradlew :app:installDebug
```

1. Einstellungen → „Körperdaten": Gewicht 80, „Männlich" → App neu starten → Werte noch gesetzt (DataStore).
2. Verlauf → alte Session öffnen → KALORIEN > 0 (on-read, rückwirkend, keine Migration).
3. Gewicht in Settings leeren → Detail erneut öffnen → KALORIEN „—" + Hinweis, kein Crash.
4. Session ohne HRR-Peak → Erholungs-Card sichtbar mit „—".
5. **NEU**: Session starten → Live-Screen „Stop" → Report öffnet sich; Zurück: Scan-Screen (nicht Live).
6. **NEU**: Session starten → per Zurück-Geste auf den Scan-Screen wechseln (Session läuft weiter, „Fortsetzen"/„Stoppen" erscheinen) → „Stoppen" drücken → Report öffnet sich, Notification verschwindet, Foreground Service beendet.

## Fallen (teuer gelernt)

- **JAVA_HOME muss gesetzt sein**, Android-Studio-JBR ist kaputt. Wrapper liegt in `code/`:
  `$env:JAVA_HOME='C:\Users\Kiwi PC\.gradle\jdks\jetbrains_s_r_o_-21-amd64-windows.2'`
- **PowerShell expandiert `--tests "*Foo*"`** gegen die cwd → Gradle bekommt einen Dateinamen. Immer FQCN + separat quoten:
  `& '.\gradlew.bat' :app:testDebugUnitTest '--tests' 'com.kevin.hrtracker.domain.CalorieCalculatorTest'`
- **`git commit -F <file>` mit Heredoc** schlug hier fehl (Pfad nicht schreibbar) — mehrere `-m` in Bash funktionierten.
- graphify post-commit Hook meldet `chunk failed: Connection error` (ollama offline). Kosmetisch, blockiert keinen Commit.
