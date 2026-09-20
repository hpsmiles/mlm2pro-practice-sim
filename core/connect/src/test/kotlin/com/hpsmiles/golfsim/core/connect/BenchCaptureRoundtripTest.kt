package com.hpsmiles.golfsim.core.connect

import com.hpsmiles.golfsim.core.ble.EventParser
import com.hpsmiles.golfsim.core.ble.Mlm2proCrypto
import com.hpsmiles.golfsim.core.ble.Mlm2proEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.GeneralSecurityException
import java.util.Properties

/**
 * M4b golden bench capture (2026-09-20): the exact `capture.properties`
 * exported from the Settings DEBUG card during a live MLM2PRO session
 * (link stayed up on plaintext heartbeats; auto-armed; battery events
 * notified on the EVENTS characteristic on a ~30s cadence).
 *
 * This pins the full chain end-to-end: exported ciphertext -> decrypt
 * with the deterministic bench session key -> decrypted hex matches the
 * export -> EventParser yields the pinned battery percentages, plus the
 * wrong-key expectation (decrypt must fail, never return garbage).
 *
 * Adding a new capture is dropping another file under bench/ (a .properties
 * export from the DEBUG card) and, if it carries new event shapes, extending
 * the expectedEvent mapping.
 */
class BenchCaptureRoundtripTest {

    /** AppRoot.benchSessionKey(): deterministic 0x00..0x1F bench key. */
    private val benchKey = ByteArray(32) { it.toByte() }

    private fun fixture(): Properties =
        javaClass.classLoader.getResourceAsStream(
            "bench/m4b-bench-capture-2026-09-20.properties",
        ).use { Properties().apply { load(it) } }

    @Test
    fun headerCarriesFirmwareMarkerAndEventType() {
        val props = fixture()
        assertTrue("firmware header must be present", props.getProperty("firmware").isNotBlank())
        assertEquals("event", props.getProperty("type"))
    }

    @Test
    fun decryptsEveryEventToItsPinnedPlaintextOnTheBenchKey() {
        val props = fixture()
        val count = props.stringPropertyNames().count { it.matches(Regex("event-\\d{3}\\.encryptedHex")) }
        assertTrue("expected at least one captured event, got $count", count > 0)
        repeat(count) { i ->
            val n = (i + 1).toString().padStart(3, '0')
            val plain = Mlm2proCrypto.decrypt(props.hexBytes("event-$n.encryptedHex"), benchKey)
            assertEquals(
                "event-$n plaintext must match the exported decryptedHex",
                props.getProperty("event-$n.decryptedHex"),
                plain.toHex(),
            )
        }
    }

    @Test
    fun everyEventParsesAsThePinnedBatterySequence() {
        val props = fixture()
        // 85% -> 84% -> 83% -> 83% across the ~2-minute capture window.
        val expected = listOf(85, 84, 83, 83)
        expected.forEachIndexed { i, percent ->
            val n = (i + 1).toString().padStart(3, '0')
            assertEquals("02E525FD", props.getProperty("event-$n.uuid"))
            assertEquals(
                Mlm2proEvent.Battery(percent),
                EventParser.parse(props.hexBytes("event-$n.decryptedHex")),
            )
        }
    }

    @Test
    fun wrongKeyMustNeverDecryptTheSameCiphertext() {
        val props = fixture()
        val wrongKey = ByteArray(32) { (it + 1).toByte() } // same wrong key the EXPORT wrongKey vector uses
        try {
            val plain = Mlm2proCrypto.decrypt(props.hexBytes("event-001.encryptedHex"), wrongKey)
            fail("wrong key must not decrypt, got ${plain.toHex()}")
        } catch (expected: GeneralSecurityException) {
            // The exported wrongKey.decryptedHex is empty for exactly this reason.
        }
    }

    private fun Properties.hexBytes(key: String): ByteArray =
        getProperty(key).chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}
