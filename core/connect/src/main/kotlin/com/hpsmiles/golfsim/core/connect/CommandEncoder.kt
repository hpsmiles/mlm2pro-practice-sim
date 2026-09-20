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

    /**
     * Heartbeat keep-alive. Reference (BluetoothBase.SendHeartbeatSignal) writes
     * a single UNENCRYPTED 0x01 byte to the heartbeat characteristic every 2 s.
     * Bench attempt 6: an encrypted 16-byte payload here was not recognized by
     * the device and the link dropped ~15 s after auth.
     */
    fun heartbeat(key: ByteArray): WriteCommand =
        WriteCommand(CommandTarget.HEARTBEAT, byteArrayOf(0x01))

    /** CONFIGURE write: 14-byte plaintext, then encrypted. Token from the Rapsodo API. */
    fun config(cfg: EnvironmentConfig, token: Int, key: ByteArray): WriteCommand {
        val p = ByteArray(14)
        p[0] = 0x01
        p[1] = cfg.ballType.toByte()
        p[2] = if (cfg.indoor) 1 else 0
        p[3] = 0x00
        // Wire layout verified against the reference implementation
        // (DeviceManager.GetInitialParameters + ByteConversionUtils):
        // header {1,2,0,0} + pressure LE16 (wire bytes 7D C8 — our BE16 default
        // emits the identical bytes) + temp LE16 + token LE32 (BitConverter
        // semantics in the reference; the M4a pin misread it as BE32) + {0,0}.
        p[4] = ((cfg.pressureRaw shr 8) and 0xFF).toByte()
        p[5] = (cfg.pressureRaw and 0xFF).toByte()
        p[6] = (cfg.tempCentiC and 0xFF).toByte()
        p[7] = ((cfg.tempCentiC shr 8) and 0xFF).toByte()
        p[8] = (token and 0xFF).toByte()
        p[9] = ((token shr 8) and 0xFF).toByte()
        p[10] = ((token shr 16) and 0xFF).toByte()
        p[11] = ((token shr 24) and 0xFF).toByte()
        p[12] = 0x00
        p[13] = 0x00
        return WriteCommand(CommandTarget.CONFIGURE, Mlm2proCrypto.encrypt(p, key))
    }

    private val ARM_PLAINTEXT = byteArrayOf(0x01, 0x0D, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00)
    private val DISARM_PLAINTEXT = byteArrayOf(0x01, 0x0D, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
}
