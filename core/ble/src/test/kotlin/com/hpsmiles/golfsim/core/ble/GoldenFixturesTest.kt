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
 *   type   = measurement | misread | malformed
 *   hex    = payload bytes (case-insensitive; empty string = empty payload)
 *   (measurement only) clubHeadSpeed, ballSpeed, launchDirection, launchAngle,
 *   spinAxis (Double); totalSpin, unknown1, unknown2 (Int)
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
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): Collection<Array<Any>> =
            goldenFiles().map { file ->
                arrayOf(file.name, file.inputStream().use { Properties().apply { load(it) } })
            }

        private fun goldenFiles(): List<File> =
            GoldenFixturesTest::class.java.classLoader.getResources("golden").toList()
                .map { File(it.toURI()) }
                .flatMap { dir -> dir.walkTopDown().filter { f -> f.isFile && f.name.endsWith(".properties") } }
                .sortedBy { it.name }
    }
}
