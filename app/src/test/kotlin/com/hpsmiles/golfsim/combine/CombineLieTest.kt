package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.scoring.Lie
import org.junit.Assert.assertEquals
import org.junit.Test

class CombineLieTest {

    private val wedges = CombineCatalog.byId("WEDGES")!!
    private val approach = CombineCatalog.byId("APPROACH")!!

    @Test
    fun `inside the green oval is green`() {
        // WEDGES 50 m, visit-1 pin at (0, 50), radius 15
        assertEquals(Lie.GREEN, CombineLie.classify(wedges, 0, 0.0, 50.0))
        assertEquals(Lie.GREEN, CombineLie.classify(wedges, 0, 10.0, 45.0))
    }

    @Test
    fun `fairway rough boundary short of the green follows the corridor width`() {
        // fairwayHalfWidth(30) ≈ 14.1, fairwayHalfWidth(40) ≈ 14.2 (28 m corridor)
        assertEquals(Lie.FAIRWAY, CombineLie.classify(wedges, 0, 0.0, 30.0))
        assertEquals(Lie.FAIRWAY, CombineLie.classify(wedges, 0, 14.0, 40.0))
        assertEquals(Lie.ROUGH, CombineLie.classify(wedges, 0, 15.0, 40.0))
    }

    @Test
    fun `wide of the fairway is rough`() {
        assertEquals(Lie.ROUGH, CombineLie.classify(wedges, 0, 30.0, 30.0))
        assertEquals(Lie.ROUGH, CombineLie.classify(wedges, 0, 0.0, 340.0))
    }

    @Test
    fun `driver has no green - fairway or rough only`() {
        // APPROACH shotIndex 4 = DRIVER (driver shot indices are 4,5,10,11,16,17)
        assertEquals(Lie.FAIRWAY, CombineLie.classify(approach, 4, 5.0, 280.0))
        assertEquals(Lie.ROUGH, CombineLie.classify(approach, 4, 28.0, 280.0))
        assertEquals(Lie.ROUGH, CombineLie.classify(approach, 4, 0.0, 350.0))
    }
}
