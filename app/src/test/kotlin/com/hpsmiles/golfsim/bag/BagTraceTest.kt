package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
