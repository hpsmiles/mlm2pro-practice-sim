package com.hpsmiles.golfsim.core.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class HandshakeSequencerTest {

    private val key = ByteArray(32) { it.toByte() }
    private val cfg = EnvironmentConfig()

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { String.format(Locale.ROOT, "%02X", it) }

    private fun authedSequencer(): HandshakeSequencer {
        val s = HandshakeSequencer(key, cfg)
        s.onSubscriptionsComplete(nowMs = 0)
        // Deviation (mechanical, test-fixture only): the plan's resp array had
        // bytes [1] and [2] transposed — as written it parses to 0x00A63C5A,
        // contradicting the plan's own pinned assert 0x00A63C44. Corrected to
        // (02, 5A, 44, 3C, A6, 00) so bytes [2..5] LE = 0x00A63C44.
        val resp = byteArrayOf(0x02, 0x5A, 0x44, 0x3C, 0xA6.toByte(), 0x00)
        s.onWriteResponse(resp, nowMs = 100)
        return s
    }

    @Test
    fun subscriptionsCompleteSendsAuthAndMovesToAuthSent() {
        val s = HandshakeSequencer(key, cfg)
        val write = s.onSubscriptionsComplete(nowMs = 0)
        assertEquals(HandshakeState.AUTH_SENT, s.state)
        assertEquals(CommandTarget.AUTH_REQUEST, write.target)
        assertEquals(38, write.plaintext.size) // 6-byte header + 32-byte key
    }

    @Test
    fun writeResponseAcceptParsesUserId() {
        val s = authedSequencer()
        assertEquals(HandshakeState.TOKEN_WAIT, s.state)
        assertEquals(0x00A63C44, s.userId)
    }

    @Test
    fun writeResponseRejectFaults() {
        val s = HandshakeSequencer(key, cfg)
        s.onSubscriptionsComplete(nowMs = 0)
        s.onWriteResponse(byteArrayOf(0x03, 0, 0, 0, 0, 0), nowMs = 100)
        assertEquals(HandshakeState.FAULTED, s.state)
    }

    @Test
    fun tokenArrivalSendsFirstConfig() {
        val s = authedSequencer()
        val w = s.onToken(0xA63C5A44.toInt(), nowMs = 200)
        assertNotNull(w)
        assertEquals(CommandTarget.CONFIGURE, w!!.target)
        // Wire pin: the CONFIG-sample ciphertext from the header conventions.
        assertEquals("4A4552B700BFBFC3D19C87B2B9379766", hex(w.plaintext))
        assertEquals(HandshakeState.CONFIG_WRITE_1, s.state)
    }

    @Test
    fun tokenFailureFaults() {
        val s = authedSequencer()
        s.onTokenFailed(nowMs = 500)
        assertEquals(HandshakeState.FAULTED, s.state)
    }

    @Test
    fun secondConfigWaits200Ms() {
        val s = authedSequencer()
        s.onToken(1, nowMs = 200)
        assertTrue(s.poll(nowMs = 399).isEmpty())     // 199 ms after config #1
        val at400 = s.poll(nowMs = 400)               // exactly +200 ms
        assertEquals(1, at400.size)
        assertEquals(CommandTarget.CONFIGURE, at400[0].target)
        assertEquals(HandshakeState.READY, s.state)
    }

    @Test
    fun heartbeatsEvery2sOnceReady() {
        val s = authedSequencer()
        s.onToken(1, nowMs = 200)
        s.poll(nowMs = 400) // config #2 -> READY (heartbeat clock starts here)
        assertTrue(s.poll(nowMs = 2399).isEmpty())
        val beat = s.poll(nowMs = 2400)
        assertEquals(1, beat.size)
        assertEquals(CommandTarget.HEARTBEAT, beat[0].target)
    }

    @Test
    fun armDisarmRoundTripFromReady() {
        val s = authedSequencer()
        s.onToken(1, nowMs = 200)
        s.poll(nowMs = 400) // READY
        val arm = s.arm(nowMs = 1000)!!
        assertEquals(HandshakeState.ARMED, s.state)
        assertEquals("4D40E953E85F1672CAC463A695E8C9D8", hex(arm.plaintext))
        val disarm = s.disarm(nowMs = 1200)!!
        assertEquals(HandshakeState.DISARMED, s.state)
        assertEquals("D5BC053B9452BA6D4406F8659EDFC066", hex(disarm.plaintext))
        // Arm again from DISARMED is legal.
        assertNotNull(s.arm(nowMs = 1300))
    }

    @Test
    fun armFromIdleIsRefused() {
        val s = HandshakeSequencer(key, cfg)
        assertNull(s.arm(nowMs = 0))
        assertEquals(HandshakeState.IDLE, s.state)
    }

    @Test
    fun resubscribePredicateUses20Seconds() {
        val s = HandshakeSequencer(key, cfg)
        assertFalse(s.isResubscribeDue(nowMs = 999_999)) // no notification yet
        s.onNotification(nowMs = 0)
        assertFalse(s.isResubscribeDue(nowMs = 19_999))
        assertTrue(s.isResubscribeDue(nowMs = 20_000))
    }
}
