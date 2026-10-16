package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BagTraceTest {

    private fun shot(
        ballSpeedMps: Double = 45.0,
        launchAngleDeg: Double = 20.0,
        launchDirDeg: Double = 0.0,
        spinAxisDeg: Double = 0.0,
        totalSpinRpm: Int = 6000,
        carryM: Double = 140.0,
        totalM: Double = 152.0,
        included: Boolean = true,
    ) = BagMappingShotEntity(
        sessionId = 1L,
        clubName = "7i",
        clubType = "IRON",
        timestampMs = 0L,
        clubHeadSpeedMps = 35.0,
        ballSpeedMps = ballSpeedMps,
        launchAngleDeg = launchAngleDeg,
        launchDirDeg = launchDirDeg,
        spinAxisDeg = spinAxisDeg,
        totalSpinRpm = totalSpinRpm,
        carryM = carryM,
        totalM = totalM,
        includeOverride = if (included) null else false,
    )

    @Test
    fun `launchFor builds conditions from one shot`() {
        val launch = BagTrace.launchFor(shot(ballSpeedMps = 44.0, totalSpinRpm = 5500))
        assertNotNull(launch)
        assertEquals(44.0, launch!!.ballSpeedMps, 1e-9)
        assertEquals(5500, launch.spinRpm)
    }

    @Test
    fun `medianLaunch takes the median of each metric over kept shots only`() {
        val shots = listOf(
            shot(ballSpeedMps = 40.0, totalSpinRpm = 5000),
            shot(ballSpeedMps = 45.0, totalSpinRpm = 6000),
            shot(ballSpeedMps = 50.0, totalSpinRpm = 7000),
            shot(ballSpeedMps = 99.0, totalSpinRpm = 9000, included = false),
        )
        val launch = BagTrace.medianLaunch(shots)
        assertNotNull(launch)
        assertEquals(45.0, launch!!.ballSpeedMps, 1e-9)
        assertEquals(6000, launch.spinRpm)
    }

    @Test
    fun `medianLaunch is null when no shots are kept`() {
        assertNull(BagTrace.medianLaunch(listOf(shot(included = false))))
        assertNull(BagTrace.medianLaunch(emptyList()))
    }

    @Test
    fun `medianLaunch is null when a median falls outside the engine's range`() {
        // launch angle 90 deg cannot build LaunchConditions (valid 0..85).
        assertNull(BagTrace.medianLaunch(listOf(shot(launchAngleDeg = 90.0))))
    }

    @Test
    fun `medianDistance follows the basis and ignores excluded shots`() {
        val shots = listOf(
            shot(carryM = 130.0, totalM = 141.0),
            shot(carryM = 140.0, totalM = 152.0),
            shot(carryM = 150.0, totalM = 163.0),
            shot(carryM = 300.0, totalM = 320.0, included = false),
        )
        assertEquals(140.0, BagTrace.medianDistance(shots, DistanceBasis.CARRY)!!, 1e-9)
        assertEquals(152.0, BagTrace.medianDistance(shots, DistanceBasis.TOTAL)!!, 1e-9)
    }

    @Test
    fun `medianDistance is null with no kept shots`() {
        assertNull(BagTrace.medianDistance(emptyList(), DistanceBasis.CARRY))
    }

    @Test
    fun `trace ends exactly on the authoritative median distance`() {
        val shots = listOf(
            shot(carryM = 130.0), shot(carryM = 140.0), shot(carryM = 150.0),
        )
        val trace = BagTrace.trace("7i", shots, DistanceBasis.CARRY)
        assertNotNull(trace)
        assertEquals(140.0, trace!!.distanceM, 1e-9)
        // Scale-to-fit: the drawn trace lands on the printed number, whatever
        // the simulation's own carry was.
        assertEquals(140.0, trace.points.last().downrangeM, 1e-6)
        assertTrue(trace.points.size > 1)
    }

    @Test
    fun `trace starts at the tee and its apex is positive`() {
        val trace = BagTrace.trace("7i", listOf(shot()), DistanceBasis.CARRY)!!
        assertEquals(0.0, trace.points.first().downrangeM, 1e-9)
        assertEquals(0.0, trace.points.first().heightM, 1e-9)
        assertTrue(trace.apexM > 0.0)
    }

    @Test
    fun `total basis runs further than carry basis`() {
        val shots = listOf(shot(carryM = 140.0, totalM = 152.0))
        val carry = BagTrace.trace("7i", shots, DistanceBasis.CARRY)!!
        val total = BagTrace.trace("7i", shots, DistanceBasis.TOTAL)!!
        assertTrue(total.distanceM > carry.distanceM)
        assertTrue(total.points.size > carry.points.size)
    }

    @Test
    fun `trace is null with no kept shots or unbuildable launch`() {
        assertNull(BagTrace.trace("7i", emptyList(), DistanceBasis.CARRY))
        assertNull(BagTrace.trace("7i", listOf(shot(included = false)), DistanceBasis.CARRY))
        assertNull(BagTrace.trace("7i", listOf(shot(launchAngleDeg = 90.0)), DistanceBasis.CARRY))
    }
}
