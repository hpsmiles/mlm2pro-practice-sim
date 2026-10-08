package com.hpsmiles.golfsim.fitting

import java.util.Locale

/** Metric formatting for the FIT comparison card + table (M7 design notes §4). */
object FittingFormats {
    const val MPH_PER_MS = 2.23694

    fun m1(v: Double) = String.format(Locale.US, "%.1f", v)

    fun mph(v: Double) = String.format(Locale.US, "%.1f", v * MPH_PER_MS)

    /** "140.2 ± 3.1" — σ shown only from ~0.1 m up. */
    fun avgSigma(avg: Double?, sigma: Double?): String = when {
        avg == null -> "-"
        sigma == null || sigma < 0.05 -> m1(avg)
        else -> String.format(Locale.US, "%.1f ± %.1f", avg, sigma)
    }

    fun deg(v: Double?) = if (v == null) "-" else String.format(Locale.US, "%.1f°", v)

    fun degSigned(v: Double?) = if (v == null) "-" else String.format(Locale.US, "%+.1f°", v)

    fun rpm(v: Double?) = if (v == null) "-" else String.format(Locale.US, "%.0f", v)

    fun smash(v: Double?) = if (v == null) "-" else String.format(Locale.US, "%.3f", v)
}
