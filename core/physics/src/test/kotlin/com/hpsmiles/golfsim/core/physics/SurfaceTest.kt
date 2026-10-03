package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceTest {

    @Test
    fun greenDecelIsStimpAnchored() {
        // Stimpmeter physics: 1.83 m/s release, decel = 5.49 / stimp m/s^2 (spec §6)
        assertEquals(0.4991, Surface.GREEN_NORMAL.rollDecelMps2, 1e-4)
        assertEquals(0.549, Surface.green(stimp = 10.0).rollDecelMps2, 1e-4)
    }

    @Test
    fun fairwayAndRoughDecelAreMuAnchored() {
        assertEquals(1.00, Surface.FAIRWAY_NORMAL.rollDecelMps2, 1e-6)     // literature-anchored
        assertEquals(3.1392, Surface.ROUGH_NORMAL.rollDecelMps2, 1e-6)    // 0.32 * 9.81
    }

    @Test
    fun firmnessScalesRollDecel() {
        // 2026-10-01 tablet feedback: firm mu ratio x0.60 -> x0.75 (x0.60
        // rolled like glass; punch run-out blew past realistic).
        assertEquals(1.00 * 0.75, Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM).rollDecelMps2, 1e-6)
        assertEquals(1.00 * 1.40, Surface.FAIRWAY_NORMAL.withFirmness(Firmness.SOFT).rollDecelMps2, 1e-6)
    }

    @Test
    fun firmnessScalesCorThetaAndSpin() {
        assertEquals(0.432, Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM).cor, 1e-9)   // 0.40 * 1.08
        assertEquals(0.368, Surface.FAIRWAY_NORMAL.withFirmness(Firmness.SOFT).cor, 1e-9)   // 0.40 * 0.92
        assertEquals(0.29 * (0.25 / 0.29), Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM).thetaCritRad, 1e-9)
        // Plan deviation (minimal test-only fix): expected constant corrected
        // 0.35 -> 0.70 — rough spin scale is 0.70 in the spec §6 table (0.35 is
        // the fairway tuned value); the soft ratio 0.92/0.78 is unchanged.
        assertEquals(0.70 * (0.92 / 0.78), Surface.ROUGH_NORMAL.withFirmness(Firmness.SOFT).spinbackScale, 1e-9)
    }

    @Test
    fun uniformSurfaceAlwaysAnswersTheSameSurface() {
        val provider = UniformSurface(Surface.ROUGH_NORMAL)
        assertEquals(Surface.ROUGH_NORMAL, provider.surfaceAt(-5.0, 0.0))
        assertEquals(Surface.ROUGH_NORMAL, provider.surfaceAt(50.0, 300.0))
    }

    @Test
    fun zoneTableAnswersByDistanceBand() {
        val zones = ZoneTable()
        assertEquals(Surface.GREEN_NORMAL, zones.surfaceAt(0.0, 150.0))
        assertEquals(Surface.FAIRWAY_NORMAL, zones.surfaceAt(0.0, 230.0))
        assertEquals(Surface.ROUGH_NORMAL, zones.surfaceAt(0.0, 300.0))
    }

    @Test
    fun firmnessOrderingRollsFartherToShorter() {
        // roll distance is inversely proportional to rollDecel on the same speed
        assertTrue(
            Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM).rollDecelMps2 <
                Surface.FAIRWAY_NORMAL.rollDecelMps2
        )
        assertTrue(
            Surface.FAIRWAY_NORMAL.rollDecelMps2 <
                Surface.FAIRWAY_NORMAL.withFirmness(Firmness.SOFT).rollDecelMps2
        )
    }
}
