# Games Polish Batch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add high-score detection + celebration to both games, recalibrate the Break-the-Pane pane, add BP difficulty/green sizing and an intersection dot, and stop the stale MisRead pill at launch.

**Architecture:** Records are derived from the existing `game_results` table (no schema change): AppRoot snapshots the prior best before inserting the new row, compares via a pure `GameRecord` object, and publishes the result to the game state holder for the overlay. The pane fix is a one-line physics profile change. BP difficulty reuses the existing `Difficulty` enum and the existing nullable `difficulty` column. The MisRead fix stops seeding the live counter from the persisted open-session count.

**Tech Stack:** Kotlin, Jetpack Compose (Canvas + `withFrameNanos`), Room, JUnit4 (+ Robolectric for `:core:data`), Gradle.

**Spec:** `docs/superpowers/specs/2026-09-30-games-polish-highscore-pane-misread-design.md`

> **Note on one deviation from the spec:** the spec suggested a pure `GameRecord.distanceBin`, but `:core:data` cannot depend on `:app`. The distance-bin helper therefore lives in `SessionRepository` (both the save and the best-query use it, so the key cannot drift). `GameRecord` stays pure comparison + overlay copy.

> **Windows/Gradle reminder (AGENTS.md):** set `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` before any Gradle command. Gradle flags precede task names. Use the typed `testDebugUnitTest` variant for `--tests` (the aggregate `test` rejects it). Build everything with `.\gradlew.bat build`.

---

## File Structure

**Create**
- `app/src/main/kotlin/com/hpsmiles/golfsim/games/GameRecord.kt` — pure record comparison + overlay copy.
- `app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPaneGreen.kt` — pure per-difficulty green radius.
- `app/src/main/kotlin/com/hpsmiles/golfsim/games/PaneIntersection.kt` — pure pane-crossing + reveal fraction.
- `app/src/main/kotlin/com/hpsmiles/golfsim/games/ConfettiBurst.kt` — one-shot confetti composable.
- `app/src/test/kotlin/com/hpsmiles/golfsim/games/GameRecordTest.kt`
- `app/src/test/kotlin/com/hpsmiles/golfsim/games/BreakPaneGreenTest.kt`
- `app/src/test/kotlin/com/hpsmiles/golfsim/games/PaneIntersectionTest.kt`

**Modify**
- `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/ReferenceTrajectory.kt` (+ test)
- `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/GameResultDao.kt`
- `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt` (+ test)
- `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/entity/GameResultEntity.kt` (doc only)
- `app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakThePaneGame.kt` (+ test)
- `app/src/main/kotlin/com/hpsmiles/golfsim/games/TargetPracticeGame.kt`
- `app/src/main/kotlin/com/hpsmiles/golfsim/games/GamesScreen.kt`
- `app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPanePlay.kt`
- `app/src/main/kotlin/com/hpsmiles/golfsim/games/TargetPracticePlay.kt`
- `app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPaneCanvas.kt`
- `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt`
- `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeSession.kt` (+ test)

---

## Task 1: Recalibrate the Break-the-Pane reference trajectory

**Files:**
- Modify: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/ReferenceTrajectory.kt:9-13,52-56`
- Test: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/ReferenceTrajectoryTest.kt`

- [ ] **Step 1: Write the failing test**

Add these two tests to `ReferenceTrajectoryTest.kt` (keep the existing three):

```kotlin
    @Test
    fun `recalibrated mid bracket anchors the pane middle at iron distances`() {
        // 2026-09-30 recalibration: mid/iron bracket 20 deg / 6500 rpm.
        // refCross(125) ~= 10.78 m, refCross(140) ~= 12.50 m (calibration sweep).
        val z125 = ReferenceTrajectory.paneCrossingHeightM(125.0)
        val z140 = ReferenceTrajectory.paneCrossingHeightM(140.0)
        assertTrue("zRef(125)=$z125", z125 in 10.3..11.3)
        assertTrue("zRef(140)=$z140", z140 in 12.0..13.0)
    }
```

Then tighten the existing `pane crossing height inside reachable band for 140m` test body to:

```kotlin
        val z = ReferenceTrajectory.paneCrossingHeightM(140.0)
        assertTrue("zRef $z outside [12, 13]", z in 12.0..13.0)
```

- [ ] **Step 2: Run test to verify it fails**

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :core:physics:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.physics.ReferenceTrajectoryTest"
```
Expected: FAIL — `zRef(125)≈9.66` / `zRef(140)≈11.20` are below the new bands.

- [ ] **Step 3: Change the profile and its doc comment**

In `ReferenceTrajectory.kt`, replace the header bracket docs (lines 11-13):

```kotlin
 *   - target <= 120 m  : 21 degrees, 7500 rpm
 *   - 120 < target <= 220 m : 20 degrees, 6500 rpm
 *   - target > 220 m   : 14 degrees, 3000 rpm
```

and `profileFor` (lines 52-56):

```kotlin
    private fun profileFor(targetM: Double): Pair<Double, Int> = when {
        targetM <= 120.0 -> 21.0 to 7500
        targetM <= 220.0 -> 20.0 to 6500
        else -> 14.0 to 3000
    }
```

Then update the stale comment in `app/src/test/kotlin/com/hpsmiles/golfsim/games/PaneGeomTest.kt:11`:

```kotlin
    // 140 m: plane y=35, cellH=2.8, cellW=5.6, zRef~=12.5, bottom~=8.3.
