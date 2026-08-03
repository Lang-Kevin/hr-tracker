# Widget-Varianten Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der User wählt in den Einstellungen zwischen vier Anzeige-Presets, die gleichzeitig das PiP-Fenster und die Foreground-Service-Notification steuern.

**Architecture:** Ein Enum `WidgetVariant` in `domain/` plus eine reine Textfunktion für die Notification. Die Variante wird wie alle anderen Einstellungen in DataStore über `SettingsRepository` persistiert und fließt in `UserSettings`. `PipViewModel` reicht sie in `PipUiState` durch, `PipContent` verzweigt darauf in vier Layouts, `HrRecordingService` baut seinen Notification-Text darüber.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, DataStore Preferences, Coroutines/Flow, JUnit 4.

## Global Constraints

- Gradle-Root ist `code/`. Alle Gradle-Befehle laufen aus `code/`, z. B. `cd code && ./gradlew :app:testDebugUnitTest`.
- Package-Präfix: `com.kevin.hrtracker`. Quellen unter `code/app/src/main/kotlin/...`, Tests unter `code/app/src/test/kotlin/...`.
- Strikt MVVM: keine Business-Logik in Composables.
- Hard Rules aus `CLAUDE.md` bleiben unangetastet: CCCD-Aktivierung, Device-Adress-Guard, `notifyWatch()` nur im WATCH-Modus, RR-Persistenz, Room-Migration v1→v2, `fgsType = FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`.
- Keine Room-Migration in diesem Plan — die Einstellung liegt ausschließlich in DataStore.
- Default der neuen Einstellung ist `WidgetVariant.STANDARD`, damit bestehende Installationen ihr heutiges Verhalten behalten.
- Trennzeichen im Notification-Text ist exakt `"  •  "` (zwei Leerzeichen, Bullet, zwei Leerzeichen) — so wie heute in `HrRecordingService.kt:56`.
- Testnamen auf Deutsch in Backticks, wie in `PipUiStateTest.kt`.
- Commit-Format: Conventional Commits, Subject klein geschrieben, ≤ 50 Zeichen.
- Compose-Composables werden nicht getestet, wie im Projekt üblich.

---

### Task 1: `WidgetVariant` + Notification-Textfunktion

**Files:**
- Create: `code/app/src/main/kotlin/com/kevin/hrtracker/domain/WidgetVariant.kt`
- Test: `code/app/src/test/kotlin/com/kevin/hrtracker/domain/WidgetVariantTest.kt`

**Interfaces:**
- Consumes: nichts.
- Produces:
  - `enum class WidgetVariant { MINIMAL, STANDARD, ZONE, TIMER }`
  - `fun widgetNotificationText(variant: WidgetVariant, bpm: Int?, zone: Int?, elapsed: String): String`

- [ ] **Step 1: Write the failing test**

Datei `code/app/src/test/kotlin/com/kevin/hrtracker/domain/WidgetVariantTest.kt`:

```kotlin
package com.kevin.hrtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetVariantTest {

    @Test
    fun `minimal zeigt nur bpm`() {
        assertEquals(
            "142 BPM",
            widgetNotificationText(WidgetVariant.MINIMAL, 142, 3, "00:12:04")
        )
    }

    @Test
    fun `standard zeigt bpm zone und dauer`() {
        assertEquals(
            "142 BPM  •  Zone 3  •  00:12:04",
            widgetNotificationText(WidgetVariant.STANDARD, 142, 3, "00:12:04")
        )
    }

    @Test
    fun `zone stellt die zone voran`() {
        assertEquals(
            "Zone 3  •  142 BPM",
            widgetNotificationText(WidgetVariant.ZONE, 142, 3, "00:12:04")
        )
    }

    @Test
    fun `timer stellt die dauer voran`() {
        assertEquals(
            "00:12:04  •  142 BPM",
            widgetNotificationText(WidgetVariant.TIMER, 142, 3, "00:12:04")
        )
    }

    @Test
    fun `ohne bpm steht ein platzhalter`() {
        assertEquals(
            "-- BPM",
            widgetNotificationText(WidgetVariant.MINIMAL, null, null, "00:00:00")
        )
    }

    @Test
    fun `ohne zone entfaellt das zonen-segment`() {
        assertEquals(
            "142 BPM  •  00:12:04",
            widgetNotificationText(WidgetVariant.STANDARD, 142, null, "00:12:04")
        )
        assertEquals(
            "142 BPM",
            widgetNotificationText(WidgetVariant.ZONE, 142, null, "00:12:04")
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd code && ./gradlew :app:testDebugUnitTest --tests "com.kevin.hrtracker.domain.WidgetVariantTest"`
Expected: Kompilierfehler — `WidgetVariant` und `widgetNotificationText` existieren nicht.

