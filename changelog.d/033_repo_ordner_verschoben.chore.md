## Repo eine Ebene tiefer (Workspace/Repo-Split)

**Problem:**
Claude-Arbeitsdateien (CLAUDE.md, .claude/, Handover, Pläne) lagen im Git-Arbeitsverzeichnis und vermischten sich mit Code. Checkouts über gitignorierte Dateien haben lokale Kopien überschrieben bzw. gelöscht.

**Lösung:**
- Das Repo liegt jetzt unter `hr_tracker_app/repo/`. Der Ordner darüber ist ein nicht versionierter Claude-Workspace.
- `includeBuild` für `shared-android-lib` um eine Ebene angepasst (`../../../shared-android-lib`).

**Dateien:**
- `code/settings.gradle.kts`
