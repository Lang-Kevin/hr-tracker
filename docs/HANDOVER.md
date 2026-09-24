# Handover — Zonen-Modell explizit

Stand: 2026-09-24. Plan: `docs/superpowers/plans/2026-09-18-zone-model-and-observed-hrmax.md` (Option A + E umgesetzt, B/C/D bewusst offen).

## Branch state

Branch `fix/bpm-no-contact-drop` — gepusht bis `11e8382`, alles danach nur lokal. **Gepusht** (`11e8382`). PR: https://github.com/Lang-Kevin/hr-tracker/pull/2 (wird nach Push gemerged, gegen `master`). `gh` ist jetzt als `Lang-Kevin` aktiv, passt zum Remote-Owner.

| SHA | Was |
| --- | --- |
| `1c371dd` | **✓ aktuell** fix(onboarding): ruhepuls-text auf neues zonen-modell (2 Dateien, +5/−2) |
| `416fa1d` | docs(handover): geraete-verifikation zonen-modell |
| `c86893a` | fix(zones): zonen-modell explizit statt implizit (14 Dateien, +247/−25) |
| `f5861a0` | docs(handover) Vorgänger-Feature |
| `2f23253`..`92dd666` | Kalorien + Report-Stats — abgeschlossen, verifiziert, hier nicht mehr relevant |

Uncommitted (bewusst, **nicht** anfassen): `.claude/settings.json`, `CLAUDE.md`, `code/build_output.txt`, `code/test_output.txt`, `fail.png`, `session_45.json`, `session_48.json`, `graphify-out/`, `code/app/src/test/.../ui/detail/`, `docs/superpowers/plans/2026-08-28-ponytail-review.md`.

## Was `c86893a` löst

**Bug (User-Report):** Zone 2 eines 30-Jährigen lag bei ~135 statt ~114 BPM.

**Ursache:** Eine Prozenttabelle (50/60/70/80/90) auf zwei Bezugsgrößen. Das Modell sprang still von %HRmax auf Karvonen, sobald ein Ruhepuls gespeichert war. Alter 30 / HRmax 187 / Ruhepuls 60 → Zone 2 wanderte 112–130 → 136–148, ohne sichtbaren Hinweis. Kein Rechenfehler, ein Anker-Mismatch.

**Fix:**
- `ZoneModel { HR_MAX, KARVONEN }` (`domain/HrZoneCalculator.kt`) — explizite Einstellung, Default `HR_MAX`.
- DataStore-Key `zone_model`, Segmented-Button in Settings (**keine** Room-Migration, Schema bleibt v5).
- `KARVONEN` ohne Ruhepuls → Fallback `HR_MAX` + UI-Warnung.
- Prozentstufen unverändert — sie sind pro Modell standarddefiniert.
- **Neu (Option E):** Settings schlägt den höchsten BPM der letzten 90 Tage als HRmax vor (`HrSampleDao.getObservedMaxBpm`). Opt-in per „Übernehmen" → schreibt in vorhandenes `manualMaxHr`. Nie automatisch.

**Nebenbefund gefixt:** `LiveViewModel.kt:147` hat nach Pause/Resume die Zonen neu berechnet statt den Session-Snapshot zu lesen → nutzt jetzt `resolveZones`.

**Reviewer-Warning gefixt:** `MIGRATION_1_2` (`di/DatabaseModule.kt:19`) hat `zoneSnapshotJson` ohne Backfill hinzugefügt → v1-Sessions bleiben dauerhaft `null` und wären durch den neuen Default rückwirkend neu bewertet worden. `resolveZones` hält genau diese auf der Legacy-Regel (Karvonen wenn `restingHr` gesetzt). Backfill-Migration bewusst verworfen — hätte User-Freigabe gebraucht (CLAUDE.md Regel 6).

**Verworfen:** Karvonen-Stufen über die Swain-Regression umzurechnen — hätte ein drittes, nicht-standardisiertes Modell erfunden und die Regression außerhalb ihres validierten Bereichs extrapoliert.

## Blockers

Keine. Nichts hängt, nichts läuft im Hintergrund.

## Offene Follow-ups

