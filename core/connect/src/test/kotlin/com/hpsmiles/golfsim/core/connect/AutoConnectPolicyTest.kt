package com.hpsmiles.golfsim.core.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins every rule of the auto-connect cycle (spec
 * 2026-09-30-auto-connect-boot-design.md §2): immediate first attempt,
 * 3 retries with 5/10/20 s backoff, arm-on-establish with budget reset,
 * stop-on-disconnect, manual restart.
 */
class AutoConnectPolicyTest {

    @Test
    fun startEmitsStartAttemptAndClearsStop() {
        val p = AutoConnectPolicy()
        p.userDisconnect()
        assertEquals(AutoConnectPolicy.Action.StartAttempt, p.start())
        assertFalse(p.stopped)
    }

    @Test
    fun immediateAttemptThenThreeBackoffRetriesThenGiveUp() {
        val p = AutoConnectPolicy()
        assertEquals(AutoConnectPolicy.Action.StartAttempt, p.start())
        assertEquals(AutoConnectPolicy.Action.WaitThenAttempt(1, 5_000L), p.attemptFailed())
        assertEquals(AutoConnectPolicy.Action.WaitThenAttempt(2, 10_000L), p.attemptFailed())
        assertEquals(AutoConnectPolicy.Action.WaitThenAttempt(3, 20_000L), p.attemptFailed())
        assertEquals(AutoConnectPolicy.Action.GiveUp, p.attemptFailed())
        // A given-up machine ignores further failure events.
        assertNull(p.attemptFailed())
    }

    @Test
    fun linkEstablishedArms() {
        val p = AutoConnectPolicy()
        p.start()
        assertEquals(AutoConnectPolicy.Action.Arm, p.linkEstablished())
    }

    @Test
    fun linkDropAfterEstablishStartsFreshRetryCycle() {
        val p = AutoConnectPolicy()
        p.start()
        p.attemptFailed() // burned one retry (2 left)
        p.linkEstablished() // resets the budget
        assertEquals(AutoConnectPolicy.Action.WaitThenAttempt(1, 5_000L), p.linkDropped())
    }

    @Test
    fun dropWithoutEstablishIsIgnored() {
        val p = AutoConnectPolicy()
        assertNull(p.linkDropped())
    }

    @Test
    fun attemptFailedWhileEstablishedIsIgnored() {
        val p = AutoConnectPolicy()
        p.start()
        p.linkEstablished()
        assertNull(p.attemptFailed())
    }

    @Test
    fun secondEstablishIsIgnored() {
        val p = AutoConnectPolicy()
        p.start()
        p.linkEstablished()
        assertNull(p.linkEstablished())
    }

    @Test
    fun userDisconnectStopsMachine() {
        val p = AutoConnectPolicy()
        p.start()
        p.linkEstablished()
        assertEquals(AutoConnectPolicy.Action.Stop, p.userDisconnect())
        assertTrue(p.stopped)
        assertNull(p.attemptFailed())
        assertNull(p.linkDropped())
        assertNull(p.linkEstablished())
    }

    @Test
    fun manualConnectAfterStopRestartsFreshCycle() {
        val p = AutoConnectPolicy()
        p.start()
        p.userDisconnect()
        assertEquals(AutoConnectPolicy.Action.StartAttempt, p.manualConnect())
        assertFalse(p.stopped)
        // Full retry budget again after the restart.
        assertEquals(AutoConnectPolicy.Action.WaitThenAttempt(1, 5_000L), p.attemptFailed())
        assertEquals(AutoConnectPolicy.Action.WaitThenAttempt(2, 10_000L), p.attemptFailed())
        assertEquals(AutoConnectPolicy.Action.WaitThenAttempt(3, 20_000L), p.attemptFailed())
        assertEquals(AutoConnectPolicy.Action.GiveUp, p.attemptFailed())
    }
}
