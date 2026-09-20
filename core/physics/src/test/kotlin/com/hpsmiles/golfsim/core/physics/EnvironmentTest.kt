package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class EnvironmentTest {

    @Test
    fun defaultAirDensityIsOnePointTwoFive() {
        assertEquals(1.225, Environment().airDensity, 0.001)
    }

    @Test
    fun hotLowPressureDayIsLessDense() {
        val env = Environment(temperatureC = 30.0, pressureHpa = 1000.0)
        assertEquals(1.1492, env.airDensity, 0.001)
    }

    @Test
    fun excessiveWindThrows() {
        try {
            Environment(windXmps = 41.0)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun negativePressureThrows() {
        try {
            Environment(pressureHpa = -1.0)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }
}
