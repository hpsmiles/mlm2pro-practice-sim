package com.hpsmiles.golfsim.games

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaneSoundCuesTest {

    @Test
    fun `green rests ding - broke and green-missed-pane - others fail`() {
        assertTrue(PaneSoundCues.restsOnGreen(BreakOutcomeKind.BROKE))
        assertTrue(PaneSoundCues.restsOnGreen(BreakOutcomeKind.GREEN_MISSED_PANE))
        assertFalse(PaneSoundCues.restsOnGreen(BreakOutcomeKind.HIT_PANE_MISSED_GREEN))
        assertFalse(PaneSoundCues.restsOnGreen(BreakOutcomeKind.MISSED_BOTH))
    }

    @Test
    fun `glass fires once at the reveal fraction`() {
        assertFalse(PaneSoundCues.glassDue(0.4f, 0.5f, alreadyPlayed = false))
        assertTrue(PaneSoundCues.glassDue(0.5f, 0.5f, alreadyPlayed = false))
        assertTrue(PaneSoundCues.glassDue(0.9f, 0.5f, alreadyPlayed = false))
        assertFalse(PaneSoundCues.glassDue(0.9f, 0.5f, alreadyPlayed = true))
    }
}