- [ ] **Step 3: Write minimal implementation**

Datei `code/app/src/main/kotlin/com/kevin/hrtracker/domain/WidgetVariant.kt`:

```kotlin
package com.kevin.hrtracker.domain

/**
 * Anzeige-Preset für PiP-Fenster und Foreground-Notification.
 * MINIMAL = nur BPM, STANDARD = heutiges Verhalten, ZONE = Zone dominant, TIMER = Dauer dominant.
 */
enum class WidgetVariant { MINIMAL, STANDARD, ZONE, TIMER }

private const val SEPARATOR = "  •  "

/**
 * Baut den Notification-Text zur gewählten Variante.
 * Rein und Android-frei, damit unit-testbar.
 */
fun widgetNotificationText(
    variant: WidgetVariant,
    bpm: Int?,
    zone: Int?,
    elapsed: String
): String {
    val bpmText = "${bpm ?: "--"} BPM"
    val zoneText = zone?.let { "Zone $it" }
    val parts = when (variant) {
        WidgetVariant.MINIMAL -> listOf(bpmText)
        WidgetVariant.STANDARD -> listOfNotNull(bpmText, zoneText, elapsed)
        WidgetVariant.ZONE -> listOfNotNull(zoneText, bpmText)
        WidgetVariant.TIMER -> listOf(elapsed, bpmText)
    }
    return parts.joinToString(SEPARATOR)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd code && ./gradlew :app:testDebugUnitTest --tests "com.kevin.hrtracker.domain.WidgetVariantTest"`
Expected: PASS, 6 Tests.

- [ ] **Step 5: Commit**

```bash
git add code/app/src/main/kotlin/com/kevin/hrtracker/domain/WidgetVariant.kt code/app/src/test/kotlin/com/kevin/hrtracker/domain/WidgetVariantTest.kt
git commit -m "feat(widget): WidgetVariant enum + notification-text"
```

---

### Task 2: Persistenz der Variante

**Files:**
- Modify: `code/app/src/main/kotlin/com/kevin/hrtracker/domain/UserSettings.kt`
- Modify: `code/app/src/main/kotlin/com/kevin/hrtracker/data/repository/SettingsRepository.kt`

**Interfaces:**
- Consumes: `WidgetVariant` aus Task 1.
- Produces:
  - `UserSettings.widgetVariant: WidgetVariant` (Default `WidgetVariant.STANDARD`)
  - `suspend fun SettingsRepository.setWidgetVariant(variant: WidgetVariant)`

- [ ] **Step 1: Feld in `UserSettings` ergänzen**

In `UserSettings.kt` den Import ergänzen ist nicht nötig — `WidgetVariant` liegt im selben Package `com.kevin.hrtracker.domain`. Das Feld hinter `chartDynamicScale` einfügen:

```kotlin
data class UserSettings(
    val age: Int = 30,
    val manualMaxHr: Int? = null,
    val restingHr: Int? = null,
    val targetZone: Int = 2,
    val hrSource: HrSource = HrSource.BLE,
    val customZones: List<ZoneBounds>? = null,
    val chartDynamicScale: Boolean = true,
    val widgetVariant: WidgetVariant = WidgetVariant.STANDARD
) {
```

Der bestehende Body (`maxHrUsed`, `effectiveZones`) bleibt unverändert.

- [ ] **Step 2: DataStore-Key und Mapping ergänzen**

In `SettingsRepository.kt` den Import ergänzen:

```kotlin
import com.kevin.hrtracker.domain.WidgetVariant
```

Im `Keys`-Objekt hinter `CHART_DYNAMIC_SCALE` einfügen:

```kotlin
        val WIDGET_VARIANT   = stringPreferencesKey("widget_variant")
```

Im `userSettings`-Mapping hinter `chartDynamicScale = ...` einfügen (Komma nach der Vorzeile nicht vergessen):

