// app/src/test/kotlin/com/hpsmiles/golfsim/range/PovProjectorTest.kt
package com.hpsmiles.golfsim.range

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pinhole projection used by the POV range canvas. Values verified
 * by hand: camera height 1.7 m at the origin looking down +y, so a point on
 * the ground at distance d has v = 1.7 / d and scale = 1 / d.
 */
class PovProjectorTest {

    @Test
    fun groundBandsProjectAtExactFractions() {
        // v for a ground point at distance d is 1.7 / d.
        assertEquals(1.7 / 50.0, PovProjector.bandV(50.0), 1e-12)
        assertEquals(1.7 / 100.0, PovProjector.bandV(100.0), 1e-12)
        assertEquals(1.7 / 150.0, PovProjector.bandV(150.0), 1e-12)
        assertEquals(1.7 / 200.0, PovProjector.bandV(200.0), 1e-12)
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
        assertEquals(1.7 / 100.0, p.v, 1e-12)
        assertEquals(1.0 / 100.0, p.scale, 1e-12)
    }

    @Test
    fun apexAboveEyeLevelProjectsAboveHorizon() {
        // Apex at 30 m up, 150 m out: v = (1.7 - 30) / 150 < 0 (above horizon).
        val p = PovProjector.project(0.0, 150.0, 30.0)!!
        assertTrue(p.v < 0.0)
    }

    @Test
    fun typicalLandingPointIsInBounds() {
        // Landing at x=+10 m lateral, 163 m out: small positives both axes.
        val p = PovProjector.project(10.0, 163.0, 0.0)!!
        assertEquals(10.0 / 163.0, p.u, 1e-12)
        assertTrue(p.v > 0.0)
        assertTrue(p.v < 1.7 / 100.0) // 163 m is farther than the 100 m band
    }

    @Test
    fun pointsAtOrBehindTheCameraAreRejected() {
        assertNull(PovProjector.project(1.0, 0.0, 0.0))
        assertNull(PovProjector.project(1.0, -5.0, 2.0))
    }

    @Test
    fun scaleShrinksWithDistance() {
        val near = PovProjector.project(0.0, 50.0, 0.0)!!
        val far = PovProjector.project(0.0, 200.0, 0.0)!!
        assertTrue(near.scale > far.scale)
    }
}