```

*(No absolute `zRefM` value is asserted there — those tests use `pane.zRefM` symbolically — so only the comment changes.)*

- [ ] **Step 4: Run tests to verify they pass**

```
.\gradlew.bat :core:physics:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.physics.ReferenceTrajectoryTest"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/ReferenceTrajectory.kt core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/ReferenceTrajectoryTest.kt app/src/test/kotlin/com/hpsmiles/golfsim/games/PaneGeomTest.kt
git commit -m "fix(physics): recalibrate pane reference profile to 20deg/6500rpm"
```

---

## Task 2: Pure high-score comparison (`GameRecord`)

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/games/GameRecord.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/games/GameRecordTest.kt`

- [ ] **Step 1: Write the failing test**

Create `GameRecordTest.kt`:

```kotlin
package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.core.data.entity.GameModes
import org.junit.Assert.assertEquals
import org.junit.Test

class GameRecordTest {

    @Test
    fun `no prior best is first score - no celebration`() {
        val c = GameRecord.compare(prevBest = null, score = 90, lowerIsBetter = false)
        assertEquals(RecordOutcome.FIRST_SCORE, c.outcome)
        assertEquals(null, c.best)
        assertEquals(0, c.delta)
    }

    @Test
    fun `higher is better detects a new record and its margin`() {
        val c = GameRecord.compare(prevBest = 90, score = 105, lowerIsBetter = false)
        assertEquals(RecordOutcome.NEW_RECORD, c.outcome)
        assertEquals(90, c.best)
        assertEquals(15, c.delta)
    }

    @Test
    fun `higher is better off-record reports the shortfall`() {
        val c = GameRecord.compare(prevBest = 105, score = 90, lowerIsBetter = false)
        assertEquals(RecordOutcome.OFF_RECORD, c.outcome)
        assertEquals(105, c.best)
        assertEquals(15, c.delta)
    }

    @Test
    fun `a tie is not a new record`() {
        val c = GameRecord.compare(prevBest = 90, score = 90, lowerIsBetter = false)
        assertEquals(RecordOutcome.OFF_RECORD, c.outcome)
        assertEquals(0, c.delta)
    }

    @Test
    fun `lower is better detects a new record and its margin`() {
        val c = GameRecord.compare(prevBest = 14, score = 11, lowerIsBetter = true)
        assertEquals(RecordOutcome.NEW_RECORD, c.outcome)
        assertEquals(14, c.best)
        assertEquals(3, c.delta)
    }

    @Test
    fun `lower is better off-record reports the excess shots`() {
        val c = GameRecord.compare(prevBest = 11, score = 14, lowerIsBetter = true)
        assertEquals(RecordOutcome.OFF_RECORD, c.outcome)
        assertEquals(3, c.delta)
    }

    @Test
    fun `break pane is lower-is-better, target practice is not`() {
        assertEquals(true, GameRecord.lowerIsBetter(GameModes.BREAK_PANE))
        assertEquals(false, GameRecord.lowerIsBetter(GameModes.TARGET_PRACTICE))
    }

    @Test
    fun `overlay copy names the unit per game`() {
        val tpOff = RecordComparison(RecordOutcome.OFF_RECORD, 105, 15)
        assertEquals("15 points off your best (105).", GameRecord.detail(tpOff, lowerIsBetter = false))
        val bpOff = RecordComparison(RecordOutcome.OFF_RECORD, 11, 3)
        assertEquals("3 shots off your best (11).", GameRecord.detail(bpOff, lowerIsBetter = true))
        val new = RecordComparison(RecordOutcome.NEW_RECORD, 90, 12)
        assertEquals("Beat your best (90) by 12 points.", GameRecord.detail(new, lowerIsBetter = false))
        assertEquals("NEW HIGH SCORE", GameRecord.headline(new, lowerIsBetter = false))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.games.GameRecordTest"
```
Expected: FAIL — `GameRecord` unresolved.

- [ ] **Step 3: Write the implementation**

Create `GameRecord.kt`:

```kotlin
package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.core.data.entity.GameModes

/** Cross-game high-score comparison outcome (spec 2026-09-30 §3.2). */
enum class RecordOutcome { FIRST_SCORE, NEW_RECORD, OFF_RECORD }

/** A completed game's score compared against its record key's prior best. */
data class RecordComparison(val outcome: RecordOutcome, val best: Int?, val delta: Int)

/**
 * Pure high-score comparison + overlay copy. Records are derived from the
 * existing game_results table (no schema change). TP scores points
 * (higher-is-better); BP scores shots taken (lower-is-better).
 */
object GameRecord {

    /** BP scores shots-taken (lower is better); TP scores points (higher is better). */
    fun lowerIsBetter(mode: String): Boolean = mode == GameModes.BREAK_PANE

    /**
     * Compares [score] against [prevBest]. Null [prevBest] means no matching
     * prior row (first score at this key) -> FIRST_SCORE, no celebration.
     * [delta] is the improvement (NEW_RECORD) or the shortfall (OFF_RECORD),
     * always >= 0.
     */
    fun compare(prevBest: Int?, score: Int, lowerIsBetter: Boolean): RecordComparison =
        when {
            prevBest == null -> RecordComparison(RecordOutcome.FIRST_SCORE, null, 0)
            better(score, prevBest, lowerIsBetter) ->
                RecordComparison(RecordOutcome.NEW_RECORD, prevBest, kotlin.math.abs(prevBest - score))
            else ->
                RecordComparison(RecordOutcome.OFF_RECORD, prevBest, kotlin.math.abs(score - prevBest))
        }

    /** Short overlay headline. */
    fun headline(comparison: RecordComparison, lowerIsBetter: Boolean): String = when (comparison.outcome) {
        RecordOutcome.FIRST_SCORE -> "FIRST SCORE"
        RecordOutcome.NEW_RECORD -> "NEW HIGH SCORE"
        RecordOutcome.OFF_RECORD -> if (lowerIsBetter) "SHOTS OFF YOUR BEST" else "PTS OFF YOUR BEST"
    }

    /** Second overlay line: the margin vs the prior best. */
    fun detail(comparison: RecordComparison, lowerIsBetter: Boolean): String {
        val unit = if (lowerIsBetter) "shots" else "points"
        return when (comparison.outcome) {
            RecordOutcome.FIRST_SCORE -> "First score at this distance and difficulty."
            RecordOutcome.NEW_RECORD -> "Beat your best (${comparison.best}) by ${comparison.delta} $unit."
            RecordOutcome.OFF_RECORD -> "${comparison.delta} $unit off your best (${comparison.best})."
        }
    }

    private fun better(score: Int, best: Int, lowerIsBetter: Boolean): Boolean =
        if (lowerIsBetter) score < best else score > best
}
```

