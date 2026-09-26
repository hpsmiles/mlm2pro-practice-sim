package com.hpsmiles.golfsim.core.data.record

import com.hpsmiles.golfsim.core.ble.BallData

/** What the repository exposes for one persisted shot. */
data class ShotRecord(
    val id: Long,
    val sessionId: Long,
    val seq: Int,
    val timestampMs: Long,
    val source: ShotSource,
    val clubName: String?,
    val ballData: BallData,
    val carryM: Double,
    val totalM: Double,
    val sideM: Double,
    val apexM: Double,
    val flightTimeSec: Double,
)

/** One bag entry as UI sees it. */
data class ClubRecord(val id: Long, val name: String)

/** The open session plus its shots handed back at app launch. */
data class RestoredSession(
    val sessionId: Long,
    val misreadCount: Int,
    val shots: List<ShotRecord>,
)
