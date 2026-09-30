## CI: Lint und Prüfung auf hartkodierte UI-Texte, gepinnte Lib-Version

**Problem:**
Neue Texte konnten wieder hartkodiert im Code landen oder in `values-de` fehlen, ohne dass CI es merkt. Außerdem baute CI immer gegen den aktuellen `master` der Shared Library – beim gleichzeitigen Mergen von Lib und App entstand ein Race.

**Lösung:**
- CI führt `:app:lintDebug` aus (u. a. `MissingTranslation`, Plurals, Format-Argumente).
- CI prüft Compose-Code mit `tools/check_hardcoded_strings.py` aus der Shared Library auf hartkodierte UI-Texte. Bewusste Literale (Einheiten, Akronyme) sind mit `// i18n-ignore` markiert.
- Die Lib-Version für CI ist in `code/shared-app-lib.ref` gepinnt (Tag, aktuell `v0.3.1`); ein gleichnamiger Lib-Branch hat Vorrang.

**Betroffene Dateien:**
- `.github/workflows/android.yml`, `code/shared-app-lib.ref`
- `// i18n-ignore` in `TrendCards.kt`, `OnboardingScreen.kt`, `SettingsScreen.kt`, `TrainingUi.kt`