1. ~~Geräte-Verifikation `c86893a`~~ — **erledigt 2026-09-19**, siehe unten. Alle erreichbaren Punkte PASS.
2. **Stop-Pfade aus dem Vorgänger-Feature** — Pfad 1 erledigt, Pfad 2 offen:
   - ✓ **Live „Stop" → Report — PASS (2026-09-19, Fire-Tablet, Pseudo-Sensor).** Stop-Button öffnet „Session verlassen?" → „Speichern" → Report. `dumpsys activity services` von 1 auf 0 (Foreground Service beendet), Notification-Record weg (übrig nur eine `post_frequency`-Statistikzeile, kein aktiver Eintrag). Zurück aus dem Report landet auf **Scan**, nicht auf Live.
   - ✓ erledigt (2026-09-24): Produktentscheidung = toten Zweig entfernt (ScanScreen + MainActivity, changelog.d/029), Reviewer ohne Befund, build/lint/test grün.
   - ✓ **Verwerfen-Pfad — PASS (2026-09-23, Fire-Tablet).** Zurück → „Verwerfen": Foreground Service 1 → 0, Notification-Record 1 → 0, zurück auf Scan mit „Training starten", Log `Session 3 discarded`.
   - Der Pseudo-Sensor (Debug-Modus in den Einstellungen → „Pseudo-Sensor [Test]" im Scan, `FA:CE:00:00:00:01`) erreicht `ConnectionState.Ready`. Kein Brustgurt nötig.
3. **Zonen-Ausbaustufen, dokumentiert aber nicht gebaut** (im Plan, Abschnitt „Deferred"): LTHR-Anker nach Friel (30-min-TT), Fitness-Level-Preset, DFA-α1-Schwellenerkennung aus den gespeicherten RR-Intervallen. Nur auf Ansage bauen.
4. **shared-android-lib** (`C:\Code\Android\shared-android-lib`, **separates Repo**): `BaseRecordingService.kt:32-58` — `ACTION_STOP`/suspend-Zweig ist Dead Code für diese App. Cleanup-Ticket.
5. **Build-Logs im Repo**: `code/build_output.txt`, `code/test_output.txt` — untracked. Löschen oder `.gitignore`.

## Next 3 steps

1. **Telefon manuell aufräumen** — siehe „Geräte-Zustand".
2. **Nach Merge von PR #2: Branch aufräumen / nächstes Thema.**
3. **Offene Zonen-Optionen B/C/D aus dem Plan bewerten.**

## QA-Gate

**`@qa-verifier`: PASS** (2026-09-18, Commit `c86893a`). Vom Product Owner unabhängig über die JUnit-XMLs nachgerechnet: `tests=69 skipped=0 failures=0 errors=0`.

| Prüfung | Ergebnis |
| --- | --- |
| `assembleDebug lint test` | BUILD SUCCESSFUL |
| Lint | 48 Warnings, **0 Errors** — keine in den 10 geänderten Dateien |
| Unit-Tests | 69/69 PASS (6 neue in `HrZoneCalculatorTest`, 3 bestehende auf `ZoneModel.KARVONEN` umgehängt) |
| Hard Rules | unberührt — kein Diff in `ble/`, kein Schema-Change |
| DB-Schema | `HrDatabase.kt:12` weiterhin `version = 5` |
| `@reviewer` | 0 Critical / 1 Warning (gefixt) / 2 Suggestions (1 übernommen, 1 bewusst abgelehnt) |

## Geräte-Verifikation (durchgeführt 2026-09-19)

Gerät: **SM-A546B** (Galaxy A54, Serial `RZCWC0AEV1F`), `:app:installDebug` → BUILD SUCCESSFUL. Per `adb` + `uiautomator dump`, kein Brustgurt nötig. Phone-Nutzung vorher mit der Parallel-Session `jump-tracker-app` abgestimmt (deren Auflage: kein `connectedDebugAndroidTest`, kein fremdes `pm clear` — beides eingehalten).

Testdaten: Alter 30 → Tanaka 187, Ruhepuls 50 → HRR 137.

| # | Prüfung | Ergebnis |
| --- | --- | --- |
| 1 | Default-Modell nach Update | **PASS** — `%HRmax` vorausgewählt, Feld heißt „Ruhepuls (für Karvonen)". |
| 2 | Zonengrenzen %HRmax | **PASS** — Z1 93 · **Z2 112–130** · Z3 130–149 · Z4 149–168 · Z5 168–187. Das war der gemeldete Bug (vorher ~135). |
| 3 | Umschalten auf Karvonen | **PASS** — Z1 118 · **Z2 132–145** · Z3 145–159 · Z4 159–173 · Z5 173–187. Deckt sich exakt mit der HRR-Rechnung. |
| 4 | Karvonen ohne Ruhepuls | **PASS** — Warnung „Ohne Ruhepuls wird %HRmax verwendet.", Grenzen fallen auf 93/112/130/149/168 zurück, kein Crash. |
| 5 | Gemessener HRmax, negativer Fall | **PASS** — Zeile bleibt verborgen. DB-gegengeprüft: höchster BPM 90 Tage = **172** < aktiv 187. |
| 5b | Gemessener HRmax, positiver Fall | **PASS** — manueller HRmax 160 gesetzt → „Gemessen: 172 BPM (letzte 90 Tage)" + „Übernehmen" erscheint; Tap schreibt 172, Zeile verschwindet. Nie automatisch. |
| 6 | Alte Session, Snapshot vs. Settings | **PASS, entscheidender Test** — siehe unten. |
| 7 | Pause → Resume | **PASS** — Achsenbeschriftung vor und nach Resume identisch 93/112/130/149/168, kein Sprung, kein Crash. |

### Punkt 6 im Detail (der aussagekräftigste Nachweis)

Alle 6 vorhandenen Sessions tragen einen **Karvonen**-Snapshot (`118/132/145/159/173`) — aufgezeichnet unter dem alten impliziten Verhalten. Die Settings stehen jetzt auf `%HRmax` (`93/112/130/149/168`).

Report „Beachvolleyball" 19.09.2026 10:01 geöffnet → Chart-Achse zeigt **Z1 118 · Z2 132 · Z3 145 · Z4 159 · Z5 173**.

Historische Grenzen werden also **nicht** rückwirkend umgeschrieben; der Snapshot ist die Quelle der Wahrheit. Genau das garantiert der `resolveZones`-Fix, und es ist derselbe Mechanismus wie beim `LiveViewModel`-Nebenbefund.

### Nicht verifizierbar auf diesem Gerät

`zoneSnapshotJson IS NULL` → **0 Sessions**. Der Legacy-Fallback für Pre-v2-Zeilen ist damit auf diesem Gerät nicht erreichbar. Er ist unit-getestet, aber **nicht** gerätegeprüft — ehrlich als Lücke vermerkt, nicht als PASS.

### Neuer Befund: Stop-Pfade sind ohne Brustgurt testbar

Der **Pseudo-Sensor [Test]** (Debug-Modus → Scan) erreicht `ConnectionState.Ready`. Damit greift das Gating in `ScanScreen.kt:154` **nicht** mehr, und die seit dem Vorgänger-Feature offenen Stop-Pfad-Tests sind ohne den HR8 durchführbar. Eine volle Session (Start → Pause → Resume → Verwerfen) lief damit sauber durch.

### Zustand nach dem Test (vollständig zurückgesetzt)

Alle Teständerungen rückgängig: Alter 30, manueller HRmax leer (aktiv 187), Ruhepuls 50, Modell `%HRmax`, Gewicht 80, Männlich, Debug-Modus **aus**, Pseudo-Sensor aus „Gemerkte Geräte" entfernt, Testsession verworfen → **6 Sessions** wie vorher, 0 laufende Services.

> Methodenhinweis: Room läuft im WAL-Modus. Ein `run-as cat databases/hr_tracker.db` **ohne** `-wal` zeigt einen veralteten Stand — dabei sah die verworfene Session kurzzeitig wie eine verwaiste offene Session aus. Immer `hr_tracker.db` **und** `hr_tracker.db-wal` ziehen.

## Geräte-Zustand

**Fire-Tablet (`GN42DM04427500BA`) — aufgeräumt (2026-09-24): Verlauf leer (Test-Session vom 19.09. gelöscht, Papierkorb geleert), Debug-Modus aus, Pseudo-Sensor aus „Gemerkte Geräte" vergessen, App force-stopped.**

**Telefon (`RZCWC0AEV1F`) — Aufräumen teils offen.**
- ✓ `POST_NOTIFICATIONS` revoke (2026-09-24, granted=false).
- ✗ Laufende Pseudo-Sensor-Session → verwerfen, nicht speichern. Service läuft nicht mehr; beim App-Start wird sie per `closeOrphanedSessions` als normale Session in den Verlauf übernommen → dort löschen.
- ✗ Debug-Modus an, war vorher aus → in den Einstellungen wieder ausschalten.
- **Per adb nicht möglich:** App-Start wird vom Auto-Mode-Classifier blockiert (Interfere With Workloads, Telefon wird von JumpTracker-Sessions genutzt) → manuell durch Kevin.

Telefon teilt sich Sessions „Jump Tracker - PO 1" und „Jump Tracker - PO 2". **Vor jedem Input per SendMessage fragen** — beide Sessions müssen OK sein, `uiautomator dump` und Taps kollidieren sonst. Kein `adb kill-server` — trennt die anderen Sessions.

## Fallen (teuer gelernt)

- **JAVA_HOME muss gesetzt sein**, Android-Studio-JBR ist kaputt. Wrapper liegt in `code/`, nicht im Repo-Root:
  `$env:JAVA_HOME='C:\Users\Kiwi PC\.gradle\jdks\jetbrains_s_r_o_-21-amd64-windows.2'`
- **PowerShell expandiert `--tests "*Foo*"`** gegen die cwd → Gradle bekommt einen Dateinamen. Immer FQCN + separat quoten.
- **`git commit -F <file>`** funktioniert — die Datei muss aber ins Scratchpad, nicht ins Repo-Root (dort schlug es vorher fehl).
- **cwd driftet** zwischen Tool-Calls. Immer absolute Pfade.
- graphify post-commit Hook meldet `chunk failed: Connection error` (ollama offline). Kosmetisch, blockiert keinen Commit.

## Geräte-Verifikation Fire-Tablet (2026-09-19)

Zweiter Durchlauf auf anderer Hardware, um die pre-12-Permission-Abzweigung und ein Fresh Install abzudecken. Das Telefon war zu dem Zeitpunkt abgesteckt.

| Gerät | Fire HD 8, `GN42DM04427500BA`, Modell `KFSNWI` |
| --- | --- |
| Android | 11 (API 30) — **unterhalb** von Android 12, deckt den zweiten Permission-Zweig ab |
| Orientierung | Landscape, ~2000×1200 — Layout hält, kein Clipping in Onboarding/Settings/Scan |
| Zustand | `pm clear com.kevin.hrtracker` → echtes Fresh Install, keine migrierten Daten |

**Gefunden und gefixt: Onboarding-Text-Drift (`1c371dd`).**
Schritt 3 des Onboardings versprach für den Ruhepuls „genauere Zonen-Berechnungen nach der Karvonen-Formel". Seit `c86893a` ist `%HRmax` der Default — ein Fresh Install mit eingetragenem Ruhepuls landet also **nicht** auf Karvonen, der Text war schlicht falsch. Neu (`OnboardingScreen.kt:196`): „Nötig, falls du in den Einstellungen das Karvonen-Modell wählst. Standard bleibt %HRmax." Nach Rebuild + Reinstall + erneutem `pm clear` im UI-Dump bestätigt.

Das ist eine eigene Fehlerklasse, nicht ein Einzelfall: Onboarding-Copy wird einmal geschrieben, der Default kippt später in einem anderen Commit, nichts erzwingt den Abgleich. **Bei jeder künftigen Default-Änderung Onboarding- und Settings-Texte mitprüfen.**

**Permission-Zweig < Android 12 — PASS.**
Auf API 30 sind `BLUETOOTH` + `BLUETOOTH_ADMIN` normal-level und automatisch gewährt; `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`/`POST_NOTIFICATIONS` sind deklariert, auf diesem API-Level aber nicht anwendbar. Übrig bleibt `ACCESS_FINE_LOCATION` als Runtime-Permission. Ablauf verifiziert: Scan-Screen zeigt „Bluetooth-Berechtigung gewähren" → Tap öffnet den System-Dialog „HR Tracker erlauben, den Gerätestandort abzurufen?" (`com.android.permissioncontroller`) → „Bei Nutzung der App" → `dumpsys` meldet `ACCESS_FINE_LOCATION: granted=true, flags=[USER_SET|...]` → Scan startet („Scan läuft…", „Verfügbare HR-Geräte:"). Kein Absturz, keine Dauerschleife.

**Onboarding + Settings nach Fresh Install — PASS.**
Alter 30, Ruhepuls 50 eingegeben. Settings zeigt danach `Tanaka HRmax: 187 • Aktiv: 187 BPM`, `Ruhepuls (für Karvonen)`, Zonen-Modell-Auswahl auf `%HRmax`. Default greift also auch ohne Migration, nicht nur beim Update-Pfad.

**Nicht auf dem Tablet gemacht:** die Stop-Pfade aus Follow-up 2 (Pseudo-Sensor lag bereit, Session endete vorher) und alles, was den echten Brustgurt braucht.
