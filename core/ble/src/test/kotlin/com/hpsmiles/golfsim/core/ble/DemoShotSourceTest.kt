// core/ble/src/test/kotlin/com/hpsmiles/golfsim/core/ble/DemoShotSourceTest.kt
package com.hpsmiles.golfsim.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoShotSourceTest {

    private fun sourceA() = DemoShotSource(seed = 42L)
    private fun sourceB() = DemoShotSource(seed = 42L)
    private fun sourceC() = DemoShotSource(seed = 7L)

    @Test
    fun sameSeedProducesIdenticalSequence() {
        repeat(20) {
            assertEquals(sourceA().nextShot(), sourceB().nextShot())
        }
        // Fresh instances too: construction order must not matter.
        assertEquals(DemoShotSource(42L).nextShot(), DemoShotSource(42L).nextShot())
    }

    @Test
    fun differentSeedProducesDifferentShot() {
        var different = false
        repeat(10) {
            if (sourceA().nextShot() != sourceC().nextShot()) different = true
        }
        assertTrue(different)
    }

    @Test
    fun valuesStayWithinClubBoundsOverManyShots() {
        val source = DemoShotSource(seed = 1L)
        repeat(400) {
            val shot = source.nextShot()
            assertTrue("ball speed ${shot.ballSpeed}", shot.ballSpeed in 40.0..85.0)
            assertTrue("vla ${shot.launchAngle}", shot.launchAngle in 9.0..28.0)
            assertTrue("spin ${shot.totalSpin}", shot.totalSpin.toDouble() in 2000.0..10500.0)
            assertTrue("hla ${shot.launchDirection}", Math.abs(shot.launchDirection) <= 2.5)
            assertTrue("axis ${shot.spinAxis}", Math.abs(shot.spinAxis) <= 7.0)
            assertTrue("smash", shot.clubHeadSpeed > 0.0 && shot.clubHeadSpeed < shot.ballSpeed)
            assertEquals(0, shot.unknown1)
            assertEquals(0, shot.unknown2)
        }
    }

    @Test
    fun clubsCycleInOrder() {
        val source = DemoShotSource(seed = 99L)
        val firstFour = List(4) { source.nextShot().ballSpeed }
        // Centers: Driver 75, 5-iron 60, 7-iron 55, PW 46 — each within ±9%.
        assertTrue(firstFour[0] in 68.3..81.8) // Driver ±9%
        assertTrue(firstFour[1] in 54.6..65.4)  // 5-iron ±9%
        assertTrue(firstFour[2] in 50.1..60.0)  // 7-iron ±9%
        assertTrue(firstFour[3] in 41.9..50.1) // PW ±9%
        val fifth = source.nextShot().ballSpeed
        assertTrue(fifth in 68.3..81.8)        // cycles back to Driver
    }

    @Test
    fun isAShotSource() {
        val source: ShotSource = DemoShotSource()
        assertTrue(source.nextShot().ballSpeed > 0.0)
    }
}
