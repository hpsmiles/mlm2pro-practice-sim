# M5 — Sessions & Persistence Design

Status: approved design, 2026-09-26 · Exit criterion (ROADMAP): **sessions survive app restart.**

## 1. Mission

Persist every accepted shot into on-device storage (Room), add club tagging, and a past-session review screen. No network, no cloud — single-device constraint is hard. M5 is the substrate for M6 (bag mapping), M7 (A/B club testing), M8 (custom combines).

## 2. Decisions (all locked with user, 2026-09-26)

| # | Decision | Choice |
|---|---|---|
| D1 | Session lifecycle | **Auto-start** on first accepted shot, **explicit END**. Auto-title from start time; optional rename later |
| D2 | Club tagging | **Pre-select active club** while hitting + **fix-up re-tag in review** |
| D3 | Club list | **Editable simple bag** (add/rename/delete), seeded with a standard set; duplicates allowed ("7i-A"/"7i-B" — the M7 case) |
| D4 | Demo shots | **Stored with LIVE/DEMO flag**; review defaults to live-only with a toggle |
| D5 | Review layout | **Master–detail** in a new HISTORY rail tab |
| D6 | Re-tag interaction | **Long-press row → multi-select → club chip strip → APPLY** |
| D7 | Active-club UI | **Pill top-left on range + big-tile picker overlay** |
| D8 | Storage shape | **Hybrid**: raw `BallData` is source of truth + cached scalar columns written at insert; trajectories recomputed on demand |

## 3. Architecture

New Gradle module **`core/data`** — Room only, no Compose, no BLE:

- `Mlm2proDatabase` — `@Database(entities = [SessionEntity, ShotEntity, ClubEntity], version = 1, exportSchema = true)`, db file `golfsim.db`, Room schemas exported to the repo.
- DAOs: `SessionDao`, `ShotDao`, `ClubDao`.
- `SessionRepository` — the single facade `:app` talks to. All suspend/Flow; Room supplies IO dispatching. No other module changes; `:app` gains the dependency.

Build note: `build-logic` gains KSP + Room wiring for the new module (version catalog entries included).

## 4. Schema

### `sessions`
| Column | Type | Notes |
|---|---|---|
| `id` | Long auto | PK |
| `startedAtEpochMs` | Long | |
| `endedAtEpochMs` | Long? | **null = open session. Invariant: at most one open session at any time.** |
| `title` | String? | null → auto-title rendered from `startedAtEpochMs` ("Mon 23 Sep · 16:10") |
| `misreadCount` | Int | coalesced counter (existing 500 ms coalesce in `RangeSession`) |

### `shots`
| Column | Type | Notes |
|---|---|---|
| `id` | Long auto | PK |
| `sessionId` | Long | FK → sessions, **indexed** |
| `seq` | Int | 0-based order within session |
| `timestampMs` | Long | capture time |
| `source` | Int | 0 = LIVE, 1 = DEMO — **origin of the data**, not UI mode (a BLE `onMeasurement` is LIVE even if the demo toggle is on) |
| `clubName` | String? | snapshot of active club at capture; **null = untagged** (pill defaults to "—") |
| `ballSpeedMps`, `launchAngleDeg`, `launchDirDeg`, `spinRpm`, `spinAxisDeg`, `clubHeadSpeedMps` | — | **raw block (source of truth)** — exactly the fields `BallData` decodes |
| `unknown1`, `unknown2` | Int? | MEASUREMENT offsets 12–15 preserved raw for the distance-verification experiment vs Rapsodo readouts |
| `carryM`, `totalM`, `sideM`, `apexM`, `flightTimeSec` | — | **cached scalars**, written once at insert from the physics result; never re-computed for lists |

### `clubs`
| Column | Type | Notes |
|---|---|---|
| `id` | Long auto | PK |
| `name` | String | unique |
| `sortOrder` | Int | bag display order |

Seeded on **first-ever open only if empty**: D, 3W, 5W, 4H, 4i, 5i, 6i, 7i, 8i, 9i, PW, GW, SW, LW.

**Club identity is snapshotted, not foreign-keyed.** Shots store a plain `clubName` string. Renaming/deleting a club in the bag never rewrites history; duplicates work naturally for M7.

**Active-club UX state** (what the pill shows) persists in SharedPreferences (same pattern as `SecretStore`; **no DataStore migration** — requirements did not grow).

## 5. Session lifecycle

