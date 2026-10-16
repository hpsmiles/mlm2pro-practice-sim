package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.range.RangeScene
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
    fun `true scale - one px per metre on both axes`() {
        // 2026-10-16 review: the vertical scale must equal the horizontal one,
        // otherwise rings read as tall, narrow ellipses that contradict the
        // tick spacing. (Previously (heightPx/2)/lateralHalf — an independent
        // per-axis fit.)
        val fit = computeBagTopDownFit(1000f, 400f, clubs)!!
        assertEquals(fit.pxPerMM, fit.pxPerMSide, 1e-6f)
        // Lateral zero stays on the mid line and metres map symmetrically.
        assertEquals(200f, fit.sy(0.0), 1e-3f)
        assertEquals(200f + fit.pxPerMSide * 5f, fit.sy(-5.0), 1e-3f)
    }

    @Test
    fun `realistic lateral spread fits inside the pane at true scale`() {
        // At parity the vertical reach is (heightPx/2)/pxPerMM metres; on a
        // 50 m minimum window at 1000 px that is ~±10 m, so ±8 m must fit.
        val spread = listOf(
            ClubDots("D", listOf(-8.0 to 200.0, 8.0 to 210.0, 0.0 to 205.0)),
        )
        val fit = computeBagTopDownFit(1000f, 400f, spread)!!
        assertTrue(fit.sy(-8.0) <= 400f)
        assertTrue(fit.sy(8.0) >= 0f)
    }

    @Test
    fun `a short pane grows the window until the true-scale reach covers the spread`() {
        // 2026-10-16 round 2 (F2b): panel open = short pane. At strict parity
        // a ±40 m spread on a 300 px pane would overflow; the span must grow
        // (zoom out, re-snapped to the 25 m grid) until
        //   span * heightPx / (2 * plotWidth) >= maxAbsSide + margin
        // required = 42 * 2 * 962 / 300 = 269.4 → grown to 275 m.
        val wide = listOf(
            ClubDots("D", listOf(-40.0 to 200.0, 40.0 to 210.0, 0.0 to 205.0)),
        )
        val fit = computeBagTopDownFit(1000f, 300f, wide)!!
        assertEquals(275.0, fit.windowMaxM - fit.windowMinM, 1e-9)
        // Parity preserved…
        assertEquals(fit.pxPerMM, fit.pxPerMSide, 1e-6f)
        // …and the extreme dots stay inside the pane.
        assertTrue(fit.sy(-40.0) <= 300f)
        assertTrue(fit.sy(40.0) >= 0f)
    }

    @Test
    fun `a pane too short even for the full range falls back to a squashed vertical scale`() {
        // 2026-10-16 round 3: the 330 m range cap limits the span, so 1:1
        // cannot contain the data. required = 42 * 2 * 962 / 100 = 808 m —
        // the window grows to the full 0..330 m, and the fallback squashes
        // pxPerMSide to (heightPx / 2) / (maxAbsSide + margin): the one
        // deviation from strict 1:1. Containment always wins over parity.
        val wide = listOf(
            ClubDots("D", listOf(-40.0 to 200.0, 40.0 to 210.0, 0.0 to 205.0)),
        )
        val fit = computeBagTopDownFit(1000f, 100f, wide)!!
        assertEquals(0.0, fit.windowMinM, 1e-9)
        assertEquals(RangeScene.FAIRWAY_END_Y, fit.windowMaxM, 1e-9)
        assertTrue("fallback must squash the vertical scale", fit.pxPerMSide < fit.pxPerMM)
        assertEquals(100f / 2f / 42f, fit.pxPerMSide, 1e-4f)
        assertTrue(fit.sy(-40.0) <= 100f)
        assertTrue(fit.sy(40.0) >= 0f)
    }
}