- [ ] **Step 4: Run test to verify it passes**

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.games.GameRecordTest"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/GameRecord.kt app/src/test/kotlin/com/hpsmiles/golfsim/games/GameRecordTest.kt
git commit -m "feat(games): pure GameRecord high-score comparison"
```

---

## Task 3: Persist-layer best-score query

**Files:**
- Modify: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/GameResultDao.kt`
- Modify: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt:295-320,360-364`
- Modify: `core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/entity/GameResultEntity.kt:21`
- Test: `core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/SessionRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `SessionRepositoryTest.kt` (imports already cover `ShotSource`, `runTest`, `assertEquals`, `assertNull`):

```kotlin
    @Test
    fun `bestGameScore returns the prior best per key and never the new row`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        // Two TP rows at the same key (140 m -> bin 140), plus a different key.
        repo.saveGameResult("TARGET_PRACTICE", "MEDIUM", 140.0, 90, ShotSource.LIVE, 1L)
        repo.saveGameResult("TARGET_PRACTICE", "MEDIUM", 140.0, 80, ShotSource.LIVE, 2L)
        repo.saveGameResult("TARGET_PRACTICE", "HARD", 140.0, 120, ShotSource.LIVE, 3L)
        repo.saveGameResult("BREAK_PANE", "MEDIUM", 140.0, 12, ShotSource.LIVE, 4L)
        repo.saveGameResult("BREAK_PANE", "MEDIUM", 140.0, 9, ShotSource.LIVE, 5L)

        assertEquals(90, repo.bestGameScore("TARGET_PRACTICE", "MEDIUM", 140.0, lowerIsBetter = false))
        assertEquals(120, repo.bestGameScore("TARGET_PRACTICE", "HARD", 140.0, lowerIsBetter = false))
        assertEquals(9, repo.bestGameScore("BREAK_PANE", "MEDIUM", 140.0, lowerIsBetter = true))
        // No matching row -> null (first score), never a celebration.
        assertNull(repo.bestGameScore("BREAK_PANE", "HARD", 140.0, lowerIsBetter = true))
    }

    @Test
    fun `best score keys on the nearest-10m distance bin`() = runTest {
        val repo = newRepo()
        repo.initializeAndRestore()
        // 141 and 144 both bin to 140; 155 bins to 160 (a different key).
        repo.saveGameResult("TARGET_PRACTICE", "MEDIUM", 141.0, 100, ShotSource.LIVE, 1L)
        assertEquals(100, repo.bestGameScore("TARGET_PRACTICE", "MEDIUM", 144.0, lowerIsBetter = false))
        assertNull(repo.bestGameScore("TARGET_PRACTICE", "MEDIUM", 155.0, lowerIsBetter = false))
    }
```

- [ ] **Step 2: Run test to verify it fails**

```
.\gradlew.bat :core:data:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.data.SessionRepositoryTest"
```
Expected: FAIL — `bestGameScore` unresolved.

- [ ] **Step 3: Add the DAO queries**

Replace the body of `GameResultDao` with:

```kotlin
    @Insert
    suspend fun insert(result: GameResultEntity): Long

    @Query("SELECT * FROM game_results ORDER BY playedAtEpochMs DESC, id DESC")
    suspend fun getAll(): List<GameResultEntity>

    @Query("SELECT * FROM game_results ORDER BY playedAtEpochMs DESC, id DESC")
    fun observeAll(): Flow<List<GameResultEntity>>

    /** Prior-best score (higher is better) for a record key, or null when none. */
    @Query(
        "SELECT MAX(score) FROM game_results " +
            "WHERE mode = :mode AND difficulty = :difficulty AND distanceBin = :distanceBin",
    )
    suspend fun bestHighScore(mode: String, difficulty: String, distanceBin: Int): Int?

    /** Prior-best score (lower is better) for a record key, or null when none. */
    @Query(
        "SELECT MIN(score) FROM game_results " +
            "WHERE mode = :mode AND difficulty = :difficulty AND distanceBin = :distanceBin",
    )
    suspend fun bestLowScore(mode: String, difficulty: String, distanceBin: Int): Int?
```

- [ ] **Step 4: Add the repository query + shared bin helper**

In `SessionRepository.kt`, replace the `saveGameResult` body (lines 295-320) with:

