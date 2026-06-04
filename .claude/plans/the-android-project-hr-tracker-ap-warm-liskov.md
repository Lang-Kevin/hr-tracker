# HR-Tracker-AP: Implementation Plan (7 Steps)

## Context
Gap analysis identified 7 missing requirements from `HR-Tracker-App-Plan.md`. Each step below is independently executable. Trigger any step with: "start implementation of step N from the implementation plan file."

---

## Step 1 — Label picker on Scan screen

**Goal:** Replace hardcoded `"Training"` (ScanScreen.kt:76) with a dropdown of sport labels.

**Files:**
- `ui/scan/ScanViewModel.kt` — inject `HrDatabase`; add `val sportLabels: StateFlow<List<SportLabel>> = db.sportLabelDao().getAllLabels().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())`
- `ui/scan/ScanScreen.kt` — collect `sportLabels`; add `var selectedLabel by remember { mutableStateOf("") }` initialized via `LaunchedEffect(sportLabels)` to first item; wrap start button in `ExposedDropdownMenuBox`; pass `selectedLabel` instead of `"Training"` to `onSessionStarted()`

**Reuse:** `SportLabelDao.getAllLabels()` exists. 8 predefined labels seeded by `DatabaseModule`.

**Test:** Device connected → dropdown shows labels → select one → start session → JSON export confirms label.

---

## Step 2 — Custom label add/delete backend (Settings ViewModel)

**Goal:** Add label management functions to SettingsViewModel (required by Step 6 UI).

**Files:**
- `ui/settings/SettingsViewModel.kt` — inject `HrDatabase`; add:
  - `val sportLabels: StateFlow<List<SportLabel>> = db.sportLabelDao().getAllLabels().stateIn(...)`
  - `fun addLabel(name: String)` → `viewModelScope.launch { db.sportLabelDao().insert(SportLabel(name = name.trim(), isPredefined = false)) }`
  - `fun deleteLabel(label: SportLabel)` → `viewModelScope.launch { if (!label.isPredefined) db.sportLabelDao().delete(label) }`

**Note:** `SportLabelDao.insert` uses `OnConflictStrategy.IGNORE` — duplicate names silently ignored.

**Test:** Add "Klettern" → appears in flow; attempt to delete a predefined label → guarded, no-op.

---

## Step 3 — Ø/Max BPM in History list

**Goal:** Show average and max BPM on each session card (HistoryScreen.kt:51–62).

**Files:**
- `data/db/HrSampleDao.kt` — add data class + query:
  ```
  data class SessionStats(val sessionId: Long, val avgBpm: Int, val maxBpm: Int)

  @Query("SELECT sessionId, CAST(AVG(bpm) AS INT) AS avgBpm, MAX(bpm) AS maxBpm FROM hr_sample GROUP BY sessionId")
  fun getStatsPerSession(): Flow<List<SessionStats>>
  ```
- `ui/history/HistoryViewModel.kt` — inject `HrDatabase`; replace `sessions` flow with a `combine()` of `getAllSessions()` + `getStatsPerSession()` producing `StateFlow<List<Pair<Session, SessionStats?>>>`
- `ui/history/HistoryScreen.kt` — update `SessionListItem` to accept `SessionStats?`; add `Text("Ø ${stats?.avgBpm ?: "–"} • Max ${stats?.maxBpm ?: "–"} bpm")` below duration

**Test:** Record session → History card shows Ø and Max matching DetailScreen stats card values.

---

## Step 4 — Zone banding in detail chart

**Goal:** Draw horizontal threshold lines at zone boundaries on the BPM chart (DetailScreen.kt:80–86).

