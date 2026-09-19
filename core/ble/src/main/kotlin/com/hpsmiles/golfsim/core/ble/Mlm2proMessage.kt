package com.hpsmiles.golfsim.core.ble

/**
 * Result of decoding one GATT notification. Runtime data problems never
 * throw — they land here as sealed variants a live session can log and skip.
 */
sealed interface Mlm2proMessage {
    data class Measurement(val result: BallDataResult) : Mlm2proMessage
    data class Event(val event: Mlm2proEvent) : Mlm2proMessage
    data class Malformed(val reason: String) : Mlm2proMessage
    data class Unrecognized(val characteristicUuid: String) : Mlm2proMessage
}
