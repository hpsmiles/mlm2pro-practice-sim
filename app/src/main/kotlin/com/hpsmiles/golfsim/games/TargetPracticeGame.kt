package com.hpsmiles.golfsim.games

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.entity.GameModes
import com.hpsmiles.golfsim.core.physics.BallFlightEngine
import com.hpsmiles.golfsim.core.physics.Environment
import com.hpsmiles.golfsim.core.physics.GreenZoneSurfaceProvider
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.Surface
import com.hpsmiles.golfsim.range.DisplayShot

/**
 * Target Practice state holder (RangeSession pattern: plain Compose state,
 * injectable clock -- spec section 6). 5 shots, points by rest-distance bands.
 * `simulator` is an injectable seam so JVM tests pin scoring without the ODE;
 * the default wires the real engine with the green-zone surface provider.
 */
class TargetPracticeGame {

    val shots: SnapshotStateList<GameShot> = mutableStateListOf()
    val tick: MutableIntState = mutableIntStateOf(0)

    /** High-score comparison for the last completed game; null until AppRoot loads it. */
    val record: MutableState<RecordComparison?> = mutableStateOf(null)

    /** Injectable clock (ms). AppRoot can leave the default. */
    var clockMs: () -> Long = { System.currentTimeMillis() }

    /**
     * Green surface used by the default simulator (Settings > GREEN). AppRoot
     * keeps this in sync with the user's selection; JVM tests can leave it.
     */
    var greenSurface: Surface = Surface.GREEN_NORMAL

    /** Fairway surface used off the green (Settings > TURF). */
    var fairwaySurface: Surface = Surface.FAIRWAY_NORMAL

    /**
     * Injectable simulation seam. The default closure calls [greenRadiusM]
     * every shot so the rollout surface matches the visual green as the
     * difficulty changes (amendment 4: green now fills the outermost ring).
     */
    var simulator: (LaunchConditions) -> ShotResult = { launch ->
        BallFlightEngine.simulate(
            launch,
            Environment(),
            GreenZoneSurfaceProvider(0.0, targetM, greenRadiusM(), green = greenSurface, fairway = fairwaySurface),
        )
    }

    var targetM: Double = 140.0
        private set
    var difficulty: Difficulty = Difficulty.MEDIUM
        private set
    private var resultTaken = false
    private var started = false

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

    val complete: Boolean get() = shots.size >= TargetPracticeScoring.SHOTS_PER_GAME
    val totalPoints: Int get() = shots.sumOf { it.points }

    /**
     * Green surface radius for the active difficulty. Fills the outermost
     * scoring ring so the entire target face is green. Before [start] is
     * called, falls back to the pre-start default (6 m at 140 m).
     */
    fun greenRadiusM(): Double {
        val baseRadiusM = if (started) TargetPracticeScoring.outerBandRadiusM(difficulty) else 6.0
        return baseRadiusM * targetM / 140.0
    }

    /** One decoded measurement -> scored shot. Null on guard violation or completed game. */
    fun add(ballData: BallData): GameShot? {
        if (complete) return null
        val launch = try {
            LaunchConditions(
                ballSpeedMps = ballData.ballSpeed,
                launchAngleDeg = ballData.launchAngle,
                spinRpm = ballData.totalSpin,
                spinAxisDeg = ballData.spinAxis,
                launchDirDeg = ballData.launchDirection,
            )
        } catch (_: IllegalArgumentException) {
            return null
        }
        val result = simulator(launch)
        val miss = TargetPracticeScoring.missDistanceM(result.restX, result.restY, targetM)
        val pts = TargetPracticeScoring.points(miss, difficulty)
        val gameShot = GameShot(DisplayShot(ballData, launch, result, clockMs()), miss, pts)
        shots.add(gameShot)
        tick.intValue++
        return gameShot
    }

    /** Non-null exactly once once [complete]; the summary AppRoot persists. */
    fun takeResult(): GameResultPayload? {
        if (!complete || resultTaken) return null
        resultTaken = true
        return GameResultPayload(GameModes.TARGET_PRACTICE, difficulty.name, targetM, totalPoints)
    }
}

data class GameShot(val shot: DisplayShot, val missM: Double, val points: Int)
