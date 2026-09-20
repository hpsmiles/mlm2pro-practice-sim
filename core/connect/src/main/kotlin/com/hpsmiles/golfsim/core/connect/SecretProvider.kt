// core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/SecretProvider.kt
package com.hpsmiles.golfsim.core.connect

import com.hpsmiles.golfsim.core.ble.Mlm2proCrypto

/**
 * The Rapsodo Web API `Secret` header value. The encrypted blob is the
 * PUBLIC hex constant from Duwaynef/MLM2PRO-BT-APP (WebApiClient.cs,
 * `_secretEnc`); it decrypts at runtime with the connector's PREDETERMINED
 * AES-256-CBC key (Encryption.cs parameterless ctor) and the same fixed
 * device IV Mlm2proCrypto ships. The plaintext value
 * NEVER appears in source, tests, logs, or docs — by design decision
 * (M4b spec §2, Q1). HttpRapsodoTokenProvider consumes this header value.
 */
object SecretProvider {

    private const val SECRET_ENC_HEX =
        "19605BE9BD42E0B3AEB20003847376012404EC9D72BB5586391F01BE03F031163242C34CD55C2C3E77D10D9A43A677A6"

    // Duwaynef Encryption.cs parameterless-ctor PREDETERMINED key (the
    // community connector's fixed web key, also documented in the M1
    // protocol research) — NOT the per-session device key. The IV is the
    // same fixed device IV Mlm2proCrypto already ships.
    private val PRED_KEY = hexToBytes(
        "1A180126F99A3C3F95B9CD967EA0263D59C7448CFF15FA8337A579FA3179E915"
    )

    fun apiSecret(): String = String(Mlm2proCrypto.decrypt(hexToBytes(SECRET_ENC_HEX), PRED_KEY), Charsets.UTF_8)

    private fun hexToBytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
