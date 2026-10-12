package com.hpsmiles.golfsim.fitting

import com.hpsmiles.golfsim.range.RangeScene
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
    /** Rotation of the a-axis in radians, side↔total frame (x = side, y = total) — world/math space; the top-down draw site negates it for the screen's flipped y-axis. */
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

/** Smallest fitted distance window, metres (1–2 clustered shots must not zoom absurdly). */
private const val MIN_SPAN_M = 30.0

/** Minimum buffer each side of the fitted distance window, metres. */
private const val MIN_PAD_M = 10.0

/** Lateral visibility factor over the fairway half-width — so the 28 m fairway is always visible. */
private const val CORRIDOR_VISIBILITY_SCALE = 2.5

/**
 * Horizontal (tee-left) plan fit. Screen mapping:
 *   sx(d) = originX + d * pxPerMX   (d = distance metres from the tee)
 *   sy(s) = midY - s * pxPerMY      (s = lateral metres, positive up)
 * [originX] is the screen x of world distance 0 and may fall off-canvas when the
 * fitted distance window excludes the tee.
 */
data class TopDownFitH(
    val pxPerMX: Double,
    val pxPerMY: Double,
    val originX: Double,
    val midY: Double,
)

const val LATERAL_CONTENT_MARGIN_M = 2.0
const val DEFAULT_LEFT_PAD_PX = 26.0
const val DEFAULT_RIGHT_PAD_PX = 12.0
const val DEFAULT_V_PAD_PX = 12.0

fun computeTopDownFitH(
    width: Double,
    height: Double,
    minDistM: Double,
    maxDistM: Double,
    maxAbsSideM: Double,
    hasData: Boolean,
    leftPadPx: Double = DEFAULT_LEFT_PAD_PX,
    rightPadPx: Double = DEFAULT_RIGHT_PAD_PX,
    vPadPx: Double = DEFAULT_V_PAD_PX,
): TopDownFitH {
    val lo = if (hasData) minDistM else 0.0
    val hi = if (hasData) maxDistM else RangeScene.FAIRWAY_END_Y
    val span = maxOf(hi - lo, MIN_SPAN_M)
    val centre = (lo + hi) / 2
    val pad = maxOf(MIN_PAD_M, 0.06 * span)
    val pxPerMX = (width - leftPadPx - rightPadPx) / (span + 2 * pad)
    val originX = leftPadPx - (centre - span / 2 - pad) * pxPerMX
    val lateralHalfM = maxOf(RangeScene.fairwayHalfWidth(0.0) * CORRIDOR_VISIBILITY_SCALE, maxAbsSideM + LATERAL_CONTENT_MARGIN_M)
    val pxPerMY = (height / 2 - vPadPx) / lateralHalfM
    return TopDownFitH(pxPerMX, pxPerMY, originX, height / 2)
}

/** Candidate distance-grid steps, metres, finest first. */
private val GRID_STEP_CANDIDATES_M = doubleArrayOf(10.0, 25.0, 50.0, 100.0)

/** Most labeled intervals allowed across the visible distance window. */
private const val MAX_GRID_INTERVALS = 8.0

/**
 * Adaptive distance-grid step for the top-down pane's scale references
 * (device feedback 2026-10-12): the smallest candidate step keeping
 * ≤ [MAX_GRID_INTERVALS] intervals across the visible [spanM]. Fitting zooms
 * (span ≥ [MIN_SPAN_M]) land on 10–25 m — always at least 2 labeled
 * references; the full-range fallback (~370 m visible) lands on 50 m, so the
 * gridlines coincide with the standing sign boards and never crowd.
 */
fun distanceGridStepM(spanM: Double): Double {
    for (step in GRID_STEP_CANDIDATES_M) {
        if (spanM / step <= MAX_GRID_INTERVALS) return step
    }
    return GRID_STEP_CANDIDATES_M.last()
}
