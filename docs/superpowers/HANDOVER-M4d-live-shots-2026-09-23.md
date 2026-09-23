# HANDOVER — M4d live shots in Range UI (golf sim)

Written 2026-09-23 after the M4d implementation branch was completed and
reviewed. Self-contained: what shipped, why, what is verified, and what
remains. Companion to `TEST-GUIDE-M4d-live-shots-2026-09-23.md` (the bench
checklist you'll run before merging the PR).

---

## 1. Project state

- Repo: `hpsmiles/mlm2pro-practice-sim`. Branch **`m4d-live-shots`**
  (3 commits on master `2e71ea6`, merge-base fast-forward, tree clean apart
  from docs, awaiting push):
  - `5460003` — `feat(app): RangeSession state holder for live + demo shots`
  - `ef8f86e` — `feat(core-connect): onMisread callback routes live misread indications`
  - `edfb8b8` — `feat(app): live BLE shots render in Range UI + no-read pill`
  - `docs` commit — plan + this guide + this handover.
- Milestones: **M0–M4c COMPLETE.** M4d implemented; **bench validation
  pending** (the test guide) → then PR → merge.
- App installed and launched on the Lenovo TB373FU tablet from this branch
  (2026-09-23, before bench validation).
- Test census: **183** (core:ble 51 / core:connect 40 / app 13 incl. 5 new
  RangeSessionTest / core:designsystem 14 / core:physics 65).
  Full build: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat build`.

## 2. What M4d changed (goal summary)

Real decoded MLM2PRO shots now reach the Range UI (previously demo-only),
and live misreads surface as a dismissible "no read (N)" pill instead of
being dropped.

The chain, end to end:

```
MLM2PRO notification
  → Mlm2proGattClient.handleNotification        (core/connect; binder thread)
      Shot        → onMeasurement(BallData)
      Misread 05 00 / MisreadAlert / all-zero MEASUREMENT → onMisread()
  → AppRoot DisposableEffect hops binder→main   (scope.launch, rememberCoroutineScope)
  → RangeSession.add / markMisread              (pure state holder)
      add: LaunchConditions require-guarded conversion → BallFlightEngine.simulate
           → DisplayShot appended → tick++
      markMisread: 500 ms coalescing → misreadCount++
  → RangeScreen renders from session.shots / session.tick / session.misreadCount
```

Key design decisions (all per plan `2026-09-23-m4d-live-shots.md`):

- **`RangeSession`** (`app/...range/RangeSession.kt`) is a plain state
  holder owned by `AppRoot` (`remember { RangeSession() }`) — repo
  convention, no ViewModels/DI. Both the demo fold (`fire()`, gated on
  `demo`) and live callbacks append to the SAME list, so demo and live
  shots interleave in one session.
- **Guard at the boundary:** `add()` catches the `IllegalArgumentException`
  from `LaunchConditions` `require()` (ballSpeed ∉ [0.5, 100] m/s, spin ∉
  [0, 12000] rpm, VLA ∉ [0, 85], dir/axis ∉ ±90°) and appends nothing — a
  bad live decode can never crash the UI thread.
- **Misread coalescing:** one real mishit emits EVENTS `05 00` AND the
  all-zero MEASUREMENT sentinel ~200 ms apart (M4c evidence, event-010 +
  event-027). `markMisread()` coalesces anything within 500 ms to ONE pill
  increment.
- **Threading:** the ONLY sanctioned off-main touch is `scope.launch` from
  the main-dispatched composition scope; all snapshot-state mutation
  happens after the hop. Verified in the whole-branch review.
- **Tracer reset:** `LaunchedEffect(session.tick)` sets `playFraction = 0f`
  when any new shot lands (demo OR live). `RangeScreen`'s private shots
  list is gone; it takes `session: RangeSession` and all references go
  through `session.shots`.
- **Task 2 dropped a dead branch** in `handleNotification` (the old no-op
  `_state.value = _state.value`), and replaced the `as? Shot` silent cast
  with an exhaustive sealed `when` (`Shot`/`Misread`/`Malformed`) — a
  future `BallDataResult` variant will force a compile revisit.

## 3. Review status / known minor deviations (all adjudicated, deferred)

Final whole-branch review verdict: **READY TO MERGE** (code gate). No
Critical/Important findings. Deferred Minors (fix opportunistically):

1. **Stale comment** in `RangeScreen.kt` (~line 222): pill placement comment
   still says "next to the DEMO controls" but the pill is anchored
   `BottomStart` (TopStart was occupied by the DEMO/TRACER/PREV column).
   One-line comment fix on next touch.
2. **Non-monotonic clock:** `RangeSession.clockMs` default is
   `System.currentTimeMillis()`; an NTP backward step could swallow one
   pill increment. Fast-follow: in AppRoot, set
   `session.clockMs = { SystemClock.elapsedRealtime() }` (injectable clock
   exists precisely for this).
3. **`dismissMisreads()` deliberately does NOT reset `lastMisreadMs`** —
   that's correct as-is: a dismissal mid-pair must still swallow the
   ~200 ms-later paired sentinel (reviewed downgraded; do not "fix").
4. **Product note — demo mode also ingests live shots:** `onMeasurement` is
   not gated by `demo` (per plan architecture; the gate lives on `fire()`).
   With a live link while toggled to DEMO, real shots will appear in the
   list. Bench check 2 will reveal whether this surprises anyone; decide
   product intent then.
5. Plan-doc snippets had two defects fixed during implementation (don't
   copy old snippets verbatim): `lastMisreadMs = Long.MIN_VALUE` overflow →
   `0L`; `ShotResult.carryDistanceM` → real field `carryM`; a
   `when`-else snippet needed braces; on Android app modules use
   `:app:testDebugUnitTest` (bare `:app:test --tests` is invalid).
6. The plan doc (`docs/superpowers/plans/2026-09-23-m4d-live-shots.md`) was
   untracked and is committed with the docs (M4c pattern).

## 4. Pre-bench state (what the tester will have done)

- App installed from this branch + launched (adb path:
  `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"`).
- Follow `TEST-GUIDE-M4d-live-shots-2026-09-23.md` — six checks, including
  the two behaviour claims code review cannot prove: (a) live shot → list
  within ~2 s with plausible 8i metrics; (b) one duff → "no read (1)"
  exactly once, dismiss works.

## 5. After the bench (merge + follow-on work)

1. If all six checks pass: PR title **`M4d: Live shots in Range UI`**, body
   with the ticked checklist boxes, merge, delete branch (M4b/M4c pattern).
2. If a check fails: record exactly what happened in the guide's failure
   notes and debug per repo convention (systematic root cause; the failure
   is most likely in the glue, since decode paths are fixture-pinned).
3. Fast-follow candidates (from the review): `elapsedRealtime()` clock
   wiring; stale comment; product decision on demo-mode live ingestion.
4. Next milestone per the master plan: misread data now flows as non-shots
   instead of zero rows — this is the raw material for **bag mapping &
   misread filtering in aggregation** (Feature 1, M4d scope notes §5b of
   the M4c handover): never append misreads as BallData rows, keep the 500
   ms coalescing, and keep duff/suspect-metric heuristics out of scope
   until gapping data exists.
5. Bench logs if needed: `adb logcat -d -s BenchGatt`; capture export path
   and drill unchanged from M4c (`settings → CAPTURE ON → EXPORT`).

## 6. Conventions reminder (unchanged from M4c doc §2/§7)

- JDK not on PATH; `$env:JAVA_HOME` first on every gradle command.
- PowerShell 5.1: no `&&`; multi-line commits via `git commit -F` file.
- TDD for pure logic; GATT/UI glue is bench-validated, not unit-tested.
- Single-device constraint: no cloud/PC dependencies ever.