```kotlin
    /**
     * One summary row per completed game (M5.5 spec S7). Never throws -
     * same contract as [appendShot]. Game shots themselves are never persisted.
     */
    suspend fun saveGameResult(
        mode: String,
        difficulty: String?,
        distanceM: Double,
        score: Int,
        source: ShotSource,
        playedAtEpochMs: Long,
    ) {
        try {
            gameResultDao.insert(
                GameResultEntity(
                    mode = mode,
                    difficulty = difficulty,
                    distanceBin = distanceBin(distanceM),
                    score = score,
                    source = source.code,
                    playedAtEpochMs = playedAtEpochMs,
                ),
            )
            persistError.value = false
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "saveGameResult failed", t)
            persistError.value = true
        }
    }

    /**
     * Prior best for a record key (mode + difficulty + nearest-10m bin), or
     * null when no matching row exists. MUST be read BEFORE [saveGameResult]
     * for the same game, or it reads the row just written. Never throws — a
     * query failure also yields null (no celebration), same contract as
     * [appendShot].
     */
    suspend fun bestGameScore(
        mode: String,
        difficulty: String,
        distanceM: Double,
        lowerIsBetter: Boolean,
    ): Int? = try {
        val bin = distanceBin(distanceM)
        if (lowerIsBetter) {
            gameResultDao.bestLowScore(mode, difficulty, bin)
        } else {
            gameResultDao.bestHighScore(mode, difficulty, bin)
        }
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        Log.e(TAG, "bestGameScore failed", t)
        null
    }
```

In the `companion object`, add after `private const val MAX_CLUB = 20`:

```kotlin
        /** Target distance rounded to the nearest 10 m — the game record key. */
        internal fun distanceBin(distanceM: Double): Int = (Math.round(distanceM / 10.0) * 10).toInt()
```

- [ ] **Step 5: Update the entity doc comment**

In `GameResultEntity.kt:21`, replace `/** EASY/MEDIUM/HARD; null for Break the Pane. */` with:

```kotlin
    /** EASY/MEDIUM/HARD. Always set; legacy Break-the-Pane rows may be null. */
```

- [ ] **Step 6: Run tests to verify they pass**

```
.\gradlew.bat :core:data:testDebugUnitTest --tests "com.hpsmiles.golfsim.core.data.SessionRepositoryTest"
```
Expected: PASS (all pre-existing tests plus the two new ones).

- [ ] **Step 7: Commit**

```bash
git add core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/dao/GameResultDao.kt core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/SessionRepository.kt core/data/src/main/kotlin/com/hpsmiles/golfsim/core/data/entity/GameResultEntity.kt core/data/src/test/kotlin/com/hpsmiles/golfsim/core/data/SessionRepositoryTest.kt
git commit -m "feat(data): prior-best game score query keyed by mode/difficulty/distance bin"
```

---

## Task 4: Break-the-Pane difficulty + green sizing

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPaneGreen.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakThePaneGame.kt:26-64,122-127`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/games/BreakPaneGreenTest.kt`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/games/BreakThePaneGameTest.kt:116`

- [ ] **Step 1: Write the failing test**

Create `BreakPaneGreenTest.kt`:

```kotlin
package com.hpsmiles.golfsim.games

import org.junit.Assert.assertEquals
import org.junit.Test

class BreakPaneGreenTest {

    @Test
    fun `green radii at 140m per difficulty`() {
        assertEquals(10.0, BreakPaneGreen.radiusAt140m(Difficulty.EASY), 1e-9)
        assertEquals(8.0, BreakPaneGreen.radiusAt140m(Difficulty.MEDIUM), 1e-9)
        assertEquals(6.0, BreakPaneGreen.radiusAt140m(Difficulty.HARD), 1e-9)
    }

    @Test
    fun `game green radius scales the difficulty radius by target`() {
        val game = BreakThePaneGame()
        game.start(70.0, Difficulty.EASY)
        assertEquals(5.0, game.greenRadiusM(), 1e-9) // 10 * 70/140
        game.start(140.0, Difficulty.HARD)
        assertEquals(6.0, game.greenRadiusM(), 1e-9)
    }