```kotlin
            widgetVariant = prefs[Keys.WIDGET_VARIANT]?.let {
                runCatching { WidgetVariant.valueOf(it) }.getOrDefault(WidgetVariant.STANDARD)
            } ?: WidgetVariant.STANDARD
```

Das entspricht exakt dem Muster von `hrSource` (`SettingsRepository.kt:47-49`) — ein unbekannter oder umbenannter Enum-Wert fällt still auf den Default zurück, statt die App beim Start zu werfen.

- [ ] **Step 3: Setter ergänzen**

Ans Ende der Klasse, hinter `setChartDynamicScale`:

```kotlin
    suspend fun setWidgetVariant(variant: WidgetVariant) {
        dataStore.edit { it[Keys.WIDGET_VARIANT] = variant.name }
    }
```

- [ ] **Step 4: Kompilieren und bestehende Tests laufen lassen**

Run: `cd code && ./gradlew :app:testDebugUnitTest`
Expected: PASS. `UserSettings` hat nur Parameter mit Defaults dazubekommen, bestehende Aufrufe bleiben gültig.

- [ ] **Step 5: Commit**

```bash
git add code/app/src/main/kotlin/com/kevin/hrtracker/domain/UserSettings.kt code/app/src/main/kotlin/com/kevin/hrtracker/data/repository/SettingsRepository.kt
git commit -m "feat(widget): variante in usersettings + datastore"
```

---

### Task 3: `PipUiState` und `PipViewModel` durchreichen

**Files:**
- Modify: `code/app/src/main/kotlin/com/kevin/hrtracker/ui/pip/PipUiState.kt`
- Modify: `code/app/src/main/kotlin/com/kevin/hrtracker/ui/pip/PipViewModel.kt`
- Test: `code/app/src/test/kotlin/com/kevin/hrtracker/ui/pip/PipUiStateTest.kt`

**Interfaces:**
- Consumes: `WidgetVariant` (Task 1), `UserSettings.widgetVariant` (Task 2).
- Produces: `PipUiState.variant: WidgetVariant`, und `buildPipUiState(bpm, zones, startedAtMs, nowMs, paused, variant = WidgetVariant.STANDARD)`.

Der neue Parameter steht **am Ende und hat einen Default** — dadurch bleiben die fünf bestehenden positionalen Aufrufe in `PipUiStateTest.kt` unverändert gültig.

- [ ] **Step 1: Write the failing test**

In `PipUiStateTest.kt` den Import ergänzen:

```kotlin
import com.kevin.hrtracker.domain.WidgetVariant
```

Und diese zwei Tests am Ende der Klasse anfügen:

```kotlin
    @Test
    fun `variante wird durchgereicht`() {
        val state = buildPipUiState(
            bpm = 142,
            zones = zones,
            startedAtMs = 1_000_000L,
            nowMs = 1_060_000L,
            paused = false,
            variant = WidgetVariant.ZONE
        )
        assertEquals(WidgetVariant.ZONE, state.variant)
    }

    @Test
    fun `ohne angabe ist die variante standard`() {
        val state = buildPipUiState(142, zones, 1_000_000L, 1_060_000L, false)
        assertEquals(WidgetVariant.STANDARD, state.variant)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd code && ./gradlew :app:testDebugUnitTest --tests "com.kevin.hrtracker.ui.pip.PipUiStateTest"`
Expected: Kompilierfehler — `variant` ist kein Parameter von `buildPipUiState` und kein Feld von `PipUiState`.

- [ ] **Step 3: `PipUiState.kt` erweitern**

