package com.hpsmiles.golfsim.core.ble

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-CBC + PKCS7 crypto used by the MLM2PRO BLE link.
 *
 * The device protocol wraps every GATT payload except the initial auth
 * request with this scheme: a fixed IV (reverse-engineered from the Rapsodo
 * app — see docs/superpowers/specs/2026-09-20-m1-ble-decoder-design.md §2)
 * and a 32-byte client-chosen session key sent in the clear in the auth
 * request. Decrypt handles device→client notifications; encrypt is for the
 * future M4 GATT client's command writes (arm/disarm/config).
 *
 * Pure JVM (javax.crypto), no Android dependencies. This is the device's
 * obfuscation layer, not a security boundary: the IV is public and the key
 * crosses the wire in plaintext by protocol design.
 *
 * Key-length errors are programmer errors → IllegalArgumentException, per
 * the spec's fail-fast policy. Corrupt ciphertext throws
 * GeneralSecurityException from [decrypt]; the orchestrator (Mlm2proDecoder)
 * converts that to a Malformed message.
 */
object Mlm2proCrypto {

    private val FIXED_IV = byteArrayOf(
        0x6D, 0x2E, 0x52, 0x13, 0x21, 0x32, 0x04, 0x45,
        0x6F, 0x2C, 0x79, 0x48, 0x10, 0x65, 0x6D, 0x42,
    )

    // PKCS5Padding in the JCA name = PKCS7 for a 16-byte AES block.
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"

    fun encrypt(plaintext: ByteArray, key: ByteArray): ByteArray =
        doCipher(Cipher.ENCRYPT_MODE, plaintext, key)

    fun decrypt(ciphertext: ByteArray, key: ByteArray): ByteArray =
        doCipher(Cipher.DECRYPT_MODE, ciphertext, key)

    private fun doCipher(mode: Int, input: ByteArray, key: ByteArray): ByteArray {
        require(key.size == 32) { "AES-256 session key must be 32 bytes, was ${key.size}" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(FIXED_IV))
        return cipher.doFinal(input)
    }
}
