package com.hpsmiles.golfsim.fitting

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure dispersion-oval math (M7 spec §5): mean + covariance of an (x, y)
 * point cloud → 1σ ellipse parameters + outline polygon. Metres in, metres
 * out; no framework types. Null below 3 points or for a degenerate cloud
 * (zero variance OR collinear — λ2 ≈ 0 with λ1 > 0 gives a half-line, no
 * meaningful spread to draw).
 */
object DispersionOval {

    data class Oval(
        val cx: Double,
        val cy: Double,
        /** 1σ semi-axis along [angleRad] measured from +x. */
        val a: Double,
        /** 1σ semi-axis perpendicular to [angleRad]. */
        val b: Double,
        val angleRad: Double,
    )

    fun compute(points: List<Pair<Double, Double>>): Oval? {
        if (points.size < 3) return null
        val n = points.size.toDouble()
        val mx = points.sumOf { it.first } / n
        val my = points.sumOf { it.second } / n
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for ((x, y) in points) {
            val dx = x - mx
            val dy = y - my
            sxx += dx * dx
            syy += dy * dy
            sxy += dx * dy
        }
        sxx /= n; syy /= n; sxy /= n
        val sum = sxx + syy
        val diff = sxx - syy
        val root = sqrt(diff * diff / 4.0 + sxy * sxy)
        val lambda1 = sum / 2.0 + root   // larger eigenvalue
        val lambda2 = sum / 2.0 - root
        if (lambda1 <= 1e-12 || lambda2 <= 1e-12) return null
        // Eigenvector of the LARGER eigenvalue (closed form, symmetric 2×2).
        val angle = 0.5 * atan2(2.0 * sxy, diff)
        return Oval(mx, my, sqrt(lambda1), sqrt(lambda2), angle)
    }

    /**
     * Area of the ellipse at [sigmaScale]·1σ: π·(σa)·(σb) m² (area scales
     * quadratically with σ — the 2σ ring covers ~86 % of a bivariate normal).
     */
    fun area(oval: Oval, sigmaScale: Double): Double =
        PI * sigmaScale * sigmaScale * oval.a * oval.b

    /** Ellipse outline points (x, y) at [sigmaScale]·1σ, CCW, [segments] vertices. */
    fun polygon(oval: Oval, sigmaScale: Double, segments: Int = 32): List<Pair<Double, Double>> {
        val a = oval.a * sigmaScale
        val b = oval.b * sigmaScale
        val cosA = cos(oval.angleRad)
        val sinA = sin(oval.angleRad)
        return (0 until segments).map { i ->
            val t = 2.0 * PI * i / segments
            val ex = a * cos(t)
            val ey = b * sin(t)
            (oval.cx + ex * cosA - ey * sinA) to (oval.cy + ex * sinA + ey * cosA)
        }
    }
}
