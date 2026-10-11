package com.hpsmiles.golfsim.combine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.physics.BallFlightEngine
import com.hpsmiles.golfsim.core.physics.Environment
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.Surface
import com.hpsmiles.golfsim.core.scoring.Lie
import com.hpsmiles.golfsim.range.DisplayShot

data class CombineShot(
    val index: Int,
    val stationIndex: Int,
    val display: DisplayShot,
    val restLie: Lie,
    val restDistanceM: Double,
)

class CombineGame {
    var entry: CombineCatalogEntry? = null
    var sessionId: Long = 0L

    /** Shots already taken before this [start] (Room-derived on a resumed session). */
    var baselineTaken: Int = 0

    /**
     * Scoring facts for shots already taken before this [start] (Room-derived on
     * a resumed session). Kept separate from [shots] so results and records score
     * the FULL run, not just the post-resume tail.
     */
    var baselineFacts: List<CombineShotFacts> = emptyList()

    /** Shots taken since [start] only; the resumed baseline lives in [baselineTaken]. */
    val shots: SnapshotStateList<CombineShot> = mutableStateListOf()
    var tick by mutableIntStateOf(0)
    var record: CombineRecordComparison? by mutableStateOf(null)

    /**
     * Set when a DEMO-source shot is routed into this combine. A synchronous
     * in-memory signal for the completion gate: the persisted `source` column is
     * updated asynchronously by a Room write, so the gate cannot rely on it for
     * the very shot that ends the run (the write may not have landed yet).
     */
    var sawDemoShot by mutableStateOf(false)

    var clockMs: () -> Long = { System.currentTimeMillis() }
    var greenSurface: Surface = Surface.GREEN_NORMAL
    var fairwaySurface: Surface = Surface.FAIRWAY_NORMAL
    var roughSurface: Surface = Surface.ROUGH_NORMAL

    var simulator: (LaunchConditions) -> ShotResult = { launch ->
        val currentEntry = requireNotNull(entry) { "combine not started" }
        val index = nextShotIndex
        BallFlightEngine.simulate(
            launch,
            Environment(),
            CombineSurfaceProvider(currentEntry, index, greenSurface, fairwaySurface, roughSurface),
        )
    }

    val takenCount: Int get() = baselineTaken + shots.size
    val nextShotIndex: Int get() = takenCount
    val complete: Boolean get() = takenCount >= CombinePlan.SHOTS_TOTAL

    /** Full-run scoring facts: the resumed baseline first, then this run's shots. */
    val allFacts: List<CombineShotFacts> get() = baselineFacts + shots.map { it.toFacts() }

    fun start(
        entry: CombineCatalogEntry,
        sessionId: Long,
        baselineTaken: Int,
        baselineFacts: List<CombineShotFacts> = emptyList(),
    ) {
        this.entry = entry
        this.sessionId = sessionId
        this.baselineTaken = baselineTaken
        this.baselineFacts = baselineFacts
        shots.clear()
        record = null
        sawDemoShot = false
        tick = 0
    }

    fun reset() {
        entry = null
        sessionId = 0L
        baselineTaken = 0
        baselineFacts = emptyList()
        shots.clear()
        record = null
        sawDemoShot = false
        tick = 0
    }

    fun add(ballData: BallData): CombineShot? {
        val currentEntry = entry ?: return null
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
        val index = nextShotIndex
        val result = simulator(launch)
        val restLie = CombineLie.classify(currentEntry, index, result.restX, result.restY)
        val restDistanceM = CombinePlan.restDistanceM(currentEntry, index, result.restX, result.restY)
        val shot = CombineShot(
            index = index,
            stationIndex = CombinePlan.stationIndexFor(index),
            display = DisplayShot(ballData, launch, result, clockMs()),
            restLie = restLie,
            restDistanceM = restDistanceM,
        )
        shots.add(shot)
        tick += 1
        return shot
    }
}
