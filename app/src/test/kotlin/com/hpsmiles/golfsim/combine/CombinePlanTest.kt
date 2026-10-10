package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.scoring.Lie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CombinePlanTest {

    private val wedges = CombineCatalog.byId("WEDGES")!!
    private val approach = CombineCatalog.byId("APPROACH")!!

    @Test
    fun `station sequence is an interleaved round-robin`() {
        val sequence = (0 until CombinePlan.SHOTS_TOTAL).map { CombinePlan.stationIndexFor(it) }
        assertEquals(
            listOf(0, 0, 1, 1, 2, 2, 0, 0, 1, 1, 2, 2, 0, 0, 1, 1, 2, 2),
            sequence,
        )
    }

    @Test
    fun `visit numbers advance every cycle`() {
        assertEquals(0, CombinePlan.visitFor(5))
        assertEquals(1, CombinePlan.visitFor(6))
        assertEquals(2, CombinePlan.visitFor(17))
    }

    @Test
    fun `banner shows on visit boundaries`() {
        assertTrue(CombinePlan.isBannerShot(2))
        assertTrue(CombinePlan.isBannerShot(6))
        assertTrue(CombinePlan.isBannerShot(12))
        assertFalse(CombinePlan.isBannerShot(0))
        assertFalse(CombinePlan.isBannerShot(1))
        assertFalse(CombinePlan.isBannerShot(18))
        assertEquals("NEXT: 70 m · VISIT 1", CombinePlan.bannerLabel(wedges, 2))
        assertEquals("NEXT: 50 m · VISIT 2", CombinePlan.bannerLabel(wedges, 6))
    }

    @Test
    fun `counts track shots per station`() {
        assertEquals(listOf(0, 0, 0), CombinePlan.countsFor(wedges, 0))
        assertEquals(listOf(3, 2, 2), CombinePlan.countsFor(wedges, 7))
        assertEquals(listOf(6, 6, 6), CombinePlan.countsFor(wedges, 18))
    }

    @Test
    fun `pins follow the visit for approach stations and are absent for the driver`() {
        assertEquals(CombineCatalog.visitPins[0], CombinePlan.pinFor(approach, 0))
        assertEquals(CombineCatalog.visitPins[1], CombinePlan.pinFor(approach, 6))
        assertEquals(CombineCatalog.visitPins[2], CombinePlan.pinFor(approach, 12))
        assertNull(CombinePlan.pinFor(approach, 4))
    }

    @Test
    fun `approach rest distance is measured to the visit pin`() {
        // WEDGES 50 m, visit 1 pin at (0,0); rest 3 m long and 1 m left
        val distance = CombinePlan.restDistanceM(wedges, 0, 3.0, 49.0)
        assertEquals(kotlin.math.sqrt(10.0), distance, 1e-9)
    }

    @Test
    fun `driver rest distance is measured to the invisible pin at the hole baseline`() {
        val distance = CombinePlan.restDistanceM(approach, 4, 10.0, 300.0)
        assertEquals(kotlin.math.sqrt(100.0 + 40.0 * 40.0), distance, 1e-9)
    }

    @Test
    fun `shot inputs map lies and distances`() {
        val approachInput = CombinePlan.shotInput(wedges, 0, Lie.GREEN, 3.2)
        assertEquals(Lie.FAIRWAY, approachInput.startLie)
        assertEquals(50.0, approachInput.startM, 1e-9)
        val driverInput = CombinePlan.shotInput(approach, 4, Lie.FAIRWAY, 41.2)
        assertEquals(Lie.TEE, driverInput.startLie)
        assertEquals(340.0, driverInput.startM, 1e-9)
    }
}
