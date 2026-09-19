package com.hpsmiles.golfsim.core.ble

/** Test-only hex string helpers. */
object Hex {
    fun parse(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    fun format(bytes: ByteArray): String =
        bytes.joinToString("") { "%02X".format(it) }
}
