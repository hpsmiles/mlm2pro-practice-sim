package com.hpsmiles.golfsim.core.ble

/**
 * The two MLM2PRO GATT notify characteristics that carry device-to-client
 * data decodable in M1. The remaining known characteristics (AUTH_REQUEST,
 * COMMAND, CONFIGURE are write-only; WRITE_RESPONSE, HEARTBEAT are M4 scope)
 * are deliberately absent: hand one to [Mlm2proDecoder.decode] via its UUID
 * overload and it yields [Mlm2proMessage.Unrecognized].
 */
enum class Characteristic(val uuid: String) {
    MEASUREMENT("76830BCE-B9A7-4F69-AEAA-FD5B9F6B0965"),
    EVENTS("02E525FD-7960-4EF0-BFB7-DE0F514518FF");

    companion object {
        fun fromUuid(uuid: String): Characteristic? =
            entries.firstOrNull { it.uuid.equals(uuid, ignoreCase = true) }
    }
}
