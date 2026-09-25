// app/src/test/kotlin/com/hpsmiles/golfsim/range/FollowCamTest.kt
package com.hpsmiles.golfsim.range

import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.TrajectorySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the FollowCam phase machine (spec 2026-09-25): static window ->
 * blend -> chase -> descent pitch-in -> landing hold -> snap back.
 *
 * Synthetic shots: straight-line lateral drift, py proportional to t, a
 * pz parabola peaking at t = T/2, samples every 50 ms. The apex clamp
 * keeps every shot inside the static frame on the reference geometry, so
 * all of these engage on the 1.0 s timer (early engage only fires on
 * squarer canvases where the clamp line sits above the top of frame).
 */
class FollowCamTest {

    private fun parabolicShot(
        carryM: Double = 180.0,
        apexM: Double = 30.0,
        flightTimeSec: Double = 6.0,
        sideM: Double = 5.0,
        rolloutM: Double = 5.0,
    ): ShotResult {
        val samples = ArrayList<TrajectorySample>()
        var t = 0.0
        while (t < flightTimeSec) {
            val f = t / flightTimeSec
            samples.add(
                TrajectorySample(
                    px = sideM * f,
                    py = carryM * f,
                    pz = apexM * 4.0 * f * (1.0 - f),
                    tSec = t,
                )
            )
            t += 0.05
        }
        samples.add(TrajectorySample(sideM, carryM, 0.0, flightTimeSec))
        return ShotResult(
            carryM = carryM,
            rolloutM = rolloutM,
            totalM = carryM + rolloutM,
            sideM = sideM,
            apexM = apexM,
            flightTimeSec = flightTimeSec,
            samples = samples,
        )
    }

    private fun frac(shot: ShotResult, tSec: Double): Float =
        (tSec / shot.flightTimeSec).toFloat()

    /**
     * Descent must keep the drawn ball inside the frame the whole way
     * down (user report 2026-09-25 round 3: "long shots always go out
     * of screen above; short shots are ok"). Short shots never enter the
     * descent branch, which is why they were unaffected: the old descent
     * lerped in WORLD space between the chase rig (behind the moving
     * ball) and the overlook (a fixed point at the landing), so on long
     * shots the camera flew PAST the ball mid-flight — the ball ended up
     * behind the camera plane (drawn at the launch anchor) or above the
     * frame top on tall slots.
     *
     * The ball-relative morph keeps the ball between dead center and the
     * chase lock line (+CHASE_UP_M / CHASE_BACK_M = +0.125) for every
     * descent frame, on any slot geometry.
     */
    @Test
    fun descentKeepsTheDrawnBallInsideTheFrame() {
        val shot = parabolicShot(carryM = 240.0, apexM = 45.0, flightTimeSec = 6.0)
        for (vMin in listOf(-0.125, -0.20)) {
            val drawn = FollowCam.scaledSamples(shot, vMin)
            val descentT = drawn.maxByOrNull { it.pz }!!.tSec
            var f = (descentT / shot.flightTimeSec).toFloat()
            val frameBottomV = 0.125f + 0.20f // generous: any real slot is deeper than this
            while (f < 1.0f) {
                val cam = FollowCam.cameraAt(shot, f, vMin)
                val timeSec = f.toDouble() * shot.flightTimeSec
                val head = drawn.lastOrNull { it.tSec <= timeSec } ?: drawn.first()
                val p = PovProjector.project(cam, head.px, head.py, head.pz)
                assertNotNull("ball behind camera plane at f=$f (vMin=$vMin); descent rig left the ball behind", p)
                val v = p!!.v
                assertTrue(
                    "ball above frame at f=$f (vMin=$vMin): v=$v",
                    v >= -0.15,
                )
                assertTrue(
                    "ball below frame at f=$f (vMin=$vMin): v=$v",
                    v <= frameBottomV,
                )
                f += 0.004f
            }
        }
    }

