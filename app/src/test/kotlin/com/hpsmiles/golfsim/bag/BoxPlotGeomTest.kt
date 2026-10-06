package com.hpsmiles.golfsim.bag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Axis mapping for the carry-matrix painter (spec §6). */
class BoxPlotGeomTest {

    @Test
    fun `empty values - no axis`() {
        assertNull(BoxPlotGeom.axis(emptyList(), leftPadPx = 60f, plotWidthPx = 300f))
    }

    @Test
    fun `endpoints map to plot edges - mid to center`() {
        val axis = BoxPlotGeom.axis(listOf(100.0, 200.0), leftPadPx = 60f, plotWidthPx = 300f)!!
        // span 100 → pad 8 each side → [92, 208]
        assertEquals(92.0, axis.minM, 1e-9)
        assertEquals(208.0, axis.maxM, 1e-9)
        assertEquals(60f, BoxPlotGeom.x(92.0, axis), 0.01f)
        assertEquals(360f, BoxPlotGeom.x(208.0, axis), 0.01f)
        assertEquals(210f, BoxPlotGeom.x(150.0, axis), 0.01f)
    }

    @Test
    fun `zero-width data - padded so the plot never degenerates`() {
        val axis = BoxPlotGeom.axis(listOf(140.0), leftPadPx = 0f, plotWidthPx = 300f)!!
        assertNotNull(axis)
        assertEquals(133.0, axis.minM, 1e-9) // max(1 m, 5%) pad → 5% of 140 = 7
        assertEquals(147.0, axis.maxM, 1e-9)
        assertEquals(150f, BoxPlotGeom.x(140.0, axis), 0.01f)
    }

    @Test
    fun `values outside the axis clamp to plot edges`() {
        val axis = BoxPlotGeom.axis(listOf(100.0, 200.0), leftPadPx = 60f, plotWidthPx = 300f)!!
        assertEquals(60f, BoxPlotGeom.x(0.0, axis), 0.01f)
        assertEquals(360f, BoxPlotGeom.x(999.0, axis), 0.01f)
    }
}
