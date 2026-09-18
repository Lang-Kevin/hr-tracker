# Kalorienverbrauch + Report-Stats Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nach Session-Ende öffnet sich automatisch der Detail-Screen und zeigt dort einen geschätzten Kalorienverbrauch neben allen vorhandenen HF-Statistiken.

**Architecture:** Kalorien werden **on-read** berechnet (wie HRR60), nicht persistiert — keine Room-Migration. Neues Domain-Objekt `CalorieCalculator` (Keytel-Formel) konsumiert avgBpm + Dauer aus `DetailViewModel.stats` und Gewicht/Geschlecht/Alter aus `UserSettings`. Der Detail-Screen bleibt die einzige Report-View; Session-Ende navigiert dorthin statt `popBackStack()`.

**Tech Stack:** Kotlin, Jetpack Compose (M3), Hilt, Room, DataStore Preferences, JUnit4.

**Spec:** `docs/SPEC.md` (Abschnitte *Analytics (DetailScreen)*, *Erholung / Heart Rate Recovery*, *Screens*) — wird in Task 5 erweitert.

---

## Context

Warum: Die App sammelt HF-Daten, wertet sie aber nicht energetisch aus. Nach `onStopSession` landet der Nutzer auf dem Scan-Screen (`code/app/src/main/kotlin/com/kevin/hrtracker/MainActivity.kt:172-176`) — das Ergebnis der gerade beendeten Einheit ist nur über Verlauf → Detail erreichbar. Zwei Lücken:

1. **Kein Kalorienwert.** Grep über `app/src/main` liefert null Treffer für `calorie|kcal|energy`. `UserSettings` kennt weder Gewicht noch Geschlecht — beides Pflichteingaben für jede HF-basierte Formel.
2. **Report-View unvollständig / nicht auffindbar.** `DetailScreen` zeigt bereits Ø/Max/Min BPM, Dauer, RMSSD, TRIMP, Zeit-in-Zone, Meilensteine. Es fehlen: Kalorien, die Zonen-Basis (`maxHrUsed` / `restingHr` der Session), und die HRR-Card verschwindet **komplett**, wenn die Peak-Kriterien nicht erfüllt sind — der Nutzer sieht nicht, dass die Kennzahl existiert.

Ergebnis nach diesem Plan: Session stoppen → Detail-Screen öffnet sich → alle Kennzahlen inkl. Kalorien auf einen Blick.

## Global Constraints

