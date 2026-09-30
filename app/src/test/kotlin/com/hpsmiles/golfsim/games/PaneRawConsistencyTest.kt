package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.core.ble.BallDataResult
import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.ble.MeasurementParser
import com.hpsmiles.golfsim.core.physics.BallFlightEngine
import com.hpsmiles.golfsim.core.physics.Environment
import com.hpsmiles.golfsim.core.physics.GreenZoneSurfaceProvider
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.TrajectorySample
import com.hpsmiles.golfsim.range.FollowCam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the Break-the-Pane raw-height rendering fix.
 *
 * The pane cells are defined and scored in raw (unscaled) z-space. Drawing the
 * tracer/camera with apex scaling made the visible crossing row disagree with
 * the scored row. In BP we now pass [FollowCam.RAW_APEX_VMIN] everywhere so the
 * drawn flight is the raw flight and the marked cell equals the scored cell.
 */
class PaneRawConsistencyTest {

    /** 28 hardcoded capture shots (corpus from the retired PaneFadeAnalysisTempTest). */
    private val decryptedHex = listOf(
        "7A01E2010700C6002400551B84008C0000000000",
        "6F01DD01EBFFBF00EBFFC01885008D0000000000",
        "7201D0010300C700F1FF941A7F00870000000000",
        "6D01B60109009F003C00AC1B73007C0000000000",
        "7201E0010100B0003800301C83008B0000000000",
        "6801C101F7FFE600E0FFB61A7A00800000000000",
        "7001BA01E9FFD1009C00E32174007A0000000000",
        "8101D001CDFFD1007E00BC227B00800000000000",
        "8D01DE01D6FFFB00730013217E00830000000000",
        "8601E301CDFF09016300F81F7F00850000000000",
        "7501B201E0FF62004001C71C5F006C0000000000",
        "0000000000000000000000000000000000000000",
        "0000000000000000000000000000000000000000",
        "7A01D101EFFFDC009B0066247A007F0000000000",
        "8101E301E2FFDC006A00B81F8200870000000000",
        "6D01CB01DBFFD4008B0036207A00800000000000",
        "9401C301D3FFD800AD008A2376007B0000000000",
        "8801F201CFFFE7007000AA1F85008B0000000000",
        "9001F6010B00A1000700421B8B00930000000000",
        "8401F101F6FFB6003400661B8900910000000000",
        "8801EF01F3FFBE000C0087198A00920000000000",
        "8601F2011300D60029004E1C88008F0000000000",
        "8601EF010500E20028003F1C86008D0000000000",
        "8801F801F0FFC4002000461B8B00930000000000",
        "8601F5010600DC002F00BD1B8900900000000000",
        "8601EE01EDFFC200FDFFC71A8900910000000000",
        "8401FE01FBFFE000F0FF4D1B8B00920000000000",
        "8601E501FAFFD5000100F41A85008C0000000000",
    )

    private val shots: List<BallData?> = decryptedHex.mapIndexed { idx, hex ->
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        when (val r = MeasurementParser.parse(bytes)) {
            is BallDataResult.Shot -> r.data
            else -> {
                println("index $idx parse result: $r")
                null
            }
        }
    }

    @Test
    fun `raw apexVMin never scales samples and mark matches scored cell at 140m`() {
        assertRawConsistency(target = 140.0)
    }

    @Test
    fun `raw apexVMin never scales samples and mark matches scored cell at 125m`() {
        assertRawConsistency(target = 125.0)
    }

    private fun assertRawConsistency(target: Double) {
        val pane = PaneGeom(target)
        val radius = BreakPaneGreen.radiusAt140m(Difficulty.MEDIUM) * target / 140.0

        var checked = 0
        for (data in shots.filterNotNull()) {
            val result = simulate(data, target, radius)

            // Raw clamp must leave samples untouched.
            assertEquals(
                "scaledSamples with RAW_APEX_VMIN must equal raw samples",
                result.samples,
                FollowCam.scaledSamples(result, FollowCam.RAW_APEX_VMIN),
            )

            val scored = pane.firstCrossing(result.samples)
            if (scored == null) {
                // No forward pane crossing -> nothing to visually mark.
                assertNull(
                    "mark must be null when the raw flight does not cross the pane",
                    PaneIntersection.mark(result, pane.planeYM, FollowCam.RAW_APEX_VMIN),
                )
                continue
            }

            val mark = PaneIntersection.mark(result, pane.planeYM, FollowCam.RAW_APEX_VMIN)
            assertTrue("mark must exist when raw flight crosses the pane", mark != null)
            mark!!

            assertEquals("xM must match scored crossing", scored.xM, mark.xM, 1e-6)
            assertEquals("zM must match scored crossing", scored.zM, mark.zM, 1e-6)

            val drawnCell = pane.cellAt(mark.xM, mark.zM)
            assertEquals(
                "drawn cell must equal scored cell",
                scored.cell,
                drawnCell,
            )
            checked++
        }
        assertTrue("expected at least one checked crossing", checked > 0)
    }

    @Test
    fun `empty samples stay empty and produce no mark`() {
        val result = ShotResult(
            carryM = 0.0,
            rolloutM = 0.0,
            totalM = 0.0,
            sideM = 0.0,
            apexM = 0.0,
            flightTimeSec = 0.0,
            samples = emptyList(),
            restX = 0.0,
            restY = 0.0,
        )
        assertEquals(emptyList<TrajectorySample>(), FollowCam.scaledSamples(result, FollowCam.RAW_APEX_VMIN))
        assertNull(PaneIntersection.mark(result, 35.0, FollowCam.RAW_APEX_VMIN))
    }

    private fun simulate(data: BallData, target: Double, radius: Double): ShotResult {
        val launch = LaunchConditions(
            ballSpeedMps = data.ballSpeed,
            launchAngleDeg = data.launchAngle,
            spinRpm = data.totalSpin,
            launchDirDeg = data.launchDirection,
            spinAxisDeg = data.spinAxis,
        )
        return BallFlightEngine.simulate(
            launch,
            Environment(),
            GreenZoneSurfaceProvider(0.0, target, radius),
        )
    }
}
