package com.hpsmiles.golfsim.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.Properties

/**
 * Table-driven golden-fixture regression test: every *.properties file under
 * src/test/resources/golden/ becomes one test case named after the file.
 * Adding an M4 shed capture is dropping a new .properties file here — no
 * test-code change.
 *
 * File schema:
 *   type   = measurement | misread | malformed | event-battery | event-misread
 *   hex    = payload bytes (case-insensitive; empty string = empty payload)
 *   (measurement only) clubHeadSpeed, ballSpeed, launchDirection, launchAngle,
 *   spinAxis (Double); totalSpin, unknown1, unknown2 (Int)
 *
 * A fixture file may carry free-text `#` comments before its keys — used to
 * record capture provenance (session, event id, observed flight shape). The
 * verbatim multi-event capture is `mlm2pro-live-shapetest-2026-09-23.properties`;
 * per-event measurement/misread assertions live in [LiveShapetestCaptureTest].
 *
 * NOTE: this harness exercises the DECRYPTED payload against MeasurementParser.
 * Full ciphertext-in → result-out coverage lives in Mlm2proDecoderTest; M4
 * capture files that bundle ciphertext + session key will extend the schema.
 */
@RunWith(Parameterized::class)
class GoldenFixturesTest(private val fixtureName: String, private val fixture: Properties) {

    @Test
    fun decodesAsExpected() {
        val payload = Hex.parse(fixture.getProperty("hex"))
        when (fixture.getProperty("type")) {
            "measurement" -> assertShot(payload)
            "misread" -> assertEquals(BallDataResult.Misread, MeasurementParser.parse(payload))
            // M4b live bench capture: battery events on the EVENTS
            // characteristic; first byte 0x03, second byte = percent.
            "event-battery" -> assertEquals(
                Mlm2proEvent.Battery(i("percent")),
                EventParser.parse(payload),
            )
            // M4c live shed capture (2026-09-23): EVENTS characteristic
            // delivering the 0x05 0x00 misread alert after a real duff.
            "event-misread" -> assertEquals(
                Mlm2proEvent.MisreadAlert,
                EventParser.parse(payload),
            )
            "malformed" -> assertTrue(
                "$fixtureName should be Malformed",
                MeasurementParser.parse(payload) is BallDataResult.Malformed,
            )
            else -> fail("unknown fixture type: ${fixture.getProperty("type")}")
        }
    }

    private fun assertShot(payload: ByteArray) {
        val result = MeasurementParser.parse(payload)
        val shot = result as? BallDataResult.Shot
            ?: throw AssertionError("$fixtureName should parse to a Shot, was $result")
        assertEquals(d("clubHeadSpeed"), shot.data.clubHeadSpeed, 1e-9)
        assertEquals(d("ballSpeed"), shot.data.ballSpeed, 1e-9)
        assertEquals(d("launchDirection"), shot.data.launchDirection, 1e-9)
        assertEquals(d("launchAngle"), shot.data.launchAngle, 1e-9)
        assertEquals(d("spinAxis"), shot.data.spinAxis, 1e-9)
        assertEquals(i("totalSpin"), shot.data.totalSpin)
        assertEquals(i("unknown1"), shot.data.unknown1)
        assertEquals(i("unknown2"), shot.data.unknown2)
    }

    private fun d(key: String) = fixture.getProperty(key).toDouble()
    private fun i(key: String) = fixture.getProperty(key).toInt()

    companion object {
        // The schema types the table harness knows how to assert. Raw
        // multi-event captures (e.g. mlm2pro-live-shapetest-2026-09-23) carry
        // a protocol-level `type=event` key that is NOT a harness schema type;
        // they are pinned by LiveShapetestCaptureTest, so the harness skips
        // anything outside this set instead of failing on unknown types.
        private val HARNESS_TYPES = setOf(
            "measurement", "misread", "malformed", "event-battery", "event-misread",
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): Collection<Array<Any>> =
            goldenFiles()
                .mapNotNull { file ->
                    val props = file.inputStream().use { Properties().apply { load(it) } }
                    if (props.getProperty("type") in HARNESS_TYPES) {
                        arrayOf(file.name, props)
                    } else {
                        null
                    }
                }

        private fun goldenFiles(): List<File> =
            GoldenFixturesTest::class.java.classLoader.getResources("golden").toList()
                .map { File(it.toURI()) }
                .flatMap { dir -> dir.walkTopDown().filter { f -> f.isFile && f.name.endsWith(".properties") } }
                .sortedBy { it.name }
    }
}
