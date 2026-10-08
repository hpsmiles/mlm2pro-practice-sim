package com.hpsmiles.golfsim.fitting

import com.hpsmiles.golfsim.range.RangeScene
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * World→pixel mapping for the M7 top-down pane (design notes §2.3). World
 * frame: x lateral (side, metres), y down-range (rest position = total,
 * metres). Screen: origin at (originX, originY) = the world origin; a world
 * point (x, y) maps to (originX + x·pxPerM, originY − y·pxPerM).
 */
data class TopDownFit(
    /** World metres → screen px. Isotropic — one scale for both axes, rings stay circular. */
    val pxPerM: Double,
    /** Screen x of the world x = 0 axis (lateral centre). */
    val originX: Double,
    /** Screen y of the world y = 0 origin (the mat/tee). */
    val originY: Double,
)

/**
 * Rotated minimum-enclosing ellipse (Löwner / MVEE) of the KEPT shots,
 * uniformly scaled ×1.05 about its centre (device ruling 2026-10-08). The
 * outline the top-down pane draws is this ellipse and the table's AREA
 * column shows its area — one dispersion metric, drawn and reported
 * identically.
 *
 * Solved with Khachiyan's algorithm on the lifted points (x, y, 1) —
 * iterative, deterministic (relative ε = 1e-9, ≤ 200 iterations). The raw
 * minimum-area ellipse encloses every kept shot; scaling both semi-axes by
 * (1 + [BUFFER_FRACTION]) about the centre keeps that containment with the
 * 5 % margin, and a final per-point check (refusing to draw a lying ring)
 * makes containment a hard guarantee. Unlike the axis-aligned bbox ellipse
 * it replaces, the ROTATED ellipse also contains the diagonal corner-extremes
 * (near-longest + far-lateral shots simultaneously), which is exactly the
 * containment failure the device flagged on the inscribed-ellipse ring.
 *
 * Metres in, metres out; no framework types. [fromKept] returns null for
 * < 3 points or degenerate sets (collinear / near-singular — no enclosing
 * ellipse in the limit), matching the old zero-span "no ring" treatment.
 */
