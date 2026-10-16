package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LATERAL (SIDE) CALIBRATION — pins the engine's side behaviour against
 * published reference figures. These are regression tests, not calibration:
 * every hand-tuned aerodynamic constant (SD_COEFF, SD_CAP, MS_BOOST_MAX,
 * SPIN_TAU_SEC in AerodynamicModel.kt) was tuned for carry/total/rollout and no
 * existing test or golden fixture pinned a lateral magnitude. The MLM2PRO
 * transmits no lateral measurement, so BAG MAPPING's top-down view re-simulates
 * each shot's lateral offset — the lateral model is user-visible in shipped
 * code and must not drift silently. It happens to agree with the published
 * figures; these tests record that and stop a future re-tune silently breaking
 * it. "Lateral" here is [ShotResult.sideM] (rest x, including bounce/roll, per
 * ShotResult's +x-right-of-target convention) unless stated otherwise.
 *
 * Reference figures (provenance):
 *  - TrackMan rule of thumb: a ball swerves 0.7 % off line per 1 deg of
 *    spin-axis tilt -> 1.66 m per degree at 237 m carry.
 *  - Titleist rule: 3.5 yd of curve per 5 deg of tilt per 100 yd of carry
 *    -> ~1.65 m per degree at 237 m.
 *  - Independent point-mass ODE integration: 1.77-1.79 yd/deg at 258 yd carry.
 *  - This engine currently produces 1.59-1.65 m per degree at driver speed —
 *    inside the band, at the low end.
 *
 * The curve-per-degree band [1.4, 1.9] m/deg is deliberately wide: it must fail
 * loudly on a unit error or a missing sin projection (off by ~10x) while
 * tolerating a legitimate future re-tune. And with zero spin-axis tilt the path
 * stays in the vertical plane at the launch azimuth (the only Magnus component
 * is the in-plane forward/backward drag-like part), so the landing lateral is
 * carry * sin(launchDirDeg) exactly — pinned in the start-line test below.
 */
class LateralCalibrationTest {

    // Driver launch conditions shared by the curve tests (task spec: ball speed
    // 72.0 m/s, launch ~11.5-12.0 deg, spin ~2500-2600 rpm).
    private fun driver(launchDirDeg: Double = 0.0, spinAxisDeg: Double = 0.0) = LaunchConditions(
        ballSpeedMps = 72.0,
        launchAngleDeg = 12.0,
        spinRpm = 2500,
        launchDirDeg = launchDirDeg,
        spinAxisDeg = spinAxisDeg,
    )

    private val curveBand = 1.4..1.9

    @Test
    fun `curve per degree of spin axis stays inside the reference band`() {
        // Driver-class, straight aim (launchDir 0): sideM is pure curve. The
        // per-degree figure = |sideM(+axis) - sideM(-axis)| / (2 * axis).
        for (axis in listOf(5.0, 10.0)) {
            val plus = BallFlightEngine.simulate(driver(spinAxisDeg = axis))
            val minus = BallFlightEngine.simulate(driver(spinAxisDeg = -axis))
            // Mirror symmetry: +spinAxis (fade) curves right, -spinAxis (draw)
            // left. The existing suite only checks this on a wind case.
            assertEquals(-minus.sideM, plus.sideM, 1e-9)
            val perDeg = Math.abs(plus.sideM - minus.sideM) / (2.0 * axis)
            assertTrue(
                "axis $axis: sideM(+)=${plus.sideM} sideM(-)=${minus.sideM} -> $perDeg m/deg outside $curveBand",
                perDeg in curveBand,
            )
        }
    }

    @Test
    fun `start-line geometry stays in the vertical plane at the launch azimuth`() {
        // Zero spin-axis tilt: no out-of-plane Magnus force, so the flight stays
        // in the vertical plane at the launch azimuth and the landing lateral is
        // carry * sin(launchDirDeg) exactly. This is the pure-geometry start-line
        // component (~half of an apparent miss at these angles).
        for (dir in listOf(-5.0, -2.5, 2.5, 5.0)) {
            val shot = BallFlightEngine.simulate(driver(launchDirDeg = dir))
            val az = Math.toRadians(dir)
            val landingX = shot.samples.last().px
            assertEquals(
                "dir $dir: landing lateral must be carry * sin(az)",
                shot.carryM * Math.sin(az), landingX, 1e-9,
            )
            // The ground phase also stays in-plane (zero sidespin -> zero ground
            // kick), so the rest lateral is total * sin(az) — NOT carry * sin(az).
            assertEquals(
                "dir $dir: rest lateral must stay in-plane (total * sin(az))",
                shot.totalM * Math.sin(az), shot.sideM, 1e-9,
            )
        }
    }

    @Test
    fun `real driver shots finish left of target in the 15-30 m band`() {
        // The three kept driver shots from our live session:
        //   (launchDir -2.5, spinAxis -6.4, ballSpeed 72.2)
        //   (launchDir -3.0, spinAxis -5.6, ballSpeed 72.0)
        //   (launchDir -1.6, spinAxis -11.2, ballSpeed 72.7)
        // Live BLE data records launchDir / spinAxis / ballSpeed but NOT the
        // launch angle or spin the engine needs, so pick a plausible driver pair
        // for all three: 12.0 deg launch, 2550 rpm (inside the 11.5-12.25 /
        // 2500-2600 window). Point is direction and order of magnitude, not the
        // exact metre figure.
        val launchAngleDeg = 12.0
        val spinRpm = 2550
        data class Shot(val speed: Double, val dir: Double, val axis: Double)
        val shots = listOf(
            Shot(72.2, -2.5, -6.4),
            Shot(72.0, -3.0, -5.6),
            Shot(72.7, -1.6, -11.2),
        )
        for (s in shots) {
            val result = BallFlightEngine.simulate(
                LaunchConditions(
                    ballSpeedMps = s.speed,
                    launchAngleDeg = launchAngleDeg,
                    spinRpm = spinRpm,
                    launchDirDeg = s.dir,
                    spinAxisDeg = s.axis,
                ),
            )
            // Negative spinAxis = draw (left curve), negative launchDir = aim
            // left: the shot must finish left of target.
            assertTrue("shot $s sideM=${result.sideM} not left of target", result.sideM < 0.0)
            val magnitude = Math.abs(result.sideM)
            assertTrue(
                "shot $s offline magnitude $magnitude m outside [15, 30]",
                magnitude in 15.0..30.0,
            )
        }
    }

    @Test
    fun `more spin-axis tilt produces strictly more curve`() {
        // Fixed speed / angle / spin, straight aim: |sideM| must increase
        // strictly across 0 -> 5 -> 10 -> 15 deg.
        val straight = BallFlightEngine.simulate(driver(spinAxisDeg = 0.0))
        assertEquals(0.0, straight.sideM, 1e-9) // zero tilt at launchDir 0 -> dead straight
        var prev = 0.0
        for (axis in listOf(5.0, 10.0, 15.0)) {
            val shot = BallFlightEngine.simulate(driver(spinAxisDeg = axis))
            val magnitude = Math.abs(shot.sideM)
            assertTrue(
                "axis $axis: |sideM|=$magnitude must strictly exceed $prev",
                magnitude > prev,
            )
            prev = magnitude
        }
    }
}
