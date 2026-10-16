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
     * The launch-metric ranges [LaunchConditions] enforces in its own `init`.
     * Checked explicitly rather than by catching, so an out-of-range club is
     * skipped, never thrown.
     */
    private fun inRange(
        ballSpeedMps: Double,
        spinRpm: Int,
        launchAngleDeg: Double,
        launchDirDeg: Double,
        spinAxisDeg: Double,
    ): Boolean =
        ballSpeedMps in 0.5..100.0 &&
            spinRpm in 0..12000 &&
            launchAngleDeg in 0.0..85.0 &&
            launchDirDeg in -90.0..90.0 &&
            spinAxisDeg in -90.0..90.0

    /**
     * Launch conditions for a single stored shot, or null when its raw block
     * cannot build valid conditions (legacy or tampered rows). Mirrors the
     * range-checked construction the engine requires; validated explicitly
     * rather than by catching, so the failure mode is a skip, never a throw.
     */
    fun launchFor(shot: BagMappingShotEntity): LaunchConditions? {
        val spinRpm = shot.totalSpinRpm
        if (!inRange(shot.ballSpeedMps, spinRpm, shot.launchAngleDeg, shot.launchDirDeg, shot.spinAxisDeg)) {
            return null
        }
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
        if (!inRange(ballSpeed, spinRpm, launchAngle, launchDir, spinAxis)) return null
        return LaunchConditions(
            ballSpeedMps = ballSpeed,
            launchAngleDeg = launchAngle,
            spinRpm = spinRpm,
            launchDirDeg = launchDir,
            spinAxisDeg = spinAxis,
        )
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

    /**
     * Landing points for the top-down view, one per kept shot.
     *
     * The entity stores no lateral column, so the lateral offset comes from a
     * per-shot simulation: for CARRY the `px` of the last flight sample (the
     * carry landing), for TOTAL `ShotResult.sideM` (the rest position's lateral
     * offset). The **distance** always comes from the persisted cache via
     * [distanceFor], never from the simulation — the roll law is the part that
     * gets recalibrated, and it moves distance but never lateral deviation, so
     * this keeps every dot's distance identical to every other number on the
     * page.
     *
     * A non-positive cached distance is skipped (mirroring [trace]): a corrupt
     * row would otherwise plant a dot at or behind the tee that the top-down
     * clips away but `DispersionEllipse.fromKept` would still fit a ring
     * around (2026-10-16 round 4, finding F). Known asymmetry, accepted: a
     * cached distance beyond [RangeScene.FAIRWAY_END_Y] is clamped out of the
     * top-down window (so its dot is not drawn there) while SIDE still draws
     * the arc to the full cached distance — the top-down's world ends at the
     * range fence, the side view's axis does not.
     */
    fun dots(clubName: String, shots: List<BagMappingShotEntity>, basis: DistanceBasis): ClubDots {
        val points = shots.filter { it.effectiveIncluded() }.mapNotNull { shot ->
            val distance = shot.distanceFor(basis)
            if (distance <= 0.0) return@mapNotNull null
            val launch = launchFor(shot) ?: return@mapNotNull null
            val simulated = BallFlightEngine.simulate(launch)
            val lateral = when (basis) {
                DistanceBasis.CARRY -> simulated.samples.lastOrNull()?.px ?: return@mapNotNull null
                DistanceBasis.TOTAL -> simulated.sideM
            }
            lateral to distance
        }
        return ClubDots(clubName, points)
    }
}
