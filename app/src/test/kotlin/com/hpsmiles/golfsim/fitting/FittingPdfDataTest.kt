package com.hpsmiles.golfsim.fitting

import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * M7 PDF export (2026-10-08): pure row-order + formatting tests for
 * [FittingPdfData]. The builder must mirror FittingTable's summary/delta
 * semantics and FittingTopDownPane's world mapping inputs — these tests pin
 * the row layout the user specified (club1 summary → club1 shots → club2
 * summary → club2 shots → Δ row last) and the excluded/Δ conventions.
 */
class FittingPdfDataTest {

    private fun shot(
        id: Long,
        clubId: Long = 1,
        name: String = "A",
        excluded: Boolean = false,
        ts: Long = id,
        chs: Double = 33.0,
        ball: Double = 48.0,
        side: Double = 0.0,
        carry: Double = 140.0,
        total: Double = 150.0,
        dir: Double = -1.0,
    ) = FittingShotEntity(
        id = id, sessionId = 1, clubId = clubId, clubName = name, clubType = "DRIVER",
        clubWasTemp = false, timestampMs = ts, clubHeadSpeedMps = chs, ballSpeedMps = ball,
        launchAngleDeg = 12.0, launchDirDeg = dir, spinAxisDeg = -4.0, totalSpinRpm = 8000,
        carryM = carry, totalM = total, sideM = side, apexM = 27.0, flightTimeSec = 6.0,
        excluded = excluded,
    )

    @Test
    fun `row order - per club summary then shots, delta row last`() {
        val shots = listOf(
            shot(1, 1, "A", ts = 100),
            shot(2, 1, "A", ts = 200),
            shot(3, 2, "B", ts = 300),
            shot(4, 2, "B", ts = 400),
        )
        val pdf = FittingPdfData.build(shots)
        assertEquals(2, pdf.clubs.size)
        assertEquals("A", pdf.clubs[0].name)
        assertEquals(2, pdf.clubs[0].shots.size)
        assertEquals(listOf(1L, 2L), pdf.clubs[0].shots.map { it.shotId })
        assertEquals("B", pdf.clubs[1].name)
        assertEquals(listOf(3L, 4L), pdf.clubs[1].shots.map { it.shotId })
        assertEquals(1, pdf.deltaRows.size)
        assertEquals("Δ B−A", pdf.deltaRows[0].label)
    }

    @Test
    fun `excluded shots stay listed and flagged`() {
        val shots = listOf(
            shot(1, 1, "A", excluded = false, ts = 100),
            shot(2, 1, "A", excluded = true, ts = 200),
        )
        val pdf = FittingPdfData.build(shots)
        val block = pdf.clubs.single()
        assertEquals(2, block.shots.size)
        assertFalse(block.shots[0].isExcluded)
        assertTrue(block.shots[1].isExcluded)
        // n (kept count) reflects exclusion; shot record is full.
        assertEquals("1", block.summary[0])
        assertEquals(1, pdf.excludedCount)
        assertEquals(2, pdf.totalShots)
    }

    @Test
    fun `delta row - auto-pair two clubs, cells are dash where delta is null`() {
        // club2 has no kept shots (only an excluded one) → every delta is null.
        val shots = listOf(
            shot(1, 1, "A", carry = 140.0, total = 150.0, chs = 33.0, ball = 48.0, ts = 100),
            shot(2, 1, "A", carry = 150.0, total = 160.0, chs = 34.0, ball = 49.0, ts = 200),
            shot(3, 2, "B", excluded = true, carry = 90.0, total = 95.0, ts = 300),
        )
        val pdf = FittingPdfData.build(shots)
        assertEquals(1, pdf.deltaRows.size)
        val cells = pdf.deltaRows[0].cells
        assertEquals(12, cells.size)
        cells.forEach { assertEquals("-", it) }
    }

    @Test
    fun `delta row - signed values when both sides have data`() {
        val shots = listOf(
            shot(1, 1, "A", carry = 140.0, total = 150.0, chs = 33.0, ball = 48.0, ts = 100),
            shot(2, 2, "B", carry = 150.0, total = 160.0, chs = 35.0, ball = 51.0, ts = 200),
        )
        val pdf = FittingPdfData.build(shots)
        val cells = pdf.deltaRows[0].cells
        assertEquals(12, cells.size)
        assertEquals("-", cells[0]) // n column has no delta, same as the table
        assertEquals(
            String.format(Locale.US, "%+.1f", 2.0 * FittingFormats.MPH_PER_MS),
            cells[1], // CHS: 35.0 − 33.0 = 2.0 m/s, shown in MPH
        )
        assertEquals("+10 m", cells[4])  // CARRY
        assertEquals("+10 m", cells[5])  // TOTAL
        assertEquals("-", cells[11])     // AREA has no Δ row, same as the table
    }

    @Test
    fun `summary cells match the table formatting`() {
        val shots = listOf(
            shot(1, 1, "A", carry = 140.0, total = 150.0, chs = 33.0, ball = 48.0, side = -2.0, ts = 100),
            shot(2, 1, "A", carry = 150.0, total = 160.0, chs = 33.0, ball = 50.0, side = 4.0, ts = 200),
        )
        val pdf = FittingPdfData.build(shots)
        val block = pdf.clubs.single()
        val summary = block.summary
        assertEquals(12, summary.size)
        assertEquals("2", summary[0])                                    // n
        assertEquals(FittingFormats.mph(33.0), summary[1])               // CHS mean 33.0
        assertEquals(FittingFormats.mph(49.0), summary[2])               // BALL mean 49.0
        assertEquals(String.format(Locale.US, "%.2f", 49.0 / 33.0), summary[3]) // SMASH
        assertEquals(FittingFormats.avgSigma(145.0, 5.0), summary[4])    // CARRY
        assertEquals(FittingFormats.avgSigma(155.0, 5.0), summary[5])    // TOTAL
        assertEquals(FittingFormats.deg(12.0), summary[6])               // LAUNCH
        assertEquals(FittingFormats.degSigned(-1.0), summary[7])         // DIR
        assertEquals(FittingFormats.rpm(8000.0), summary[8])             // SPIN
        assertEquals(FittingFormats.degSigned(-4.0), summary[9])         // SPIN AXIS
        assertEquals("3 / 4", summary[10])                               // OFFLINE avg |−2|,|4| → 3 / worst 4
        // AREA: 2 kept shots → no enclosing ellipse → "-" and null area.
        assertEquals("-", summary[11])
        assertNull(block.areaM2)
    }

