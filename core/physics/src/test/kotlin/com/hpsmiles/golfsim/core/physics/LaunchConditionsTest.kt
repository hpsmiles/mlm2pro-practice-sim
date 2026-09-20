package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LaunchConditionsTest {

    @Test
    fun validShotConstructs() {
        val lc = LaunchConditions(
            ballSpeedMps = 76.67,
            launchAngleDeg = 10.4,
            spinRpm = 2545,
            launchDirDeg = -3.0,
            spinAxisDeg = -5.6,
        )
        assertTrue(lc.spinRpm == 2545)
    }

    @Test
    fun ballSpeedTooLowThrows() {
        try {
            LaunchConditions(ballSpeedMps = 0.4, launchAngleDeg = 10.0, spinRpm = 2000)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun ballSpeedTooHighThrows() {
        try {
            LaunchConditions(ballSpeedMps = 100.1, launchAngleDeg = 10.0, spinRpm = 2000)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun negativeSpinThrows() {
        try {
            LaunchConditions(ballSpeedMps = 70.0, launchAngleDeg = 10.0, spinRpm = -1)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun spinAboveTwelveThousandThrows() {
        try {
            LaunchConditions(ballSpeedMps = 70.0, launchAngleDeg = 10.0, spinRpm = 12001)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun launchAngleBoundsThrow() {
        try {
            LaunchConditions(ballSpeedMps = 70.0, launchAngleDeg = -0.1, spinRpm = 2000)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
        try {
            LaunchConditions(ballSpeedMps = 70.0, launchAngleDeg = 85.5, spinRpm = 2000)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun directionAndAxisBoundsThrow() {
        try {
            LaunchConditions(ballSpeedMps = 70.0, launchAngleDeg = 10.0, spinRpm = 2000, launchDirDeg = 91.0)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
        try {
            LaunchConditions(ballSpeedMps = 70.0, launchAngleDeg = 10.0, spinRpm = 2000, spinAxisDeg = -90.5)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }
}
