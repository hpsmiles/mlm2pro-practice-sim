package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Test

class GreenZoneSurfaceProviderTest {

    private val provider = GreenZoneSurfaceProvider(centerX = 0.0, centerY = 140.0, radiusM = 6.0)

    @Test
    fun `inside oval is green`() {
        assertEquals(Surface.GREEN_NORMAL, provider.surfaceAt(0.0, 140.0))
        assertEquals(Surface.GREEN_NORMAL, provider.surfaceAt(3.0, 142.0))
    }

    @Test
    fun `oval boundary is green - inclusive`() {
        assertEquals(Surface.GREEN_NORMAL, provider.surfaceAt(0.0, 146.0))
    }

    @Test
    fun `outside oval is fairway`() {
        assertEquals(Surface.FAIRWAY_NORMAL, provider.surfaceAt(0.0, 150.0))
        assertEquals(Surface.FAIRWAY_NORMAL, provider.surfaceAt(10.0, 140.0))
    }
}
