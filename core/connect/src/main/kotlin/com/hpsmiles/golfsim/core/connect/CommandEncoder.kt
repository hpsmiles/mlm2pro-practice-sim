package com.hpsmiles.golfsim.core.connect

import com.hpsmiles.golfsim.core.ble.Mlm2proCrypto

/** GATT characteristic a command targets (UUIDs live in Mlm2proGattClient KDoc). */
enum class CommandTarget { AUTH_REQUEST, COMMAND, CONFIGURE, HEARTBEAT }

/**
 * A protocol write produced by [HandshakeSequencer]. `plaintext` is the
 * on-wire payload: ciphered by [CommandEncoder] where the protocol encrypts,
 * left raw where it does not (the auth request carries the key in the clear).
 */
data class WriteCommand(
    val target: CommandTarget,
    val plaintext: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is WriteCommand && target == other.target && plaintext.contentEquals(other.plaintext)

    override fun hashCode(): Int = 31 * target.hashCode() + plaintext.contentHashCode()
}

/**
 * Inputs to the CONFIGURE write. Field semantics per the reverse-engineered
 * protocol spec (`mlm2pro.md`): ball type byte, indoor/outdoor flag, barometric
 * pressure and temperature — PROVENANCE NOTE: pressure/temp encodings are
 * unverified against a live device and are re-checked in Phase B.
 */
data class EnvironmentConfig(
    val ballType: Int = 0x02,
    val indoor: Boolean = false,
    val pressureRaw: Int = 0x7DC8,
    val tempCentiC: Int = 1500,
)

/** Builds the protocol writes; all encryption goes through :core:ble Mlm2proCrypto. */
object CommandEncoder {

    /** Auth request: 6-byte header (version 1 LE + encryption type 1) + the raw 32-byte key. */
    fun authRequest(key: ByteArray): WriteCommand {
        require(key.size == 32) { "session key must be 32 bytes" }
        val payload = byteArrayOf(0x01, 0x00, 0x00, 0x00, 0x00, 0x01) + key
        return WriteCommand(CommandTarget.AUTH_REQUEST, payload)
    }

    fun arm(key: ByteArray): WriteCommand =
        WriteCommand(CommandTarget.COMMAND, Mlm2proCrypto.encrypt(ARM_PLAINTEXT, key))

    fun disarm(key: ByteArray): WriteCommand =
        WriteCommand(CommandTarget.COMMAND, Mlm2proCrypto.encrypt(DISARM_PLAINTEXT, key))

    fun heartbeat(key: ByteArray): WriteCommand =
        WriteCommand(CommandTarget.HEARTBEAT, Mlm2proCrypto.encrypt(byteArrayOf(0x01), key))

    /** CONFIGURE write: 14-byte plaintext, then encrypted. Token from the Rapsodo API. */
    fun config(cfg: EnvironmentConfig, token: Int, key: ByteArray): WriteCommand {
        val p = ByteArray(14)
        p[0] = 0x01
        p[1] = cfg.ballType.toByte()
        p[2] = if (cfg.indoor) 1 else 0
        p[3] = 0x00
        // Plan deviation (mechanical, pin-verified): the verified CONFIG
        // ciphertext decrypts to 01 02 00 00 7DC8 DC05 A63C5A44 0000 — i.e.
        // pressure BE16, temperature LE16, token BE32 (the plan's LE helpers
        // produced a different ciphertext and self-contradicted the pin).
        p[4] = ((cfg.pressureRaw shr 8) and 0xFF).toByte()
        p[5] = (cfg.pressureRaw and 0xFF).toByte()
        p[6] = (cfg.tempCentiC and 0xFF).toByte()
        p[7] = ((cfg.tempCentiC shr 8) and 0xFF).toByte()
        p[8] = ((token shr 24) and 0xFF).toByte()
        p[9] = ((token shr 16) and 0xFF).toByte()
        p[10] = ((token shr 8) and 0xFF).toByte()
        p[11] = (token and 0xFF).toByte()
        p[12] = 0x00
        p[13] = 0x00
        return WriteCommand(CommandTarget.CONFIGURE, Mlm2proCrypto.encrypt(p, key))
    }

    private val ARM_PLAINTEXT = byteArrayOf(0x01, 0x0D, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00)
    private val DISARM_PLAINTEXT = byteArrayOf(0x01, 0x0D, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
}
