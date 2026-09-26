package com.hpsmiles.golfsim.range

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Post-M5 final-review finding (first post-merge fix): the three Room-backed
 * getter flows (summaries / hasOpenSession / clubs) must not be captured from
 * the first composition frame — they race the restore effect, and a corrupt-DB
 * recovery that swaps the database would leave captured flows bound to the
 * closed old DB. The gate defers the getter until restore has finished.
 */
class GatedFlowTest {

    @Test
    fun `live getter is not captured before ready`() = runBlocking {
        var captured = false
        val flow = gatedFlow(
            ready = false,
            live = { captured = true; flowOf(1) },
            fallback = flowOf(0),
        )
        // The whole point: accessing the Room getter before the restore
        // effect finished is the race — the producer must stay untouched.
        assertFalse(captured)
        assertEquals(0, flow.first())
        // Collecting the fallback must still not reach for the getter.
        assertFalse(captured)
    }

    @Test
    fun `live getter flow is used once ready`() = runBlocking {
        var captured = false
        val flow = gatedFlow(
            ready = true,
            live = { captured = true; flowOf(7) },
            fallback = flowOf(0),
        )
        assertEquals(7, flow.first())
        // The producer ran exactly because the gate was open.
        assert(captured)
    }
}
