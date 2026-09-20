package com.hpsmiles.golfsim.core.connect

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CommandEncoderTest {

    private val key = ByteArray(32) { it.toByte() }
    private val deviceKey = "6D2E5213213204456F2C794810656D42"

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { String.format(Locale.ROOT, "%02X", it) }

    @Test
    fun authRequestCarriesHeaderAndPlainKey() {
        val cmd = CommandEncoder.authRequest(key)
        assertEquals(CommandTarget.AUTH_REQUEST, cmd.target)
        // Header: version 0x01 + three zero bytes + encryption-type 0x01, then the raw key.
        val expected = byteArrayOf(0x01, 0x00, 0x00, 0x00, 0x00, 0x01) + key
        assertArrayEquals(expected, cmd.plaintext)
    }

    @Test
    fun armPinsCiphertext() {
        val cmd = CommandEncoder.arm(key)
        assertEquals(CommandTarget.COMMAND, cmd.target)
        assertEquals("4D40E953E85F1672CAC463A695E8C9D8", hex(cmd.plaintext))
    }

    @Test
    fun disarmPinsCiphertext() {
        val cmd = CommandEncoder.disarm(key)
        assertEquals(CommandTarget.COMMAND, cmd.target)
        assertEquals("D5BC053B9452BA6D4406F8659EDFC066", hex(cmd.plaintext))
    }

    @Test
    fun heartbeatIsPlaintextSingleByte() {
        val cmd = CommandEncoder.heartbeat(key)
        assertEquals(CommandTarget.HEARTBEAT, cmd.target)
        // Reference (BluetoothBase.SendHeartbeatSignal): byte[] heartbeatData = [0x01];
        // written UNENCRYPTED to the heartbeat characteristic every 2 s.
        // Bench attempt 6: the 16-byte AES ciphertext we used to send here was
        // not recognized by the device as a keep-alive -> link dropped ~15 s
        // after auth.
        assertArrayEquals(byteArrayOf(0x01), cmd.plaintext)
    }

    @Test
    fun configPinsCiphertextAndLayout() {
        val cfg = EnvironmentConfig(
            ballType = 0x02,
            indoor = false,
            pressureRaw = 0x7DC8,
            tempCentiC = 1500,
        )
        // Deviation (mechanical): 0xA63C5A44 > Int.MAX_VALUE, so the hex
        // literal is a Long — same .toInt() narrowing the plan itself uses
        // for onToken; bit pattern and pinned ciphertext unchanged.
        val cmd = CommandEncoder.config(cfg, token = 0xA63C5A44.toInt(), key = key)
        assertEquals(CommandTarget.CONFIGURE, cmd.target)
        assertEquals("9C34D51DEE811E6383ABBA5742BAC222", hex(cmd.plaintext))
        // Decrypt back and verify the 14-byte layout field by field.
        val p = com.hpsmiles.golfsim.core.ble.Mlm2proCrypto.decrypt(cmd.plaintext, key)
        assertEquals(14, p.size)
        assertEquals(0x01, p[0].toInt() and 0xFF)
        assertEquals(0x02, p[1].toInt() and 0xFF)
        assertEquals(0x00, p[2].toInt() and 0xFF)
        assertEquals(0x00, p[3].toInt() and 0xFF)
        // Pressure wire bytes are 7D C8: the reference packs LE16(51325);
        // our BE16(0x7DC8) default produces the identical bytes (pin-verified).
        assertEquals(0x7DC8, ((p[4].toInt() and 0xFF) shl 8) or (p[5].toInt() and 0xFF))
        // temp is LE16 in the pin-verified layout (plan's LE read was right here):
        assertEquals(1500, (p[6].toInt() and 0xFF) or ((p[7].toInt() and 0xFF) shl 8))
        // Token is LITTLE-ENDIAN per the reference (DeviceManager.GetInitialParameters
        // -> LongToUintToByteArray -> BitConverter.GetBytes). The M4a pin's captured
        // plaintext 01 02 00 00 7DC8 DC05 A63C5A44 0000 reads correctly as
        // LE32(0x445A3CA6) for the token; the old BE read reversed the bytes.
        assertEquals(0xA63C5A44.toInt(), (p[8].toInt() and 0xFF) or
            ((p[9].toInt() and 0xFF) shl 8) or ((p[10].toInt() and 0xFF) shl 16) or
            ((p[11].toInt() and 0xFF) shl 24))
        assertEquals(0, p[12].toInt())
        assertEquals(0, p[13].toInt())
    }

    @Test
    fun authRequestRejectsWrongKeyLength() {
        try {
            CommandEncoder.authRequest(ByteArray(16))
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