data class DispersionEllipse(
    val centreSideM: Double,
    val centreTotalM: Double,
    /** Semi-axis along [angleRad] (the long axis), metres. */
    val semiAxisM: Double,
    /** Semi-axis perpendicular to [angleRad], metres. */
    val semiCrossM: Double,
    /** Rotation of the a-axis in radians, side↔total frame (x = side, y = total). */
    val angleRad: Double,
) {
    /** Area of the drawn ring = the AREA column's metric (post-5%-buffer). */
    fun areaM2(): Double = PI * semiAxisM * semiCrossM

    /** Extent of the ellipse along the side (x) axis, metres (auto-fit). */
    fun projectedHalfSideM(): Double =
        sqrt(
            semiAxisM * semiAxisM * cos(angleRad) * cos(angleRad) +
                semiCrossM * semiCrossM * sin(angleRad) * sin(angleRad),
        )

    /** Extent of the ellipse along the total (y) axis, metres (auto-fit). */
    fun projectedHalfTotalM(): Double =
        sqrt(
            semiAxisM * semiAxisM * sin(angleRad) * sin(angleRad) +
                semiCrossM * semiCrossM * cos(angleRad) * cos(angleRad),
        )

    companion object {
        /** Uniform buffer applied to both semi-axes about the centre (device ruling 2026-10-08: "5 %"). */
        const val BUFFER_FRACTION = 0.05

        /** Khachiyan relative convergence tolerance (dimensionless). */
        private const val KHACHIYAN_EPS = 1e-9

        /** Khachiyan iteration cap (safety net — real clouds converge far sooner). */
        private const val KHACHIYAN_MAX_ITER = 200

        /** Relative collinearity threshold — a cloud this flat has no enclosing ellipse. */
        private const val COLLINEAR_REL = 1e-12

        /**
         * Minimum-area enclosing ellipse of the kept shots via Khachiyan's
         * algorithm, both semi-axes scaled ×(1 + [BUFFER_FRACTION]) about the
         * centre. Null for < 3 points or a degenerate (collinear / singular)
         * set.
         */
        fun fromKept(points: List<Pair<Double, Double>>): DispersionEllipse? {
            val n = points.size
            if (n < 3) return null
            val xs = DoubleArray(n)
            val ys = DoubleArray(n)
            for (i in 0 until n) {
                xs[i] = points[i].first
                ys[i] = points[i].second
            }
            // Reject collinear / near-singular clouds up front (rank-1 spread
            // has no enclosing ellipse in the limit — the "no ring" treatment).
            var mx = 0.0
            var my = 0.0
            for (i in 0 until n) {
                mx += xs[i]
                my += ys[i]
            }
            mx /= n
            my /= n
            var c00 = 0.0
            var c01 = 0.0
            var c11 = 0.0
            for (i in 0 until n) {
                val dx = xs[i] - mx
                val dy = ys[i] - my
                c00 += dx * dx
                c01 += dx * dy
                c11 += dy * dy
            }
            val covTrace = c00 + c11
            val covDet = c00 * c11 - c01 * c01
            if (covTrace <= 0.0 || covDet <= COLLINEAR_REL * covTrace * covTrace) return null

            // Khachiyan on the lifted dual: M = Σ u_i q_i q_i^T, q_i = (x_i, y_i, 1).
            var u = DoubleArray(n) { 1.0 / n }
            val m = Array(3) { DoubleArray(3) }
            var iter = 0
            while (iter < KHACHIYAN_MAX_ITER) {
                for (r in 0..2) for (c in 0..2) m[r][c] = 0.0
                for (i in 0 until n) {
                    val x = xs[i]
                    val y = ys[i]
                    val w = u[i]
                    m[0][0] += w * x * x
                    m[0][1] += w * x * y
                    m[0][2] += w * x
                    m[1][1] += w * y * y
                    m[1][2] += w * y
                    m[2][2] += w
                }
                m[1][0] = m[0][1]
                m[2][0] = m[0][2]
                m[2][1] = m[1][2]
                val mInv = inv3(m) ?: return null
                var maxDi = 0.0
                var jMax = 0
                for (i in 0 until n) {
                    // d_i = q_i^T M^{-1} q_i; a point is outside the current
                    // ellipse iff d_i > d+1 = 3.
                    val x = xs[i]
                    val y = ys[i]
                    val t0 = mInv[0][0] * x + mInv[0][1] * y + mInv[0][2]
                    val t1 = mInv[1][0] * x + mInv[1][1] * y + mInv[1][2]
                    val t2 = mInv[2][0] * x + mInv[2][1] * y + mInv[2][2]
                    val di = x * t0 + y * t1 + t2
                    if (di > maxDi) {
                        maxDi = di
                        jMax = i
                    }
                }
                if (maxDi <= (1.0 + KHACHIYAN_EPS) * 3.0) break
                val delta = (maxDi - 3.0) / (3.0 * (maxDi - 1.0))
                // Khachiyan's simplex-preserving update: scale EVERY weight by
                // (1 − δ), then add δ to the worst point — Σu stays 1. Updating
                // only u[j] (a bug in the first draft) leaves the weights off
                // the simplex and drives the iteration to a wrong fixed point
                // whose ellipse excludes hull vertices — the exact device
                // complaint this rework is fixing.
                for (i in 0 until n) u[i] *= 1.0 - delta
                u[jMax] += delta
                iter++
            }
            // Centre c = Σ u_i p_i.
            var cx = 0.0
            var cy = 0.0
            for (i in 0 until n) {
                cx += u[i] * xs[i]
                cy += u[i] * ys[i]
            }
            // A = (1/d)·(Σ u_i p_i p_i^T − c c^T)^{-1}; ellipse = {x : (x−c)^T A (x−c) ≤ 1}.
            var s00 = 0.0
            var s01 = 0.0
            var s11 = 0.0
            for (i in 0 until n) {
                s00 += u[i] * xs[i] * xs[i]
                s01 += u[i] * xs[i] * ys[i]
                s11 += u[i] * ys[i] * ys[i]
            }
            s00 -= cx * cx
            s01 -= cx * cy
            s11 -= cy * cy
            val detS = s00 * s11 - s01 * s01
            if (!detS.isFinite() || detS <= 0.0) return null
            val a00 = (s11 / detS) / 2.0
            val a01 = (-s01 / detS) / 2.0
            val a11 = (s00 / detS) / 2.0
            // 2×2 eigen-decomposition of A (closed form). λ1 ≥ λ2; the long
            // semi-axis is along the eigenvector of the smaller eigenvalue.
            val trace = a00 + a11
            val disc = sqrt((a00 - a11) * (a00 - a11) / 4.0 + a01 * a01)
            val lam1 = trace / 2.0 + disc
            val lam2 = trace / 2.0 - disc
            val semiLong = 1.0 / sqrt(lam2)
            val semiShort = 1.0 / sqrt(lam1)
            val angle = if (abs(a01) < 1e-12) {
                // Axis-aligned (a01 = 0): the long axis follows the smaller A entry.
                if (a00 < a11) 0.0 else PI / 2.0
            } else {
                // Eigenvector of the smaller eigenvalue λ2: v = (λ2 − a11, a01).
                atan2(a01, lam2 - a11)
            }
            val scale = 1.0 + BUFFER_FRACTION
            val semiAxis = semiLong * scale
            val semiCross = semiShort * scale
            // Deterministic containment (device ruling 2026-10-08): the whole
            // point of the rework is that the ring ACTUALLY contains every kept
            // dot. The 5 % buffer absorbs Khachiyan's slow tail (measured < 0.93
            // residual on hundreds of clouds), but verify it — if a pathological
            // cloud somehow still pokes out, return null (no ring) rather than
            // draw a lying ring that re-introduces the device bug.
            val cosA = cos(angle)
            val sinA = sin(angle)
            for (i in 0 until n) {
                val dx = xs[i] - cx
                val dy = ys[i] - cy
                val uu = dx * cosA + dy * sinA
                val vv = -dx * sinA + dy * cosA
                val uNorm = uu / semiAxis
                val vNorm = vv / semiCross
                if (uNorm * uNorm + vNorm * vNorm > 1.0 + 1e-9) return null
            }
            return DispersionEllipse(
                centreSideM = cx,
                centreTotalM = cy,
                semiAxisM = semiAxis,
                semiCrossM = semiCross,
                angleRad = angle,
            )
        }

        /** Inverse of a 3×3 matrix, or null when singular. */
        private fun inv3(m: Array<DoubleArray>): Array<DoubleArray>? {
            val a = m[0][0]; val b = m[0][1]; val c = m[0][2]
            val d = m[1][0]; val e = m[1][1]; val f = m[1][2]
            val g = m[2][0]; val h = m[2][1]; val i = m[2][2]
            val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
            if (!det.isFinite() || abs(det) < 1e-18) return null
            return arrayOf(
                doubleArrayOf((e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det),
                doubleArrayOf((f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det),
                doubleArrayOf((d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det),
            )
        }
    }
}

/**
 * Computes the world→pixel mapping that fits the kept shots AND their
 * rotated enclosing-ellipse ring extents plus a buffer into the canvas
 * (device feedback 2026-10-08: "scaled to fit the shots and rings plus a
 * small buffer" — 0–150 m of empty range is useless on a driver fitting).
 * Isotropic; falls back to the full-range mapping when there is no data.
 *
 * [minY]/[maxY] are the min/max rest-distance (down-range, metres) and
 * [maxAbsX] the max |side| (lateral, metres) over the kept shots INCLUDING
 * each club's ellipse outer extents. [hasData] is false when there are
 * no kept shots — the mapping then matches the pre-fix full-range view
 * (`height / GROUND_END_Y`, origin [bottomMarginPx] above the canvas bottom).
 *
 * Behaviour:
 *  - ySpan = maxY − minY; a minimum span of [MIN_SPAN_M] is enforced by
 *    expanding symmetrically around the centre (prevents absurd zoom on 1–2
 *    clustered shots). pad = max([MIN_PAD_M], 0.06·ySpan) metres each side.
 *  - pxPerM = min(yFit, xFit) with yFit = height / (ySpan + 2·pad) and
 *    xFit = (width/2 − [lateralPadPx]) / maxAbsX (the x constraint is skipped
 *    when maxAbsX ≈ 0).
 *  - The y-window [minY − pad, maxY + pad] fills the canvas height when the
 *    lateral constraint doesn't bind; x stays centred (originX = width/2).
 */
fun computeTopDownFit(
    width: Double,
    height: Double,
    minY: Double,
    maxY: Double,
    maxAbsX: Double,
    hasData: Boolean,
    bottomMarginPx: Double = 0.0,
    lateralPadPx: Double = DEFAULT_LATERAL_PAD_PX,
): TopDownFit {
    val originX = width / 2.0
    if (!hasData) {
        // Exact pre-fix mapping: the whole ground extent fitted to the height,
        // origin a fixed [bottomMarginPx] above the canvas bottom — the empty
        // state looks unchanged.
        return TopDownFit(height / RangeScene.GROUND_END_Y, originX, height - bottomMarginPx)
    }
    var lo = minY
    var hi = maxY
    if (hi - lo < MIN_SPAN_M) {
        val centre = (lo + hi) / 2.0
        lo = centre - MIN_SPAN_M / 2.0
        hi = centre + MIN_SPAN_M / 2.0
    }
    val pad = max(MIN_PAD_M, 0.06 * (hi - lo))
    val yFit = height / (hi - lo + 2.0 * pad)
    var pxPerM = yFit
    if (maxAbsX > X_EPSILON) {
        pxPerM = min(pxPerM, (width / 2.0 - lateralPadPx) / maxAbsX)
    }
    // Window bottom [lo − pad] maps to the canvas bottom (y = height); the
    // world origin sits pxPerM·(lo − pad) below it (off-screen when zoomed).
    val originY = height + (lo - pad) * pxPerM
    return TopDownFit(pxPerM, originX, originY)
}

/** Smallest padded y-window, metres (1–2 clustered shots must not zoom absurdly). */
private const val MIN_SPAN_M = 30.0

/** Minimum vertical buffer each side of the y-window, metres. */
private const val MIN_PAD_M = 10.0

/** Side-buffer reserved on each lateral edge of the fitted x-extent, px. */
private const val DEFAULT_LATERAL_PAD_PX = 48.0

/** Below this |side| the lateral constraint is skipped (shots on the centre line). */
private const val X_EPSILON = 1e-9