    /**
     * During the landing hold the camera must track the rolling ball, not
     * park at a fixed overlook aimed at the carry point: a 200+ shot rolls
     * 15-25 m, and with the 45 deg overlook the visible ground band ends
     * only ~14 m past carry, so the ball sailed out the frame TOP mid-roll
     * (user report 2026-09-25: "when the ball hits the ground it can go
     * out of frame, especially on long 200+ shots").
     *
     * The extended drawn path is the apex-clamped flight plus the synthetic
     * ground roll; the hold uses the ball-relative overlook (descentRig at
     * s = 1.0 anchored to the live head), so the ball's screen position is
     * identical to its touchdown-frame position the whole roll.
     */
    @Test
    fun rolloutKeepsTheBallInsideTheFrame() {
        val shot = parabolicShot(carryM = 230.0, apexM = 42.0, flightTimeSec = 6.5, rolloutM = 25.0)
        val drawn = FollowCam.scaledSamples(shot) + RangeRollout.samples(shot)
        val frameBottomV = 0.125 + 0.20 // generous: any real slot is deeper than this
        var f = 1.0f
        val endF = (1.0 + 1.8 / shot.flightTimeSec).toFloat()
        while (f <= endF) {
            val timeSec = f.toDouble() * shot.flightTimeSec
            val head = drawn.lastOrNull { it.tSec <= timeSec } ?: drawn.first()
            val p = PovProjector.project(FollowCam.cameraAt(shot, f), head.px, head.py, head.pz)
            assertNotNull("ball behind camera plane during rollout at f=$f", p)
            val v = p!!.v
            assertTrue("ball above frame during rollout at f=$f: v=$v", v >= -0.15)
            assertTrue("ball below frame during rollout at f=$f: v=$v", v <= frameBottomV)
            f += 0.004f
        }
    }

    @Test
    fun staticBeforeTheDelay() {
        val shot = parabolicShot()
        // t = 0.7 s is inside the 1.0 s static timer window (the apex
        // clamp keeps this flight framed, so no early engage either).
        assertEquals(RangeCamera.STATIC, FollowCam.cameraAt(shot, frac(shot, 0.7)))
    }

    @Test
    fun noShotTimeMeansStatic() {
        val broken = ShotResult(180.0, 5.0, 185.0, 5.0, 30.0, 0.0, emptyList())
        assertEquals(RangeCamera.STATIC, FollowCam.cameraAt(broken, 0.5f))
    }

    @Test
    fun highApexEngagesBeforeTheTimer() {
        val driver = parabolicShot(carryM = 240.0, apexM = 45.0)
        // On the reference-wide geometry the apex clamp keeps even this
        // monster inside the static frame (timer-only engage). On a
        // squarer canvas (apexVMin = -0.20) the drawn flight still
        // breaches the top of frame at ~0.8 s and early engage must
        // fire BEFORE the 1.0 s timer.
        assertEquals(RangeCamera.STATIC, FollowCam.cameraAt(driver, frac(driver, 0.7), apexVMin = -0.20))
        assertNotEquals(RangeCamera.STATIC, FollowCam.cameraAt(driver, frac(driver, 0.9), apexVMin = -0.20))
    }

    @Test
    fun chaseTracksBehindAndAboveTheDrawnBall() {
        val shot = parabolicShot()
        // t = 2.8 s: past blend end (~2.4 s), before apex (3.0 s) -> chase.
        // The rig follows the DRAWN flight, and this shot's 30 m apex IS
        // clamped on the reference geometry (drawn z < raw z).
        val drawn = FollowCam.scaledSamples(shot)
        assertTrue(drawn.last { it.tSec <= 2.8 }.pz < shot.samples.last { it.tSec <= 2.8 }.pz)
        val head = drawn.last { it.tSec <= 2.8 }
        val cam = FollowCam.cameraAt(shot, frac(shot, 2.8))
        assertEquals(0.0, cam.pitchRad, 1e-12)
        assertEquals(head.px, cam.x, 1e-9)
        assertEquals(head.py - FollowCam.CHASE_BACK_M, cam.y, 1e-9)
        assertEquals(head.pz + FollowCam.CHASE_UP_M, cam.z, 1e-9)
    }

