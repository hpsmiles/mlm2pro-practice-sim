package com.hpsmiles.golfsim.core.data.record

/**
 * Aggregate-query POJO (all shots; live and demo slices side by side).
 * Mapped to [SessionSummary] by [toSummary] according to the live-only filter.
 */
data class SessionSummaryRow(
    val id: Long,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val title: String?,
    val misreadCount: Int,
    val shotCount: Int,          // ALL shots incl. demo
    val liveCount: Int,
    val demoCount: Int,
    val liveAvgCarryM: Double?,
    val liveMaxCarryM: Double?,
    val allAvgCarryM: Double?,
    val allMaxCarryM: Double?,
    val liveClubsCsv: String?,
    val allClubsCsv: String?,
)

data class SessionSummary(
    val id: Long,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val title: String?,
    val misreadCount: Int,
    val shotCount: Int,
    val avgCarryM: Double?,
    val maxCarryM: Double?,
    val clubNames: List<String>,
    val hasDemoShot: Boolean,
    val isOpen: Boolean,
)

fun SessionSummaryRow.toSummary(liveOnly: Boolean): SessionSummary = SessionSummary(
    id = id,
    startedAtEpochMs = startedAtEpochMs,
    endedAtEpochMs = endedAtEpochMs,
    title = title,
    misreadCount = misreadCount,
    shotCount = if (liveOnly) liveCount else shotCount,
    avgCarryM = if (liveOnly) liveAvgCarryM else allAvgCarryM,
    maxCarryM = if (liveOnly) liveMaxCarryM else allMaxCarryM,
    clubNames = (if (liveOnly) liveClubsCsv else allClubsCsv)
        ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
    hasDemoShot = demoCount > 0,
    isOpen = endedAtEpochMs == null,
)
