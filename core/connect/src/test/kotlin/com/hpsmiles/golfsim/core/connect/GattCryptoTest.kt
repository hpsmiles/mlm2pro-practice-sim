package com.hpsmiles.golfsim.core.connect

import com.hpsmiles.golfsim.core.ble.Mlm2proCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.security.GeneralSecurityException

class GattCryptoTest {

    private val key = ByteArray(32) { it.toByte() }

    @Test
    fun maybeDecryptPassesPlaintextThroughWhenNot16ByteAligned() {
        // Auth responses and event payloads arrive raw; malformed ciphertext too.
        val raw = byteArrayOf(0x02, 0x44, 0x5A, 0x3C, 0xA6.toByte(), 0x00)
        val out = Mlm2proGattClient.maybeDecrypt(raw, key)
        assertSame(raw, out)
    }

    @Test
    fun maybeDecryptDecryptsValidCiphertext() {
        val secret = "44004F00E2FF0A01C8FFFC0705000A0000000000"
        val plaintext = ByteArray(20)
        for (i in 0 until 20) {
            plaintext[i] = ((Character.digit(secret[i * 2], 16) shl 4) or
                Character.digit(secret[i * 2 + 1], 16)).toByte()
        }
        val ciphertext = Mlm2proCrypto.encrypt(plaintext, key)
        val out = Mlm2proGattClient.maybeDecrypt(ciphertext, key)
        assertArrayEquals(plaintext, out)
    }

    @Test
    fun maybeDecryptFallsBackToRawOnBadPadding() {
        // A 32-byte blob that is NOT valid ciphertext must not crash the client;
        // it passes through raw for the decoder to reject as Malformed.
        val junk = ByteArray(32) { (it * 7 + 3).toByte() }
        val out = Mlm2proGattClient.maybeDecrypt(junk, key)
        assertSame(junk, out)
    }
}
