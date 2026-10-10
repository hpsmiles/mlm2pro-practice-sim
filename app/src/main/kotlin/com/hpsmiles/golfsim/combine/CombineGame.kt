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
    var baselineTaken: Int = 0
    val shots: SnapshotStateList<CombineShot> = mutableStateListOf()
    var tick by mutableIntStateOf(0)
    var record: CombineRecordComparison? by mutableStateOf(null)

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

    fun start(entry: CombineCatalogEntry, sessionId: Long, baselineTaken: Int) {
        this.entry = entry
        this.sessionId = sessionId
        this.baselineTaken = baselineTaken
        shots.clear()
        record = null
        tick = 0
    }

    fun reset() {
        entry = null
        sessionId = 0L
        baselineTaken = 0
        shots.clear()
        record = null
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
