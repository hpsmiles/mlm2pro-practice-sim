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
import kotlin.math.hypot

enum class BreakOutcomeKind { BROKE, HIT_PANE_MISSED_GREEN, GREEN_MISSED_PANE, MISSED_BOTH }

data class BreakOutcome(val kind: BreakOutcomeKind, val brokenCell: Int?, val feedback: String)

/**
 * Break the Pane state holder (spec section 5). A shot breaks a cell only when it
 * crosses the pane plane inside an UNBROKEN cell AND comes to rest on the
 * green. Score = shots taken until all 9 cells are broken (lower is better).
 */
class BreakThePaneGame {

    val shots: SnapshotStateList<PaneShot> = mutableStateListOf()
    val brokenCells: SnapshotStateList<Int> = mutableStateListOf()
    val tick: MutableIntState = mutableIntStateOf(0)

    /** High-score comparison for the last completed game; null until AppRoot loads it. */
    val record: MutableState<RecordComparison?> = mutableStateOf(null)

    var clockMs: () -> Long = { System.currentTimeMillis() }

    /** Green surface used by the default simulator (Settings > GREEN). */
    var greenSurface: Surface = Surface.GREEN_NORMAL

    /** Fairway surface used off the green (Settings > TURF). */
    var fairwaySurface: Surface = Surface.FAIRWAY_NORMAL

    /** Injectable seam, same contract as TargetPracticeGame.simulator. */
    var simulator: (LaunchConditions) -> ShotResult = { launch ->
        BallFlightEngine.simulate(
            launch,
            Environment(),
            GreenZoneSurfaceProvider(0.0, targetM, greenRadiusM(), green = greenSurface, fairway = fairwaySurface),
        )
    }

    var pane: PaneGeom? = null
        private set
    var targetM: Double = 140.0
        private set
    var difficulty: Difficulty = Difficulty.MEDIUM
        private set
    /** Custom pane plane distance (m), null = default 20%; survives PLAY AGAIN via explicit pass-through. */
    var paneDistanceM: Double? = null
        private set
    var lastFeedback: String = ""
        private set
    private var resultTaken = false

    fun start(targetM: Double, difficulty: Difficulty = Difficulty.MEDIUM, paneDistanceM: Double? = null) {
        this.targetM = targetM
        this.difficulty = difficulty
        this.paneDistanceM = paneDistanceM
        pane = PaneGeom(targetM, paneDistanceM)
        shots.clear()
        brokenCells.clear()
        lastFeedback = ""
        resultTaken = false
        record.value = null
    }

    val complete: Boolean get() = brokenCells.size == PaneCellCount
    val shotCount: Int get() = shots.size

    /** Green oval radius, scaled to the target by difficulty (spec 2026-09-30 §5). */
    fun greenRadiusM(): Double = BreakPaneGreen.radiusAt140m(difficulty) * targetM / 140.0

    /** Publishes the high-score comparison for the just-completed game. */
    fun setRecord(comparison: RecordComparison) {
        record.value = comparison
    }

    /** One decoded measurement -> evaluated shot. Null on guard violation or completed game. */
    fun add(ballData: BallData): PaneShot? {
        val p = pane ?: return null
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
        val onGreen = hypot(result.restX, result.restY - targetM) <= greenRadiusM()
        val crossing = p.firstCrossing(result.samples)
        val cell = crossing?.cell
        val outcome = when {
            cell != null && onGreen && cell !in brokenCells -> {
                brokenCells.add(cell)
                BreakOutcome(BreakOutcomeKind.BROKE, cell, "BROKE CELL ${cell + 1}")
            }
            cell != null && onGreen -> BreakOutcome(BreakOutcomeKind.GREEN_MISSED_PANE, null, "GREEN OK - CELL ALREADY BROKEN")
            cell != null -> BreakOutcome(BreakOutcomeKind.HIT_PANE_MISSED_GREEN, null, "PANE OK - MISSED GREEN")
            onGreen -> {
                val dir = when {
                    crossing == null -> "MISSED PANE"
                    crossing.zM < p.bottomZM -> "GREEN OK - UNDER THE PANE"
                    crossing.zM > p.topZM -> "GREEN OK - OVER THE PANE"
                    crossing.xM < p.leftXM -> "GREEN OK - MISSED LEFT"
                    else -> "GREEN OK - MISSED RIGHT"
                }
                BreakOutcome(BreakOutcomeKind.GREEN_MISSED_PANE, null, dir)
            }
            else -> {
                val dir = when {
                    crossing == null -> "MISSED BOTH"
                    crossing.zM < p.bottomZM -> "SHORT - UNDER THE PANE"
                    crossing.zM > p.topZM -> "OVER THE PANE"
                    result.restY < targetM - greenRadiusM() -> "SHORT"
                    result.restY > targetM + greenRadiusM() && kotlin.math.abs(result.restX) <= greenRadiusM() -> "LONG"
                    result.restX < 0.0 -> "MISSED LEFT"
                    else -> "MISSED RIGHT"
                }
                BreakOutcome(BreakOutcomeKind.MISSED_BOTH, null, dir)
            }
        }
        lastFeedback = outcome.feedback
        val paneShot = PaneShot(DisplayShot(ballData, launch, result, clockMs()), outcome)
        shots.add(paneShot)
        tick.intValue++
        return paneShot
    }

    /** Non-null exactly once once [complete]; the summary AppRoot persists. */
    fun takeResult(): GameResultPayload? {
        if (!complete || resultTaken) return null
        resultTaken = true
        return GameResultPayload(GameModes.BREAK_PANE, difficulty.name, targetM, shotCount)
    }

    private companion object {
        const val PaneCellCount = 9
    }
}

data class PaneShot(val shot: DisplayShot, val outcome: BreakOutcome)
