package com.hpsmiles.golfsim.core.data.record

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Auto-title for sessions without a custom label: "Mon 21 Sep · 16:10". */
object SessionTitles {
    fun auto(
        startedAtEpochMs: Long,
        locale: Locale = Locale.getDefault(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM \u00b7 HH:mm", locale)
        return fmt.format(Instant.ofEpochMilli(startedAtEpochMs).atZone(zone))
    }
}
