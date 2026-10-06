# Bag Club Reordering — Design

Date: 2026-10-06
Status: Approved (brainstorm decisions 2026-10-06)
Follows: `2026-09-26-m5x-ui-edits-design.md` (§4.2 type model), `2026-10-06-m6-bag-mapping-design.md`

## 1. Problem

Bag order is type-first, then insertion order: `SessionRepository.clubs` sorts by
`ClubType.fromName(type).ordinal`, then `sortOrder`. `addClub` assigns
`maxSortOrder() + 1`, so a newly added club always lands at the bottom of its
type group — a new 3i appears under 9i (user report 2026-10-06). Settings ▸ BAG
supports add/rename/delete but has no way to fix the order.

## 2. Decisions

1. **Within-type reorder only.** The Driver → Wood → Hybrid → Driving Iron →
   Iron → Wedge → Putter grouping stays fixed; a drag is clamped to the
   dragged club's type group. Cross-type arrangement remains impossible (M5x
   §4.2 type model unchanged).
2. **Drag handle in a focused reorder panel**, opened by an `EDIT ORDER` chip
   in the BAG card; `DONE` returns to the normal settings page. Rejected
   inline handles: the settings page is one tall scroll column (dragging near
   edges would need fragile page auto-scroll), rows get busier, and browsing
   settings could start accidental drags.
3. **No schema change, no migration.** `clubs.sortOrder` already exists and is
   only interpreted within a type group; a drop renumbers only that group's
   values to `0..k−1`.
4. **Group membership is `ClubType.fromName(entity.type)`** — the same
   normalization the display sort uses — so legacy/garbage type strings cannot
   split a group.
5. **The repository owns the move** (`moveClub(id, toIndexInType)`). It never
   throws (same contract as `deleteClub`: flags `persistError`, which renders
   `DB WRITE FAILING` on the status strip); the `clubs` flow re-emits
   authoritative order, so a failed write visibly snaps back.
6. **Drag math is a pure JVM-tested file** (`BagReorderMath.kt`), matching the
   repo's plain-Compose + pure-logic discipline (no ViewModels).
7. **Add flow unchanged** — a new club still appends at the bottom of its type
   group; one drag places it.

## 3. Data & repository (`:core:data`)

### 3.1 `ClubDao` additions

```kotlin
@Query("SELECT * FROM clubs WHERE id = :id")
suspend fun findById(id: Long): ClubEntity?

@Query("SELECT * FROM clubs")
suspend fun all(): List<ClubEntity>

@Query("UPDATE clubs SET sortOrder = :sortOrder WHERE id = :id")
suspend fun updateSortOrder(id: Long, sortOrder: Int)
```

### 3.2 `SessionRepository.moveClub`

```kotlin
/**
 * Moves a club to a 0-based slot within its own type group. The slot is a
 * final position and is clamped to 0..k-1. Renumbers only that group's
 * sortOrder to 0..k-1. Same-slot drop is a no-op. Never throws.
 */
suspend fun moveClub(id: Long, toIndexInType: Int)
```

- One `db.withTransaction`:
  1. `findById(id)`; missing club → silent no-op.
  2. Group = `all()`
     `.filter { ClubType.fromName(it.type) == ClubType.fromName(club.type) }`
     `.sortedWith(compareBy({ it.sortOrder }, { it.id }))`.
  3. Remove the club, re-insert at `toIndexInType.coerceIn(0, group.size - 1)`.
  4. Unchanged order → no write.
  5. Else `updateSortOrder(id, index)` for each club in the new order, values
     `0..k-1`.
- `sortOrder` is deliberately **not** unique across groups (each group
  renumbers independently). The bag sort's primary key is the type ordinal,
  so cross-group value collisions are inert. Existing `addClub`'s
  `maxSortOrder() + 1` stays correct for appending within a group.
- `addClub` / `renameClub` / `deleteClub` / `endSession` behavior unchanged.

## 4. Pure logic (`:app`, `settings/BagReorderMath.kt`)

```kotlin
/** One displayed type group: its clubs plus the flat-list index span. */
data class BagGroup(val type: ClubType, val startIndex: Int, val clubs: List<ClubRecord>)

/** Consecutive grouping that preserves the repository's emitted order. */
fun bagGroups(clubs: List<ClubRecord>): List<BagGroup>

/** Drop slot for a drag; uniform row pitch; clamped to the group span. */
fun dropTargetIndex(fromIndex: Int, dragDeltaY: Float, rowHeightPx: Float, group: BagGroup): Int

/** Applies a from→to move to an id list (live drag preview). */
fun moveId(ids: List<Long>, fromIndex: Int, toIndex: Int): List<Long>
```

Edges, pinned:

| Input | Result |
|---|---|
| `dropTargetIndex` below group start (negative delta past the edge) | `group.startIndex` |
| `dropTargetIndex` past group end | last index of the group |
| Delta of half a row or more | moves one slot (`roundToInt` on the delta / pitch) |
| Delta less than half a row | current slot |
| Single-club group | always its own index |
| `moveId` same index | list unchanged |
| `moveId` from → to up/down | single move, other ids keep relative order |

## 5. UI

### 5.1 Entry — `SettingsScreen` BAG card

