# Zone model selector + observed HRmax (2026-09-18)

## Problem
`HrZoneCalculator.calculateZones` applies one percentage table (50/60/70/80/90) to two different anchors. It switches from %HRmax to Karvonen implicitly the moment `restingHr` is non-null — invisible to the user.

Age 30, Tanaka HRmax 187, resting 60 (HRR 127):
| Model | Zone 2 |
|---|---|
| %HRmax | 112–130 |
| Karvonen (%HRR) | 136–148 |

Both are textbook-correct; `%HRmax ≈ 0.64·%HRR + 37` (Swain 1994). The defect is the silent switch, not the arithmetic.

Secondary: Tanaka carries SD ±7–11 bpm, so every boundary is already ±10 bpm uncertain — a larger error than the model choice.

## Decisions
- Explicit `ZoneModel { HR_MAX, KARVONEN }`, stored in DataStore, **default `HR_MAX`** (matches Garmin/Polar, and matches what the user expected).
- Keep textbook percentages for both models. Rejected: remapping Karvonen breakpoints through the Swain regression — that would invent a non-standard third model and extrapolate the regression outside its validated ~40–95% range.
- `KARVONEN` with `restingHr == null` falls back to `HR_MAX` rather than erroring.
- No Room migration needed, but the snapshot only covers v2+ sessions — `MIGRATION_1_2` did not backfill `zoneSnapshotJson`. v1 sessions keep `null` and are handled by the `resolveZones` legacy-rule fallback (KARVONEN when `restingHr` is set, else HR_MAX) instead.
- Review finding: a backfill migration was considered and rejected in favour of the fallback — no migration means no schema-change approval needed.
- Observed HRmax is **suggested, never auto-applied** — it reuses the existing `manualMaxHr` setting, so no new preference key.

## Waves
### Wave 1 — domain + data (done first)
- `domain/HrZoneCalculator.kt` — `ZoneModel` enum, `calculateZones(maxHr, restingHr, model = HR_MAX)`, `resolveZones` passthrough
- `domain/UserSettings.kt` — `zoneModel` field, `effectiveZones` passes it
- `data/repository/SettingsRepository.kt` — `zone_model` key, read + `setZoneModel`
- `test/.../HrZoneCalculatorTest.kt` — both models, null-resting fallback, default param

### Wave 2 — call sites honour the setting
- `data/repository/SessionRepository.kt:69` — `startSession` takes and forwards `zoneModel` into the snapshot
- `ui/live/LiveViewModel.kt:147` — pass the model (open question: this recomputes zones instead of using `resolveZones` on the session snapshot; flagged, out of scope)
- `data/db/HrSampleDao.kt` — observed-max query over non-deleted sessions in a time window

### Wave 3 — UI
- `ui/settings/SettingsScreen.kt` — `SingleChoiceSegmentedButtonRow` for the model (reuse the existing `Sex` selector pattern); replace the read-only "Zonen-Modell: $model" line. Observed-HRmax suggestion row under the Tanaka line with an "Übernehmen" action.
- `ui/settings/SettingsViewModel.kt` — wire `setZoneModel`, expose observed max

## Deferred
- LTHR-anchored zones (Friel, %LTHR from a 30-min TT) — the only option that adapts to fitness level from measurement rather than assumption. Revisit if percentage zones stay wrong in practice.
- DFA-α1 aerobic-threshold detection from the stored RR intervals.

## Verification
Build/lint/test require an explicit `JAVA_HOME` (`C:\Users\Kiwi PC\.gradle\jdks\jetbrains_s_r_o_-21-amd64-windows.2`); the Gradle wrapper lives in `code/`, not the repo root.
