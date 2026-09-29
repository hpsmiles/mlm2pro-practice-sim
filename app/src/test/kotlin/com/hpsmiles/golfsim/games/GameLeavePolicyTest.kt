package com.hpsmiles.golfsim.games

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLeavePolicyTest {

    @Test
    fun `NONE never prompts`() {
        assertFalse(GameLeavePolicy.confirmRequired(GameMode.NONE, shotsTaken = false, complete = false))
        assertFalse(GameLeavePolicy.confirmRequired(GameMode.NONE, shotsTaken = true, complete = false))
        assertFalse(GameLeavePolicy.confirmRequired(GameMode.NONE, shotsTaken = false, complete = true))
        assertFalse(GameLeavePolicy.confirmRequired(GameMode.NONE, shotsTaken = true, complete = true))
    }

    @Test
    fun `TARGET_PRACTICE with shots and incomplete prompts`() {
        assertTrue(GameLeavePolicy.confirmRequired(GameMode.TARGET_PRACTICE, shotsTaken = true, complete = false))
    }

    @Test
    fun `TARGET_PRACTICE with zero shots does not prompt`() {
        assertFalse(GameLeavePolicy.confirmRequired(GameMode.TARGET_PRACTICE, shotsTaken = false, complete = false))
        assertFalse(GameLeavePolicy.confirmRequired(GameMode.TARGET_PRACTICE, shotsTaken = false, complete = true))
    }

    @Test
    fun `TARGET_PRACTICE completed does not prompt`() {
        assertFalse(GameLeavePolicy.confirmRequired(GameMode.TARGET_PRACTICE, shotsTaken = true, complete = true))
    }

    @Test
    fun `BREAK_PANE with shots and incomplete prompts`() {
        assertTrue(GameLeavePolicy.confirmRequired(GameMode.BREAK_PANE, shotsTaken = true, complete = false))
    }
}
