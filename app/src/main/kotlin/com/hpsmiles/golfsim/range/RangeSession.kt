package com.hpsmiles.golfsim.range

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.record.RestoredSession
import com.hpsmiles.golfsim.core.data.record.ShotRecord
import com.hpsmiles.golfsim.core.physics.BallFlightEngine
import com.hpsmiles.golfsim.core.physics.Environment
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.Surface
import com.hpsmiles.golfsim.core.physics.UniformSurface

/**
 * Owns the Range shot list plus the no-read pill counter. Lives in AppRoot so
 * that BOTH the demo fold (RangeScreen) and live BLE callbacks (binder thread,
 * hopped to main by AppRoot) append to the same list.
 *
 * `tick` advances once per accepted shot and is the RangeScreen tracer's
 * reset signal (playFraction → 0 when a new shot lands).
 *
 * `clockMs` is injectable so misread coalescing is unit-testable on the JVM.
 */
class RangeSession {

    val shots: SnapshotStateList<DisplayShot> = mutableStateListOf()
    val tick = mutableIntStateOf(0)
    val misreadCount = mutableIntStateOf(0)

    /** Injectable clock (ms). AppRoot can leave the default. */
    var clockMs: () -> Long = { System.currentTimeMillis() }

    // 0L (epoch) not Long.MIN_VALUE: `atMs - Long.MIN_VALUE` overflows and
    // would swallow the very first misread on any normal clock.
    private var lastMisreadMs = 0L

    /**
     * Converts one decoded measurement into a rendered shot. Returns null —
     * appending nothing — when the data violates LaunchConditions require()
     * guards, so a bad live decode can never crash the UI thread.
     * M4c evidence + golden fixtures pin the expected live values.
     * M5: returns the accepted DisplayShot (timestamped from the injectable
     * clock) so AppRoot can hand it to the persistence layer.
     */
    fun add(ballData: BallData): DisplayShot? {
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
        val result = BallFlightEngine.simulate(
            launch,
            Environment(),
            UniformSurface(Surface.FAIRWAY_NORMAL),
        )
        val shot = DisplayShot(ballData, launch, result, clockMs())
        shots.add(shot)
        tick.intValue++
        return shot
    }

    /**
     * M5 restart resume: rebuilds scalar-backed resting shots from the open
     * session. No trajectories (nothing replays), no tick bump.
     */
    fun restore(restored: RestoredSession) {
        shots.clear()
        restored.shots.forEach { record ->
            record.toDisplayShot()?.let { shots.add(it) }
        }
        misreadCount.intValue = restored.misreadCount
    }

    /**
     * One real mishit emits BOTH the EVENTS MisreadAlert and the all-zero
     * MEASUREMENT sentinel ~200 ms apart (M4c, event-010 + event-027).
     * Coalesce that pair into a single pill increment.
     */
    fun markMisread(atMs: Long = clockMs()) {
        if (atMs - lastMisreadMs < MISREAD_COALESCE_MS) return
        lastMisreadMs = atMs
        misreadCount.intValue++
    }

    fun dismissMisreads() {
        misreadCount.intValue = 0
    }

    private companion object {
        const val MISREAD_COALESCE_MS = 500L
    }
}

/**
 * Rebuilds a persisted shot as a scalar-backed resting DisplayShot (empty
 * trajectory samples — nothing replays after a restart). Returns null if the
 * stored row no longer passes LaunchConditions guards (defensive against
 * tampered data).
 */
fun ShotRecord.toDisplayShot(): DisplayShot? {
    val bd = ballData
    val launch = try {
        LaunchConditions(
            ballSpeedMps = bd.ballSpeed,
            launchAngleDeg = bd.launchAngle,
            spinRpm = bd.totalSpin,
            spinAxisDeg = bd.spinAxis,
            launchDirDeg = bd.launchDirection,
        )
    } catch (_: IllegalArgumentException) {
        return null
    }
    val result = ShotResult(
        carryM = carryM,
        rolloutM = totalM - carryM,
        totalM = totalM,
        sideM = sideM,
        apexM = apexM,
        flightTimeSec = flightTimeSec,
    )
    return DisplayShot(bd, launch, result, timestampMs)
}
