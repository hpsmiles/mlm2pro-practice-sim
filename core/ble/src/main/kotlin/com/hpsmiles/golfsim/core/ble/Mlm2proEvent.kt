package com.hpsmiles.golfsim.core.ble

/**
 * Device lifecycle events from the MLM2PRO EVENTS characteristic.
 *
 * Codes: 0x00 shot happened, 0x01 processing shot, 0x02 ready for shot,
 * 0x03 battery (byte 1 = percent), 0x05 0x00 last shot misread,
 * 0x05 0x01 device disarmed. Everything else — including the unresolved
 * 0x04 — lands in [Unknown] with both bytes preserved, per the design's
 * "never throw, never lose data" event policy.
 */
sealed interface Mlm2proEvent {
    data object ShotDetected : Mlm2proEvent   // 0x00
    data object Processing : Mlm2proEvent     // 0x01
    data object Ready : Mlm2proEvent           // 0x02
    data class Battery(val percent: Int) : Mlm2proEvent  // 0x03
    data object MisreadAlert : Mlm2proEvent    // 0x05 0x00
    data object Disarmed : Mlm2proEvent        // 0x05 0x01
    data class Unknown(val code: Int, val subCode: Int?) : Mlm2proEvent
}