    @Test
    fun `difficulty is persisted on the result`() {
        val game = BreakThePaneGame()
        game.start(140.0, Difficulty.HARD)
        assertEquals(Difficulty.HARD, game.difficulty)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.games.BreakPaneGreenTest"
```
Expected: FAIL — `BreakPaneGreen` unresolved and `start(70.0, Difficulty.EASY)` arity mismatch.

- [ ] **Step 3: Write `BreakPaneGreen`**

Create `BreakPaneGreen.kt`:

```kotlin
package com.hpsmiles.golfsim.games

/**
 * Break the Pane green radius per difficulty, at the 140 m reference target
 * (spec 2026-09-30 §5). Scaled by target in [BreakThePaneGame.greenRadiusM].
 */
object BreakPaneGreen {

    fun radiusAt140m(difficulty: Difficulty): Double = when (difficulty) {
        Difficulty.EASY -> 10.0
        Difficulty.MEDIUM -> 8.0
        Difficulty.HARD -> 6.0
    }
}
```

- [ ] **Step 4: Add difficulty to `BreakThePaneGame`**

In `BreakThePaneGame.kt`, add imports:

```kotlin
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
```

Add the record state (after `val tick` at line 30):

```kotlin
    /** High-score comparison for the last completed game; null until AppRoot loads it. */
    val record: MutableState<RecordComparison?> = mutableStateOf(null)
```

Add the difficulty field (next to `targetM` at lines 45-46):

```kotlin
    var difficulty: Difficulty = Difficulty.MEDIUM
        private set
```

Replace `start` (lines 51-58) with:

```kotlin
    fun start(targetM: Double, difficulty: Difficulty = Difficulty.MEDIUM) {
        this.targetM = targetM
        this.difficulty = difficulty
        pane = PaneGeom(targetM)
        shots.clear()
        brokenCells.clear()
        lastFeedback = ""
        resultTaken = false
        record.value = null
    }
```

Replace `greenRadiusM` (lines 63-64) with:

```kotlin
    /** Green oval radius, scaled to the target by difficulty (spec 2026-09-30 §5). */
    fun greenRadiusM(): Double = BreakPaneGreen.radiusAt140m(difficulty) * targetM / 140.0

    /** Publishes the high-score comparison for the just-completed game. */
    fun setRecord(comparison: RecordComparison) {
        record.value = comparison
    }
```

Replace `takeResult` (lines 122-127) with:

```kotlin
    /** Non-null exactly once once [complete]; the summary AppRoot persists. */
    fun takeResult(): GameResultPayload? {
        if (!complete || resultTaken) return null
        resultTaken = true
        return GameResultPayload(GameModes.BREAK_PANE, difficulty.name, targetM, shotCount)
    }
```

- [ ] **Step 5: Update the existing BP test expectation**

In `BreakThePaneGameTest.kt:116`, replace `assertEquals(null, result.difficulty)` with:

```kotlin
        assertEquals("MEDIUM", result.difficulty)
```

- [ ] **Step 6: Run tests to verify they pass**

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.games.BreakPaneGreenTest" --tests "com.hpsmiles.golfsim.games.BreakThePaneGameTest"
```
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPaneGreen.kt app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakThePaneGame.kt app/src/test/kotlin/com/hpsmiles/golfsim/games/BreakPaneGreenTest.kt app/src/test/kotlin/com/hpsmiles/golfsim/games/BreakThePaneGameTest.kt
git commit -m "feat(games): Break-the-Pane difficulty tiers and green sizing"
```

---

## Task 5: Break-the-Pane difficulty picker

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/GamesScreen.kt:85-96,183-212`

- [ ] **Step 1: Pass difficulty into `start`**

In `GamesScreen.kt`, replace the `SETUP_BP` branch (lines 85-96) with:

```kotlin
        pickerState == "SETUP_BP" -> {
            Column(modifier = modifier.fillMaxSize().background(GolfColors.Base).padding(GolfSpacing.Xl)) {
                BreakPaneSetup(
                    onStart = { target, difficulty ->
                        breakPane.start(target, difficulty)
                        onActiveGameChange(GameMode.BREAK_PANE)
                        pickerState = "PLAYING"
                    },
                    onCancel = { pickerState = "PICKER" },
                )
            }
        }
```

- [ ] **Step 2: Add the chips to `BreakPaneSetup`**

Replace `BreakPaneSetup` (lines 183-212) with:

```kotlin
@Composable
private fun BreakPaneSetup(onStart: (Double, Difficulty) -> Unit, onCancel: () -> Unit) {
    var sliderM by remember { mutableStateOf(140.0) }
    var text by remember { mutableStateOf("140") }
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }

    Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Lg)) {
        Text("BREAK THE PANE", style = GolfTypography.ScreenTitle, color = GolfColors.Teal)
        Slider(
            value = sliderM.toFloat(),
            onValueChange = {
                sliderM = it.toDouble()
                text = String.format(Locale.US, "%.0f", sliderM)
            },
            valueRange = DIST_MIN_M.toFloat()..DIST_MAX_M.toFloat(),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                text = raw
                raw.toDoubleOrNull()?.let { sliderM = it.coerceIn(DIST_MIN_M, DIST_MAX_M) }
            },
            label = { Text("Distance (m, $DIST_MIN_M-$DIST_MAX_M)") },
            modifier = Modifier.width(260.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
            Difficulty.entries.forEach { d ->
                Box(
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (difficulty == d) GolfColors.Teal else GolfColors.Line,
                            RoundedCornerShape(GolfSpacing.Sm),
                        )
                        .clickable { difficulty = d }
                        .padding(horizontal = GolfSpacing.Md, vertical = GolfSpacing.Xs),
                ) {
                    Text(d.name, style = GolfTypography.Status, color = if (difficulty == d) GolfColors.Teal else GolfColors.TextSecondary)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
            Button(onClick = { onStart(sliderM, difficulty) }) { Text("START") }
            OutlinedButton(onClick = onCancel) { Text("CANCEL") }
        }
    }
}
```

- [ ] **Step 3: Compile**

```
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/GamesScreen.kt
git commit -m "feat(games): add Break-the-Pane difficulty picker"
```

---

## Task 6: Wire record comparison through AppRoot + game holders

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/TargetPracticeGame.kt:24,50-56`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt:48-70,164-189`

- [ ] **Step 1: Add record state to `TargetPracticeGame`**

Add imports to `TargetPracticeGame.kt`:

```kotlin
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
```

Add after `val tick` (line 25):

```kotlin
    /** High-score comparison for the last completed game; null until AppRoot loads it. */
    val record: MutableState<RecordComparison?> = mutableStateOf(null)
```

Replace `start` (lines 50-56) with:

```kotlin
    fun start(targetM: Double, difficulty: Difficulty) {
        this.targetM = targetM
        this.difficulty = difficulty
        started = true
        shots.clear()
        resultTaken = false
        record.value = null
    }

    /** Publishes the high-score comparison for the just-completed game. */
    fun setRecord(comparison: RecordComparison) {
        record.value = comparison
    }
```

*(`BreakThePaneGame.record` / `setRecord` were added in Task 4.)*

- [ ] **Step 2: Add the imports to `AppRoot.kt`**

In `AppRoot.kt`, alongside the existing `com.hpsmiles.golfsim.games.*` imports (lines 57-61), add:

```kotlin
import com.hpsmiles.golfsim.games.GameRecord
import com.hpsmiles.golfsim.games.GameResultPayload
import com.hpsmiles.golfsim.games.RecordComparison
```

- [ ] **Step 3: Add `completeGame` and rewrite `routeShot`**

In `AppRoot.kt`, insert this helper immediately before `routeShot` (before line 169), and replace the whole `routeShot` body:

```kotlin
    /**
     * 2026-09-30: a game just completed. Snapshot the PRIOR best for the
     * record key, publish the comparison to the game holder, then persist the
     * new row. The query must precede the insert or it reads its own write.
     */
    fun completeGame(
        payload: GameResultPayload,
        onRecord: (RecordComparison) -> Unit,
        clockMs: () -> Long,
        source: ShotSource,
    ) {
        val lowerIsBetter = GameRecord.lowerIsBetter(payload.mode)
        val difficulty = payload.difficulty
        scope.launch {
            if (difficulty != null) {
                val prevBest = sessionRepository.bestGameScore(payload.mode, difficulty, payload.targetM, lowerIsBetter)
                onRecord(GameRecord.compare(prevBest, payload.score, lowerIsBetter))
            }
            sessionRepository.saveGameResult(
                payload.mode, payload.difficulty, payload.targetM, payload.score, source, clockMs(),
            )
        }
    }

    /**
     * M5.5 dispatch (spec §3): a shot goes to the active game OR the range
     * session — never both. Game shots are never range-persisted; completed
     * games emit exactly one summary row via takeResult().
     */
    fun routeShot(ballData: com.hpsmiles.golfsim.core.ble.BallData, source: com.hpsmiles.golfsim.core.data.record.ShotSource) {
        when (activeGame) {
            GameMode.NONE -> persist(session.add(ballData), source)
            GameMode.TARGET_PRACTICE -> {
                targetPractice.add(ballData)
                targetPractice.takeResult()?.let {
                    completeGame(it, targetPractice::setRecord, { targetPractice.clockMs() }, source)
                }
            }
            GameMode.BREAK_PANE -> {
                breakPane.add(ballData)
                breakPane.takeResult()?.let {
                    completeGame(it, breakPane::setRecord, { breakPane.clockMs() }, source)
                }
            }
        }
    }
```

- [ ] **Step 4: Compile**

```
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/TargetPracticeGame.kt app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt
git commit -m "feat(games): publish prior-best comparison on game completion"
```

---

## Task 7: Confetti celebration composable

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/games/ConfettiBurst.kt`

- [ ] **Step 1: Write the composable**

Create `ConfettiBurst.kt`:

```kotlin
package com.hpsmiles.golfsim.games

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import kotlin.random.Random

/**
 * One-shot confetti burst for a NEW high score (spec 2026-09-30 §3.4). Drawn
 * with the games' manual withFrameNanos loop (no Animatable), ~2.5 s, gravity
 * + horizontal drift. Amber stays reserved for the live tracer, so the
 * palette is teal / white / gold.
 */
@Composable
fun ConfettiBurst(
    modifier: Modifier = Modifier,
    durationMs: Float = 2500f,
    particleCount: Int = 64,
) {
    val palette = remember {
        listOf(GolfColors.Teal, GolfColors.TextPrimary, Color(0xFFF0D64A), GolfColors.Teal55)
    }
    val particles = remember {
        val rnd = Random(System.nanoTime())
        List(particleCount) {
            Particle(
                startX = rnd.nextFloat(),
                drift = -1f + rnd.nextFloat() * 2f,
                speed = 0.6f + rnd.nextFloat() * 0.9f,
                color = palette[rnd.nextInt(palette.size)],
                size = 5f + rnd.nextFloat() * 7f,
            )
        }
    }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        t = 0f
        while (t < 1f) {
            val now = withFrameNanos { it }
            t = (t + (now - last) / 1_000_000f / durationMs).coerceAtMost(1f)
            last = now
        }
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = t
        if (p >= 1f) return@Canvas
        val alpha = (1f - p).coerceIn(0f, 1f)
        particles.forEach { c ->
            val cx = w * c.startX + c.drift * w * 0.30f * c.speed * p
            val cy = h * 0.12f + 0.5f * 1400f * c.speed * p * p
            if (cy > h + 24f) return@forEach
            drawCircle(c.color.copy(alpha = alpha), c.size, Offset(cx, cy))
        }
    }
}

private data class Particle(
    val startX: Float,
    val drift: Float,
    val speed: Float,
    val color: Color,
    val size: Float,
)
```

- [ ] **Step 2: Compile**

```
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/ConfettiBurst.kt
git commit -m "feat(games): confetti burst composable for a new high score"
```

---

## Task 8: Target Practice result overlay

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/TargetPracticePlay.kt:107-177`

- [ ] **Step 1: Add confetti above the HUD**

In `TargetPracticePlay.kt`, immediately after the inner `BoxWithConstraints` block closes (after line 114) and before the `// HUD:` comment, insert:

```kotlin
                if (showResultOverlay && game.record.value?.outcome == RecordOutcome.NEW_RECORD) {
                    ConfettiBurst(modifier = Modifier.matchParentSize())
                }
```

- [ ] **Step 2: Add the record lines to the RESULT card**

In the `SectionCard(title = "RESULT")` block, insert between the `TOTAL …` `Text` (ends line 168) and the buttons `Row` (line 169):

```kotlin
                            game.record.value?.let { rec ->
                                Text(
                                    text = GameRecord.headline(rec, lowerIsBetter = false),
                                    style = GolfTypography.MetricValue,
                                    color = if (rec.outcome == RecordOutcome.NEW_RECORD) GolfColors.Teal else GolfColors.TextSecondary,
                                )
                                Text(
                                    text = GameRecord.detail(rec, lowerIsBetter = false),
                                    style = GolfTypography.Body,
                                    color = GolfColors.TextSecondary,
                                )
                            }
```

- [ ] **Step 3: Compile**

```
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/TargetPracticePlay.kt
git commit -m "feat(games): Target Practice high-score line + confetti"
```

---

## Task 9: Break the Pane result overlay

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPanePlay.kt:93-151`

- [ ] **Step 1: Add confetti above the HUD**

In `BreakPanePlay.kt`, immediately after the inner `BoxWithConstraints` block closes (after line 99) and before the `// HUD:` comment (line 101), insert:

```kotlin
                if (showResultOverlay && game.record.value?.outcome == RecordOutcome.NEW_RECORD) {
                    ConfettiBurst(modifier = Modifier.matchParentSize())
                }
```

- [ ] **Step 2: Add the record lines and fix PLAY AGAIN**

Replace the `SectionCard(title = "PANE BROKEN")` content column (lines 142-149) with:

```kotlin
                        Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs)) {
                            Text("Total shots: ${game.shotCount}", style = GolfTypography.MetricValue, color = GolfColors.Teal)
                            game.record.value?.let { rec ->
                                Text(
                                    text = GameRecord.headline(rec, lowerIsBetter = true),
                                    style = GolfTypography.MetricValue,
                                    color = if (rec.outcome == RecordOutcome.NEW_RECORD) GolfColors.Teal else GolfColors.TextSecondary,
                                )
                                Text(
                                    text = GameRecord.detail(rec, lowerIsBetter = true),
                                    style = GolfTypography.Body,
                                    color = GolfColors.TextSecondary,
                                )
                            }
                            Text("Lower is better.", style = GolfTypography.Body, color = GolfColors.TextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                                Button(onClick = { game.start(game.targetM, game.difficulty) }) { Text("PLAY AGAIN") }
                                OutlinedButton(onClick = onBack) { Text("BACK") }
                            }
                        }
```

- [ ] **Step 3: Compile**

```
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPanePlay.kt
git commit -m "feat(games): Break-the-Pane high-score line, confetti, difficulty on replay"
```

---

## Task 10: Pane intersection dot

**Files:**
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/games/PaneIntersection.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPaneCanvas.kt:57-58`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/games/PaneIntersectionTest.kt`

- [ ] **Step 1: Write the failing test**

Create `PaneIntersectionTest.kt`:

```kotlin
package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.TrajectorySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PaneIntersectionTest {

    private val planeY = 35.0

    private fun flight(crossX: Double, crossZ: Double): ShotResult = ShotResult(
        carryM = 140.0, rolloutM = 0.0, totalM = 140.0, sideM = 0.0, apexM = 25.0, flightTimeSec = 6.0,
        samples = listOf(
            TrajectorySample(0.0, 0.0, 0.0, 0.0),
            TrajectorySample(crossX, planeY, crossZ, 3.0),
            TrajectorySample(0.0, 140.0, 0.0, 6.0),
        ),
        restX = 0.0, restY = 140.0,
    )

    @Test
    fun `mark reports the crossing point and a reveal fraction inside 0..1`() {
        // apexVMin below the shot's apex v disables apex scaling, so the drawn
        // samples stay raw and z is unscaled (FollowCam.scaledSamples returns
        // shot.samples unchanged when apexScale >= 1).
        val mark = PaneIntersection.mark(flight(0.5, 10.0), planeY, apexVMin = -1.0)
        assertNotNull(mark)
        assertEquals(0.5, mark!!.xM, 1e-9)
        assertEquals(10.0, mark.zM, 1e-9)
        // Crossing is the 2nd of 3 samples -> fraction ~= 0.5.
        assertEquals(0.5f, mark.revealFraction, 1e-4f)
    }

    @Test
    fun `no forward crossing reports null`() {
        // py never reaches the 35 m plane.
        val below = ShotResult(
            carryM = 30.0, rolloutM = 0.0, totalM = 30.0, sideM = 0.0, apexM = 4.0, flightTimeSec = 3.0,
            samples = listOf(
                TrajectorySample(0.0, 0.0, 0.0, 0.0),
                TrajectorySample(0.0, 20.0, 2.0, 1.5),
                TrajectorySample(0.0, 30.0, 0.0, 3.0),
            ),
            restX = 0.0, restY = 30.0,
        )
        assertNull(PaneIntersection.mark(below, planeY, apexVMin = -1.0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.games.PaneIntersectionTest"
```
Expected: FAIL — `PaneIntersection` unresolved.

- [ ] **Step 3: Write `PaneIntersection`**

Create `PaneIntersection.kt`:

```kotlin
package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.TrajectorySample
import com.hpsmiles.golfsim.range.FollowCam
import com.hpsmiles.golfsim.range.RangeRollout

/**
 * Where (and when) the drawn flight crosses the pane plane, so the canvas can
 * mark the break point once the tracer has reached it (spec 2026-09-30 §6).
 * Pure; uses the exact sample list [GameScene.drawTracer] draws.
 */
object PaneIntersection {

    /** Crossing point plus when it becomes visible along the tracer (0..1). */
    data class Mark(val xM: Double, val zM: Double, val revealFraction: Float)

    /** Null when the drawn flight never crosses the pane plane forward. */
    fun mark(result: ShotResult, planeYM: Double, apexVMin: Double): Mark? {
        val samples = drawnSamples(result, apexVMin)
        if (samples.size < 2) return null
        for (i in 1 until samples.size) {
            val a = samples[i - 1]
            val b = samples[i]
            if (a.py < planeYM && b.py >= planeYM) {
                val t = (planeYM - a.py) / (b.py - a.py)
                val x = a.px + (b.px - a.px) * t
                val z = a.pz + (b.pz - a.pz) * t
                val frac = ((i - 1) + t) / (samples.size - 1)
                return Mark(x, z, frac.coerceIn(0.0, 1.0).toFloat())
            }
        }
        return null
    }

    /** The same sample list [GameScene.drawTracer] renders (frame-scaled + rollout). */
    fun drawnSamples(result: ShotResult, apexVMin: Double): List<TrajectorySample> =
        if (result.flightTimeSec <= 0.0) {
            FollowCam.scaledSamples(result, apexVMin)
        } else {
            FollowCam.scaledSamples(result, apexVMin) + RangeRollout.samples(result)
        }
}
```

- [ ] **Step 4: Draw the dot in `BreakPaneCanvas`**

In `BreakPaneCanvas.kt`, replace the last shot line (line 58) with:

```kotlin
            game.shots.lastOrNull()?.let { shot ->
                val result = shot.shot.shotResult
                val apexVMin = GameScene.apexVMin(w, h)
                drawTracer(camera, w, h, result, playFraction, apexVMin)
                game.pane?.let { pane ->
                    val mark = PaneIntersection.mark(result, pane.planeYM, apexVMin)
                    if (mark != null && playFraction >= mark.revealFraction) {
                        project(camera, w, h, mark.xM, pane.planeYM, mark.zM)?.let { pt ->
                            drawCircle(GolfColors.TextPrimary, 6f, pt)
                            drawCircle(color = GolfColors.Amber, radius = 6f, center = pt, style = Stroke(2f))
                        }
                    }
                }
            }
```

- [ ] **Step 5: Run tests + compile**

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.games.PaneIntersectionTest"
.\gradlew.bat :app:compileDebugKotlin
```
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/games/PaneIntersection.kt app/src/main/kotlin/com/hpsmiles/golfsim/games/BreakPaneCanvas.kt app/src/test/kotlin/com/hpsmiles/golfsim/games/PaneIntersectionTest.kt
git commit -m "feat(games): mark the tracer/pane intersection point"
```

---

## Task 11: Stop seeding the live MisRead pill at launch

**Files:**
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeSession.kt:77-94`
- Test: `app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeSessionTest.kt:79-94`

- [ ] **Step 1: Update the failing test**

In `RangeSessionTest.kt`, change the expectation at line 85 from `assertEquals(3, session.misreadCount.intValue)` to:

```kotlin
        assertEquals(0, session.misreadCount.intValue) // live pill starts fresh (spec §7)
```

and add a dedicated test after that method:

```kotlin
    @Test
    fun `restore does not seed the live misread pill from the persisted count`() {
        val session = RangeSession()
        session.restore(RestoredSession(5L, 7, listOf(shotRecord(0, "7i"))))
        assertEquals(0, session.misreadCount.intValue)
    }
```

- [ ] **Step 2: Run test to verify it fails**

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.RangeSessionTest"
```
Expected: FAIL — current `restore` seeds `misreadCount` from `restored.misreadCount`.

- [ ] **Step 3: Fix `RangeSession.restore`**

In `RangeSession.kt`, replace line 91 (`misreadCount.intValue = restored.misreadCount`) with:

```kotlin
        // Do NOT seed the live pill from the persisted count: a stale nonzero
        // count from the prior open session rendered as a "no read" pill right
        // around connection at launch (spec 2026-09-30 §7). The persisted count
        // still feeds History via RestoredSession.
        misreadCount.intValue = 0
```

- [ ] **Step 4: Run test to verify it passes**

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.hpsmiles.golfsim.range.RangeSessionTest"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hpsmiles/golfsim/range/RangeSession.kt app/src/test/kotlin/com/hpsmiles/golfsim/range/RangeSessionTest.kt
git commit -m "fix(range): do not seed the misread pill from the persisted count"
```

---

## Task 12: Full build + on-device verification

**Files:** none (verification only).

- [ ] **Step 1: Full build**

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat build
```
Expected: BUILD SUCCESSFUL, no new warnings that break the build.

- [ ] **Step 2: Install on the tablet**

```
.\gradlew.bat :app:installDebug
```

- [ ] **Step 3: On-device checklist**

- **Target Practice:** play 5 shots to completion. First-ever score at a distance/difficulty shows `FIRST SCORE`, **no** confetti. Play again and beat it → `NEW HIGH SCORE` + confetti. Play again and fall short → `N points off your best (M)`.
- **Break the Pane:** play to completion. First-ever shows `FIRST SCORE`; beating it shows `NEW HIGH SCORE` + confetti. `PLAY AGAIN` keeps the chosen difficulty.
- **Pane alignment:** at 125 m and 140 m, a well-struck iron breaks the **middle** row (not the top). All three rows remain reachable.
- **BP difficulty:** EASY/MEDIUM/HARD change the drawn green size (EASY ~10 m at 140 m, HARD ~6 m).
- **Intersection dot:** when the tracer reaches the pane, a white dot with an amber ring appears at the exact crossing point.
- **MisRead:** launch the app with a link (or after a prior session that had misreads) — no `no read (n)` pill appears at startup. A real mishit still shows and increments it.

- [ ] **Step 4: Commit any fix-ups**

If the checklist exposes issues, fix them TDD-style (test first where pure) and commit with a `fix:` message.
