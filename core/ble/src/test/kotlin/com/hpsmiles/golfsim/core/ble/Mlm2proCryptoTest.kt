package com.hpsmiles.golfsim.core.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Mlm2proCryptoTest {

    companion object {
        private val KEY = ByteArray(32) { it.toByte() } // test key = bytes 0x00..0x1F
        private val SAMPLE = Hex.parse("44004F00E2FF0A01C8FFFC0705000A0000000000")

        // Wire-format guard generated with an independent AES-256-CBC/PKCS7
        // implementation (key 0x00..0x1F, the device's fixed IV, the published
        // sample plaintext). Pinned on purpose: recomputing it from
        // Mlm2proCrypto itself would be circular and couldn't catch drift.
        private val PINNED_CIPHERTEXT = Hex.parse(
            "F3E3B5638AFD4B44F165257345EF9E58B18F8C84637EBEB62C3D2EA82981241C"
        )
    }

    @Test
    fun roundTripsTwentyByteMeasurement() {
        val ciphertext = Mlm2proCrypto.encrypt(SAMPLE, KEY)
        assertArrayEquals(SAMPLE, Mlm2proCrypto.decrypt(ciphertext, KEY))
    }

    @Test
    fun roundTripsEmptyPlaintext() {
        val ciphertext = Mlm2proCrypto.encrypt(ByteArray(0), KEY)
        assertArrayEquals(ByteArray(0), Mlm2proCrypto.decrypt(ciphertext, KEY))
    }

    @Test
    fun roundTripsSevenByteCommand() {
        val arm = Hex.parse("010D0001000000") // device arm command (M4 will write this)
        assertArrayEquals(arm, Mlm2proCrypto.decrypt(Mlm2proCrypto.encrypt(arm, KEY), KEY))
    }

    @Test
    fun encryptsToPaddedBlockSize() {
        assertEquals(32, Mlm2proCrypto.encrypt(SAMPLE, KEY).size)      // 20 B + PKCS7 → 2 blocks
        assertEquals(16, Mlm2proCrypto.encrypt(ByteArray(0), KEY).size) // 0 B → 1 block
    }

    @Test
    fun pinnedVectorDecryptsToPublishedSample() {
        assertArrayEquals(SAMPLE, Mlm2proCrypto.decrypt(PINNED_CIPHERTEXT, KEY))
    }

    @Test
    fun wrongKeyLengthThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            Mlm2proCrypto.encrypt(SAMPLE, ByteArray(16))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Mlm2proCrypto.decrypt(SAMPLE, ByteArray(64))
        }
    }
}