    @Test
    fun chaseKeepsTheDrawnBallInsideTheFrame() {
        // A monster drive: the clamp pulls its 45 m apex down to ~26 m on
        // the reference geometry. Chasing the DRAWN flight pins the ball
        // exactly CHASE_UP_M / CHASE_BACK_M below camera level (v =
        // +0.125 on screen) — centered, never out of frame (user gate
        // 2026-09-25: long shots lost the ball over the frame bottom).
        val driver = parabolicShot(carryM = 240.0, apexM = 45.0)
        val drawn = FollowCam.scaledSamples(driver)
        val head = drawn.last { it.tSec <= 3.0 } // apex
        val cam = FollowCam.cameraAt(driver, frac(driver, 3.0))
        val p = PovProjector.project(cam, head.px, head.py, head.pz)
        assertNotNull(p)
        assertEquals(FollowCam.CHASE_UP_M / FollowCam.CHASE_BACK_M, p!!.v, 1e-9)
    }

    @Test
    fun descentStartsSmoothlyFromTheChase() {
        // At the apex the camera must NOT halt: it keeps riding the
        // moving chase rig while easing toward the overlook, so forward
        // displacement right after the apex stays on par with just
        // before it (the old frozen-rig lerp dropped it to ~zero — the
        // user felt it as an abrupt pan-down, gate 2026-09-25).
        // Windows are 0.2 s wide: the frac() float roundtrip quantizes
        // the head to the 0.05 s sample grid, so a narrow window would
        // measure unequal spans even with smooth motion.
        val shot = parabolicShot() // apex at 3.0 s
        fun camAt(tSec: Double) = FollowCam.cameraAt(shot, frac(shot, tSec))
        val before = camAt(2.8)
        val at = camAt(3.0)
        val after = camAt(3.2)
        fun dist(a: RangeCamera, b: RangeCamera): Double {
            val dxy = Math.hypot(a.x - b.x, a.y - b.y)
            return Math.hypot(dxy, a.z - b.z)
        }
        assertTrue("descent start halted the camera", dist(at, after) >= 0.5 * dist(before, at))
    }

    @Test
    fun blendCrossFadesFromStaticToChase() {
        val wedge = parabolicShot(carryM = 60.0, apexM = 10.0, flightTimeSec = 3.5)
        // Low apex never leaves the frame -> engages on the 1.0 s timer,
        // blend spans 1.0-2.4 s. t = 1.9 s is mid-blend.
        val cam = FollowCam.cameraAt(wedge, frac(wedge, 1.9))
        val head = wedge.samples.last { it.tSec <= 1.9 }
        assertEquals(0.0, cam.pitchRad, 1e-12)
        assertTrue(cam.y > RangeCamera.STATIC.y)             // left -21.2 m ...
        assertTrue(cam.y < head.py - FollowCam.CHASE_BACK_M)  // ... but not at the chase rig
        assertTrue(cam.z > RangeCamera.STATIC.z)             // rose off the crane height
    }

    @Test
    fun blendIsMonotonicTowardTheChaseRig() {
        // A shot that is still RISING through the whole blend window
        // (apex at 4.0 s > blend end 2.4 s), so both the smoothstep factor
        // and the chase rig move monotonically — the camera must too.
        val rising = parabolicShot(carryM = 80.0, apexM = 12.0, flightTimeSec = 8.0)
        var lastY = RangeCamera.STATIC.y
        var lastZ = RangeCamera.STATIC.z
        var t = FollowCam.FOLLOW_DELAY_SEC
        while (t < FollowCam.FOLLOW_DELAY_SEC + FollowCam.BLEND_SEC) {
            val cam = FollowCam.cameraAt(rising, frac(rising, t))
            assertTrue(cam.y >= lastY) // forward only
            assertTrue(cam.z >= lastZ) // rising only
            assertEquals(0.0, cam.pitchRad, 1e-12)
            lastY = cam.y
            lastZ = cam.z
            t += 0.1
        }
    }

