package com.hpsmiles.golfsim.fitting

import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.data.fitting.FittingStats
import java.util.Locale
import kotlin.math.abs

/**
 * M7 fitting-session PDF (2026-10-08): pure, android-free builder that turns
 * a session's shots into the exact rows [FittingPdfWriter] renders — per club
 * (first-appearance order via [FittingStats.clubOrder]) a summary row followed
 * by that club's chronological shot rows (excluded shots still listed, flagged
 * `isExcluded`), then the Δ row(s) at the very end (2 clubs → one Δ B−A;
 * 3+ → a Δ row per non-baseline club against the first club as baseline —
 * FittingTable's delta semantics). The builder ALSO returns the top-down model
 * inputs (kept (side, total) points per club, the [DispersionEllipse] results
 * and the auto-fit bounds [TopDownModel]) so the renderer never re-derives —
 * the same derivation FittingTopDownPane performs.
 *
 * Summary/Δ cells reuse [FittingStats.summarize] + [FittingFormats] (and the
 * table's own SMASH/OFFLINE/Δ formatters) so the PDF matches the app table
 * exactly. No android imports — unit-testable on the JVM.
 */
object FittingPdfData {

    /** Number of summary metric columns (the table's 12: n … OFFLINE, AREA). */
    const val SUMMARY_COL_COUNT = 12

    /** One drill-down shot row — full record, matching FittingTable's SHOT_COLS. */
    data class ShotRow(
        val shotId: Long,
        val isExcluded: Boolean,
        val carry: String,
        val total: String,
        val ball: String,
        val chs: String,
        val smash: String,
        val spin: String,
        val dir: String,
    )

    /** One club's PDF block: formatted summary row + its shot rows + ring model. */
    data class ClubBlock(
        val clubId: Long,
        val name: String,
        /** Position in first-appearance order — [FittingColors.clubColor] input. */
        val colorIndex: Int,
        /** 12 pre-formatted summary cells, in the table's column order. */
        val summary: List<String>,
        val shots: List<ShotRow>,
        /** KEPT (side, total) pairs — the dots and the ellipse inputs. */
        val keptPoints: List<Pair<Double, Double>>,
        /** The kept shots' enclosing ellipse (null < 3 kept or collinear). */
        val ellipse: DispersionEllipse?,
        /** The drawn ring's post-5%-buffer area in m² (π·a·b) — the table's AREA column; null when [ellipse] is null. */
        val areaM2: Double?,
    )

    /** One Δ row: label + 12 cells ("-" where the delta is null; AREA is always "-"). */
    data class DeltaRow(
        val label: String,
        val cells: List<String>,
    )

    /** computeTopDownFit inputs derived from the kept shots + rings (FittingTopDownPane mirror). */
    data class TopDownModel(
        val minY: Double,
        val maxY: Double,
        val maxAbsX: Double,
        val hasData: Boolean,
    )

    /** The full PDF model: ordered club blocks + trailing Δ rows + top-down. */
    data class Pdf(
        val clubs: List<ClubBlock>,
        val deltaRows: List<DeltaRow>,
        val topDown: TopDownModel,
        /** First shot timestamp, or the caller's fallback when the session is empty. */
        val sessionDateMs: Long?,
        val totalShots: Int,
        val excludedCount: Int,
    )

    /**
     * Builds the PDF model for [shots]. [sessionDateMs] is the "now" fallback
     * used only when there are no shots (live export always has ≥ 1).
     */
    fun build(shots: List<FittingShotEntity>, sessionDateMs: Long? = null): Pdf {
        val order = FittingStats.clubOrder(shots)
        val clubs = order.mapIndexed { index, key ->
            val summary = FittingStats.summarize(key, shots)
            val mine = shots.filter { it.clubId == key.id }
            val kept = mine.filter { !it.excluded }
            val keptPoints = kept.map { it.sideM to it.totalM }
            val ellipse = DispersionEllipse.fromKept(keptPoints)
            ClubBlock(
                clubId = key.id,
                name = key.name,
                colorIndex = index,
                summary = summaryCells(summary, ellipse?.areaM2()),
                shots = mine.map { shotRow(it) },
                keptPoints = keptPoints,
                ellipse = ellipse,
                areaM2 = ellipse?.areaM2(),
            )
        }
        val deltaRows = buildDeltaRows(shots, clubs)
        return Pdf(
            clubs = clubs,
            deltaRows = deltaRows,
            topDown = buildTopDownModel(clubs),
            sessionDateMs = shots.firstOrNull()?.timestampMs ?: sessionDateMs,
            totalShots = shots.size,
            excludedCount = shots.count { it.excluded },
        )
    }