- **Auto-start:** first accepted shot (live or demo) with no open session → repository creates one at `startedAt = now`. Misreads and physics-rejects (`RangeSession.add` returning false) never open or extend a session.
- **Append:** each accepted shot inserts one row. `RangeSession.add()` changes return type from `Boolean` to `DisplayShot?` (null = rejected) so AppRoot can hand the exact accepted record to the repository with source + club context. `DisplayShot` gains `timestampMs` (set at add time from the injectable clock — also improves testability).
- **Misreads:** coalesced count increments `sessions.misreadCount`.
- **END SESSION:** rail chip (visible while a session is open) sets `endedAt`. No confirmation dialog — non-destructive, session remains fully browsable.
- **Restart resume:** at launch the app queries for the open session. Its shots restore into `RangeSession` as **scalar-backed resting shots**: `DisplayShot` rebuilt from raw columns + `LaunchConditions`, `ShotResult` carrying the cached scalars with **empty trajectory samples**. Resting balls render on the range; no physics re-run; nothing replays. New shots keep appending to the same session. A mid-session force-stop loses nothing.
- **Rename:** tapping the title in the detail header opens a text-input dialog that sets `title`.

## 6. UI (per approved mockups — `.superpowers/brainstorm/m5-1790379911/`)

1. **NavRail:** HISTORY joins RANGE · SETTINGS.
2. **Range:** club pill top-left ("—" until first selection); tap → big-tile picker overlay (active highlighted, "+ ADD" tile → inline add-club dialog); closes on selection.
3. **Rail:** END SESSION chip while a session is open.
4. **`history/` package in `:app`:** master–detail `HistoryScreen`.
   - Left list: title/date, shot count, club summary, DEMO badge (shown when the session contains any demo shots); "LIVE ONLY / ALL" toggle (default LIVE ONLY).
   - Detail: stat chips (shots, misreads, avg carry, longest + club), shot table (`# · CLUB · CARRY · SIDE · BALL · SPIN`), long-press → multi-select re-tag bar → club chip strip → APPLY (null club renders "—").
   - Newest session selected by default.
5. **Settings:** BAG section — add, rename, delete. Reorder deferred.

## 7. Data flow

- **Insert:** BLE callback (binder) → hop to main → `RangeSession.add()` (physics once, UI truth) → on accept, `SessionRepository.appendShot(displayShot, source, clubName)` fire-and-forget on a `SupervisorJob` scope. Session auto-create handled inside the repository (single-writer: all mutations funnel through AppRoot's main-thread mediation — the open-session race can't happen structurally).
- **Read:** `SessionDao.observeSessionSummaries(): Flow` — one aggregate query (COUNT, live-only filter, `AVG(carryM)`, `MAX(carryM)`, distinct clubNames) → live-updating master list. `ShotDao.observeShots(sessionId): Flow`. `retagShots(ids, clubName)` = single `UPDATE ... WHERE id IN (...)`; Flows re-emit automatically. The repository exposes small data-layer records (`SessionSummary`, `ShotRecord`) rather than raw Room entities; `:app` maps these to its display models.
- **Restore:** `openSessionWithShots()` at launch → `RangeSession` refill (resting shots only).

## 8. Error handling (all non-crashing)

- **Insert failure** (disk full/IO): logged; persistence-error flag surfaces once in StatusStrip text ("DB WRITE FAILING"). Live UI continues — shots render from memory; accepted data-loss window beats crashing mid-practice.
- **Corrupt DB at open:** caught, bad file renamed aside, fresh empty DB created, surfaced the same way.
- **Single writer:** structural (main-thread mediation), see §7.
- **Schema export** (`exportSchema = true`) gives future migrations a baseline.

## 9. Testing

- **Pure JVM (`:core/data` + `:app`):** mappers (raw↔entity round trips incl. both unknowns), auto-title formatting, summary shaping, `RangeSession` restore-to-resting (scalar-backed, no samples), `add()` returning `DisplayShot?` + rejection behavior unchanged.
- **Robolectric in-memory Room (`:core/data`):** aggregate queries (count/avg/max, LIVE-only filter), single-open-session invariant, retag UPDATE re-emission, END SESSION behavior, club seed-once.
- **On-device exit checklist:**
  1. Demo shots — some untagged, one run mis-tagged then fixed via re-tag — END SESSION → force-stop → relaunch → HISTORY shows the session with correct numbers.
  2. Session open → force-stop mid-way → relaunch → keep firing → one continuous session, resting shots restored on the range.
  3. Live shed session vs Rapsodo app readouts (also eyeball `unknown1/unknown2` vs its carry/total display).

## 10. Out of scope (deferred)

- Landing-dot map / dispersion analytics / carry matrix — M6.
- Trajectory replay & eye-line visit in review — parked M4 follow-up; M5 storage enables it.
- CSV export — M9. Bag reordering. Any network/cloud — hard boundary, unchanged.

## 11. Risks / watch items

- **Robolectric first run** downloads android-all JARs once (network). Fallback if blocked: DAO coverage as on-device androidTest.
- **build-logic** gains KSP/Room wiring — contained to the new module's convention.
- **DB write latency** during rapid shots: one insert per shot, trivial for Room.
