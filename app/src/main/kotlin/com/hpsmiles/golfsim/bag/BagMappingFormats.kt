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
    fun carry(m: Double): String = String.format(Locale.US, "%.0f m", m)

    /** Smash factor = ball speed / club head speed. */
    fun smash(clubMps: Double, ballMps: Double): String =
        if (clubMps <= 0.0) "—" else String.format(Locale.US, "%.2f", ballMps / clubMps)

    fun dateTime(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))
}
