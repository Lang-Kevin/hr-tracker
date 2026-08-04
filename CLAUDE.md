# HR-Tracker

Native Android-App zur Aufzeichnung von Herzfrequenzdaten eines BLE-Brustgurts (moofit HR8).

> **Hinweis:** Das Wear-OS-Companion-Feature (Samsung Galaxy Watch D227) ist ausgelagert
> in Branch `feat/wear-os-companion` und noch nicht fertig. Auf diesem Branch bewusst nicht enthalten.

## Commands

```bash
./gradlew assembleDebug          # App-Modul bauen
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
├── service/     ui/
```

## Hard Rules

Diese Regeln sind **nicht verhandelbar**. Wer sie kippt, bricht entweder eine BLE-Spezifikation oder einen bereits debuggten Bug.

- **Foreground Service** während aktiver Session, `connectedDevice`-Type.
- **CCCD aktivieren** (`0x2902`) — sonst keine Notifications.
- **RR-Intervalle persistieren** (RR = `raw * 1000 / 1024`). Pflicht für HRV.
- **BPM-Clamp 30–220** im `HeartRateParser` (M-Batch-1, #2).
- **Device-Adress-Guard** in `HrBleManager.onCharacteristicChanged` — Samsung-Bug, sonst mischt sich Watch-HR in BLE-Stream (Cross-Notification-Bugfix).
- **Auto-Reconnect**, inkrementelles Speichern, kein Sample-Verlust.
- Migration v1→v2 für `Session.zoneSnapshotJson` darf nicht entfernt werden (Batch 5, #21).

## Zonen-Modell

- HRmax: Tanaka (`208 - 0.7·Alter`), optionaler Manual Override.
- Default Karvonen, Fallback %HRmax.
- Pro Session als Snapshot persistieren (`maxHrUsed`, `restingHr`, `zoneSnapshotJson`).

## Workflow

1. **Pro Milestone**: build → lint → test → commit → `/compact`.
2. **Commit ist Pflicht** sobald ein Feature, Bugfix oder Milestone abgeschlossen ist — kein halbfertiger Zustand committen. Format via `caveman-commit` (Caveman-Plugin).
3. **Vor dem Commit**: `changelog.d` und `docs/SPEC.md` aktualisieren.
4. Ein Commit pro Milestone. Keine Sammelcommits.
5. Kleinste mögliche Änderung. Großflächige Refactorings nur auf Anfrage.
6. Migrations-Änderungen → vorher kurz freigeben lassen.

## Permissions / Plattform-Constraints

- minSdk 26, targetSdk 35.
- Android 12+: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`.
- < 12: `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION`.
- Zusätzlich: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `POST_NOTIFICATIONS`.

## Weiterführend

- Vollständige Spezifikation: `docs/SPEC.md`
- Designentscheidungen & Farbpalette: `docs/SPEC.md#design-system`
- Historie (abgeschlossene Milestones, Batches, Bugfixes): `docs/CHANGELOG.md`
- Visualisierungsregeln (Charts, Graphs, Vico): via `/graphify` Slash-Command (nach `graphify install`). Output-Artefakte unter `graphify-out/` — siehe `docs/SPEC.md#visualisierung--graphify`

Nur lesen, wenn der aktuelle Task es konkret braucht.

---

Allgemeine Arbeitskonventionen (Modell-Routing, Retrieval-First, Logs, Reviewstrategie, Shell, Session-Start, graphify) gelten global — siehe `~/.claude/CONVENTIONS.md`.