    // ---- summary cells (FittingTable COLS formatters, 12 columns incl. AREA) ----

    private fun summaryCells(s: FittingStats.ClubSummary, areaM2: Double?): List<String> = listOf(
        "${s.kept}",
        s.chs?.let { FittingFormats.mph(it) } ?: "-",
        s.ballSpeed?.let { FittingFormats.mph(it) } ?: "-",
        s.smash?.let { String.format(Locale.US, "%.2f", it) } ?: "-",
        FittingFormats.avgSigma(s.carry, s.carrySigma),
        FittingFormats.avgSigma(s.total, s.totalSigma),
        FittingFormats.deg(s.launch),
        FittingFormats.degSigned(s.dir),
        FittingFormats.rpm(s.spin),
        FittingFormats.degSigned(s.spinAxis),
        if (s.offlineAvg != null && s.offlineWorst != null) {
            String.format(Locale.US, "%.0f / %.0f", s.offlineAvg, s.offlineWorst)
        } else {
            "-"
        },
        // AREA (device ruling 2026-10-08): the drawn ring's area in m² (π·a·b of
        // the buffered rotated MVEE) — the same ellipse the top-down ring draws.
        // Mirrors FittingTable's AREA Col: "%.0f m²" or "-" (< 3 kept / collinear).
        areaM2?.let { String.format(Locale.US, "%.0f m²", it) } ?: "-",
    )

    // ---- Δ rows (FittingTable DeltaRow semantics: pick + noise + fmt) ----

    /** (pick, noise, deltaFmt) per summary column — mirror of FittingTable's COLS. */
    private data class DeltaSpec(
        val pick: (FittingStats.ClubSummary) -> Double?,
        val noise: Double,
        val fmt: (Double) -> String,
    )

    private val DELTA_SPECS: List<DeltaSpec> = listOf(
        DeltaSpec({ null }, 0.0, { "-" }), // n — no delta, "-" always
        DeltaSpec({ it.chs }, FittingStats.NOISE_CHS_MPS,
            { d -> String.format(Locale.US, "%+.1f", d * FittingFormats.MPH_PER_MS) }),
        DeltaSpec({ it.ballSpeed }, FittingStats.NOISE_BALL_SPEED_MPS,
            { d -> String.format(Locale.US, "%+.1f", d * FittingFormats.MPH_PER_MS) }),
        DeltaSpec({ it.smash }, FittingStats.NOISE_SMASH,
            { d -> String.format(Locale.US, "%+.2f", d) }),
        DeltaSpec({ it.carry }, FittingStats.NOISE_CARRY_M,
            { d -> String.format(Locale.US, "%+.0f m", d) }),
        DeltaSpec({ it.total }, FittingStats.NOISE_TOTAL_M,
            { d -> String.format(Locale.US, "%+.0f m", d) }),
        DeltaSpec({ it.launch }, FittingStats.NOISE_LAUNCH_DEG,
            { d -> String.format(Locale.US, "%+.1f°", d) }),
        DeltaSpec({ it.dir }, FittingStats.NOISE_DIR_DEG,
            { d -> String.format(Locale.US, "%+.1f°", d) }),
        DeltaSpec({ it.spin }, FittingStats.NOISE_SPIN_RPM,
            { d -> String.format(Locale.US, "%+.0f", d) }),
        DeltaSpec({ it.spinAxis }, FittingStats.NOISE_SPIN_AXIS_DEG,
            { d -> String.format(Locale.US, "%+.1f°", d) }),
        DeltaSpec({ it.offlineAvg }, FittingStats.NOISE_OFFLINE_M,
            { d -> String.format(Locale.US, "%+.1f m", d) }),
        // AREA: no Δ row in the app (quadratic noise — deliberately excluded);
        // pick → null renders "-" exactly like FittingTable's AREA Col.
        DeltaSpec({ null }, 0.0, { "-" }),
    )

