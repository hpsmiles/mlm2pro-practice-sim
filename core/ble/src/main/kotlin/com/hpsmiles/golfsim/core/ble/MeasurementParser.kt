package com.hpsmiles.golfsim.core.ble

/**
 * Parses the decrypted 20-byte MLM2PRO MEASUREMENT payload.
 *
 * Layout (little-endian): 0–1 club head speed, 2–3 ball speed (both Int16
 * raw/10 m/s); 4–5 HLA, 6–7 VLA, 8–9 spin axis (signed Int16 raw/10 °);
 * 10–11 total spin (UInt16 rpm); 12–13 / 14–15 unknown raw; 16–19 zeros.
 * An all-zero payload is the documented misread sentinel.
 */
object MeasurementParser {

    const val PAYLOAD_SIZE: Int = 20

    fun parse(payload: ByteArray): BallDataResult = when {
        payload.size != PAYLOAD_SIZE ->
            BallDataResult.Malformed("expected $PAYLOAD_SIZE-byte payload, was ${payload.size}")
        payload.all { it == 0.toByte() } ->
            BallDataResult.Misread
        else -> BallDataResult.Shot(
            BallData(
                clubHeadSpeed = int16(payload, 0) / 10.0,
                ballSpeed = int16(payload, 2) / 10.0,
                launchDirection = int16(payload, 4) / 10.0,
                launchAngle = int16(payload, 6) / 10.0,
                spinAxis = int16(payload, 8) / 10.0,
                totalSpin = uint16(payload, 10),
                unknown1 = uint16(payload, 12),
                unknown2 = uint16(payload, 14),
            )
        )
    }

    private fun int16(b: ByteArray, off: Int): Int {
        val raw = le16(b, off)
        return if (raw >= 0x8000) raw - 0x10000 else raw
    }

    private fun uint16(b: ByteArray, off: Int): Int = le16(b, off)

    private fun le16(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or (b[off + 1].toInt() and 0xFF shl 8)
}
