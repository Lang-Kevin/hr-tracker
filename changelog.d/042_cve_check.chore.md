## CVE-Check in CI, Kotlin-Update wegen CVE-2026-53914

**Problem:**
Abhängigkeiten wurden nicht auf bekannte Schwachstellen geprüft. Kotlin 2.1.0 ist von CVE-2026-53914 / GHSA-r937-wjx7-w2jp betroffen (unsichere Deserialisierung im Build-Cache des Kotlin-Gradle-Plugins, Codeausführung möglich; behoben ab 2.4.20).

**Lösung:**
- Neuer Workflow `CVE-Check` (`.github/workflows/security.yml`): löst den vollständigen Abhängigkeitsbaum (App-Classpaths, KSP-Prozessoren, Plugin-Classpath, Shared Library) per Init-Skript als Gradle-Lockfiles auf und prüft sie mit osv-scanner gegen die OSV-Datenbank. Läuft bei Push auf `master`, in PRs, wöchentlich (Mo.) und manuell. Bekannte Schwachstellen lassen den Job fehlschlagen; bewusst akzeptierte Funde mit Begründung in `code/osv-scanner.toml` eintragen.
- Dependabot (`.github/dependabot.yml`) für den Versionskatalog und die GitHub Actions, wöchentlich.
- Kotlin 2.1.0 → 2.4.20 (inkl. Compose-Compiler und Serialization-Plugin).
- KSP 2.1.0-1.0.29 → 2.3.12 (KSP2, nicht mehr an die Kotlin-Version gekoppelt).
- Hilt 2.57.1 → 2.60.1: ältere Versionen können die Metadaten von Kotlin 2.4 nicht lesen.
- `kotlinOptions` → `kotlin { compilerOptions { … } }` (seit Kotlin 2.2 nicht mehr erlaubt).
- Build-Tooling (AGP-Plugin-Classpath, Android Lint, Unified Test Platform): verwundbare transitive Versionen per Regel in `code/settings.gradle.kts` angehoben – Netty 4.1.138, Bouncy Castle 1.86, jdom2 2.0.6.1, jose4j 0.9.7, commons-lang3 3.21.0, httpclient 4.5.14. Die Runtime-Classpaths der App waren nicht betroffen.
- Shared Library auf `v0.3.2` (ebenfalls Kotlin 2.4.20); das Tag muss nach dem Merge der Lib gesetzt werden.

**Betroffene Dateien:**
- `.github/workflows/security.yml`, `.github/security/dependency-locking.init.gradle`, `.github/dependabot.yml`
- `code/gradle/libs.versions.toml`, `code/app/build.gradle.kts`, `code/.gitignore`, `code/shared-app-lib.ref`, `code/settings.gradle.kts`
