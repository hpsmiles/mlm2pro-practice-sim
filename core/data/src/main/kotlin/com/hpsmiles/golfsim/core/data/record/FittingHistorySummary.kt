package com.hpsmiles.golfsim.core.data.record

/** One (session, club) row of the fitting-history details projection. */
data class FittingClubCountRow(
    val sessionId: Long,
    val clubName: String,
    val shotCount: Int,
)
