# Graph Report - .  (2026-06-14)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 477 nodes · 609 edges · 56 communities (41 shown, 15 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 11 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `9d5e8641`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 39|Community 39]]
- [[_COMMUNITY_Community 42|Community 42]]
- [[_COMMUNITY_Community 43|Community 43]]
- [[_COMMUNITY_Community 44|Community 44]]
- [[_COMMUNITY_Community 45|Community 45]]
- [[_COMMUNITY_Community 46|Community 46]]
- [[_COMMUNITY_Community 47|Community 47]]
- [[_COMMUNITY_Community 48|Community 48]]
- [[_COMMUNITY_Community 55|Community 55]]
- [[_COMMUNITY_Community 57|Community 57]]
- [[_COMMUNITY_Community 58|Community 58]]
- [[_COMMUNITY_Community 59|Community 59]]

## God Nodes (most connected - your core abstractions)
1. `ScanViewModel` - 24 edges
2. `HrBleManager` - 20 edges
3. `SettingsRepository` - 18 edges
4. `HistoryViewModel` - 16 edges
5. `DetailViewModel` - 16 edges
6. `SettingsViewModel` - 14 edges
7. `HrRecordingService` - 13 edges
8. `HR-Tracker` - 13 edges
9. `SessionDao` - 13 edges
10. `LiveViewModel` - 12 edges

## Surprising Connections (you probably didn't know these)
- `DetailScreen()` --calls--> `durationString()`  [INFERRED]
  code/app/src/main/kotlin/com/kevin/hrtracker/ui/detail/DetailScreen.kt → code/app/src/main/kotlin/com/kevin/hrtracker/ui/history/HistoryScreen.kt

## Import Cycles
- None detected.

## Communities (56 total, 15 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.08
Nodes (21): Bundle, Long, String, String, Boolean, String, ComponentActivity, DetailScreen() (+13 more)

### Community 1 - "Community 1"
Cohesion: 0.11
Nodes (13): HrBleManager, BluetoothGatt, ByteArray, BluetoothDevice, ConnectionState, Job, List, StateFlow (+5 more)

### Community 2 - "Community 2"
Cohesion: 0.10
Nodes (17): BaseRecordingService, Boolean, HrSource, Job, String, Context, DatabaseModule, HrBleManager (+9 more)

### Community 3 - "Community 3"
Cohesion: 0.13
Nodes (10): Boolean, HrSource, Int, List, SavedDevice, String, Flow, Keys (+2 more)

### Community 4 - "Community 4"
Cohesion: 0.12
Nodes (11): BluetoothDevice, Boolean, ConnectionState, DiscoveredDevice, Job, List, Long, SavedDevice (+3 more)

### Community 5 - "Community 5"
Cohesion: 0.28
Nodes (9): Context, HrSample, Intent, List, Long, Session, String, SessionExporter (+1 more)

### Community 6 - "Community 6"
Cohesion: 0.13
Nodes (14): Context, Float, HrSample, Int, Intent, List, Long, Map (+6 more)

### Community 7 - "Community 7"
Cohesion: 0.11
Nodes (15): ConnectionState, Int, List, Long, StateFlow, String, Boolean, Int (+7 more)

### Community 8 - "Community 8"
Cohesion: 0.21
Nodes (6): Flow, List, Long, Session, String, SessionDao

### Community 9 - "Community 9"
Cohesion: 0.20
Nodes (10): Boolean, List, Long, Session, StateFlow, HistoryViewModel, SessionTrimpEntry, SummaryStats (+2 more)

### Community 10 - "Community 10"
Cohesion: 0.18
Nodes (15): Color, Float, Int, List, Long, Map, Modifier, String (+7 more)

### Community 11 - "Community 11"
Cohesion: 0.17
Nodes (8): Boolean, HrSource, Int, StateFlow, String, UserSettings, HealthImportStatus, SettingsViewModel

### Community 12 - "Community 12"
Cohesion: 0.27
Nodes (7): Flow, HrSample, Int, List, Long, HrSampleDao, SessionAvgBpm