```kotlin
package com.kevin.hrtracker.ui.pip

import com.kevin.hrtracker.domain.HrZoneCalculator
import com.kevin.hrtracker.domain.WidgetVariant
import com.kevin.hrtracker.domain.ZoneBounds
import com.kevin.hrtracker.ui.formatDuration

data class PipUiState(
    val bpm: Int?,
    val zone: Int?,
    val elapsedText: String,
    val paused: Boolean,
    val variant: WidgetVariant
)

// ponytail: Dauer = Wanduhr seit startedAt, Pausen werden NICHT abgezogen —
// die Pausen-Akkumulation lebt heute nur in LiveViewModel (pausedAccumMs).
// Im PiP steht bei Pause "PAUSE" statt der Zeit, damit die Abweichung nicht auffaellt.
// Upgrade-Pfad: pausedAccumMs nach SessionRepository hochziehen, dann hier abziehen.
fun buildPipUiState(
    bpm: Int?,
    zones: List<ZoneBounds>,
    startedAtMs: Long?,
    nowMs: Long,
    paused: Boolean,
    variant: WidgetVariant = WidgetVariant.STANDARD
): PipUiState {
    val elapsedSeconds = if (startedAtMs == null) 0L
        else ((nowMs - startedAtMs) / 1000).coerceAtLeast(0L)
    return PipUiState(
        bpm = bpm,
        zone = if (bpm == null || zones.isEmpty()) null else HrZoneCalculator.zoneFor(bpm, zones),
        elapsedText = formatDuration(elapsedSeconds),
        paused = paused,
        variant = variant
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd code && ./gradlew :app:testDebugUnitTest --tests "com.kevin.hrtracker.ui.pip.PipUiStateTest"`
Expected: PASS, 8 Tests.

- [ ] **Step 5: `PipViewModel.kt` verdrahten**

`combine` nimmt typisiert maximal fünf Flows. Statt einen sechsten hinzuzufügen, werden Zonen und Variante in einem kleinen Wert zusammengefasst:

```kotlin
package com.kevin.hrtracker.ui.pip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.hrtracker.ble.HrBleManager
import com.kevin.hrtracker.data.repository.SessionRepository
import com.kevin.hrtracker.data.repository.SettingsRepository
import com.kevin.hrtracker.domain.HrSource
import com.kevin.hrtracker.domain.WidgetVariant
import com.kevin.hrtracker.domain.ZoneBounds
import com.kevin.hrtracker.wearable.WearableHrSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import javax.inject.Inject

private data class PipSettings(
    val zones: List<ZoneBounds>,
    val variant: WidgetVariant
)

@HiltViewModel
class PipViewModel @Inject constructor(
    bleManager: HrBleManager,
    sessionRepository: SessionRepository,
    settingsRepository: SettingsRepository,
    wearableHrSource: WearableHrSource
) : ViewModel() {

    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    private val bpm: Flow<Int?> = settingsRepository.userSettings
        .flatMapLatest { s ->
            if (s.hrSource == HrSource.WATCH) wearableHrSource.lastHr else bleManager.lastHr
        }
        .map { it?.bpm }

    private val pipSettings: Flow<PipSettings> = settingsRepository.userSettings
        .map { PipSettings(it.effectiveZones, it.widgetVariant) }
        .distinctUntilChanged()

    val uiState: StateFlow<PipUiState> = combine(
        bpm,
        pipSettings,
        sessionRepository.activeSession.map { it?.startedAt },
        sessionRepository.isPaused,
        ticker
    ) { currentBpm, settings, startedAt, paused, now ->
        buildPipUiState(currentBpm, settings.zones, startedAt, now, paused, settings.variant)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PipUiState(
            bpm = null,
            zone = null,
            elapsedText = "00:00:00",
            paused = false,
            variant = WidgetVariant.STANDARD
        )
    )
}
```

- [ ] **Step 6: Build to verify**

Run: `cd code && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add code/app/src/main/kotlin/com/kevin/hrtracker/ui/pip/PipUiState.kt code/app/src/main/kotlin/com/kevin/hrtracker/ui/pip/PipViewModel.kt code/app/src/test/kotlin/com/kevin/hrtracker/ui/pip/PipUiStateTest.kt
git commit -m "feat(widget): variante in pip-uistate + viewmodel"
```

---

### Task 4: Vier PiP-Layouts

**Files:**
- Modify: `code/app/src/main/kotlin/com/kevin/hrtracker/ui/pip/PipContent.kt` (vollständiger Ersatz)

**Interfaces:**
- Consumes: `PipUiState.variant` (Task 3), `WidgetVariant` (Task 1), `ZoneColors` aus `com.kevin.hrtracker.ui.theme`.
- Produces: nichts für spätere Tasks.

Pause-Regel: `PAUSE` ersetzt die Zeile, die sonst die Dauer zeigt — in `STANDARD` die untere Info-Zeile, in `TIMER` die große Zahl. In `MINIMAL` und `ZONE`, die keine Dauer anzeigen, kommt `PAUSE` als kleine Zeile unten dazu. So steht nie eine weiterlaufende Dauer neben einer pausierten Session.

- [ ] **Step 1: `PipContent.kt` ersetzen**

