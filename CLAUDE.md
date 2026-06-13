# HR-Tracker

Native Android-App zur Aufzeichnung von Herzfrequenzdaten eines BLE-Brustgurts (moofit HR8) mit Wear-OS-Companion (Samsung Galaxy Watch D227).

## Commands

```bash
./gradlew assembleDebug          # App-Modul bauen
./gradlew :wear:assembleDebug    # Wear-Modul bauen
./gradlew test                   # Unit-Tests
./gradlew lint                   # Lint
./gradlew :app:installDebug      # Auf Gerät installieren
```

## Architektur

Strikt MVVM. Business-Logik gehört **nicht** in Composables.

```text
UI (Compose) → ViewModel → Repository → BLE / Room / DataStore
```

Package-Layout:

```text
com.kevin.hrtracker
├── ble/         data/         domain/       export/
├── service/     ui/           wearable/
```

Wear-Modul: `com.kevin.hrtracker.wear` (`sensor/`, `comms/`, `ui/`).

## Hard Rules

Diese Regeln sind **nicht verhandelbar**. Wer sie kippt, bricht entweder eine BLE-Spezifikation oder einen bereits debuggten Bug.

- **Foreground Service** während aktiver Session, `connectedDevice`-Type.
- **CCCD aktivieren** (`0x2902`) — sonst keine Notifications.
- **RR-Intervalle persistieren** (RR = `raw * 1000 / 1024`). Pflicht für HRV.
- **BPM-Clamp 30–220** im `HeartRateParser` (M-Batch-1, #2).
- **Device-Adress-Guard** in `HrBleManager.onCharacteristicChanged` — Samsung-Bug, sonst mischt sich Watch-HR in BLE-Stream (Cross-Notification-Bugfix).
- **Auto-Reconnect**, inkrementelles Speichern, kein Sample-Verlust.
- **`notifyWatch()` nur im WATCH-Modus** in `HrRecordingService` — sonst Doppel-Stream.
- Migration v1→v2 für `Session.zoneSnapshotJson` darf nicht entfernt werden (Batch 5, #21).

## Zonen-Modell

- HRmax: Tanaka (`208 - 0.7·Alter`), optionaler Manual Override.
- Default Karvonen, Fallback %HRmax.
- Pro Session als Snapshot persistieren (`maxHrUsed`, `restingHr`, `zoneSnapshotJson`).

## Workflow

1. **Pro Milestone**: build → lint → test → commit → `/compact`.
2. **Commit ist Pflicht** am Ende jeder Implementierung — kein Commit = Aufgabe nicht abgeschlossen. Format via `caveman-commit` (`git add -A && git commit`).
3. Ein Commit pro Milestone. Keine Sammelcommits.
4. Kleinste mögliche Änderung. Großflächige Refactorings nur auf Anfrage.
5. Migrations-Änderungen → vorher kurz freigeben lassen.

## Modell-Routing (wichtig)

**Hauptsession läuft auf Opus.** Architekturentscheidungen, Multi-File-Refactorings, schwierige Bug-Hypothesen, Trade-off-Diskussionen — hier bleibt der Hauptkontext.

Alles andere wird an Subagenten delegiert, jeder mit eigenem Kontextfenster:

| Subagent       | Modell  | Zweck                                                   |
| -------------- | ------- | ------------------------------------------------------- |
| `@explorer`    | Caveman | Datei-/Symbol-Suche, Dependency-Audit, Code-Lookup      |
| `@implementer` | Sonnet  | Fokussierte Code-Änderung in 1–3 Dateien                |
| `@reviewer`    | Sonnet  | Code-Review nach Änderung (Spec / Sicherheit / Stil)    |
| `@architect`   | Opus    | Milestone-Planung, schwierige Architekturfragen         |

Default-Verhalten:

- **Exploration zuerst an `@explorer`.** Niemals den Hauptkontext mit `Grep`-Dumps oder Logcats fluten — der Subagent fasst zusammen und gibt nur die relevanten Datei:Zeile-Referenzen zurück.
- **Nach jeder Code-Änderung `@reviewer` triggern**, bevor committed wird.
- **`@implementer` für Punktfixes** (≤3 Dateien, klar lokalisiert).
- **Hauptsession nur** bei Multi-File-Designentscheidungen, neuem Milestone, Spec-Konflikten.
- Wenn Kontext > ~50% gefüllt: `/compact` ausführen oder Sub-Task in `@explorer` auslagern.

Override pro Aufruf möglich, z. B. `Use the implementer subagent on "BPM-Clamp anpassen"`.

## Retrieval First

Vor jedem Read:

1. Relevante Dateien benennen (kein Repo-Scan).
2. Abhängigkeiten durch `@explorer` klären, **nicht** durch rekursives `ls`.
3. Dann gezielt öffnen.

**Verboten**: vollständige Repo-Scans, rekursives `tree`, ungezielte `find`-Läufe.

## Logs

Logcat / Gradle: nur die relevanten Zeilen. Stacktraces auf den eigenen App-Code kürzen. Keine kompletten Build- oder Test-Reports einlesen — Ausschnitt reicht.

## Reviewstrategie (für Milestones)

In dieser Reihenfolge, jeweils als eigener Subagent-Lauf:

1. **Spec-Audit** (`@reviewer`) — Code vs. `docs/SPEC.md`.
2. **Dependency-Audit** (`@reviewer`).
3. **Architektur-Audit** (`@architect` bei größerem Scope, sonst `@reviewer`).
4. **Security / CVE** (`@reviewer`).
5. **Fixes** (`@implementer`).

## Permissions / Plattform-Constraints

- minSdk 26, targetSdk 35, Wear-Modul minSdk 30.
- Android 12+: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`.
- < 12: `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION`.
- Zusätzlich: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `POST_NOTIFICATIONS`, `BODY_SENSORS` (Wear).

## Weiterführend

- Vollständige Spezifikation: `docs/SPEC.md`
- Designentscheidungen & Farbpalette: `docs/SPEC.md#design-system`
- Historie (abgeschlossene Milestones, Batches, Bugfixes): `docs/CHANGELOG.md`
- Visualisierungsregeln (Charts, Graphs, Vico): via `/graphify` Slash-Command (nach `graphify install`). Output-Artefakte unter `graphify-out/` — siehe `docs/SPEC.md#visualisierung--graphify`

Nur lesen, wenn der aktuelle Task es konkret braucht.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

Before investigating architecture:

1. Read graphify-out/GRAPH_REPORT.md
2. Use graphify information as primary source
3. Use file searches only for verification

USE POWERSHELL INSTEAD OF BASH!