    private fun buildDeltaRows(shots: List<FittingShotEntity>, clubs: List<ClubBlock>): List<DeltaRow> {
        if (clubs.size < 2) return emptyList()
        // Baseline = first club (FittingTable's default when baselineClubId is
        // null). 2 clubs → one Δ B−A; 3+ → a Δ row per non-baseline club
        // (FittingTable's per-club Δ rows), all grouped at the very end.
        val order = FittingStats.clubOrder(shots)
        val baseline = order[0]
        val baselineSummary = FittingStats.summarize(baseline, shots)
        return order.drop(1).map { other ->
            val otherSummary = FittingStats.summarize(other, shots)
            DeltaRow(
                label = "Δ ${other.name}−${baseline.name}",
                cells = deltaCells(otherSummary, baselineSummary),
            )
        }
    }

    private fun deltaCells(other: FittingStats.ClubSummary, baseline: FittingStats.ClubSummary): List<String> =
        DELTA_SPECS.map { spec ->
            // Named args on purpose — (other, baseline) positional is silently
            // swappable and flips the delta sign (FittingTable's own warning).
            val verdict = FittingStats.delta(
                other = spec.pick(other),
                baseline = spec.pick(baseline),
                noise = spec.noise,
            )
            verdict?.let { spec.fmt(it.delta) } ?: "-"
        }

    // ---- drill-down shot row (FittingTable SHOT_COLS formatters) ----

    private fun shotRow(s: FittingShotEntity): ShotRow = ShotRow(
        shotId = s.id,
        isExcluded = s.excluded,
        carry = String.format(Locale.US, "%.0f m", s.carryM),
        total = String.format(Locale.US, "%.0f m", s.totalM),
        ball = String.format(Locale.US, "%.1f MPH", s.ballSpeedMps * FittingFormats.MPH_PER_MS),
        chs = String.format(Locale.US, "%.1f MPH", s.clubHeadSpeedMps * FittingFormats.MPH_PER_MS),
        smash = if (s.clubHeadSpeedMps > 0.0) {
            String.format(Locale.US, "%.2f", s.ballSpeedMps / s.clubHeadSpeedMps)
        } else {
            "-"
        },
        spin = "${s.totalSpinRpm}",
        dir = String.format(Locale.US, "%+.1f°", s.launchDirDeg),
    )

    // ---- top-down model (FittingTopDownPane bounds derivation) ----

    private fun buildTopDownModel(clubs: List<ClubBlock>): TopDownModel {
        var minY = Double.POSITIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        var maxAbsX = 0.0
        var hasData = false
        for (block in clubs) {
            if (block.keptPoints.isNotEmpty()) hasData = true
            // Buffered enclosing ellipse (3+ kept): its projected extents subsume
            // every kept point, so they drive the fit (FittingTopDownPane mirror).
            val ell = block.ellipse
            if (ell != null) {
                val halfSide = ell.projectedHalfSideM()
                val halfTotal = ell.projectedHalfTotalM()
                minY = minOf(minY, ell.centreTotalM - halfTotal)
                maxY = maxOf(maxY, ell.centreTotalM + halfTotal)
                maxAbsX = maxOf(maxAbsX, abs(ell.centreSideM) + halfSide)
            } else {
                block.keptPoints.forEach { (side, total) ->
                    minY = minOf(minY, total)
                    maxY = maxOf(maxY, total)
                    maxAbsX = maxOf(maxAbsX, abs(side))
                }
            }
        }
        return TopDownModel(
            minY = if (hasData) minY else 0.0,
            maxY = if (hasData) maxY else 0.0,
            maxAbsX = maxAbsX,
            hasData = hasData,
        )
    }
}
