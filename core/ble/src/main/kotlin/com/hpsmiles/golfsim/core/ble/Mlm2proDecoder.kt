package com.hpsmiles.golfsim.core.ble

import java.security.GeneralSecurityException

/**
 * Entry point for decoding MLM2PRO GATT notifications: decrypt, route by
 * characteristic, parse. This is what M4's GATT layer calls.
 *
 * Error policy (design spec §6): runtime data problems (corrupt ciphertext,
 * malformed payloads) become [Mlm2proMessage.Malformed] — the decoder must
 * never crash a live practice session. Programmer errors (wrong key length)
 * throw IllegalArgumentException.
 */
object Mlm2proDecoder {

    fun decode(
        characteristic: Characteristic,
        notification: ByteArray,
        key: ByteArray,
    ): Mlm2proMessage = try {
        val plaintext = Mlm2proCrypto.decrypt(notification, key)
        when (characteristic) {
            Characteristic.MEASUREMENT ->
                Mlm2proMessage.Measurement(MeasurementParser.parse(plaintext))
            Characteristic.EVENTS ->
                Mlm2proMessage.Event(
                    // Defensive: a decrypted EVENTS payload is never empty
                    // (PKCS7 always yields >= 1 byte), but EventParser's
                    // contract allows null — treat that as malformed data.
                    EventParser.parse(plaintext)
                        ?: return Mlm2proMessage.Malformed("empty EVENTS payload")
                )
        }
    } catch (e: GeneralSecurityException) {
        Mlm2proMessage.Malformed("decrypt failed: ${e.message}")
    }

    /**
     * UUID-string overload — the form Android's BLE stack naturally
     * supplies. Unknown UUIDs yield [Mlm2proMessage.Unrecognized].
     */
    fun decode(
        characteristicUuid: String,
        notification: ByteArray,
        key: ByteArray,
    ): Mlm2proMessage {
        val characteristic = Characteristic.fromUuid(characteristicUuid)
            ?: return Mlm2proMessage.Unrecognized(characteristicUuid)
        return decode(characteristic, notification, key)
    }
}
