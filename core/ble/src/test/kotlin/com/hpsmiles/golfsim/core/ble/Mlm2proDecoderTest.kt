package com.hpsmiles.golfsim.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Mlm2proDecoderTest {

    companion object {
        private val KEY = ByteArray(32) { it.toByte() }
        private val SAMPLE = Hex.parse("44004F00E2FF0A01C8FFFC0705000A0000000000")
    }

    @Test
    fun decodesMeasurementEndToEnd() {
        val notification = Mlm2proCrypto.encrypt(SAMPLE, KEY)
        val message = Mlm2proDecoder.decode(Characteristic.MEASUREMENT, notification, KEY)
        val measurement = message as Mlm2proMessage.Measurement
        val shot = measurement.result as BallDataResult.Shot
        assertEquals(7.9, shot.data.ballSpeed, 1e-9)
        assertEquals(-5.6, shot.data.spinAxis, 1e-9)
        assertEquals(2044, shot.data.totalSpin)
    }

    @Test
    fun decodesMisreadEndToEnd() {
        val notification = Mlm2proCrypto.encrypt(ByteArray(20), KEY)
        val message = Mlm2proDecoder.decode(Characteristic.MEASUREMENT, notification, KEY)
        val measurement = message as Mlm2proMessage.Measurement
        assertEquals(BallDataResult.Misread, measurement.result)
    }

    @Test
    fun decodesEventsEndToEnd() {
        val notification = Mlm2proCrypto.encrypt(Hex.parse("00"), KEY)
        val message = Mlm2proDecoder.decode(Characteristic.EVENTS, notification, KEY)
        assertEquals(Mlm2proEvent.ShotDetected, (message as Mlm2proMessage.Event).event)
    }

    @Test
    fun corruptCiphertextIsMalformedNotThrown() {
        // 31 bytes: not a multiple of the 16-byte AES block — decrypt fails,
        // the decoder must convert that to Malformed, never throw.
        val message = Mlm2proDecoder.decode(Characteristic.MEASUREMENT, ByteArray(31), KEY)
        assertTrue(message is Mlm2proMessage.Malformed)
    }

    @Test
    fun wrongKeyLengthStillThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            Mlm2proDecoder.decode(Characteristic.MEASUREMENT, ByteArray(32), ByteArray(16))
        }
    }

    @Test
    fun uuidOverloadDecodesMeasurement() {
        val notification = Mlm2proCrypto.encrypt(SAMPLE, KEY)
        // Android's BLE stack hands over lowercase UUID strings.
        val message = Mlm2proDecoder.decode(
            "76830bce-b9a7-4f69-aeaa-fd5b9f6b0965", notification, KEY
        )
        assertTrue(message is Mlm2proMessage.Measurement)
    }

    @Test
    fun unknownUuidYieldsUnrecognized() {
        // WRITE_RESPONSE characteristic — known to exist, not decoded in M1.
        val notification = Mlm2proCrypto.encrypt(SAMPLE, KEY)
        val message = Mlm2proDecoder.decode(
            "CFBBCB0D-7121-4BC2-BF54-8284166D61F0", notification, KEY
        )
        val unrecognized = message as Mlm2proMessage.Unrecognized
        assertEquals("CFBBCB0D-7121-4BC2-BF54-8284166D61F0", unrecognized.characteristicUuid)
    }
}
