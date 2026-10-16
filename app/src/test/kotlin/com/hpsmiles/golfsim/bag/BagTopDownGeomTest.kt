package com.hpsmiles.golfsim.bag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BagTopDownGeomTest {

    // Distances mirroring the live session's LW/D span; largest = 240 →
    // rawSpan 254.4 → ladder step 20 → axis max 260 (whole multiple of 20).
    private val axis = BagDistanceAxis.fromDistances(listOf(76.5, 77.0, 76.0, 237.0, 240.0, 238.0))!!
    private val maxAbsSide = 8.0

    private fun fit(heightPx: Float, widthPx: Float = 1000f) =
        computeBagTopDownFit(widthPx, heightPx, maxAbsSide, axis)

    @Test
    fun `window IS the shared axis - zero origin to the axis max`() {
        val f = fit(400f)
        assertEquals(0.0, f.windowMinM, 1e-9)
        assertEquals(axis.maxM, f.windowMaxM, 1e-9)
        assertTrue(axis.maxM >= 1.06 * 240.0 - 1e-9)
    }

    @Test
    fun `sx maps the window edges to the pads and is monotonic`() {
        val f = fit(400f)
        assertEquals(26f, f.sx(f.windowMinM), 1e-3f)
        assertEquals(1000f - 12f, f.sx(f.windowMaxM), 1e-3f)
        assertTrue(f.sx(100.0) < f.sx(200.0))
    }

    @Test
    fun `sy puts lateral zero at the mid line and positive side below it`() {
        // Sign convention (verified 2026-10-16): +side is RIGHT of the target
        // looking down-range, which draws DOWN the screen with the tee left.
        val f = fit(400f)
        assertEquals(200f, f.sy(0.0), 1e-3f)
        assertTrue(f.sy(10.0) > f.sy(0.0))
        assertTrue(f.sy(-10.0) < f.sy(0.0))
    }

    @Test
    fun `true scale - one px per metre on both axes`() {
        // 2026-10-16 review: the vertical scale must equal the horizontal one,
        // otherwise rings read as tall, narrow ellipses that contradict the
        // tick spacing. (Previously (heightPx/2)/lateralHalf — an independent
        // per-axis fit.)
        val f = fit(400f)
        assertEquals(f.pxPerMM, f.pxPerMSide, 1e-6f)
        // Lateral zero stays on the mid line and metres map symmetrically.
        assertEquals(200f, f.sy(0.0), 1e-3f)
        assertEquals(200f - f.pxPerMSide * 5f, f.sy(-5.0), 1e-3f)
    }

    @Test
    fun `realistic lateral spread fits inside the pane at true scale`() {
        // At parity the vertical reach is span * heightPx / (2 * plotWidth);
        // on the shared 260 m axis at 1000 px × 400 px that is ~50 m, so
        // ±8 m must fit without any fallback.
        val f = fit(400f)
        assertTrue(f.sy(-8.0) <= 400f)
        assertTrue(f.sy(8.0) >= 0f)
        assertEquals(f.pxPerMM, f.pxPerMSide, 1e-6f)
    }

    @Test
    fun `a pane too short for the shared span falls back to a squashed vertical scale`() {
        // 2026-10-16 round 3, recomputed against the SHARED span: on a 100 px
        // pane the true-scale reach is 260 * 100 / (2 * 962) ≈ 13.5 m — far
        // short of ±40 m + margin — so the fallback squashes pxPerMSide to
        // (heightPx / 2) / (maxAbsSide + margin): the one deviation from
        // strict 1:1. Containment always wins over parity.
        val wideSide = 40.0
        val f = computeBagTopDownFit(1000f, 100f, wideSide, axis)
        assertTrue("fallback must squash the vertical scale", f.pxPerMSide < f.pxPerMM)
        assertEquals(100f / 2f / (wideSide + TOP_DOWN_LATERAL_MARGIN_M).toFloat(), f.pxPerMSide, 1e-4f)
        assertTrue(f.sy(-40.0) <= 100f)
        assertTrue(f.sy(40.0) >= 0f)
    }

    @Test
    fun `minPaneHeightPx matches the shared-span arithmetic and drives the fit`() {
        // 2026-10-16 round 4 (finding D), shared-axis decision: the page-level
        // floor and the fit's squash threshold share this one function, now
        // over the shared span. Pinned: maxAbsSide 23.5 m, plotWidth 2761 px,
        // span 260 m → (23.5 + 2) * 2 * 2761 / 260 = 541.7 px.
        assertEquals(541.7f, minPaneHeightPx(23.5, 2761f, 260.0), 0.5f)
        // A pane exactly at the floor contains the data at strict parity —
        // the fallback must NOT trigger there.
        val f = computeBagTopDownFit(
            widthPx = 2761f + 26f + 12f,
            heightPx = minPaneHeightPx(23.5, 2761f, axis.maxM),
            maxAbsSideM = 23.5,
            axis = axis,
            leftPadPx = 26f,
            rightPadPx = 12f,
        )
        assertEquals(f.pxPerMM, f.pxPerMSide, 1e-4f)
        assertTrue(f.sy(-23.5f.toDouble()) <= minPaneHeightPx(23.5, 2761f, axis.maxM))
        assertTrue(f.sy(23.5f.toDouble()) >= 0f)
    }

    @Test
    fun `a zero-height pane never collapses every dot onto y zero`() {
        // 2026-10-16 round 4 (finding H): the height floor must apply to the
        // fallback and mid line too, not just requiredSpan.
        val f = fit(0f)
        assertTrue(f.pxPerMSide > 0f)
        assertTrue(f.midY > 0f)
    }
}
