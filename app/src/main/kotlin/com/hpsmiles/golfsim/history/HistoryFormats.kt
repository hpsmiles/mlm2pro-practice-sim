// app/src/main/kotlin/com/hpsmiles/golfsim/history/HistoryFormats.kt
package com.hpsmiles.golfsim.history

import com.hpsmiles.golfsim.core.data.record.SessionSummary
import java.util.Locale

/** Metres/second to mph for display (same constant as the range panel). */
private const val MPH_PER_MS = 2.23694

/**
 * Pure formatting for the history screen — unit-testable JVM, no Compose.
 * Minus sign is U+2212 per the M5 mockups (review-layout/retag-interaction).
 */
object HistoryFormats {

    fun carry(m: Double): String = String.format(Locale.US, "%.0f m", m)

    fun side(sideM: Double): String =
        String.format(Locale.US, "%+.1f m", sideM).replace('-', '−')

    fun ballMph(ballSpeedMps: Double): String =
        String.format(Locale.US, "%.1f mph", ballSpeedMps * MPH_PER_MS)

    fun spin(rpm: Int): String = String.format(Locale.US, "%d rpm", rpm)

    fun clubOrDash(clubName: String?): String = clubName ?: "—"

    fun sessionMeta(s: SessionSummary): String {
        val count = if (s.shotCount == 1) "1 shot" else "${s.shotCount} shots"
        val avg = s.avgCarryM?.let { " · avg ${carry(it)}" } ?: ""
        val clubs = if (s.clubNames.isEmpty()) "" else " · ${s.clubNames.joinToString(", ")}"
        return count + avg + clubs
    }
}
