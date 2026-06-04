# Claude Code Projektregeln

## Grundsatz

Ziel:

* minimale Token-Nutzung
* maximale Codequalität
* milestonebasierte Entwicklung

---

## Retrieval First

Falls verfügbar:

* MCP Code Index
* RepoMap
* Sourcegraph
* Code Graph

Immer zuerst nutzen.

Reihenfolge:

1. relevante Dateien identifizieren
2. Abhängigkeiten bestimmen
3. nur benötigte Dateien öffnen
4. Änderung durchführen

Verboten:

* vollständige Repository-Scans
* rekursive Exploration ohne Anlass
* Lesen großer Verzeichnisbäume

---

## Kontextmanagement

Nach jedem Milestone:

1. Build
2. Test
3. Commit
4. /compact

Milestones sind Kontextgrenzen.

---

## Dateizugriffe

Bevorzugt:

* Git Diff
* einzelne Dateien
* direkte Referenzen

Nicht bevorzugt:

* vollständige Projektanalysen
* globale Reads

---

## Log Management

Logs kosten Tokens.

Deshalb:

* relevante Ausschnitte verwenden
* Stacktraces kürzen
* nur benötigte Zeilen analysieren

Nicht verwenden:

* vollständige Logcat Dumps
* vollständige Gradle Logs
* komplette Testreports

---

## Android Regeln

Pflicht:

* Foreground Service
* CCCD aktivieren
* RR-Intervalle speichern
* Auto-Reconnect
* Runtime Permissions
* inkrementelles Speichern

Diese Regeln dürfen nicht entfernt werden.

---

## Architektur

MVVM.

Trennung:

```text
UI
↓
ViewModel
↓
Repository
↓
BLE / Database
```

Business-Logik gehört nicht in Compose Screens.

---

## Änderungen

Kleinste mögliche Änderung bevorzugen.

Vor Änderungen:

1. Ursache identifizieren
2. betroffene Dateien bestimmen
3. gezielte Anpassung

Großflächige Refactorings nur auf Anfrage.

---

## Review Strategie

Reihenfolge:

1. Spec Audit
2. Dependency Audit
3. Architektur Audit
4. Security Audit
5. Fixes

---

## QA

Nach jedem Milestone:

* Build
* Lint
* Tests

Falls verfügbar:

* qa-verifier Agent ausführen

---

## Commit Strategie

Ein Commit pro Milestone.

Commit enthält:

* Implementierung
* Build erfolgreich
* Tests erfolgreich

Keine Sammelcommits.

---

## Token Sparsamkeit

Immer bevorzugen:

* gezielte Fragen
* gezielte Reads
* gezielte Fixes

Vermeiden:

* Wiederholung bekannter Informationen
* erneutes Einlesen unveränderter Dateien
* Exploration ohne konkretes Ziel
