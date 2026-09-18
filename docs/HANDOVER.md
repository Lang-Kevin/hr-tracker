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

1. **Stop-Pfade 5+6 nachtesten**, sobald der Brustgurt angelegt ist (siehe „Geräte-Verifikation" unten). Punkte 1–4 sind erledigt.
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

Geräte-Verifikation wurde am 2026-09-18 vom Product Owner selbst per adb/uiautomator durchgeführt (Ergebnisse unten). Punkte 1–4 PASS, Punkte 5–6 durch fehlenden Brustgurt blockiert.

## Geräte-Verifikation (durchgeführt 2026-09-18)

Gerät: **SM-A546B** (Galaxy A54, Android 16, Serial `RZCWC0AEV1F`). Build: `:app:installDebug` → `BUILD SUCCESSFUL`, `Installed on 1 device.`
Durchgeführt per `adb` + `uiautomator dump` (kein manueller Tap nötig).

| # | Schritt | Ergebnis |
| --- | --- | --- |
| 1 | Körperdaten setzen (Gewicht 80, Männlich) → `am force-stop` → Neustart | **PASS** — Gewicht-Feld nach Kaltstart weiterhin `80`, Hinweistext verschwunden. DataStore persistiert. |
| 2 | Alte Session „Volleyball" 17.09.2026 20:14 öffnen | **PASS** — `KALORIEN: 1141 kcal`, rückwirkend berechnet, keine Migration. |
| 3 | Dieselbe Session **vor** dem Setzen der Körperdaten | **PASS** — `KALORIEN: —` + Hinweis „Kalorien: Gewicht und Geschlecht in den Einstellungen hinterlegen.", kein Crash. |
| 4 | Session ohne HRR-Peak („HRV RMSSD" 15.09.2026 15:04, 05:00) | **PASS** — Erholungs-Card gerendert mit `—` + „Zu wenig Daten oder kein Peak ≥ 70 % HRmax mit 60 s Nachlauf." |
| 5 | Session starten → Live „Stop" → Report | **BLOCKIERT** — siehe unten. |
| 6 | Session starten → Scan-Screen → „Stoppen" → Report + Notification weg | **BLOCKIERT** — siehe unten. |

### Formel-Gegenrechnung (Punkt 2)

Session: Ø 120 BPM, Dauer 01:52:10 (112,17 min). Settings: 80 kg, männlich, Alter 30 (Tanaka-HRmax 187 bestätigt beide).

```
(-55.0969 + 0.6309·120 + 0.1988·80 + 0.2017·30) / 4.184 × 112,17 = 1141
```

Gerät zeigt `1141 kcal` — term-by-term identisch. Keytel-Implementierung ist end-to-end korrekt.

### Clamp-Nachweis (Bonus, nicht geplant)

Die HRV-Session (Ø 51 BPM, 5 min) zeigt `0 kcal`. Keytel ergibt hier rechnerisch ca. `-1`; `coerceAtLeast(0)` greift auf dem Gerät. Semantisch gewollt: ohne RMR-Anteil ist Ruhe-Verbrauch definitionsgemäß 0 (siehe `docs/SPEC.md` → „Bewusst nicht gebaut").

### Report-View: sichtbare Kennzahlen (Feature 2, bestätigt)

`BPM Ø` · `DAUER` · `MAX BPM` · `RMSSD` · `TRIMP` · `MIN BPM` · `KALORIEN` · `HRMAX` · `RUHEPULS`, dazu Zonenverteilung Z1–Z5 mit „% in Ziel-Zone" und die Erholungs-Card. Alle neun Kacheln rendern gleichzeitig ohne Overflow bei 1080×2340.

### Warum 5 + 6 offen sind

Der Brustgurt **HR8 55867** (`C2:E3:8E:A2:71:6E`) sendet nicht — der moofit HR8 advertised nur bei Hautkontakt.

- Logcat: `D HRTracker: Connecting to C2:E3:8E:A2:71:6E`, danach kein GATT-Connect-Callback.
- 15 s aktiver Scan: 0 Geräte unter „Verfügbare HR-Geräte".
- Status bleibt auf `Verbinde…`, nie `ConnectionState.Ready`.

`ScanScreen.kt:154` gated die Start-/Fortsetzen-/Stoppen-Steuerung auf `ConnectionState.Ready` — ohne verbundenen Gurt ist **keiner** der beiden Stop-Pfade per UI erreichbar. Kein Softwareproblem, rein physisch.

**Nachzuholen, sobald der Gurt angelegt ist** (beides je < 1 min):

1. Session starten → Live-Screen „Stop" → Report öffnet sich; Zurück landet auf Scan, nicht auf Live.
2. Session starten → per Zurück-Geste auf Scan (Session läuft weiter, „Fortsetzen"/„Stoppen" erscheinen) → „Stoppen" → Report öffnet sich **und** die Notification verschwindet.

Punkt 2 ist der wichtigere: dort lag der Orphaned-Foreground-Service-Bug (`MainActivity.kt:165`). Die verschwindende Notification ist der eigentliche Nachweis. Baseline vor dem Test ist sauber verifiziert: `dumpsys notification` → 0 Records für `com.kevin.hrtracker`, `dumpsys activity services` → kein laufender Service.

## Fallen (teuer gelernt)

- **JAVA_HOME muss gesetzt sein**, Android-Studio-JBR ist kaputt. Wrapper liegt in `code/`:
  `$env:JAVA_HOME='C:\Users\Kiwi PC\.gradle\jdks\jetbrains_s_r_o_-21-amd64-windows.2'`
- **PowerShell expandiert `--tests "*Foo*"`** gegen die cwd → Gradle bekommt einen Dateinamen. Immer FQCN + separat quoten:
  `& '.\gradlew.bat' :app:testDebugUnitTest '--tests' 'com.kevin.hrtracker.domain.CalorieCalculatorTest'`
- **`git commit -F <file>` mit Heredoc** schlug hier fehl (Pfad nicht schreibbar) — mehrere `-m` in Bash funktionierten.
- graphify post-commit Hook meldet `chunk failed: Connection error` (ollama offline). Kosmetisch, blockiert keinen Commit.
