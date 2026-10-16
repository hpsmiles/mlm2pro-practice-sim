package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.data.bag.BagMappingStats
import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import com.hpsmiles.golfsim.core.physics.BallFlightEngine
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.range.RangeRollout
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

    /**
     * The club's median flight, ready to draw.
     *
     * The trace is simulated from the **median of the launch metrics**, which
     * does not land at the **median of the distances** (the median of a function
     * is not the function of the medians). It is therefore uniformly scaled by
     * `medianDistance / traceEndDistance` so it ends exactly on the number
     * printed under it — one number per club across the whole page. The
     * persisted caches were also written at capture time by an engine whose
     * bounce/roll law is periodically recalibrated, so a fresh simulation's
     * total can drift from the stored one. Scaling keeps the shape (which is
     * what carries the launch-angle and spin information) and discards an
     * absolute distance the trace was never authoritative for.
     *
     * Null when the club has no kept shots, its medians cannot build launch
     * conditions, or the trace has no forward extent.
     */
    fun trace(clubName: String, shots: List<BagMappingShotEntity>, basis: DistanceBasis): ClubTrace? {
        val launch = medianLaunch(shots) ?: return null
        val distance = medianDistance(shots, basis) ?: return null
        if (distance <= 0.0) return null
        val simulated = BallFlightEngine.simulate(launch)
        val samples = when (basis) {
            DistanceBasis.CARRY -> simulated.samples
            DistanceBasis.TOTAL -> simulated.samples + RangeRollout.samples(simulated)
        }
        if (samples.isEmpty()) return null
        val raw = samples.map { TracePoint(hypot(it.px, it.py), it.pz) }
        val end = raw.last().downrangeM
        if (end <= 0.0) return null
        val k = distance / end
        val scaled = raw.map { TracePoint(it.downrangeM * k, it.heightM * k) }
        return ClubTrace(clubName, scaled, distance, scaled.maxOf { it.heightM })
    }

    /** Task 5 implements this. */
    fun dots(clubName: String, shots: List<BagMappingShotEntity>, basis: DistanceBasis): ClubDots =
        ClubDots(clubName, emptyList())
}
