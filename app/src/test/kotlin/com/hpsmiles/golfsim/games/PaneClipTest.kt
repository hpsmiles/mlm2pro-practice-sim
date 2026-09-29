package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.range.PovProjector
import com.hpsmiles.golfsim.range.RangeCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaneClipTest {

    private val cam = RangeCamera.STATIC // pitch 0 -> depth = y + 21.2

    private fun quad(y: Double) = listOf(
        PaneClip.Vertex(-2.8, y, 10.0),
        PaneClip.Vertex(2.8, y, 10.0),
        PaneClip.Vertex(2.8, y, 14.0),
        PaneClip.Vertex(-2.8, y, 14.0),
    )

    @Test
    fun `fully visible quad passes through unchanged`() {
        val out = PaneClip.clip(quad(100.0), cam)
        assertEquals(4, out.size)
    }

    @Test
    fun `partially clipped quad gains interpolated vertices`() {
        // Plane at y = -21.2 + 0.5 = -20.7 -> a quad spanning y -22..-20 straddles it.
        val corners = listOf(
            PaneClip.Vertex(-2.8, -22.0, 10.0),
            PaneClip.Vertex(2.8, -20.0, 10.0),
            PaneClip.Vertex(2.8, -20.0, 14.0),
            PaneClip.Vertex(-2.8, -20.0, 14.0),
        )
        val out = PaneClip.clip(corners, cam)
        assertEquals(5, out.size)
        // Interpolated vertices sit exactly on the near plane.
        assertTrue(out.all { PaneClip.depth(it, cam) >= PovProjector.GROUND_MIN_DEPTH_M - 1e-9 })
    }

    @Test
    fun `fully behind quad clips to empty`() {
        val out = PaneClip.clip(quad(-100.0), cam)
        assertTrue(out.isEmpty())
    }
}