```kotlin
package com.kevin.hrtracker.ui.pip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.hrtracker.domain.WidgetVariant
import com.kevin.hrtracker.ui.theme.ZoneColors

@Composable
fun PipContent(viewModel: PipViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fallback = MaterialTheme.colorScheme.onSurfaceVariant
    val accent: Color = state.zone?.let { ZoneColors.getOrElse(it - 1) { fallback } } ?: fallback

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state.variant) {
            WidgetVariant.MINIMAL -> MinimalLayout(state, accent)
            WidgetVariant.STANDARD -> StandardLayout(state, accent)
            WidgetVariant.ZONE -> ZoneLayout(state, accent)
            WidgetVariant.TIMER -> TimerLayout(state, accent)
        }
    }
}

@Composable
private fun MinimalLayout(state: PipUiState, accent: Color) {
    BpmRow(state.bpm, accent, valueSize = 48.sp, iconSize = 30.dp)
    if (state.paused) PauseLine()
}

@Composable
private fun StandardLayout(state: PipUiState, accent: Color) {
    BpmRow(state.bpm, accent, valueSize = 34.sp, iconSize = 24.dp)
    Text(
        text = state.zone?.let { "Zone $it" } ?: "Zone --",
        fontSize = 14.sp,
        color = accent
    )
    Text(
        text = if (state.paused) "PAUSE" else state.elapsedText,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ZoneLayout(state: PipUiState, accent: Color) {
    Text(
        text = state.zone?.toString() ?: "--",
        fontSize = 48.sp,
        fontWeight = FontWeight.Bold,
        color = accent
    )
    Text(
        text = "ZONE",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    BpmRow(state.bpm, accent, valueSize = 18.sp, iconSize = 14.dp)
    if (state.paused) PauseLine()
}

@Composable
private fun TimerLayout(state: PipUiState, accent: Color) {
    Text(
        text = if (state.paused) "PAUSE" else state.elapsedText,
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    BpmRow(state.bpm, accent, valueSize = 18.sp, iconSize = 14.dp)
}

@Composable
private fun BpmRow(bpm: Int?, accent: Color, valueSize: TextUnit, iconSize: androidx.compose.ui.unit.Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(iconSize)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = bpm?.toString() ?: "--",
            fontSize = valueSize,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "BPM",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PauseLine() {
    Text(
        text = "PAUSE",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
```

- [ ] **Step 2: Build to verify**

Run: `cd code && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Lint**

Run: `cd code && ./gradlew :app:lintDebug`
Expected: keine neuen Fehler.

- [ ] **Step 4: Commit**

```bash
git add code/app/src/main/kotlin/com/kevin/hrtracker/ui/pip/PipContent.kt
git commit -m "feat(widget): vier pip-layouts je variante"
```

---

### Task 5: Notification folgt der Variante

**Files:**
- Modify: `code/app/src/main/kotlin/com/kevin/hrtracker/service/HrRecordingService.kt`

**Interfaces:**
- Consumes: `widgetNotificationText`, `WidgetVariant` (Task 1), `UserSettings.widgetVariant` (Task 2), `HrZoneCalculator.zoneFor(bpm: Int, zones: List<ZoneBounds>): Int`.
- Produces: nichts für spätere Tasks.

- [ ] **Step 1: Imports und Felder ergänzen**

Import-Block ergänzen:

```kotlin
import com.kevin.hrtracker.domain.HrZoneCalculator
import com.kevin.hrtracker.domain.WidgetVariant
import com.kevin.hrtracker.domain.ZoneBounds
import com.kevin.hrtracker.domain.widgetNotificationText
```

Bei den bestehenden Feldern (`HrRecordingService.kt:36-39`) ergänzen:

```kotlin
    private var settingsJob: Job? = null
    private var lastBpmValue: Int? = null
    private var currentVariant: WidgetVariant = WidgetVariant.STANDARD
    private var currentZones: List<ZoneBounds> = emptyList()
