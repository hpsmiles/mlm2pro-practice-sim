// app/src/test/kotlin/com/hpsmiles/golfsim/range/FollowCamTest.kt
package com.hpsmiles.golfsim.range

import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.TrajectorySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the FollowCam phase machine (spec 2026-09-25): static window ->
 * blend -> chase -> descent pitch-in -> landing hold -> snap back.
 *
 * Synthetic shots: straight-line lateral drift, py proportional to t, a
 * pz parabola peaking at t = T/2, samples every 50 ms. The default shot
 * (carry 180, apex 30, T = 6) early-engages at ~1.0 s (apex heads for the
 * top of frame); the low wedge (apex 10, carry 60, T = 3.5) never does,
 * so it engages on the 1.5 s timer.
 */
class FollowCamTest {

    private fun parabolicShot(
        carryM: Double = 180.0,
        apexM: Double = 30.0,
        flightTimeSec: Double = 6.0,
        sideM: Double = 5.0,
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
            rolloutM = 5.0,
            totalM = carryM + 5.0,
            sideM = sideM,
            apexM = apexM,
            flightTimeSec = flightTimeSec,
            samples = samples,
        )
    }

    private fun frac(shot: ShotResult, tSec: Double): Float =
        (tSec / shot.flightTimeSec).toFloat()

    @Test
    fun staticBeforeTheDelay() {
        val shot = parabolicShot()
        // t = 0.7 s is inside the static window (early engage ~1.0 s here).
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
        // t = 1.0 s is inside the 1.5 s static window, but a big apex has
        // already pushed the ball near the top of the frame -> early engage.
        assertNotEquals(RangeCamera.STATIC, FollowCam.cameraAt(driver, frac(driver, 1.0)))
    }

    @Test
    fun chaseTracksBehindAndAboveTheBall() {
        val shot = parabolicShot()
        // t = 2.8 s: past blend end (~1.8 s), before apex (3.0 s) -> chase.
        val head = shot.samples.last { it.tSec <= 2.8 }
        val cam = FollowCam.cameraAt(shot, frac(shot, 2.8))
        assertEquals(0.0, cam.pitchRad, 1e-12)
        assertEquals(head.px, cam.x, 1e-9)
        assertEquals(head.py - FollowCam.CHASE_BACK_M, cam.y, 1e-9)
        assertEquals(head.pz + FollowCam.CHASE_UP_M, cam.z, 1e-9)
    }

    @Test
    fun blendCrossFadesFromStaticToChase() {
        val wedge = parabolicShot(carryM = 60.0, apexM = 10.0, flightTimeSec = 3.5)
        // Low apex never leaves the frame -> engages on the 1.5 s timer,
        // blend spans 1.5-2.3 s. t = 1.9 s is mid-blend.
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
        // (apex at 4.0 s > blend end 2.3 s), so both the smoothstep factor
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
        val shot = parabolicShot()
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
    fun lateEarlyEngageStillRespectsTheTimer() {
        // Crossing that happens AFTER the 1.5 s timer (apex just over the
        // EARLY_ENGAGE_V threshold, so the ball only reaches the top of
        // frame near its peak ~1.9 s). Whichever-comes-first semantics: the
        // cam must already be blending at 1.6 s, not still STATIC.
        val late = parabolicShot(carryM = 180.0, apexM = 25.0, flightTimeSec = 6.0)
        val cam = FollowCam.cameraAt(late, frac(late, 1.6))
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
