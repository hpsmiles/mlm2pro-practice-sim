package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BagResultsViewTest {

    private fun shot(carryM: Double, totalM: Double) = BagMappingShotEntity(
        sessionId = 1L,
        clubName = "7i",
        clubType = "IRON",
        timestampMs = 0L,
        clubHeadSpeedMps = 35.0,
        ballSpeedMps = 45.0,
        launchAngleDeg = 20.0,
        launchDirDeg = 0.0,
        spinAxisDeg = 0.0,
        totalSpinRpm = 6000,
        carryM = carryM,
        totalM = totalM,
    )

    @Test
    fun `distanceFor selects the persisted cache for the basis`() {
        val s = shot(carryM = 140.0, totalM = 152.0)
        assertEquals(140.0, s.distanceFor(DistanceBasis.CARRY), 1e-9)
        assertEquals(152.0, s.distanceFor(DistanceBasis.TOTAL), 1e-9)
    }

    @Test
    fun `view enum has side as the first entry`() {
        assertEquals(BagResultsView.SIDE, BagResultsView.entries.first())
    }
}
