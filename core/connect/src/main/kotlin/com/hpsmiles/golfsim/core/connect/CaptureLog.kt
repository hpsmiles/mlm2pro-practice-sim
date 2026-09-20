package com.hpsmiles.golfsim.core.connect

import com.hpsmiles.golfsim.core.ble.Mlm2proCrypto

/**
 * M4b: in-app notification capture. When [enabled] is set the GATT client feeds
 * every notification (encrypted bytes and, when successfully decrypted, the
 * plaintext) into [record]; [exportProperties] renders the accumulated entries
 * as a golden-fixture `.properties` file with a `firmware=` header, a
 * `type=event` branch and a `wrongKey` vector (M1 oracle follow-ups).
 * Entries are pure data holders and never touch Android APIs.
 */
class CaptureLog {

    data class CapturedNotification(
        val timestampMs: Long,
        val uuid: String,
        val encryptedHex: String,
        val decryptedHex: String?,
    )

    var enabled: Boolean = false

    private val entriesInternal = mutableListOf<CapturedNotification>()

    fun record(uuid: String, encrypted: ByteArray, decrypted: ByteArray?) {
        if (!enabled) return
        entriesInternal.add(
            CapturedNotification(
                timestampMs = System.currentTimeMillis(),
                uuid = uuid,
                encryptedHex = encrypted.toHex(),
                decryptedHex = decrypted?.toHex(),
            )
        )
    }

    fun entries(): List<CapturedNotification> = entriesInternal.toList()

    fun exportProperties(firmware: String): String = buildString {
        appendLine("firmware=$firmware")
        appendLine("type=event")
        entries().forEachIndexed { i, e ->
            val n = (i + 1).toString().padStart(3, '0')
            appendLine("event-$n.timestampMs=${e.timestampMs}")
            appendLine("event-$n.uuid=${e.uuid}")
            appendLine("event-$n.encryptedHex=${e.encryptedHex}")
            appendLine("event-$n.decryptedHex=${e.decryptedHex}")
        }
        // Wrong-key test vector (M1 oracle follow-up): decrypt the first entry
        // (if any) with a deliberately wrong key to pin the Malformed path.
        entries().firstOrNull()?.let { first ->
            appendLine("wrongKey.decryptedHex=" + runCatching {
                Mlm2proCrypto.decrypt(first.encryptedHex.hexToBytes(), ByteArray(32) { (it + 1).toByte() })
                    .toHex()
            }.getOrDefault(""))
        } ?: appendLine("wrongKey.decryptedHex=")
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
