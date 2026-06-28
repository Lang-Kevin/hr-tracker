## AGP-Flags und Hilt-Kompatibilität (Tech-Debt)

**Status:** Ausstehend, nicht blockierend.

**Beschreibung:**
- `android.builtInKotlin=false` und `android.newDsl=false` in `code/gradle.properties` und `wear/gradle.properties`
- 3 Variant-API-Warnings: `applicationVariants`, `testVariants`, `unitTestVariants` in `build.gradle.kts`
- Grund: Hilt 2.57.1 hat keinen AGP-9-built-in-Kotlin-Support; `BaseExtension`-Lookup bricht mit AGP 9.0+
- Build läuft GREEN. Flags gültig bis AGP 10.0 → keine Dringlichkeit.

**Auflösen nach:**
- Hilt ≥2.58 mit bestätigtem AGP-9-Support
- **Ein** Milestone: Hilt-Upgrade (2.58+) + Flags-Entfernung + DSL-Migration (`android{}`→`ApplicationExtension`, `kotlinOptions`→`compilerOptions`)
