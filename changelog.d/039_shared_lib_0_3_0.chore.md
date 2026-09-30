## Migration auf shared-app-lib 0.3.0 (gemeinsames Theme, Auswahl/Filter/Papierkorb)

**Problem:**
hr-tracker hatte ein eigenes `HRTrackerTheme` samt Typografie und Font-Zertifikaten sowie handgestrickte Auswahl- und Label-Filter-Logik im Verlauf, die in jump-tracker parallel gepflegt wurde. Der Pfad zur Lib war fest in `settings.gradle.kts` verdrahtet (`../../../shared-android-lib`).

**Lösung:**
- Theme: `HRTrackerTheme`, `HrTrackerTypography` und der Farb-Re-Export entfallen; die App nutzt `AppTheme`, `AppTypography`, `SpaceGroteskFamily` und die Farben aus `com.kevin.shared.ui.theme`. Die Google-Font-Zertifikate liegen jetzt in der Lib (`font_certs.xml` gelöscht).
- Verlauf: Mehrfachauswahl über `Selection<Long>`, Label-Filter über `LabelFilter` (beide `com.kevin.shared.domain`). Die Auswahl-Kopfzeile ist `SelectionHeader` aus der Lib (inkl. BackHandler), der Filter nutzt `CategoryFilterRow(categories, filter, onToggle)`.
- Papierkorb: `TrashTab` und `SoftDeleteConfirmationDialog` erhalten `TrashRetention.ON_NEXT_APP_START`, passend zum Purge beim App-Start in `SessionRepository`.
- Lib-Pfad konfigurierbar: `sharedLibPath` in `code/local.properties`, Default `../../shared-app-lib`.

**Betroffene Dateien:**
- `code/settings.gradle.kts`
- `MainActivity.kt` (`AppTheme`)
- `ui/theme/Theme.kt`, `ui/theme/Type.kt`, `ui/theme/Color.kt` (gelöscht), `res/values/font_certs.xml` (gelöscht)
- `ui/history/HistoryViewModel.kt`, `ui/history/HistoryScreen.kt`
- Theme-Imports in `ui/detail`, `ui/history`, `ui/live`, `ui/onboarding`, `ui/pip`, `ui/scan`, `ui/settings`, `ui/shared`, `ui/tutorial`
- `domain/HrZoneCalculator.kt` (ungenutzter Import `validateZoneTexts` entfernt)
