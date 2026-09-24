// app/src/test/kotlin/com/hpsmiles/golfsim/range/PovProjectorTest.kt
package com.hpsmiles.golfsim.range

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pinhole projection used by the POV range canvas. Values verified
 * by hand: camera height 1.7 m, sitting [PovProjector.CAM_BACK_M] behind the
 * ball, so a ground point at world distance y has depth y + 5 and
 * v = 1.7 / (y + 5), scale = 1 / (y + 5).
 *
 * The camera-behind-tee decision (2026-09-24) exists so the ball launch is
 * on-screen: v of the tee (y = 0) is 1.7 / 5 = 0.34, which on the tablet
 * (focal 1.1w, frame below horizon 0.7h) lands ~86% down the frame.
 */
class PovProjectorTest {

    @Test
    fun groundBandsProjectAtExactFractions() {
        // v for a ground point at world distance y is 1.7 / (y + 5).
        assertEquals(1.7 / 55.0, PovProjector.bandV(50.0), 1e-12)
        assertEquals(1.7 / 105.0, PovProjector.bandV(100.0), 1e-12)
        assertEquals(1.7 / 155.0, PovProjector.bandV(150.0), 1e-12)
        assertEquals(1.7 / 205.0, PovProjector.bandV(200.0), 1e-12)
    }

    @Test
    fun nearerBandsAreLowerOnScreen() {
        // v grows toward the viewer: 50 m band is below the 100 m band.
        assertTrue(PovProjector.bandV(50.0) > PovProjector.bandV(100.0))
        assertTrue(PovProjector.bandV(100.0) > PovProjector.bandV(150.0))
        assertTrue(PovProjector.bandV(150.0) > PovProjector.bandV(200.0))
    }

    @Test
    fun straightAheadGroundPointIsCentered() {
        val p = PovProjector.project(0.0, 100.0, 0.0)!!
        assertEquals(0.0, p.u, 1e-12)
        assertEquals(1.7 / 105.0, p.v, 1e-12)
        assertEquals(1.0 / 105.0, p.scale, 1e-12)
    }

    @Test
    fun launchPointIsVisibleOnScreen() {
        // The tee (0, 0, 0) sits at depth 5 from the camera: v = 1.7 / 5 =
        // 0.34 — well inside the frame (bottom of frame is ~0.397 in v units
        // at focal 1.1w on the 2000x1250 landscape tablet). This is the whole
        // point of the camera-behind-tee change.
        val tee = PovProjector.project(0.0, 0.0, 0.0)!!
        assertEquals(1.7 / 5.0, tee.v, 1e-12)
        assertEquals(0.0, tee.u, 1e-12)
    }

    @Test
    fun apexAboveEyeLevelProjectsAboveHorizon() {
        // Apex at 30 m up, 150 m out: v = (1.7 - 30) / 155 < 0 (above horizon).
        val p = PovProjector.project(0.0, 150.0, 30.0)!!
        assertTrue(p.v < 0.0)
    }

    @Test
    fun typicalLandingPointIsInBounds() {
        // Landing at x=+10 m lateral, 163 m out: small positives both axes.
        val p = PovProjector.project(10.0, 163.0, 0.0)!!
        assertEquals(10.0 / 168.0, p.u, 1e-12)
        assertTrue(p.v > 0.0)
        assertTrue(p.v < 1.7 / 105.0) // 163 m is farther than the 100 m band
    }

    @Test
    fun pointsAtOrBehindTheCameraAreRejected() {
        // Camera plane is at y = -CAM_BACK_M.
        assertNull(PovProjector.project(1.0, -5.0, 0.0))
        assertNull(PovProjector.project(1.0, -6.0, 2.0))
    }

    @Test
    fun scaleShrinksWithDistance() {
        val near = PovProjector.project(0.0, 50.0, 0.0)!!
        val far = PovProjector.project(0.0, 200.0, 0.0)!!
        assertTrue(near.scale > far.scale)
    }
}
