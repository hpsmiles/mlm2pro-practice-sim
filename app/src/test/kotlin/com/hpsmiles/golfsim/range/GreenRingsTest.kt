package com.hpsmiles.golfsim.range

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ring banding for the concentric-ring green styling (2026-10-15): 2 m inner
 * rings, outermost ring thinner when the green radius isn't a multiple of 2,
 * always filling exactly out to the green radius.
 */
class GreenRingsTest {

    @Test
    fun exactMultiple_givesUniformTwoMetreRings() {
        assertEquals(listOf(10.0, 8.0, 6.0, 4.0, 2.0), GreenRings.boundaryRadii(10.0))
    }

    @Test
    fun nonMultiple_thinsOnlyTheOutermostRing() {
        // 5 m green: 1 m outer ring, then two 2 m rings.
        assertEquals(listOf(5.0, 4.0, 2.0), GreenRings.boundaryRadii(5.0))
    }

    @Test
    fun boundaries_areDescending_andFillToTheRadius() {
        val radii = GreenRings.boundaryRadii(7.3)
        assertEquals(7.3, radii.first(), 1e-9)
        assertTrue(radii.zipWithNext().all { (outer, inner) -> outer > inner })
        // Innermost boundary is the innermost ring's fill radius; the band
        // between it and 0 must not exceed the nominal band width.
        assertTrue(radii.last() <= GreenRings.BAND_M + 1e-9)
    }

    @Test
    fun ringBands_neverExceedTwoMetres_exceptNone() {
        for (radius in listOf(0.5, 1.0, 2.0, 2.5, 3.0, 5.0, 7.3, 12.0, 15.4)) {
            val radii = GreenRings.boundaryRadii(radius)
            if (radii.size == 1) continue // single ring fills the whole radius
            val bands = mutableListOf(radius - radii[1])
            for (i in 1 until radii.size - 1) bands.add(radii[i] - radii[i + 1])
            // Only the outermost band may be thinner; every other band is 2 m.
            assertTrue(bands.drop(1).all { abs(it - GreenRings.BAND_M) < 1e-9 })
        }
    }

    @Test
    fun smallGreen_isOneRingAtFullRadius() {
        assertEquals(listOf(1.0), GreenRings.boundaryRadii(1.0))
        assertEquals(listOf(0.4), GreenRings.boundaryRadii(0.4))
    }

    @Test
    fun zeroOrNegativeRadius_givesNoRings() {
        assertTrue(GreenRings.boundaryRadii(0.0).isEmpty())
        assertTrue(GreenRings.boundaryRadii(-3.0).isEmpty())
    }

    @Test
    fun colours_alternateByRingIndex() {
        assertTrue(GreenRings.isVeryLightRing(0))
        assertTrue(!GreenRings.isVeryLightRing(1))
        assertTrue(GreenRings.isVeryLightRing(2))
    }
}
