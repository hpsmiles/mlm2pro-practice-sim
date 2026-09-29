package com.hpsmiles.golfsim.games

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.entity.GameModes
import com.hpsmiles.golfsim.core.physics.BallFlightEngine
import com.hpsmiles.golfsim.core.physics.Environment
import com.hpsmiles.golfsim.core.physics.GreenZoneSurfaceProvider
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.core.physics.ShotResult
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

    /** Injectable clock (ms). AppRoot can leave the default. */
    var clockMs: () -> Long = { System.currentTimeMillis() }

    /**
     * Injectable simulation seam. The default closure reads the CURRENT
     * targetM/green radius so start() re-targets it.
     */
    var simulator: (LaunchConditions) -> ShotResult = { launch ->
        BallFlightEngine.simulate(
            launch,
            Environment(),
            GreenZoneSurfaceProvider(0.0, targetM, greenRadiusM()),
        )
    }

    var targetM: Double = 140.0
        private set
    var difficulty: Difficulty = Difficulty.MEDIUM
        private set
    private var resultTaken = false

    fun start(targetM: Double, difficulty: Difficulty) {
        this.targetM = targetM
        this.difficulty = difficulty
        shots.clear()
        resultTaken = false
    }

    val complete: Boolean get() = shots.size >= TargetPracticeScoring.SHOTS_PER_GAME
    val totalPoints: Int get() = shots.sumOf { it.points }

    /** Green oval radius, scaled to the target (spec: ~6 m at 140 m). */
    fun greenRadiusM(): Double = 6.0 * targetM / 140.0

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