    @Test
    fun `summary - fully excluded club shows zero kept and dashes`() {
        val shots = listOf(shot(1, 1, "A", excluded = true))
        val summary = FittingPdfData.build(shots).clubs.single().summary
        assertEquals("0", summary[0])
        assertTrue(summary.drop(1).all { it == "-" })
    }

    @Test
    fun `area cell - club with three kept shots reports the ring area`() {
        // 3 kept shots → a buffered rotated MVEE exists; the AREA cell is the
        // ring's π·a·b in m², matching FittingTable's AREA Col formatting.
        val shots = listOf(
            shot(1, 1, "A", side = 0.0, total = 100.0, ts = 100),
            shot(2, 1, "A", side = 10.0, total = 110.0, ts = 200),
            shot(3, 1, "A", side = 5.0, total = 95.0, ts = 300),
        )
        val block = FittingPdfData.build(shots).clubs.single()
        assertNotNull(block.ellipse)
        val area = block.areaM2
        assertNotNull(area)
        assertEquals(String.format(Locale.US, "%.0f m²", area!!), block.summary[11])
        // AREA is the last of the 12 summary columns.
        assertEquals(12, block.summary.size)
    }

    @Test
    fun `area cell - under three kept shots renders dash`() {
        // 2 kept shots → no enclosing ellipse → the AREA cell is "-" like the table.
        val shots = listOf(
            shot(1, 1, "A", side = 0.0, total = 100.0, ts = 100),
            shot(2, 1, "A", side = 10.0, total = 110.0, ts = 200),
        )
        val block = FittingPdfData.build(shots).clubs.single()
        assertNull(block.ellipse)
        assertNull(block.areaM2)
        assertEquals("-", block.summary[11])
    }

    @Test
    fun `delta row - area cell has no content`() {
        // The app has no AREA Δ row (area deltas are quadratic noise); the Δ
        // row's AREA cell renders "-" exactly like FittingTable's AREA Col.
        val shots = listOf(
            shot(1, 1, "A", ts = 100),
            shot(2, 2, "B", ts = 200),
        )
        val cells = FittingPdfData.build(shots).deltaRows.single().cells
        assertEquals(12, cells.size)
        assertEquals("-", cells[11])
    }

    @Test
    fun `single club - no delta row`() {
        val pdf = FittingPdfData.build(listOf(shot(1, 1, "A"), shot(2, 1, "A")))
        assertEquals(1, pdf.clubs.size)
        assertTrue(pdf.deltaRows.isEmpty())
    }

    @Test
    fun `three clubs - a delta row per non-baseline club at the end`() {
        val shots = listOf(
            shot(1, 1, "A", ts = 100),
            shot(2, 1, "A", ts = 200),
            shot(3, 2, "B", ts = 300),
            shot(4, 3, "C", ts = 400),
        )
        val pdf = FittingPdfData.build(shots)
        assertEquals(3, pdf.clubs.size)
        assertEquals(2, pdf.deltaRows.size)
        assertEquals("Δ B−A", pdf.deltaRows[0].label)
        assertEquals("Δ C−A", pdf.deltaRows[1].label)
    }

    @Test
    fun `top-down model - no kept shots means hasData false and no ring`() {
        val pdf = FittingPdfData.build(listOf(shot(1, 1, "A", excluded = true)))
        assertFalse(pdf.topDown.hasData)
        assertNull(pdf.clubs.single().ellipse)
        assertEquals(emptyList<Pair<Double, Double>>(), pdf.clubs.single().keptPoints)
    }

    @Test
    fun `top-down model - kept points drive bounds and ring under three kept`() {
        val shots = listOf(
            shot(1, 1, "A", side = 0.0, total = 100.0, ts = 100),
            shot(2, 1, "A", side = 10.0, total = 110.0, ts = 200),
            shot(3, 1, "A", side = 5.0, total = 95.0, ts = 300),
        )
        val pdf = FittingPdfData.build(shots)
        assertTrue(pdf.topDown.hasData)
        val ell = pdf.clubs.single().ellipse
        assertNotNull(ell)
        // Buffered ellipse extents subsume the raw shot extremes (the ellipse
        // contains every kept point, so its extents reach at least the raw ones).
        assertTrue(pdf.topDown.minY <= 95.0)   // lowest kept total
        assertTrue(pdf.topDown.maxY >= 110.0)  // highest kept total
        assertTrue(pdf.topDown.maxAbsX >= 10.0)
        assertEquals(3, pdf.clubs.single().keptPoints.size)
    }

    @Test
    fun `session date - first shot timestamp wins, fallback for empty`() {
        val shots = listOf(shot(1, 1, "A", ts = 111L), shot(2, 1, "A", ts = 222L))
        assertEquals(111L, FittingPdfData.build(shots).sessionDateMs)
        assertNull(FittingPdfData.build(emptyList()).sessionDateMs)
        assertEquals(999L, FittingPdfData.build(emptyList(), sessionDateMs = 999L).sessionDateMs)
    }
}