### Community 13 - "Community 13"
Cohesion: 0.17
Nodes (8): Flow, Int, Job, List, Long, StateFlow, String, SessionRepository

### Community 14 - "Community 14"
Cohesion: 0.29
Nodes (14): Boolean, List, Long, Session, String, durationString(), HistoryScreen(), SessionListItem() (+6 more)

### Community 15 - "Community 15"
Cohesion: 0.18
Nodes (12): Color, Float, Int, List, Long, Map, Modifier, String (+4 more)

### Community 16 - "Community 16"
Cohesion: 0.26
Nodes (11): DiscoveredDevice, List, SavedDevice, String, BrandHint(), DeviceItem(), SavedDeviceItem(), ScanScreen() (+3 more)

### Community 17 - "Community 17"
Cohesion: 0.31
Nodes (6): Int, List, String, HrZoneCalculator, ZoneBounds, Pair

### Community 18 - "Community 18"
Cohesion: 0.14
Nodes (13): Architektur, Commands, graphify, Hard Rules, HR-Tracker, Logs, Modell-Routing (wichtig), Permissions / Plattform-Constraints (+5 more)

### Community 19 - "Community 19"
Cohesion: 0.22
Nodes (6): MessageEvent, WatchCommandListener, MessageEvent, wearableHrSource(), WearHrListenerService, WearableListenerService

### Community 20 - "Community 20"
Cohesion: 0.28
Nodes (5): Flow, List, Long, SportLabelDao, SportLabel

### Community 21 - "Community 21"
Cohesion: 0.22
Nodes (5): HrDatabase, HrSampleDao, RoomDatabase, SessionDao, SportLabelDao

### Community 22 - "Community 22"
Cohesion: 0.36
Nodes (9): Boolean, Int, String, OnboardingScreen(), Step1(), Step2(), Step3(), SummaryRow() (+1 more)

### Community 23 - "Community 23"
Cohesion: 0.38
Nodes (4): Boolean, Int, StateFlow, WatchState

### Community 24 - "Community 24"
Cohesion: 0.33
Nodes (4): Context, DataStore, DataModule, Preferences

### Community 26 - "Community 26"
Cohesion: 0.40
Nodes (3): Application, HrTrackerApplication, WearApplication

### Community 27 - "Community 27"
Cohesion: 0.50
Nodes (3): HeartRateParser, ParsedHr, ByteArray

### Community 28 - "Community 28"
Cohesion: 0.40
Nodes (3): Boolean, Int, HealthConnectManager

### Community 29 - "Community 29"
Cohesion: 0.40
Nodes (4): Int, List, Modifier, BpmLineChart()

### Community 30 - "Community 30"
Cohesion: 0.50
Nodes (3): ParsedHr, SharedFlow, WearableHrSource

### Community 31 - "Community 31"
Cohesion: 0.40
Nodes (3): HrSensorManager, Flow, Int

### Community 55 - "Community 55"
Cohesion: 0.25
Nodes (7): Modell-Hinweis, Output-Format, Rolle, Rolle, Vorgehen, Was du nicht tust, Wichtig: Kein eigenes Searching

### Community 57 - "Community 57"
Cohesion: 0.29
Nodes (6): Hard Rules, Output-Format, Rolle, Voraussetzungen für eine saubere Übergabe, Vorgehen, Was du nicht tust

## Knowledge Gaps
- **155 isolated node(s):** `StateFlow`, `List`, `ScanResult`, `ConnectionState`, `SharedFlow` (+150 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **15 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ScanViewModel` connect `Community 4` to `Community 17`, `Community 7`?**
  _High betweenness centrality (0.033) - this node is a cross-community bridge._
- **Why does `DetailViewModel` connect `Community 6` to `Community 7`?**
  _High betweenness centrality (0.016) - this node is a cross-community bridge._
- **Why does `SettingsViewModel` connect `Community 11` to `Community 7`?**
  _High betweenness centrality (0.014) - this node is a cross-community bridge._
- **What connects `StateFlow`, `List`, `ScanResult` to the rest of the system?**
  _155 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.07765151515151515 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.11462450592885376 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.09686609686609686 - nodes in this community are weakly interconnected._