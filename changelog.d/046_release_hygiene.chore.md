## Release-Hygiene: R8, Log-Stripping, Vico entfernt

**Problem:**
Der Release-Build war weder minifiziert noch geschrumpft, Debug-Logging (`Log.d/v/i`) lief auch im Release, und die Chart-Library Vico war eingebunden, obwohl sie nirgends verwendet wird (Charts sind eigene Compose-Canvas-Implementierungen).

**Lösung:**
- Release: `isMinifyEnabled = true`, `isShrinkResources = true` (APK 20,5 MB → 2,2 MB).
- `proguard-rules.pro`: `-assumenosideeffects` für `Log.d/v/i` – R8 entfernt diese Aufrufe im Release; `Log.w/e` bleiben.
- `-keepattributes SourceFile,LineNumberTable`: lesbare Stacktraces (Retrace über `mapping.txt`).
- Keine eigenen Keep-Regeln nötig: Room, Hilt und kotlinx.serialization bringen Consumer-Rules mit.
- Vico-Abhängigkeit entfernt (Dependabot PR #18 damit obsolet).
- Backup-Regeln bewusst unverändert (Produktentscheidung offen).

**Betroffene Dateien:**
- `code/app/build.gradle.kts`, `code/app/proguard-rules.pro`, `code/gradle/libs.versions.toml`
- `docs/SPEC.md`, `README.md`