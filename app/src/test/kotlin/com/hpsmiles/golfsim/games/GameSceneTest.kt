package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.range.PovProjector
import com.hpsmiles.golfsim.range.RangeCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameSceneTest {

    @Test
    fun `screen constants match RangeScreen geometry`() {
        assertEquals(1100f, GameScene.focalPx(1000f), 0.001f)
        assertEquals(300f, GameScene.v0Px(1000f), 0.001f)
        assertEquals((0.08 * 1000 - 300) / 1100.0, GameScene.apexVMin(1000f, 1000f), 1e-9)
    }

    @Test
    fun `toScreen maps static tee to bottom-center`() {
        val p = PovProjector.project(RangeCamera.STATIC, 0.0, 0.0, 0.0) ?: error("tee should project")
        val sc = GameScene.toScreen(1000f, 1000f, p)
        assertEquals(500f, sc.x, 0.1f)
        val expectedY = GameScene.v0Px(1000f) +
            (PovProjector.CAM_HEIGHT_M / PovProjector.CAM_BACK_M * GameScene.focalPx(1000f)).toFloat()
        assertEquals(expectedY, sc.y, 0.1f)
    }

    @Test
    fun `project returns null behind camera`() {
        assertNull(GameScene.project(RangeCamera.STATIC, 0f, 0f, 0.0, -50.0, 0.0))
    }

    @Test
    fun `project returns null behind static camera plane`() {
        // A point behind the camera (negative y beyond CAM_BACK_M).
        assertNull(GameScene.project(RangeCamera.STATIC, 1000f, 1000f, 0.0, -50.0, 0.0))
    }

    @Test
    fun `toScreen is linear in projected coordinates`() {
        val p = PovProjector.ProjectedPoint(u = 0.1, v = -0.05, scale = 1.0)
        val sc = GameScene.toScreen(1000f, 1000f, p)
        assertEquals(500f + 110f, sc.x, 0.01f)
        assertEquals(300f - 55f, sc.y, 0.01f)
    }
}
