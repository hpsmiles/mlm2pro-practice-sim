package com.hpsmiles.golfsim.bag

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Display formatting for bag mapping. Metres with the app-wide "%.0f m"
 * convention (HistoryFormats style); UnitsPreference wiring is out of
 * scope (spec §2 decision).
 */
object BagMappingFormats {
    /** m/s → mph, the same constant as HistoryFormats / FittingFormats. */
    const val MPH_PER_MS = 2.23694

    fun carry(m: Double): String = String.format(Locale.US, "%.0f m", m)

    /** Total distance (carry + rollout), same "%.0f m" convention. */
    fun total(m: Double): String = String.format(Locale.US, "%.0f m", m)

    /**
     * Ball / club speed in mph — the app-wide speed convention. Bag shots are
     * STORED in m/s (the raw BallData block), so the table must convert.
     */
    fun mph(v: Double): String = String.format(Locale.US, "%.1f", v * MPH_PER_MS)

    /** Smash factor = ball speed / club head speed. */
    fun smash(clubMps: Double, ballMps: Double): String =
        if (clubMps <= 0.0) "—" else String.format(Locale.US, "%.2f", ballMps / clubMps)

    fun dateTime(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))
}
