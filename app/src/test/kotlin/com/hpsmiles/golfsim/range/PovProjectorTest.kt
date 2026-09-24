// app/src/test/kotlin/com/hpsmiles/golfsim/range/PovProjectorTest.kt
package com.hpsmiles.golfsim.range

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pinhole projection used by the POV range canvas. Values verified
 * by hand: camera height 1.7 m, sitting [PovProjector.CAM_BACK_M] behind the
 * ball, so a ground point at world distance y has depth y + 4.5 and
 * v = 1.7 / (y + 4.5), scale = 1 / (y + 4.5).
 *
 * The camera-behind-tee decision (2026-09-24) exists so the ball launch is
 * on-screen near the bottom edge: v of the tee (y = 0) is 1.7 / 4.5 = 0.378,
 * ~95% down the 0.7h ground area at focal 1.1w on the landscape tablet.
 */
class PovProjectorTest {

    private val back = PovProjector.CAM_BACK_M

    @Test
    fun groundBandsProjectAtExactFractions() {
        // v for a ground point at world distance y is 1.7 / (y + back).
        assertEquals(1.7 / (50.0 + back), PovProjector.bandV(50.0), 1e-12)
        assertEquals(1.7 / (100.0 + back), PovProjector.bandV(100.0), 1e-12)
        assertEquals(1.7 / (150.0 + back), PovProjector.bandV(150.0), 1e-12)
        assertEquals(1.7 / (200.0 + back), PovProjector.bandV(200.0), 1e-12)
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
        assertEquals(1.7 / (100.0 + back), p.v, 1e-12)
        assertEquals(1.0 / (100.0 + back), p.scale, 1e-12)
    }

    @Test
    fun launchPointIsVisibleNearBottomOfFrame() {
        // The tee (0, 0, 0) sits at depth `back` from the camera: v = 1.7/4.5
        // = 0.378 — ~95% down the ground area on the landscape tablet, i.e.
        // just above the bottom edge. This is the whole point of the
        // camera-behind-tee change.
        val tee = PovProjector.project(0.0, 0.0, 0.0)!!
        assertEquals(1.7 / back, tee.v, 1e-12)
        assertEquals(0.0, tee.u, 1e-12)
        // Bottom-edge budget at focal 1.1w: v_bottom = 0.7h / 1.1w ≈ 0.397 on
        // the 2000x1250 tablet — the tee must stay inside it with margin.
        assertTrue(tee.v < 0.397)
    }

    @Test
    fun apexAboveEyeLevelProjectsAboveHorizon() {
        // Apex at 30 m up, 150 m out: v = (1.7 - 30) / 154.5 < 0 (above horizon).
        val p = PovProjector.project(0.0, 150.0, 30.0)!!
        assertTrue(p.v < 0.0)
    }

    @Test
    fun typicalLandingPointIsInBounds() {
        // Landing at x=+10 m lateral, 163 m out: small positives both axes.
        val p = PovProjector.project(10.0, 163.0, 0.0)!!
        assertEquals(10.0 / (163.0 + back), p.u, 1e-12)
        assertTrue(p.v > 0.0)
        assertTrue(p.v < 1.7 / (100.0 + back)) // 163 m is farther than the 100 m band
    }

    @Test
    fun pointsAtOrBehindTheCameraAreRejected() {
        // Camera plane is at y = -CAM_BACK_M.
        assertNull(PovProjector.project(1.0, -PovProjector.CAM_BACK_M, 0.0))
        assertNull(PovProjector.project(1.0, -PovProjector.CAM_BACK_M - 1.0, 2.0))
    }

    @Test
    fun scaleShrinksWithDistance() {
        val near = PovProjector.project(0.0, 50.0, 0.0)!!
        val far = PovProjector.project(0.0, 200.0, 0.0)!!
        assertTrue(near.scale > far.scale)
    }
}
