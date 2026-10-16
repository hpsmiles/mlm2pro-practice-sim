package com.hpsmiles.golfsim.bag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // --- niceTicks (metre scale above the matrix, 2026-10-16) ---------------

    @Test
    fun `degenerate spans - no ticks`() {
        assertEquals(emptyList<Double>(), BoxPlotGeom.niceTicks(0.0, 0.0))
        assertEquals(emptyList<Double>(), BoxPlotGeom.niceTicks(150.0, 150.0))
        assertEquals(emptyList<Double>(), BoxPlotGeom.niceTicks(200.0, 100.0))
    }

    @Test
    fun `typical bag span - default 10 targets gives 10 m ticks`() {
        // span ~145 m over 10 targets → raw 14.5 → norm 1.45 → step 10
        val ticks = BoxPlotGeom.niceTicks(95.0, 240.0)
        assertEquals(
            listOf(100.0, 110.0, 120.0, 130.0, 140.0, 150.0, 160.0, 170.0, 180.0, 190.0, 200.0, 210.0, 220.0, 230.0, 240.0),
            ticks,
        )
    }

    @Test
    fun `wedge-to-driver span - 20 m ticks so the wedge region gets labelled ticks`() {
        // span 200 m over 10 targets → raw 20 → norm 2.0 → step 20;
        // 60/80/100/120 land inside the 60–120 m wedge region (user: "finer
        // ticks, 25 m" — the 1/2/5 ladder's reachable finer outcome).
        val ticks = BoxPlotGeom.niceTicks(56.0, 256.0)
        assertEquals(
            listOf(60.0, 80.0, 100.0, 120.0, 140.0, 160.0, 180.0, 200.0, 220.0, 240.0),
            ticks,
        )
        // Explicit targetCount override stays honoured (pinned target).
        assertEquals(
            listOf(60.0, 80.0, 100.0, 120.0, 140.0, 160.0, 180.0, 200.0, 220.0, 240.0),
            BoxPlotGeom.niceTicks(56.0, 256.0, targetCount = 10),
        )
    }

    @Test
    fun `ticks stay inside the axis and increase monotonically`() {
        val ticks = BoxPlotGeom.niceTicks(63.7, 187.3)
        assertTrue(ticks.isNotEmpty())
        assertTrue(ticks.first() >= 63.7)
        assertTrue(ticks.last() <= 187.3 + 1e-9)
        assertEquals(ticks, ticks.sorted())
    }

    @Test
    fun `single-metre span - sub-metre ticks`() {
        // default 10 targets over a 1 m span → raw 0.1 → step 0.1
        val ticks = BoxPlotGeom.niceTicks(140.0, 141.0)
        assertTrue(ticks.isNotEmpty())
        assertEquals(0.1, ticks[1] - ticks[0], 1e-9)
    }

    // --- niceStep (the ONE ladder; shared with BagDistanceAxis) -------------

    @Test
    fun `niceStep follows the 1-2-5 ladder`() {
        assertEquals(10.0, BoxPlotGeom.niceStep(145.0), 1e-9)   // raw 14.5 → 10
        assertEquals(20.0, BoxPlotGeom.niceStep(200.0), 1e-9)   // raw 20 → 20
        assertEquals(50.0, BoxPlotGeom.niceStep(350.0), 1e-9)   // raw 35 → 50
        assertEquals(100.0, BoxPlotGeom.niceStep(700.0), 1e-9)  // raw 70 → 100
        assertEquals(0.1, BoxPlotGeom.niceStep(1.0), 1e-9)      // raw 0.1
    }

    @Test
    fun `niceStep is what niceTicks uses`() {
        // The step from the ladder must reproduce the ticks' spacing exactly.
        val min = 56.0
        val max = 256.0
        val step = BoxPlotGeom.niceStep(max - min)
        val ticks = BoxPlotGeom.niceTicks(min, max)
        assertTrue(ticks.size >= 2)
        assertEquals(step, ticks[1] - ticks[0], 1e-9)
    }

    @Test
    fun `niceStep degenerate input yields 1 m`() {
        assertEquals(1.0, BoxPlotGeom.niceStep(0.0), 1e-9)
        assertEquals(1.0, BoxPlotGeom.niceStep(-5.0), 1e-9)
        assertEquals(1.0, BoxPlotGeom.niceStep(Double.NaN), 1e-9)
    }
}