- Kleinste mögliche Änderung. Keine großflächigen Refactorings (CLAUDE.md).
- **Keine Room-Migration.** DB bleibt auf Version 5. Kalorien werden nicht persistiert.
- Bestehende Hard Rules unangetastet: Foreground Service, CCCD, RR-Persistenz, BPM-Clamp 30–220, Device-Adress-Guard, Migration 1→2.
- MVVM strikt: keine Business-Logik in Composables. Formel lebt in `domain/`.
- UI-Strings: Deutsch (konsistent mit bestehendem Screen: „BPM Ø", „DAUER", „MAX BPM").
- Wiederverwenden statt neu bauen: `com.kevin.shared.ui.StatItem`, `NumberField` (`SettingsScreen.kt:450`), `SegmentedButton`-Pattern (`SettingsScreen.kt:188-200`), `formatDuration` (`shared/.../ui/Format.kt:4`).
- Pro Task: `@reviewer` auf den Diff, dann ein Commit (Conventional Commits, `caveman-commit`-Format). Ein Commit pro Task, keine Sammelcommits.
- Nächste freie changelog.d-Nummer: **027** (aktuell nur `026_pip_live_state_reset.bugfix.md`).

## Design-Entscheidungen (vom Product Owner freigegeben)

| Frage | Entscheidung |
| --- | --- |
| Formel | **Keytel** (HF-basiert) — nutzt die Daten, die die App tatsächlich sammelt |
| Speicherung | **On-read**, keine Spalte, keine Migration. Alte Sessions bekommen rückwirkend einen Wert |
| Profil-Eingabe | **Nur Settings** (kein neuer Onboarding-Schritt). Ohne Gewicht/Geschlecht zeigt die Kachel „—" + Hinweis |
| Report-View | **Bestehenden DetailScreen erweitern**, kein neuer Summary-Screen |
| Session-Ende | **Auto-Navigation** zu `Route.detail(id)` statt `popBackStack()` |
| Zusatz-Stats | Kalorien, HRmax/Ruhepuls der Session, HRR-Card immer sichtbar |

## File Structure

| Datei | Verantwortung |
| --- | --- |
| `code/app/src/main/kotlin/com/kevin/hrtracker/domain/CalorieCalculator.kt` | **Neu.** `Sex`-Enum + reine Keytel-Berechnung. Kennt kein Android. |
| `code/app/src/test/kotlin/com/kevin/hrtracker/domain/CalorieCalculatorTest.kt` | **Neu.** Formel-Verifikation inkl. Clamp- und Null-Fälle. |
| `.../domain/UserSettings.kt` | + `weightKg: Int?`, `sex: Sex?` |
| `.../data/repository/SettingsRepository.kt` | + DataStore-Keys `WEIGHT_KG`, `SEX` + Setter |
| `.../ui/settings/SettingsViewModel.kt` | + `setWeightKg`, `setSex` (Pattern von `setRestingHr`) |
| `.../ui/settings/SettingsScreen.kt` | + Abschnitt „Körperdaten" (NumberField Gewicht, SegmentedButton Geschlecht) |
| `.../ui/detail/DetailViewModel.kt` | + `calories: StateFlow<Int?>` (combine session/stats/settings) |
| `.../ui/detail/DetailScreen.kt` | + Kalorien-Kachel, + HRmax/Ruhepuls-Kachel, HRR-Card immer sichtbar |
| `.../data/repository/SessionRepository.kt` | `stopSession()` gibt beendete `id` zurück |
| `.../ui/scan/ScanViewModel.kt` | `stopSession(onStopped: (Long?) -> Unit)` |
| `.../MainActivity.kt` | `onStopSession` navigiert zu `Route.detail(id)` |
| `docs/SPEC.md`, `changelog.d/027_*.feature.md` | Doku |

---

### Task 0: Plan in das Repo kopieren

Konvention (`~/.claude/CONVENTIONS.md` → Plan & Handover Storage): die verbindliche Plankopie liegt im Repo, nicht unter `~/.claude/plans/`.

- [ ] **Step 1:** Diesen Plan nach `docs/superpowers/plans/2026-09-17-calories-and-report-stats.md` kopieren (Verzeichnis existiert bereits).
- [ ] **Step 2:** Ab hier ist die Repo-Kopie die einzige Quelle; Checkboxen dort abhaken.

---

### Task 1: CalorieCalculator (reine Domain-Logik, TDD)

**Files:**
- Create: `code/app/src/main/kotlin/com/kevin/hrtracker/domain/CalorieCalculator.kt`
- Test: `code/app/src/test/kotlin/com/kevin/hrtracker/domain/CalorieCalculatorTest.kt`

**Interfaces:**
- Consumes: nichts.
- Produces: `enum class Sex { MALE, FEMALE }` und
  `CalorieCalculator.estimateKcal(avgBpm: Int, durationMs: Long, weightKg: Int?, age: Int, sex: Sex?): Int?`
  → `null`, wenn `weightKg == null || sex == null || durationMs <= 0 || avgBpm <= 0`. Sonst ein auf `>= 0` geklemmter Int.

- [ ] **Step 1: Failing Test schreiben**

`code/app/src/test/kotlin/com/kevin/hrtracker/domain/CalorieCalculatorTest.kt`:

```kotlin
package com.kevin.hrtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalorieCalculatorTest {

    @Test
    fun `keytel male reference value`() {
        // HR 150, 80 kg, 30 J, 60 min
        // (-55.0969 + 0.6309*150 + 0.1988*80 + 0.2017*30) / 4.184 = 14.6972 kcal/min
        val kcal = CalorieCalculator.estimateKcal(
            avgBpm = 150, durationMs = 60 * 60_000L,
            weightKg = 80, age = 30, sex = Sex.MALE
        )
        assertEquals(881, kcal)
    }

    @Test
    fun `keytel female reference value`() {
        // HR 140, 60 kg, 25 J, 30 min
        // (-20.4022 + 0.4472*140 - 0.1263*60 + 0.074*25) / 4.184 = 8.7184 kcal/min
        val kcal = CalorieCalculator.estimateKcal(
            avgBpm = 140, durationMs = 30 * 60_000L,
            weightKg = 60, age = 25, sex = Sex.FEMALE
        )
        assertEquals(261, kcal)
    }

    @Test
    fun `negative result is clamped to zero`() {
        // HR 50 ergibt bei Keytel einen negativen kcal-Wert
        val kcal = CalorieCalculator.estimateKcal(
            avgBpm = 50, durationMs = 60 * 60_000L,
            weightKg = 70, age = 30, sex = Sex.MALE
        )
        assertEquals(0, kcal)
    }

    @Test
    fun `null when weight missing`() {
        assertNull(
            CalorieCalculator.estimateKcal(
                avgBpm = 150, durationMs = 60 * 60_000L,
                weightKg = null, age = 30, sex = Sex.MALE
            )
        )
    }

    @Test
    fun `null when sex missing`() {
        assertNull(
            CalorieCalculator.estimateKcal(
                avgBpm = 150, durationMs = 60 * 60_000L,
                weightKg = 80, age = 30, sex = null
            )
        )
    }

    @Test
    fun `null when duration not positive`() {
        assertNull(
            CalorieCalculator.estimateKcal(
                avgBpm = 150, durationMs = 0L,
                weightKg = 80, age = 30, sex = Sex.MALE
            )
        )
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

```bash
./gradlew :app:testDebugUnitTest --tests "*CalorieCalculatorTest*"
```

Erwartet: Compile-Fehler — `CalorieCalculator`/`Sex` unresolved.

- [ ] **Step 3: Minimale Implementierung**

`code/app/src/main/kotlin/com/kevin/hrtracker/domain/CalorieCalculator.kt`:

```kotlin
package com.kevin.hrtracker.domain

enum class Sex { MALE, FEMALE }

/**
 * Keytel et al. (2005): Schätzt den Energieverbrauch aus der mittleren Herzfrequenz.
 * Liefert kJ/min, daher /4.184 für kcal/min.
 */
object CalorieCalculator {

    // ponytail: Keytel nutzt nur die Durchschnitts-HF, nicht den Sample-Verlauf.
    // Sample-weise Integration lohnt erst, wenn Intervall-Sessions sichtbar danebenliegen.
    fun estimateKcal(
        avgBpm: Int,
        durationMs: Long,
        weightKg: Int?,
        age: Int,
        sex: Sex?
    ): Int? {
        if (weightKg == null || sex == null) return null
        if (durationMs <= 0L || avgBpm <= 0) return null

        val hr = avgBpm.toDouble()
        val kg = weightKg.toDouble()
        val yrs = age.toDouble()

        val kJPerMin = when (sex) {
            Sex.MALE   -> -55.0969 + 0.6309 * hr + 0.1988 * kg + 0.2017 * yrs
            Sex.FEMALE -> -20.4022 + 0.4472 * hr - 0.1263 * kg + 0.0740 * yrs
        }

        val minutes = durationMs / 60_000.0
        return (kJPerMin / 4.184 * minutes).toInt().coerceAtLeast(0)
    }
}
```

- [ ] **Step 4: Test laufen lassen, grün bestätigen**

```bash
./gradlew :app:testDebugUnitTest --tests "*CalorieCalculatorTest*"
```

Erwartet: 6 Tests, alle PASS. Rohe Ausgabe als Nachweis liefern.

- [ ] **Step 5: `@reviewer` auf den Diff**

- [ ] **Step 6: Commit**

```bash
git add code/app/src/main/kotlin/com/kevin/hrtracker/domain/CalorieCalculator.kt \
        code/app/src/test/kotlin/com/kevin/hrtracker/domain/CalorieCalculatorTest.kt
git commit -m "feat(domain): keytel kalorienschaetzung"
```

---

### Task 2: Körperdaten in Settings (Gewicht + Geschlecht)

**Files:**
- Modify: `code/app/src/main/kotlin/com/kevin/hrtracker/domain/UserSettings.kt`
- Modify: `code/app/src/main/kotlin/com/kevin/hrtracker/data/repository/SettingsRepository.kt`
- Modify: `code/app/src/main/kotlin/com/kevin/hrtracker/ui/settings/SettingsViewModel.kt`
- Modify: `code/app/src/main/kotlin/com/kevin/hrtracker/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `Sex` aus Task 1.
- Produces: `UserSettings.weightKg: Int?`, `UserSettings.sex: Sex?` — von Task 3 gelesen.

- [ ] **Step 1: `UserSettings` erweitern**

```kotlin
data class UserSettings(
    val age: Int = 30,
    val manualMaxHr: Int? = null,
    val restingHr: Int? = null,
    val targetZone: Int = 2,
    val customZones: List<ZoneBounds>? = null,
    val chartDynamicScale: Boolean = true,
    val widgetVariant: WidgetVariant = WidgetVariant.STANDARD,
    val weightKg: Int? = null,
    val sex: Sex? = null
) { /* maxHrUsed / effectiveZones unverändert */ }
```

- [ ] **Step 2: DataStore-Keys + Mapping + Setter in `SettingsRepository`**

Keys (neben `AGE`, `RESTING_HR` in `private object Keys`):

```kotlin
val WEIGHT_KG = intPreferencesKey("weight_kg")
val SEX       = stringPreferencesKey("sex")
```

Im `userSettings`-Mapping (Pattern von `widgetVariant` übernehmen — defensives `runCatching`):

```kotlin
weightKg = prefs[Keys.WEIGHT_KG],
sex      = prefs[Keys.SEX]?.let { runCatching { Sex.valueOf(it) }.getOrNull() }
```

Setter (Pattern von `setRestingHr`):

```kotlin
suspend fun setWeightKg(weightKg: Int?) {
    dataStore.edit {
        if (weightKg != null) it[Keys.WEIGHT_KG] = weightKg else it.remove(Keys.WEIGHT_KG)
    }
}

suspend fun setSex(sex: Sex?) {
    dataStore.edit {
        if (sex != null) it[Keys.SEX] = sex.name else it.remove(Keys.SEX)
    }
}
```

- [ ] **Step 3: Durchreichen im `SettingsViewModel`**

```kotlin
fun setWeightKg(weightKg: Int?) = viewModelScope.launch {
    settingsRepository.setWeightKg(weightKg)
}

fun setSex(sex: Sex?) = viewModelScope.launch { settingsRepository.setSex(sex) }
```

- [ ] **Step 4: UI-Abschnitt „Körperdaten" in `SettingsScreen.kt`**

Direkt **nach** dem Ruhepuls-`NumberField` (aktuell `SettingsScreen.kt:160-169`), vor `Text("Zonen-Modell: …")`. `weightText` analog zu `ageText`/`restingHrText` als `remember`-State mit `LaunchedEffect(settings.weightKg)`-Sync anlegen (bestehendes Muster im File übernehmen).

```kotlin
HorizontalDivider()
Text(
    "Körperdaten (für Kalorienschätzung)",
    style = MaterialTheme.typography.titleSmall,
    color = PrimaryPurple,
    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
)

NumberField(
    label = "Gewicht in kg (leer = keine Kalorien)",
    value = weightText,
    onValueChange = { weightText = it },
    onDone = { viewModel.setWeightKg(it.toIntOrNull()?.takeIf { v -> v in 30..250 }) },
    isError = weightError,
    supportingText = if (weightError) "Gewicht muss zwischen 30 und 250 kg liegen" else null
)

SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    val options = listOf(Sex.MALE to "Männlich", Sex.FEMALE to "Weiblich")
    options.forEachIndexed { index, (value, label) ->
        SegmentedButton(
            selected = settings.sex == value,
            onClick = { viewModel.setSex(if (settings.sex == value) null else value) },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
        ) {
            Text(label)
        }
    }
}
if (settings.sex == null || settings.weightKg == null) {
    Text(
        "Ohne Gewicht und Geschlecht bleibt die Kalorien-Kachel leer.",
        style = MaterialTheme.typography.bodySmall
    )
}
```

`weightError` analog zu `restingHrError` deklarieren:

```kotlin
val weightError = weightText.isNotBlank() && weightText.toIntOrNull()?.let { it !in 30..250 } != false
```

- [ ] **Step 5: Build + Lint**

```bash
./gradlew assembleDebug lint
```

Erwartet: BUILD SUCCESSFUL, keine neuen Lint-Fehler. Rohe Ausgabe liefern.

- [ ] **Step 6: Manuelle Verifikation**

`./gradlew :app:installDebug` → Einstellungen öffnen → Gewicht 80 eintragen, „Männlich" wählen → App neu starten → Werte sind noch gesetzt (DataStore-Persistenz).

- [ ] **Step 7: `@reviewer` auf den Diff**

- [ ] **Step 8: Commit**

```bash
git add code/app/src/main/kotlin/com/kevin/hrtracker/domain/UserSettings.kt \
        code/app/src/main/kotlin/com/kevin/hrtracker/data/repository/SettingsRepository.kt \
        code/app/src/main/kotlin/com/kevin/hrtracker/ui/settings/SettingsViewModel.kt \
        code/app/src/main/kotlin/com/kevin/hrtracker/ui/settings/SettingsScreen.kt
git commit -m "feat(settings): gewicht und geschlecht fuer kalorien"
```

> ⚠️ `SettingsScreen.kt` und `SessionRepository.kt` haben bereits uncommittete Änderungen im Working Tree. Vor Task-Start `git status`/`git diff` prüfen und nur die eigenen Hunks stagen.

---

### Task 3: Detail-Screen — Kalorien, Zonen-Basis, HRR immer sichtbar

**Files:**
- Modify: `code/app/src/main/kotlin/com/kevin/hrtracker/ui/detail/DetailViewModel.kt`
- Modify: `code/app/src/main/kotlin/com/kevin/hrtracker/ui/detail/DetailScreen.kt`

**Interfaces:**
- Consumes: `CalorieCalculator.estimateKcal(...)` (Task 1), `UserSettings.weightKg` / `.sex` (Task 2), vorhandene `stats: StateFlow<Stats?>`, `session: StateFlow<Session?>`, `recovery: StateFlow<HrrResult?>`.
- Produces: `DetailViewModel.calories: StateFlow<Int?>` — von Task 4 nicht benötigt, aber Teil der öffentlichen VM-Fläche.

- [ ] **Step 1: `calories`-Flow im `DetailViewModel`**

Direkt nach dem `trimp`-Flow (`DetailViewModel.kt:90-104`) einfügen — gleiche `combine`-Form, plus `settingsRepository.userSettings`:

```kotlin
val calories: StateFlow<Int?> = combine(
    session, stats, settingsRepository.userSettings
) { sess, st, settings ->
    if (sess == null || st == null || sess.endedAt == null) null
    else CalorieCalculator.estimateKcal(
        avgBpm = st.avgBpm,
        durationMs = sess.endedAt - sess.startedAt,
        weightKg = settings.weightKg,
        age = settings.age,
        sex = settings.sex
    )
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
```

Import ergänzen: `com.kevin.hrtracker.domain.CalorieCalculator`.

> Hinweis: `settingsRepository` ist bereits im Konstruktor (`DetailViewModel.kt:33`) — keine DI-Änderung nötig. `age` kommt bewusst aus den aktuellen Settings, nicht aus dem Session-Snapshot; die Session speichert nur `maxHrUsed`/`restingHr`.

- [ ] **Step 2: Kacheln im `DetailScreen` ergänzen**

`calories` per `collectAsStateWithLifecycle()` einsammeln (Muster der übrigen Flows im File). Die bestehende „Analytics Row" (`DetailScreen.kt:326-331`) bleibt unverändert; **darunter** eine dritte Zeile einfügen:

```kotlin
Spacer(Modifier.height(8.dp))

// Kalorien + Zonen-Basis
Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    StatItem(
        "KALORIEN",
        calories?.let { "$it kcal" } ?: "—",
        Modifier.weight(1f),
        valueColor = PrimaryPurple
    )
    StatItem("HRMAX", session?.maxHrUsed?.toString() ?: "—", Modifier.weight(1f))
    StatItem("RUHEPULS", session?.restingHr?.toString() ?: "—", Modifier.weight(1f))
}

if (calories == null && session?.endedAt != null) {
    Text(
        "Kalorien: Gewicht und Geschlecht in den Einstellungen hinterlegen.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}
```

- [ ] **Step 3: HRR-Card immer rendern**

`DetailScreen.kt:335` ff. nutzt `recovery?.let { hrr -> … }` — die Card verschwindet komplett, wenn `computeHrRecovery` `null` liefert. Das `?.let` **nicht** entfernen, sondern einen `else`-Zweig ergänzen:

```kotlin
if (recovery != null) {
    // … bestehender Card-Block unverändert, `hrr` = recovery
} else {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "ERHOLUNG (HRR60)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("—", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(
                "Zu wenig Daten oder kein Peak ≥ 70 % HRmax mit 60 s Nachlauf.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

Die `Card`-Parameter (Farben, Shape) aus dem bestehenden HRR-Block übernehmen, damit beide Zustände identisch aussehen.

> `HrRecovery.computeHrRecovery` bleibt unverändert — der Grund-Text deckt beide Null-Ursachen (`samples.size < 2`, kein qualifizierter Peak) bewusst gemeinsam ab. Ein strukturiertes Result-Objekt lohnt hier nicht.

- [ ] **Step 4: Build + Tests**

```bash
./gradlew assembleDebug test lint
```

Erwartet: BUILD SUCCESSFUL, alle Unit-Tests grün (inkl. `RmssdOrderingTest`, `HrRecoveryTest`). Rohe Ausgabe liefern.

- [ ] **Step 5: Manuelle Verifikation**

`./gradlew :app:installDebug` → Verlauf → bestehende Session öffnen.
- Mit gesetztem Gewicht/Geschlecht: KALORIEN zeigt einen Wert > 0.
- Gewicht in Settings löschen → zurück zum Detail → Kachel „—" + Hinweistext.
- Eine Session ohne HRR-Peak öffnen → Erholungs-Card ist sichtbar mit „—".

- [ ] **Step 6: `@reviewer` auf den Diff**

- [ ] **Step 7: Commit**

```bash
git add code/app/src/main/kotlin/com/kevin/hrtracker/ui/detail/DetailViewModel.kt \
        code/app/src/main/kotlin/com/kevin/hrtracker/ui/detail/DetailScreen.kt
git commit -m "feat(detail): kalorien, zonen-basis, hrr immer sichtbar"
```

---

### Task 4: Nach Session-Ende direkt in den Report

**Files:**
- Modify: `code/app/src/main/kotlin/com/kevin/hrtracker/data/repository/SessionRepository.kt:133-144`
- Modify: `code/app/src/main/kotlin/com/kevin/hrtracker/ui/scan/ScanViewModel.kt:146`
- Modify: `code/app/src/main/kotlin/com/kevin/hrtracker/MainActivity.kt:171-177`

**Interfaces:**
- Consumes: `Route.detail(id: Long)` (bereits vorhanden, genutzt in `MainActivity.kt:186`).
- Produces: `SessionRepository.stopSession(): Long?` (beendete Session-ID oder `null`), `ScanViewModel.stopSession(onStopped: (Long?) -> Unit)`.

- [ ] **Step 1: `stopSession` gibt die ID zurück**

`SessionRepository.kt:133`:

```kotlin
suspend fun stopSession(): Long? {
    val id = _activeSessionId.value ?: return null
    sampleJob?.cancel()
    sampleJob = null
    _activeSessionId.value = null
    _isPaused.value = false
    _pausedByConnectionLoss.value = false
    activeHrFlow = null
    db.sessionDao().closeSession(id, System.currentTimeMillis())
    Log.d("HRTracker", "Session $id stopped")
    return id
}
```

Rest des Rumpfs unverändert — nur Rückgabetyp und die beiden `return`-Punkte.

- [ ] **Step 2: Callback im `ScanViewModel`**

`ScanViewModel.kt:146` ersetzen:

```kotlin
fun stopSession(onStopped: (Long?) -> Unit = {}) = viewModelScope.launch {
    onStopped(sessionRepository.stopSession())
}
```

Default-Argument behält alle bestehenden Aufrufer kompilierbar.

- [ ] **Step 3: Navigation in `MainActivity`**

`MainActivity.kt:171-177`:

```kotlin
onStopSession = {
    scanViewModel.stopSession { finishedId ->
        if (finishedId != null) {
            navController.navigate(Route.detail(finishedId)) {
                popUpTo(Route.SCAN)
                launchSingleTop = true
            }
        } else {
            navController.popBackStack()
        }
    }
    stopService(HrRecordingService.stopIntent(this@MainActivity))
},
```

`onAbortSession` bleibt unverändert (Verwerfen → `popBackStack()`).

> `viewModelScope` läuft auf `Dispatchers.Main.immediate`, der Callback ist also navigationssicher. `popUpTo(Route.SCAN)` nimmt den Live-Screen vom Stack, sodass Zurück aus dem Report auf dem Scan-Screen landet und nicht in der beendeten Session.

- [ ] **Step 4: Build + Tests**

```bash
./gradlew assembleDebug test lint
```

Erwartet: BUILD SUCCESSFUL. Rohe Ausgabe liefern.

- [ ] **Step 5: Manuelle Verifikation (End-to-End)**

`./gradlew :app:installDebug`:
1. Gurt verbinden, Session starten, ~1 min messen.
2. Stop → **Speichern** → Detail-Screen der gerade beendeten Session öffnet sich, KALORIEN gefüllt.
3. Zurück-Geste → Scan-Screen (nicht Live-Screen).
4. Stop → **Verwerfen** → Scan-Screen, kein Report.
5. HRV-Messung (30 s) → Auto-Stop bei 0 → Detail-Screen mit RMSSD.

- [ ] **Step 6: `@reviewer` auf den Diff**

- [ ] **Step 7: Commit**

```bash
git add code/app/src/main/kotlin/com/kevin/hrtracker/data/repository/SessionRepository.kt \
        code/app/src/main/kotlin/com/kevin/hrtracker/ui/scan/ScanViewModel.kt \
        code/app/src/main/kotlin/com/kevin/hrtracker/MainActivity.kt
git commit -m "feat(nav): session-ende oeffnet report direkt"
```

---

### Task 5: Doku + QA-Gate

**Files:**
- Create: `changelog.d/027_kalorien_und_report_stats.feature.md`
- Modify: `docs/SPEC.md`

- [ ] **Step 1: changelog.d-Eintrag** (Format aus `changelog.d/026_pip_live_state_reset.bugfix.md`: `## Titel`, `**Problem:**`, `**Lösung:**`, `**Betroffene Dateien:**`)

- [ ] **Step 2: `docs/SPEC.md` erweitern**
- *Datenmodell* → `UserSettings` um `weightKg`, `sex` ergänzen; explizit festhalten: **DB bleibt Version 5**, Kalorien werden nicht persistiert.
- *Analytics (DetailScreen)* → neuer Punkt **Kalorien**: Keytel-Formel (beide Varianten ausschreiben), on-read wie HRR60, `null` ohne Gewicht/Geschlecht, Clamp auf `>= 0`.
- *Erholung / HRR* → notieren, dass die Card jetzt immer gerendert wird, mit „—" + Begründung statt Ausblenden.
- *Screens* → Detail-Zeile um Kalorien/HRmax/Ruhepuls ergänzen; Settings-Zeile um Gewicht/Geschlecht; ergänzen, dass Session-Ende (Speichern) direkt in den Detail-Screen navigiert.

- [ ] **Step 3: `@qa-verifier`** — Build + Lint + Test + Abgleich des Gesamt-Diffs gegen `docs/SPEC.md` und die Hard Rules in `CLAUDE.md`. Muss PASS liefern.

- [ ] **Step 4: Commit**

```bash
git add changelog.d/027_kalorien_und_report_stats.feature.md docs/SPEC.md
git commit -m "docs: kalorien und report-stats"
```

---

## Verification (gesamt)

| Ebene | Kommando / Schritt | Erwartung |
| --- | --- | --- |
| Unit | `./gradlew :app:testDebugUnitTest --tests "*CalorieCalculatorTest*"` | 6/6 PASS |
| Unit (Regression) | `./gradlew test` | alle bestehenden Tests grün |
| Build | `./gradlew assembleDebug` | BUILD SUCCESSFUL |
| Lint | `./gradlew lint` | keine neuen Findings |
| E2E | `./gradlew :app:installDebug`, Session starten → stoppen → speichern | Report öffnet sich automatisch, KALORIEN gefüllt |
| E2E (Negativ) | Gewicht in Settings leeren, Report erneut öffnen | KALORIEN „—" + Hinweistext, App stürzt nicht ab |
| E2E (HRR) | Session ohne qualifizierten Peak öffnen | Erholungs-Card sichtbar mit „—" + Begründung |
| Rückwirkend | Alte Session aus dem Verlauf öffnen | Kalorien werden berechnet (on-read, keine Migration) |

Nachweispflicht: Jeder Subagent liefert Kommando **plus rohe Ausgabe** zurück, nicht nur eine Zusammenfassung (Acceptance Contract, `~/.claude/CONVENTIONS.md`).

## Bewusst nicht gebaut

- **Keine Kalorien-Persistenz / Migration** — on-read reicht, alte Sessions profitieren rückwirkend. Nachrüsten, sobald das Profil historisiert werden soll (Gewichtsänderung würde alte Reports verschieben).
- **Kein neuer Post-Session-Summary-Screen** — `DetailScreen` ist bereits die Report-View.
- **Keine Sample-weise Kalorien-Integration** — Keytel über Ø-HF. Nachrüsten, wenn Intervall-Sessions sichtbar danebenliegen.
- **Kein Onboarding-Schritt für Körperdaten** — Settings reichen; nachrüsten, wenn Nutzer die Kalorien-Kachel dauerhaft leer lassen.
- **Kein strukturiertes HRR-Failure-Result** — ein Begründungstext deckt beide Null-Ursachen ab.
