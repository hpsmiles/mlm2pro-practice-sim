package com.hpsmiles.golfsim.bag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BagTopDownGeomTest {

    private val clubs = listOf(
        ClubDots("LW", listOf(-2.0 to 76.5, 1.0 to 77.0, 0.0 to 76.0)),
        ClubDots("D", listOf(-8.0 to 237.0, 5.0 to 240.0, 0.0 to 238.0)),
    )

    @Test
    fun `no points yields no fit`() {
        assertNull(computeBagTopDownFit(1000f, 400f, listOf(ClubDots("LW", emptyList()))))
        assertNull(computeBagTopDownFit(1000f, 400f, emptyList()))
    }

    @Test
    fun `distance window snaps outward to 25 m`() {
        val fit = computeBagTopDownFit(1000f, 400f, clubs)!!
        assertEquals(75.0, fit.windowMinM, 1e-9)
        assertEquals(250.0, fit.windowMaxM, 1e-9)
    }

    @Test
    fun `a narrow session keeps the 50 m floor`() {
        val tight = listOf(ClubDots("PW", listOf(0.0 to 121.0, 1.0 to 122.0, -1.0 to 121.5)))
        val fit = computeBagTopDownFit(1000f, 400f, tight)!!
        assertTrue(fit.windowMaxM - fit.windowMinM >= TOP_DOWN_MIN_WINDOW_M - 1e-9)
        // Re-centred on the content (100..125 snapped window is too thin):
        // centre 112.5 ± 25 m, then the guard re-derives min from max.
        assertEquals(87.5, fit.windowMinM, 1e-9)
        assertEquals(137.5, fit.windowMaxM, 1e-9)
    }

    @Test
    fun `sx maps the window edges to the pads and is monotonic`() {
        val fit = computeBagTopDownFit(1000f, 400f, clubs)!!
        assertEquals(26f, fit.sx(fit.windowMinM), 1e-3f)
        assertEquals(1000f - 12f, fit.sx(fit.windowMaxM), 1e-3f)
        assertTrue(fit.sx(100.0) < fit.sx(200.0))
    }

    @Test
    fun `sy puts lateral zero at the mid line and positive side above it`() {
        val fit = computeBagTopDownFit(1000f, 400f, clubs)!!
        assertEquals(200f, fit.sy(0.0), 1e-3f)
        assertTrue(fit.sy(10.0) < fit.sy(0.0))
    }

    @Test
    fun `the widest lateral content fits inside the pane`() {
        val wide = listOf(ClubDots("D", listOf(-40.0 to 200.0, 40.0 to 210.0, 0.0 to 205.0)))
        val fit = computeBagTopDownFit(1000f, 400f, wide)!!
        assertTrue(fit.sy(-40.0) <= 400f)
        assertTrue(fit.sy(40.0) >= 0f)
    }
}
