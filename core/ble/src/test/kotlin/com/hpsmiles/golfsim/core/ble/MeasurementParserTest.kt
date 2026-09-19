package com.hpsmiles.golfsim.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementParserTest {

    @Test
    fun parsesPublishedSample() {
        val result = MeasurementParser.parse(
            Hex.parse("44004F00E2FF0A01C8FFFC0705000A0000000000")
        )
        val shot = result as BallDataResult.Shot
        assertEquals(6.8, shot.data.clubHeadSpeed, 1e-9)
        assertEquals(7.9, shot.data.ballSpeed, 1e-9)
        assertEquals(-3.0, shot.data.launchDirection, 1e-9)
        assertEquals(26.6, shot.data.launchAngle, 1e-9)
        assertEquals(-5.6, shot.data.spinAxis, 1e-9)
        assertEquals(2044, shot.data.totalSpin)
        assertEquals(5, shot.data.unknown1)
        assertEquals(10, shot.data.unknown2)
    }

    @Test
    fun readsSignedInt16sAsNegative() {
        val result = MeasurementParser.parse(measurement(hla = -1234, spinAxis = -5678))
        val shot = result as BallDataResult.Shot
        assertEquals(-123.4, shot.data.launchDirection, 1e-9)
        assertEquals(-567.8, shot.data.spinAxis, 1e-9)
    }

    @Test
    fun readsBoundaryRawValues() {
        checkBoundary(32767, 3276.7)   // Int16 max
        checkBoundary(-32768, -3276.8) // Int16 min
        checkBoundary(1, 0.1)          // smallest step; note: a raw of 0 across
                                        // ALL fields is the misread sentinel
    }

    private fun checkBoundary(raw: Int, expectedDegrees: Double) {
        val result = MeasurementParser.parse(measurement(hla = raw, spinAxis = raw))
        val shot = result as BallDataResult.Shot
        assertEquals(expectedDegrees, shot.data.launchDirection, 1e-9)
        assertEquals(expectedDegrees, shot.data.spinAxis, 1e-9)
    }

    @Test
    fun allZeroPayloadIsMisread() {
        assertEquals(BallDataResult.Misread, MeasurementParser.parse(ByteArray(20)))
    }

    @Test
    fun wrongLengthsAreMalformed() {
        assertTrue(MeasurementParser.parse(ByteArray(0)) is BallDataResult.Malformed)
        assertTrue(MeasurementParser.parse(ByteArray(19)) is BallDataResult.Malformed)
        assertTrue(MeasurementParser.parse(ByteArray(21)) is BallDataResult.Malformed)
    }

    /** Builds a synthetic 20-byte payload from raw (pre-scale) Int16 values. */
    private fun measurement(
        chs: Int = 68,
        ball: Int = 79,
        hla: Int = -30,
        vla: Int = 266,
        spinAxis: Int = -56,
        spin: Int = 2044,
        unknown1: Int = 5,
        unknown2: Int = 10,
    ): ByteArray = ByteArray(20).also { b ->
        write16(b, 0, chs); write16(b, 2, ball); write16(b, 4, hla); write16(b, 6, vla)
        write16(b, 8, spinAxis); write16(b, 10, spin); write16(b, 12, unknown1); write16(b, 14, unknown2)
    }

    private fun write16(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
    }
}
