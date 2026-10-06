package com.hpsmiles.golfsim.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the battery readout mapping: clamp to 0..100 (byte semantics are
 * "believed percent", formally unverified) and low strictly below 20.
 */
class BatteryDisplayTest {

    @Test
    fun normalPercentPassesThrough() {
        assertEquals(BatteryDisplay(percent = 85, low = false), batteryDisplay(85))
    }

    @Test
    fun above100ClampsTo100() {
        assertEquals(BatteryDisplay(percent = 100, low = false), batteryDisplay(105))
    }

    @Test
    fun below0ClampsTo0AndIsLow() {
        assertEquals(BatteryDisplay(percent = 0, low = true), batteryDisplay(-3))
    }

    @Test
    fun lowBoundaryIsStrictlyBelow20() {
        assertTrue(batteryDisplay(19).low)
        assertFalse(batteryDisplay(20).low)
    }

    @Test
    fun zeroIsLowAndHundredIsNot() {
        assertTrue(batteryDisplay(0).low)
        assertFalse(batteryDisplay(100).low)
    }
}