- New chip `EDIT ORDER`, same styling as the RENAME chip (`MetricLabel`,
  1 dp border, rounded 50), shown when any group has ≥ 2 clubs
  (`bagGroups(clubs).any { it.clubs.size >= 2 }`).
- Panel-local `reorderMode: Boolean` state. While true, the settings body
  renders only the reorder panel — RAPSODO AUTH / GREEN / TURF / SOUND / DEBUG
  cards are hidden.
- Switching tabs disposes `SettingsScreen` (existing `when (tab)` behavior),
  so the panel resets. Accepted — the mode is not persisted.
- New defaulted parameter: `onMoveClub: (clubId: Long, toIndexInType: Int) -> Unit = { _, _ -> }`.

### 5.2 Reorder panel (new `settings/BagReorderPanel.kt`)

- Header: `REORDER CLUBS` title; caption "Drag a club's handle to move it
  within its type group — the Driver → Putter group order is fixed."; `DONE`
  chip.
- Body: `Column` + `verticalScroll`. For each non-empty group from
  `bagGroups(clubs)`: a header — uppercase `ClubType.label`, `+ "S"` for the
  pluralizable labels (`WOODS`, `HYBRIDS`, `DRIVING IRONS`, `IRONS`,
  `WEDGES`); `DRIVER` and `PUTTER` stay singular — then its rows.
- Row (uniform 44 dp pitch, no inter-row gap): drawn drag handle at the left
  (small `Canvas`: three bars, 40 × 40 dp touch area), club name
  (`GolfTypography.Body`, `weight(1f)`), amber `TEST` tag when `isTemp`.
- Drag: `pointerInput` + `detectDragGestures` on the handle, immediate start
  (no long-press). The dragged row lifts (teal border / card background);
  other rows of the group animate ±row pitch into the preview slot using
  `moveId`; the target slot is `dropTargetIndex(...)` clamped to the group.
  Rows of other groups never move.
- Edge auto-scroll: while dragging with the pointer within ~48 dp of the
  panel's top/bottom edge, the panel scrolls (rate tuned on device).
- Release: if the slot changed → `onMoveClub(club.id, targetIndex - group.startIndex)`,
  then clear the working preview; the `clubs` flow re-emits and the panel
  renders the authoritative order. Same-slot drop → no call.
- Single-club groups: handle muted, drag disabled. TEST clubs are draggable
  like any other club; they still purge at END SESSION.
- State is `remember`ed panel-local (drag id, drag delta, working order); no
  ViewModel, matching the game-code discipline. Indices are in the flat clubs
  list — group headers carry no index.

### 5.3 Wiring — `AppRoot`

- `SettingsScreen(..., onMoveClub = { id, index -> scope.launch { sessionRepository.moveClub(id, index) } })`.
- `persistError` already renders `DB WRITE FAILING` on the status strip
  (line ~822); a failed move needs no extra UI — the flow re-emits the old
  order.

### 5.4 Behavior matrix

| Situation | Result |
|---|---|
| Drag within group | rows preview live; drop commits |
| Drag past group edge | clamped to the group's first/last slot |
| Drop on current slot | no write, no call |
| Group with one club | handle inert |
| TEST club | draggable; purged later as before |
| Pointer near panel edge mid-drag | panel auto-scrolls |
| Persist failure | order snaps back; `DB WRITE FAILING` |
| Club added | still appended at the bottom of its group |
| Rename / delete | unchanged; ordering untouched |
| Picker grid / ACTIVE CLUB pill / bag-mapping plan / history grouping | follow the new order via the one `clubs` flow |
| Tab switch mid-reorder | panel closes; no state kept |

## 6. Testing & verification

- **`BagReorderRepositoryTest`** (new, `:core:data`, mirrors
  `BagMappingRepositoryTest`):
  - Add 3i (lands last of IRONS) → `moveClub(3i, 0)` → irons order
    `3i, 4i, …, 9i`; wood and wedge order untouched; order survives a fresh
    repository read.
  - Clamping: target `99` lands at the group's last slot; negative target at
    the first.
  - Group isolation: only the moved group's `sortOrder` values change
    (assert via `all()` before/after).
  - Garbage/legacy type string (`"zgarbage"` → IRON) is moved within the
    IRON group.
  - Same-slot move leaves order unchanged.
  - Unknown id → silent no-op, no throw.
  - Duplicate `sortOrder` values within a group normalize to unique `0..k-1`
    after a move.
- **`BagReorderMathTest`** (new, `:app`): grouping preserves order; spans
  correct; drop clamping at both edges with large deltas; rounding at half a
  row; single-club group; `moveId` up/down/same.
- **Build:** `.\gradlew.bat build`; focused runs via typed tasks
  (`:core:data:testDebugUnitTest`, `:app:testDebugUnitTest`).
- **On-device (manual, tablet):** install debug; add `3i` → verify it lands
  under 9i; EDIT ORDER → drag 3i to the top of IRONS → DONE; check the range
  club picker order and a bag-mapping start; force-stop + relaunch → order
  persists; drag a wedge; verify edge auto-scroll feel in landscape.
  Gesture feel and auto-scroll rate are device-verified only.

## 7. Out of scope

Cross-type reorder, editing a club's type after creation, reordering from the
range picker, name-based auto-sorting, undo, non-gesture (button/keyboard)
reorder, any schema change.
