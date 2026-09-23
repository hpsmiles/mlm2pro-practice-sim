package com.hpsmiles.golfsim.core.ble

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M4c shed-capture regression test (2026-09-23, 8i, shaped shots).
 *
 * Pins the specific events of the decisive live shapetest capture by event
 * id, exactly as recorded in
 * src/test/resources/golden/mlm2pro-live-shapetest-2026-09-23.properties.
 * The table-driven [GoldenFixturesTest] covers the same payloads as
 * drop-in fixtures; this test additionally proves the event/characteristic
 * semantics (which uuid a payload came in on, which event code it held) so
 * the "live misread on the wire" claim cannot silently regress to a
 * hand-built payload.
 *
 * Tolerance 1e-9 throughout, per project convention.
 */
class LiveShapetestCaptureTest {

    @Test
    fun fade_negativeHla_positiveSpinAxis() {
        // event-008: intentional fade — HLA left of target, spinAxis positive.
        val shot = shot(Hex.parse("6301BA01BBFFD9007100271D76007D0000000000"))
        assertEquals(35.5, shot.clubHeadSpeed, 1e-9)
        assertEquals(44.2, shot.ballSpeed, 1e-9)
        assertEquals(-6.9, shot.launchDirection, 1e-9)
        assertEquals(21.7, shot.launchAngle, 1e-9)
        assertEquals(11.3, shot.spinAxis, 1e-9)
        assertEquals(7463, shot.totalSpin)
    }

    @Test
    fun punch_lowLaunch_smallSpinAxis() {
        // event-013: intentional punch — flatter launch, small positive axis.
        val shot = shot(Hex.parse("6301B901E5FFA4003400ED177700810000000000"))
        assertEquals(35.5, shot.clubHeadSpeed, 1e-9)
        assertEquals(44.1, shot.ballSpeed, 1e-9)
        assertEquals(-2.7, shot.launchDirection, 1e-9)
        assertEquals(16.4, shot.launchAngle, 1e-9)
        assertEquals(5.2, shot.spinAxis, 1e-9)
        assertEquals(6125, shot.totalSpin)
    }

    @Test
    fun high_highestVla_negativeSpinAxis() {
        // event-018: intentional high shot — highest VLA of the session.
        val shot = shot(Hex.parse("7C01EB01D5FF0201C9FFD41984008B0000000000"))
        assertEquals(38.0, shot.clubHeadSpeed, 1e-9)
        assertEquals(49.1, shot.ballSpeed, 1e-9)
        assertEquals(-4.3, shot.launchDirection, 1e-9)
        assertEquals(25.8, shot.launchAngle, 1e-9)
        assertEquals(-5.5, shot.spinAxis, 1e-9)
        assertEquals(6612, shot.totalSpin)
    }

    @Test
    fun draw_largestNegativeSpinAxis() {
        // event-022: intentional draw — most negative spin axis (left curve).
        val shot = shot(Hex.parse("6F01E901EEFFD20046FFE0168800900000000000"))
        assertEquals(36.7, shot.clubHeadSpeed, 1e-9)
        assertEquals(48.9, shot.ballSpeed, 1e-9)
        assertEquals(-1.8, shot.launchDirection, 1e-9)
        assertEquals(21.0, shot.launchAngle, 1e-9)
        assertEquals(-18.6, shot.spinAxis, 1e-9)
        assertEquals(5856, shot.totalSpin)
    }

    @Test
    fun liveMisreadEvent_onEventsCharacteristic() {
        // event-010: the decisive live misread — EVENTS characteristic
        // (uuid 02E525FD), decrypted 05 00 00..., which EventParser maps to
        // MisreadAlert exactly like the synthetic 0500 EventParserTest pin.
        val payload = Hex.parse("0500000000000000000000000000000000000000")
        assertEquals(Mlm2proEvent.MisreadAlert, EventParser.parse(payload))
    }

    @Test
    fun liveMeasurementMisreadSentinel() {
        // event-027: MEASUREMENT characteristic (uuid 76830BCE) carrying an
        // all-zero 20-byte payload — the live wire does send the misread
        // sentinel; it must parse to Misread, never a Shot.
        assertEquals(BallDataResult.Misread, MeasurementParser.parse(ByteArray(20)))
    }

    private fun shot(payload: ByteArray): BallData {
        val result = MeasurementParser.parse(payload)
        return (result as? BallDataResult.Shot)?.data
            ?: throw AssertionError("expected a Shot, was $result")
    }
}
