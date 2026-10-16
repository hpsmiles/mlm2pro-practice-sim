package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.data.bag.BagMappingStats
import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import com.hpsmiles.golfsim.core.physics.BallFlightEngine
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import kotlin.math.hypot
import kotlin.math.roundToInt

/** One point of a club's drawn flight, in the side view's 2-D frame. */
data class TracePoint(val downrangeM: Double, val heightM: Double)

/** A club's drawn flight: the median trace, scaled to its authoritative distance. */
data class ClubTrace(
    val clubName: String,
    val points: List<TracePoint>,
    /** The authoritative median distance for the current basis (metres). */
    val distanceM: Double,
    /** Apex of the drawn (scaled) trace, metres. */
    val apexM: Double,
)

/** One club's kept-shot landing points in the top-down frame. */
data class ClubDots(
    val clubName: String,
    /** (lateralM, distanceM) per kept shot. */
    val points: List<Pair<Double, Double>>,
)

object BagTrace {

    /**
     * Launch conditions for a single stored shot, or null when its raw block
     * cannot build valid conditions (legacy or tampered rows). Mirrors the
     * range-checked construction the engine requires; validated explicitly
     * rather than by catching, so the failure mode is a skip, never a throw.
     */
    fun launchFor(shot: BagMappingShotEntity): LaunchConditions? {
        val spinRpm = shot.totalSpinRpm
        if (shot.ballSpeedMps !in 0.5..100.0) return null
        if (spinRpm !in 0..12000) return null
        if (shot.launchAngleDeg !in 0.0..85.0) return null
        if (shot.launchDirDeg !in -90.0..90.0) return null
        if (shot.spinAxisDeg !in -90.0..90.0) return null
        return LaunchConditions(
            ballSpeedMps = shot.ballSpeedMps,
            launchAngleDeg = shot.launchAngleDeg,
            spinRpm = spinRpm,
            launchDirDeg = shot.launchDirDeg,
            spinAxisDeg = shot.spinAxisDeg,
        )
    }

    /** The median launch conditions over a club's kept shots, or null. */
    fun medianLaunch(shots: List<BagMappingShotEntity>): LaunchConditions? {
        val kept = shots.filter { it.effectiveIncluded() }
        if (kept.isEmpty()) return null
        val ballSpeed = BagMappingStats.median(kept.map { it.ballSpeedMps }) ?: return null
        val launchAngle = BagMappingStats.median(kept.map { it.launchAngleDeg }) ?: return null
        val spin = BagMappingStats.median(kept.map { it.totalSpinRpm.toDouble() }) ?: return null
        val launchDir = BagMappingStats.median(kept.map { it.launchDirDeg }) ?: return null
        val spinAxis = BagMappingStats.median(kept.map { it.spinAxisDeg }) ?: return null
        val spinRpm = spin.roundToInt()
        if (ballSpeed !in 0.5..100.0) return null
        if (spinRpm !in 0..12000) return null
        if (launchAngle !in 0.0..85.0) return null
        if (launchDir !in -90.0..90.0) return null
        if (spinAxis !in -90.0..90.0) return null
        return LaunchConditions(ballSpeed, launchAngle, spinRpm, launchDir, spinAxis)
    }

    /** The median of the basis distance over a club's kept shots, or null. */
    fun medianDistance(shots: List<BagMappingShotEntity>, basis: DistanceBasis): Double? =
        BagMappingStats.median(shots.filter { it.effectiveIncluded() }.map { it.distanceFor(basis) })

    /** Task 4 implements this. */
    fun trace(clubName: String, shots: List<BagMappingShotEntity>, basis: DistanceBasis): ClubTrace? = null

    /** Task 5 implements this. */
    fun dots(clubName: String, shots: List<BagMappingShotEntity>, basis: DistanceBasis): ClubDots =
        ClubDots(clubName, emptyList())
}
