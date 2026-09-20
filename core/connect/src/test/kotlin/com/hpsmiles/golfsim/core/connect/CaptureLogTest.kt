package com.hpsmiles.golfsim.core.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureLogTest {

    @Test
    fun disabledByDefaultRecordsNothing() {
        val log = CaptureLog()
        log.record(uuid = "02E525FD", encrypted = byteArrayOf(1, 2), decrypted = null)
        assertTrue(log.entries().isEmpty())
    }

    @Test
    fun enabledRecordsEntry() {
        val log = CaptureLog()
        log.enabled = true
        log.record(uuid = "02E525FD", encrypted = byteArrayOf(1, 2), decrypted = byteArrayOf(3, 4))
        assertEquals(1, log.entries().size)
        val e = log.entries().first()
        assertEquals("02E525FD", e.uuid)
        assertEquals("0102", e.encryptedHex)
        assertEquals("0304", e.decryptedHex)
        assertTrue(e.timestampMs > 0L)
    }

    @Test
    fun exportPropertiesEmitsFirmwareAndEventEntries() {
        val log = CaptureLog()
        log.enabled = true
        log.record(uuid = "02E525FD", encrypted = byteArrayOf(1, 2), decrypted = byteArrayOf(3, 4))
        val props = log.exportProperties(firmware = "0.9.8.5")
        assertTrue(props.contains("firmware=0.9.8.5"))
        assertTrue(props.contains("type=event"))
        assertTrue(props.contains("event-001.uuid=02E525FD"))
        assertTrue(props.contains("event-001.encryptedHex=0102"))
        assertTrue(props.contains("event-001.decryptedHex=0304"))
    }

    @Test
    fun wrongKeyTestVectorIncludedInExport() {
        val log = CaptureLog()
        log.enabled = true
        log.record(uuid = "76830BCE", encrypted = byteArrayOf(1, 2), decrypted = null)
        val props = log.exportProperties(firmware = "0.9.8.5")
        // The wrong-key test (M1 oracle follow-up): export a known-junk decrypt attempt
        assertTrue(props.contains("wrongKey.decryptedHex="))
    }
}
