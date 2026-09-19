package com.hpsmiles.golfsim.core.ble

/**
 * Parses the decrypted MLM2PRO EVENTS payload: byte 0 = event code,
 * byte 1 = sub-code. Known codes map to typed variants; anything else maps
 * to [Mlm2proEvent.Unknown]. Never throws; returns null only for an empty
 * payload (nothing to parse).
 */
object EventParser {

    fun parse(payload: ByteArray): Mlm2proEvent? {
        if (payload.isEmpty()) return null
        val code = payload[0].toInt() and 0xFF
        val subCode = if (payload.size > 1) payload[1].toInt() and 0xFF else null
        return when (code) {
            0x00 -> Mlm2proEvent.ShotDetected
            0x01 -> Mlm2proEvent.Processing
            0x02 -> Mlm2proEvent.Ready
            0x03 -> if (subCode != null) Mlm2proEvent.Battery(subCode) else Mlm2proEvent.Unknown(code, subCode)
            0x05 -> when (subCode) {
                0x00 -> Mlm2proEvent.MisreadAlert
                0x01 -> Mlm2proEvent.Disarmed
                else -> Mlm2proEvent.Unknown(code, subCode)
            }
            else -> Mlm2proEvent.Unknown(code, subCode)
        }
    }
}