**Files:**
- `ui/detail/DetailViewModel.kt` — add `val zoneBounds: StateFlow<List<ZoneBounds>>` derived from `session` via `HrZoneCalculator.calculateZones(s.maxHrUsed, s.restingHr)`
- `ui/shared/BpmLineChart.kt` — add `zones: List<ZoneBounds> = emptyList()` parameter; build decoration list using `rememberHorizontalLine()` (Vico: `com.patrykandpatrick.vico.compose.cartesian.decoration`) — one line per zone upper threshold (Z1–Z4), using zone colors at 40% alpha; pass to `rememberCartesianChart(decorations = decorations)`
- `ui/detail/DetailScreen.kt` — collect `zoneBounds`; pass to `BpmLineChart(zones = zoneBounds)`
- `ui/live/LiveScreen.kt` — update `BpmLineChart` call to pass empty list (no behavior change)

**Reuse:** Zone colors already defined in `LiveScreen.kt` (`zoneColors` list) — extract to a shared constant or duplicate inline. `HrZoneCalculator.calculateZones()` already exists.

**Test:** Open completed session → chart shows 4 horizontal colored lines at zone thresholds.

---

## Step 5 — Label editing in Detail screen

**Goal:** Allow renaming a session label from DetailScreen.

**Files:**
- `data/db/SessionDao.kt` — add `@Query("UPDATE session SET label = :label WHERE id = :id") suspend fun updateLabel(id: Long, label: String)`
- `ui/detail/DetailViewModel.kt` — convert `session` from one-shot `getById` to a `MutableStateFlow<Session?>`; add `fun updateLabel(newLabel: String)` that calls `db.sessionDao().updateLabel(sessionId, newLabel.trim())` then refreshes the flow
- `ui/detail/DetailScreen.kt` — add `var editingLabel by remember { mutableStateOf(false) }`; add pencil `IconButton` next to session title; show `AlertDialog` with `OutlinedTextField` pre-filled with current label; OK → `viewModel.updateLabel(input)` + close dialog

**Test:** Detail screen → pencil icon → rename to "Fußball" → title updates → back to History → card shows "Fußball".

---

## Step 6 — Sport label management UI in Settings

**Goal:** Add "Sport-Labels" card to SettingsScreen for add/delete of custom labels.

**Depends on:** Step 2 (SettingsViewModel functions must exist first).

**Files:**
- `ui/settings/SettingsScreen.kt` — collect `sportLabels` from viewModel; add a `Card` after Zones-Vorschau card:
  - Title: "Sport-Labels"
  - `Column` of label rows: `Text(label.name)` + `IconButton(Icons.Default.Delete)` visible only if `!label.isPredefined`, calls `viewModel.deleteLabel(label)`
  - Below list: `OutlinedTextField` for new name + `TextButton("Hinzufügen")` → `viewModel.addLabel(text)` + clear field

**Test:** Settings → 8 predefined labels (no delete icons) → add "Klettern" → appears with delete icon → delete → gone → Step 1 dropdown reflects change.

---

## Step 7 — Saved device UI in Settings

**Goal:** Show stored BLE device MAC and allow clearing it from SettingsScreen.

**Files:**
- `ui/settings/SettingsViewModel.kt` — add:
  - `val savedDeviceAddress: StateFlow<String?> = settingsRepository.savedDeviceAddress.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)`
  - `fun clearSavedDevice() { viewModelScope.launch { settingsRepository.clearSavedDevice() } }`
- `ui/settings/SettingsScreen.kt` — add `Card` at the top (before HR card) titled "Gespeichertes Gerät":
  - `null` → `Text("Kein Gerät gespeichert")`
  - Non-null → `Text(address)` + `TextButton("Verbindung löschen") { viewModel.clearSavedDevice() }`

**Reuse:** `SettingsRepository.clearSavedDevice()` and `savedDeviceAddress: Flow<String?>` already exist — only ViewModel wiring and UI are new.

**Test:** Connect device → Settings → MAC shown → "Verbindung löschen" → "Kein Gerät gespeichert" → restart app → no auto-reconnect.

---

## Execution order

| Order | Step | Depends on |
|-------|------|------------|
| 1st | Step 7 | — |
| 2nd | Step 1 | — |
| 3rd | Step 3 | — |
| 4th | Step 2 | — |
| 5th | Step 6 | Step 2 |
| 6th | Step 4 | — |
| 7th | Step 5 | — |
