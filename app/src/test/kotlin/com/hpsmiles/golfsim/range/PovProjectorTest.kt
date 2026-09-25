// app/src/test/kotlin/com/hpsmiles/golfsim/range/PovProjectorTest.kt
package com.hpsmiles.golfsim.range

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the camera-based pinhole projection used by the POV range canvas.
 *
 * Static rig (spec 2026-09-25, mockup C): crane position 8 m above the
 * ground, [PovProjector.CAM_BACK_M] behind the ball. The tee (0, 0) sits
 * at v = 8 / 21.2 = 0.377, just inside the bottom-edge budget (0.397 at
 * focal 1.1w on the 2000x1250 tablet).
 *
 * Pitch is nose-down radians about the lateral axis. At pitch = 0 the
 * generalized math collapses to the old pinhole: u = x / depth,
 * v = (camZ - z) / depth, depth = y - camY.
 */
class PovProjectorTest {

    private val cam = RangeCamera.STATIC

    @Test
    fun staticRigIsTheRaisedCranePosition() {
        assertEquals(0.0, cam.x, 1e-12)
        assertEquals(-PovProjector.CAM_BACK_M, cam.y, 1e-12)
        assertEquals(PovProjector.CAM_HEIGHT_M, cam.z, 1e-12)
        assertEquals(0.0, cam.pitchRad, 0.0)
        assertEquals(8.0, PovProjector.CAM_HEIGHT_M, 1e-12)
        assertEquals(21.2, PovProjector.CAM_BACK_M, 1e-12)
    }

    @Test
    fun groundBandsProjectAtExactFractions() {
        assertEquals(8.0 / (50.0 + 21.2), PovProjector.bandV(cam, 50.0), 1e-12)
        assertEquals(8.0 / (100.0 + 21.2), PovProjector.bandV(cam, 100.0), 1e-12)
        assertEquals(8.0 / (150.0 + 21.2), PovProjector.bandV(cam, 150.0), 1e-12)
        assertEquals(8.0 / (200.0 + 21.2), PovProjector.bandV(cam, 200.0), 1e-12)
    }

    @Test
    fun nearerBandsAreLowerOnScreen() {
        assertTrue(PovProjector.bandV(cam, 50.0) > PovProjector.bandV(cam, 100.0))
        assertTrue(PovProjector.bandV(cam, 100.0) > PovProjector.bandV(cam, 150.0))
        assertTrue(PovProjector.bandV(cam, 150.0) > PovProjector.bandV(cam, 200.0))
    }

    @Test
    fun straightAheadGroundPointIsCentered() {
        val p = PovProjector.project(cam, 0.0, 100.0, 0.0)!!
        assertEquals(0.0, p.u, 1e-12)
        assertEquals(8.0 / (100.0 + 21.2), p.v, 1e-12)
        assertEquals(1.0 / (100.0 + 21.2), p.scale, 1e-12)
    }

    @Test
    fun launchPointIsVisibleNearBottomOfFrame() {
        val tee = PovProjector.project(cam, 0.0, 0.0, 0.0)!!
        assertEquals(8.0 / 21.2, tee.v, 1e-12) // = 0.377
        assertEquals(0.0, tee.u, 1e-12)
        // Bottom-edge budget at focal 1.1w: v_bottom = 0.7h / 1.1w = 0.397
        // on the 2000x1250 tablet — the tee must stay inside it.
        assertTrue(tee.v < 0.397)
    }

    @Test
    fun apexAboveEyeLevelProjectsAboveHorizon() {
        // Apex 30 m up, 150 m out: (8 - 30) / 171.2 < 0.
        val p = PovProjector.project(cam, 0.0, 150.0, 30.0)!!
        assertTrue(p.v < 0.0)
    }

    @Test
    fun typicalLandingPointIsInBounds() {
        val p = PovProjector.project(cam, 10.0, 163.0, 0.0)!!
        assertEquals(10.0 / (163.0 + 21.2), p.u, 1e-12)
        assertTrue(p.v > 0.0)
        assertTrue(p.v < PovProjector.bandV(cam, 100.0)) // farther than the 100 m band
    }

    @Test
    fun pitchZeroCollapsesToTheLevelPinhole() {
        // Camera (0, 0, 3, 0), point (2, 10, 7): the level-camera textbook case.
        val c = RangeCamera(0.0, 0.0, 3.0, 0.0)
        val p = PovProjector.project(c, 2.0, 10.0, 7.0)!!
        assertEquals(2.0 / 10.0, p.u, 1e-12)
        assertEquals((3.0 - 7.0) / 10.0, p.v, 1e-12)
        assertEquals(1.0 / 10.0, p.scale, 1e-12)
    }

    @Test
    fun noseDownPitchKeepsOnAxisPointCentered() {
        // Camera at (0, 0, 25) pitched 45 deg down. A point 10 m out along
        // the camera axis (direction (0, cos45, -sin45)) projects to the
        // exact screen centre at scale 1/10.
        val c = RangeCamera(0.0, 0.0, 25.0, Math.toRadians(45.0))
        val p = PovProjector.project(
            c,
            0.0,
            10.0 * Math.cos(Math.toRadians(45.0)),
            25.0 - 10.0 * Math.sin(Math.toRadians(45.0)),
        )!!
        assertEquals(0.0, p.u, 1e-12)
        assertEquals(0.0, p.v, 1e-12)
        assertEquals(0.1, p.scale, 1e-12)
    }

    @Test
    fun pitchedHorizonShiftsAboveFrameCentre() {
        // A far ground point under nose-down pitch tends to v = -tan(theta);
        // at 45 deg the horizon sits at v = -1 (above the top of frame).
        val c = RangeCamera(0.0, 0.0, 8.0, Math.toRadians(45.0))
        val far = PovProjector.project(c, 0.0, 1.0e9, 0.0)!!
        assertEquals(-1.0, far.v, 1e-6)
    }

    @Test
    fun pitchRaisesGroundPointsOnScreen() {
        val level = PovProjector.project(cam, 0.0, 100.0, 0.0)!!
        val pitched = PovProjector.project(
            RangeCamera(0.0, -21.2, 8.0, Math.toRadians(45.0)), 0.0, 100.0, 0.0,
        )!!
        assertTrue(pitched.v < level.v)
    }

    @Test
    fun pointsAtOrBehindTheCameraPlaneAreRejected() {
        assertNull(PovProjector.project(cam, 1.0, -PovProjector.CAM_BACK_M, 0.0))
        assertNull(PovProjector.project(cam, 1.0, -PovProjector.CAM_BACK_M - 1.0, 2.0))
        // Under nose-down pitch the camera plane tilts: a point above the
        // camera falls behind the plane even though its world y is ahead.
        val c = RangeCamera(0.0, 0.0, 25.0, Math.toRadians(45.0))
        assertNull(PovProjector.project(c, 0.0, 5.0, 35.0)) // depth = (5-10)*cos45 < 0
    }

    @Test
    fun scaleShrinksWithDistance() {
        val near = PovProjector.project(cam, 0.0, 50.0, 0.0)!!
        val far = PovProjector.project(cam, 0.0, 200.0, 0.0)!!
        assertTrue(near.scale > far.scale)
    }
}