    @Test
    fun touchdownIsTheFullOverlookRig() {
        val shot = parabolicShot()
        val cam = FollowCam.cameraAt(shot, 1f) // fraction = 1 -> touchdown, hold begins
        val pitch = Math.toRadians(FollowCam.LAND_PITCH_DEG)
        assertEquals(pitch, cam.pitchRad, 1e-12)
        assertEquals(shot.sideM, cam.x, 1e-9)
        assertEquals(shot.carryM - FollowCam.OVERLOOK_DIST_M * Math.cos(pitch), cam.y, 1e-9)
        assertEquals(FollowCam.OVERLOOK_DIST_M * Math.sin(pitch), cam.z, 1e-9)
    }

    @Test
    fun descentPitchEasesFromLevelToLandPitch() {
        val shot = parabolicShot()
        // Descent spans apex (3.0 s) -> touchdown (6.0 s).
        val mid = FollowCam.cameraAt(shot, frac(shot, 4.5))
        assertTrue(mid.pitchRad > 0.0)
        assertTrue(mid.pitchRad < Math.toRadians(FollowCam.LAND_PITCH_DEG))
    }

    @Test
    fun holdParksAtTheOverlookUntilTheEnd() {
        // rolloutM = 0: with no ground roll the ball-relative hold rig
        // collapses to the fixed overlook for the whole hold, so this
        // pins the stationary behaviour. A rolling shot is covered by
        // rolloutKeepsTheBallInsideTheFrame.
        val shot = parabolicShot(rolloutM = 0.0)
        val touchdown = FollowCam.cameraAt(shot, 1f)
        val midHold = FollowCam.cameraAt(shot, frac(shot, 6.0 + 1.25))
        val beforeEnd = FollowCam.cameraAt(shot, (FollowCam.endFraction(shot) - 0.01).toFloat())
        assertEquals(touchdown, midHold)
        assertEquals(touchdown, beforeEnd)
    }

    @Test
    fun snapBackToStaticAfterTheHold() {
        val shot = parabolicShot()
        assertEquals(RangeCamera.STATIC, FollowCam.cameraAt(shot, FollowCam.endFraction(shot).toFloat()))
        assertEquals(RangeCamera.STATIC, FollowCam.cameraAt(shot, (FollowCam.endFraction(shot) + 0.05).toFloat()))
    }

    @Test
    fun timerEngagesWhenTheClampKeepsTheBallFramed() {
        // The apex clamp keeps this shot's 25 m apex inside the static
        // frame on the reference geometry, so no early-engage crossing
        // exists: engage must come from the 1.0 s timer, and the cam
        // must already be blending at 1.6 s, not still STATIC.
        val shot = parabolicShot(carryM = 180.0, apexM = 25.0, flightTimeSec = 6.0)
        val cam = FollowCam.cameraAt(shot, frac(shot, 1.6))
        assertNotEquals(RangeCamera.STATIC, cam)
        assertTrue(cam.y > RangeCamera.STATIC.y) // left the crane position
    }

    @Test
    fun shortShotSkipsStraightToTheOverlook() {
        val chip = parabolicShot(carryM = 15.0, apexM = 6.0, flightTimeSec = 1.6)
        val cam = FollowCam.cameraAt(chip, 0.2f)
        val pitch = Math.toRadians(FollowCam.LAND_PITCH_DEG)
        assertEquals(pitch, cam.pitchRad, 1e-12)
        assertEquals(chip.carryM - FollowCam.OVERLOOK_DIST_M * Math.cos(pitch), cam.y, 1e-9)
    }

    @Test
    fun shortShotAlsoSnapsBack() {
        val chip = parabolicShot(carryM = 15.0, apexM = 6.0, flightTimeSec = 1.6)
        assertEquals(RangeCamera.STATIC, FollowCam.cameraAt(chip, FollowCam.endFraction(chip).toFloat()))
    }

    @Test
    fun endFractionExtendsByTheHold() {
        val shot = parabolicShot(flightTimeSec = 6.0)
        assertEquals(1.0 + FollowCam.LAND_HOLD_SEC / 6.0, FollowCam.endFraction(shot), 1e-12)
    }

    @Test
    fun deterministic() {
        val shot = parabolicShot()
        for (f in listOf(0f, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f, 1f, 1.2f)) {
            assertEquals(FollowCam.cameraAt(shot, f), FollowCam.cameraAt(shot, f))
        }
    }
}
