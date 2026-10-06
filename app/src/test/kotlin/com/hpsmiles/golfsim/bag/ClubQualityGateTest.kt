package com.hpsmiles.golfsim.bag

import org.junit.Assert.assertEquals
import org.junit.Test

/** Golden cases for the M6 quality gate (spec §5). */
class ClubQualityGateTest {

    private fun kept(vararg carries: Double) = carries.toList()

    @Test
    fun `dormant below target - keeps collecting`() {
        assertEquals(
            ClubQualityGate.Verdict.ACCEPT,
            ClubQualityGate.evaluate(kept(140.0, 141.0, 139.0, 142.0), filteredCount = 0),
        )
    }

    @Test
    fun `clean club at target - accept`() {
        assertEquals(
            ClubQualityGate.Verdict.ACCEPT,
            ClubQualityGate.evaluate(kept(140.0, 141.0, 139.0, 142.0, 140.5), filteredCount = 0),
        )
    }

    @Test
    fun `three or more filtered - ask for more`() {
        assertEquals(
            ClubQualityGate.Verdict.ASK_MORE,
            ClubQualityGate.evaluate(kept(140.0, 141.0, 139.0, 142.0, 140.5), filteredCount = 3),
        )
    }

    @Test
    fun `big window - ask for more`() {
        // median 141 → threshold 28.2; window 30 > 28.2
        assertEquals(
            ClubQualityGate.Verdict.ASK_MORE,
            ClubQualityGate.evaluate(kept(125.0, 140.0, 141.0, 142.0, 155.0), filteredCount = 0),
        )
    }

    @Test
    fun `window exactly at threshold - accept`() {
        // median 140, window 28 == 20% of median → not > → accept (spec: window > 20%)
        assertEquals(
            ClubQualityGate.Verdict.ACCEPT,
            ClubQualityGate.evaluate(kept(126.0, 139.0, 140.0, 141.0, 154.0), filteredCount = 0),
        )
    }

    @Test
    fun `hard cap - never prompts past fifteen kept`() {
        val fifteen = List(15) { if (it == 0) 100.0 else 140.0 + it } // terrible window
        assertEquals(
            ClubQualityGate.Verdict.ACCEPT,
            ClubQualityGate.evaluate(fifteen, filteredCount = 4),
        )
    }
}
