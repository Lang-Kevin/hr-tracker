# Plan: Shared Android Library (Gradle Composite Builds)

## Ausgangslage

Drei geplante Android-Apps teilen sich Android-Code:
- `C:\Code\Android\Projekt_TrackerApp` (HR-Tracker, aktiv)
- `C:\Code\Arduino\ArmSwingProject` (Android-Anteil vorhanden)
- Dritte App (noch unbenannt, in Planung)

Einzel-Entwickler, manuelles Deployment, keine Release-Zyklen.

## Entscheidung: Gradle Composite Builds

**Gewählt:** Separates `shared-android-lib`-Repo, eingebunden via `includeBuild()`.

**Verworfen:**
- Monorepo — Repos sollen getrennt bleiben
- Maven Local / AAR — Publish-Step bei jeder Änderung nervt im Solo-Dev-Workflow

## Ziel-Struktur

```
C:\Code\Android\
├── shared-android-lib/        ← neues Repo (noch zu erstellen)
│   ├── shared/                ← Android Library Modul
│   │   └── build.gradle.kts  (group = "com.kevin.shared")
│   └── settings.gradle.kts
├── Projekt_TrackerApp/
│   └── settings.gradle.kts   → includeBuild("../shared-android-lib")
├── ArmSwingProject/
│   └── settings.gradle.kts   → includeBuild("../shared-android-lib")
└── FutureApp/
    └── settings.gradle.kts   → includeBuild("../shared-android-lib")
```

Abhängigkeit in jeder App:
```kotlin
implementation("com.kevin.shared:shared")  // Gradle löst lokal auf, kein Maven
```

## Offene Fragen / Nächste Schritte

1. **Analyse:** Was teilen HR-Tracker und ArmSwing konkret? (BLE-Utilities, Room-Basisklassen, Design-System, etc.)
   - HR-Tracker-Seite bekannt, ArmSwing noch nicht analysiert
2. **Shared-Modul erstellen** und leere Library-Struktur aufsetzen
3. **Gemeinsamen Code migrieren** — schrittweise, Modul für Modul
4. **`includeBuild` einbinden** in beide bestehende Projekte
5. Build + Tests grün in beiden Apps

## Kontext

Ausführliche Diskussion: Chat vom 2026-06-13/14.
