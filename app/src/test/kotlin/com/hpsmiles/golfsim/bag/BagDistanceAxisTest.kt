package com.hpsmiles.golfsim.bag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page's ONE shared distance axis (2026-10-16, user decision): min 0,
 * ladder step, max = whole multiple of the step ≥ 1.06 × the largest plotted
 * distance, contiguous ticks. SIDE, TOP-DOWN and the matrix all consume it.
 */
class BagDistanceAxisTest {

    @Test
    fun `empty and non-positive input yield no axis`() {
        assertNull(BagDistanceAxis.fromDistances(emptyList()))
        assertNull(BagDistanceAxis.fromDistances(listOf(0.0, -5.0)))
    }

    @Test
    fun `min is zero - the tee`() {
        val axis = BagDistanceAxis.fromDistances(listOf(120.0, 237.0))!!
        assertEquals(0.0, axis.minM, 1e-9)
    }

    @Test
    fun `max is a whole multiple of the step and covers 1_06 x the largest distance`() {
        // largest 253.7 (a driver TOTAL) → rawSpan 268.9 → step 20 → max 280.
        val axis = BagDistanceAxis.fromDistances(listOf(76.0, 150.0, 237.4, 253.7))!!
        assertEquals(20.0, axis.stepM, 1e-9)
        assertEquals(280.0, axis.maxM, 1e-9)
        assertEquals(0.0, axis.maxM % axis.stepM, 1e-9)
        assertTrue(axis.maxM >= BagDistanceAxis.HEADROOM * 253.7 - 1e-9)
    }

    @Test
    fun `the step comes from the existing BoxPlotGeom ladder`() {
        val largest = 190.0
        val axis = BagDistanceAxis.fromDistances(listOf(90.0, largest))!!
        val expected = BoxPlotGeom.niceStep(largest * BagDistanceAxis.HEADROOM, BagDistanceAxis.TARGET_TICKS)
        assertEquals(expected, axis.stepM, 1e-9)
    }

    @Test
    fun `ticks run contiguously from zero to the axis max at the shared step`() {
        val axis = BagDistanceAxis.fromDistances(listOf(120.0, 237.0))!!
        val ticks = axis.ticksM
        assertEquals(0.0, ticks.first(), 1e-9)
        assertEquals(axis.maxM, ticks.last(), 1e-9)
        assertEquals(ticks.size - 1, Math.round(axis.maxM / axis.stepM).toInt())
        ticks.zipWithNext { a, b -> assertEquals(axis.stepM, b - a, 1e-6) }
    }

    @Test
    fun `a single short shot still produces a usable axis`() {
        val axis = BagDistanceAxis.fromDistances(listOf(5.0))!!
        assertTrue(axis.maxM >= 1.06 * 5.0 - 1e-9)
        assertEquals(0.0, axis.maxM % axis.stepM, 1e-9)
        assertTrue(axis.stepM > 0.0)
    }
}