```

`lastBpm` (String) bleibt bestehen, es wird weiterhin als Label an `updateNotification` übergeben.

- [ ] **Step 2: `buildNotification` umstellen**

`HrRecordingService.kt:47-60` ersetzen durch:

```kotlin
    override fun buildNotification(label: String, elapsed: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val zone = lastBpmValue
            ?.takeIf { currentZones.isNotEmpty() }
            ?.let { HrZoneCalculator.zoneFor(it, currentZones) }
        return NotificationCompat.Builder(this, notificationChannelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("HR Tracker läuft")
            .setContentText(widgetNotificationText(currentVariant, lastBpmValue, zone, elapsed))
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }
```

- [ ] **Step 3: Einstellungen im Service live mitlesen**

In `onRecordingStart` direkt hinter `sessionRepository.startSession(...)` (also nach `HrRecordingService.kt:70`) einfügen:

```kotlin
        settingsJob = serviceScope.launch {
            settingsRepository.userSettings.collect {
                currentVariant = it.widgetVariant
                currentZones = it.effectiveZones
            }
        }
```

In der bestehenden `hrFlow.collect`-Schleife (`HrRecordingService.kt:88-92`) zusätzlich den numerischen Wert merken:

```kotlin
            hrFlow.collect { parsed: ParsedHr ->
                lastBpmValue = parsed.bpm
                lastBpm = parsed.bpm.toString()
                val elapsed = (System.currentTimeMillis() - startMs) / 1000
                updateNotification(lastBpm, formatDuration(elapsed))
            }
```

Die Notification wird weiterhin nur bei einem neuen HR-Sample neu gebaut. Eine Umstellung der Variante schlägt also erst mit dem nächsten Messwert durch — bei ~1 Sample/s nicht wahrnehmbar.

- [ ] **Step 4: Job in `onRecordingStop` aufräumen**

In `onRecordingStop`, bei den anderen Job-Cancels:

```kotlin
        settingsJob?.cancel()
        settingsJob = null
```

- [ ] **Step 5: Build to verify**

Run: `cd code && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add code/app/src/main/kotlin/com/kevin/hrtracker/service/HrRecordingService.kt
git commit -m "feat(widget): notification folgt der variante"
```

---

### Task 6: Einstellung im Settings-Screen

**Files:**
- Modify: `code/app/src/main/kotlin/com/kevin/hrtracker/ui/settings/SettingsViewModel.kt`
- Modify: `code/app/src/main/kotlin/com/kevin/hrtracker/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `SettingsRepository.setWidgetVariant` (Task 2), `UserSettings.widgetVariant` (Task 2), `WidgetVariant` (Task 1).
- Produces: `SettingsViewModel.setWidgetVariant(variant: WidgetVariant)`.

Die Auswahl nutzt `SingleChoiceSegmentedButtonRow`, wie schon bei Ziel-Zone (`SettingsScreen.kt:211`) und HR-Quelle (`SettingsScreen.kt:413`) — kein neues UI-Pattern, keine zusätzlichen Imports über die bereits vorhandenen hinaus.

- [ ] **Step 1: ViewModel-Setter ergänzen**

In `SettingsViewModel.kt` den Import ergänzen:

```kotlin
import com.kevin.hrtracker.domain.WidgetVariant
```

Direkt hinter `setChartDynamicScale` (`SettingsViewModel.kt:43`):

```kotlin
    fun setWidgetVariant(variant: WidgetVariant) = viewModelScope.launch { settingsRepository.setWidgetVariant(variant) }
```

- [ ] **Step 2: Card im Settings-Screen ergänzen**

In `SettingsScreen.kt` den Import ergänzen:

```kotlin
import com.kevin.hrtracker.domain.WidgetVariant
```

Direkt hinter der Card für die dynamische Chart-Skalierung (endet in `SettingsScreen.kt:393`) einfügen:

```kotlin
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Widget-Anzeige",
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryPurple,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Text(
                    "Gilt für das PiP-Fenster und die Benachrichtigung während der Aufzeichnung",
                    style = MaterialTheme.typography.bodySmall
                )
                val variants = listOf(
                    WidgetVariant.MINIMAL to "Nur BPM",
                    WidgetVariant.STANDARD to "Standard",
                    WidgetVariant.ZONE to "Zone",
                    WidgetVariant.TIMER to "Zeit"
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    variants.forEachIndexed { index, (variant, label) ->
                        SegmentedButton(
                            selected = settings.widgetVariant == variant,
                            onClick = { viewModel.setWidgetVariant(variant) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = variants.size)
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }
```

- [ ] **Step 3: Build to verify**

Run: `cd code && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Falls `Arrangement`, `Card`, `Column` oder `PrimaryPurple` als unaufgelöst gemeldet werden, fehlt ein Import — die genannten Symbole werden im selben File bereits verwendet, ihre Imports also übernehmen.

- [ ] **Step 4: Lint und Tests**

Run: `cd code && ./gradlew :app:lintDebug :app:testDebugUnitTest`
Expected: PASS, keine neuen Lint-Fehler.

- [ ] **Step 5: Commit**

```bash
git add code/app/src/main/kotlin/com/kevin/hrtracker/ui/settings/SettingsViewModel.kt code/app/src/main/kotlin/com/kevin/hrtracker/ui/settings/SettingsScreen.kt
git commit -m "feat(widget): auswahl der variante in einstellungen"
```

---

### Task 7: Dokumentation und Abschluss

**Files:**
- Create: `changelog.d/023_widget_varianten.feature.md`
- Modify: `docs/SPEC.md`

**Interfaces:**
- Consumes: das fertige Feature aus Task 1–6.
- Produces: nichts.

- [ ] **Step 1: Changelog-Eintrag anlegen**

Datei `changelog.d/023_widget_varianten.feature.md`, im Stil von `changelog.d/022_pip_bpm_widget.feature.md`:

```markdown
## Widget-Varianten für PiP und Notification (Feature)

**Beschreibung:**
Das PiP-Fenster und die Benachrichtigung während der Aufzeichnung zeigten bisher einen festen Satz an Werten. Wer nur die Zahl sehen will oder primär auf die Zone schaut, kann die Anzeige jetzt in den Einstellungen unter "Widget-Anzeige" umstellen.

- Vier Presets: `MINIMAL` (nur BPM, groß), `STANDARD` (BPM + Zone + Dauer, bisheriges Verhalten), `ZONE` (Zone dominant), `TIMER` (Dauer dominant).
- Eine gemeinsame Einstellung für beide Anzeigeflächen — die Präferenz "nur die Zahl" gilt erfahrungsgemäß für PiP und Notification gleichzeitig.
- Default ist `STANDARD`, bestehende Installationen sehen keine Änderung.
- Persistenz in DataStore (`widget_variant`), unbekannter Wert fällt auf `STANDARD` zurück. Keine Room-Migration.
- Neue Datei `domain/WidgetVariant.kt` mit dem Enum und der reinen Funktion `widgetNotificationText`, die Service und Test gemeinsam nutzen.
- Pause: `PAUSE` ersetzt die Zeile, die sonst die Dauer zeigt (`STANDARD`, `TIMER`) bzw. kommt als kleine Zeile dazu (`MINIMAL`, `ZONE`).
- Bewusst nicht enthalten: Homescreen-AppWidget, getrennte Einstellungen pro Fläche, neue Metriken wie Ø-BPM oder %HRmax.
```

- [ ] **Step 2: `docs/SPEC.md` ergänzen**

Den Abschnitt zum PiP-BPM-Widget suchen (`grep -n "PiP" docs/SPEC.md`) und dort einen Unterabschnitt „Widget-Varianten" ergänzen: die vier Presets mit ihrem PiP-Inhalt und Notification-Text (Tabelle aus dem Spec-Dokument `docs/superpowers/specs/2026-08-03-widget-varianten-design.md` übernehmen), den Default `STANDARD`, den DataStore-Key `widget_variant` und die Pause-Regel.

- [ ] **Step 3: Volle Verifikation**

Run: `cd code && ./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, keine Lint-Fehler, alle Tests grün.

- [ ] **Step 4: Manuelle Abnahme**

App installieren (`cd code && ./gradlew :app:installDebug`), Session starten, dann pro Variante prüfen:

1. Einstellung auf `MINIMAL` stellen → App minimieren → PiP zeigt nur die große BPM-Zahl; Notification-Shade zeigt `142 BPM`.
2. Auf `ZONE` stellen → PiP zeigt große Zonennummer in Zonenfarbe, BPM klein darunter.
3. Auf `TIMER` stellen → PiP zeigt die Dauer groß; Session pausieren → große Zahl wechselt auf `PAUSE`.
4. Zurück auf `STANDARD` → Layout entspricht dem Zustand vor diesem Feature.

- [ ] **Step 5: Commit**

```bash
git add changelog.d/023_widget_varianten.feature.md docs/SPEC.md
git commit -m "docs(widget): changelog + SPEC fuer widget-varianten"
```
