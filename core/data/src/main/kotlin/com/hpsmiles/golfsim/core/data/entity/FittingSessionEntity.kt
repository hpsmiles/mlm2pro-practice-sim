package com.hpsmiles.golfsim.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Status values for [FittingSessionEntity.status]. */
object FittingStatus {
    const val IN_PROGRESS = "IN_PROGRESS"
    const val COMPLETED = "COMPLETED"
}

/**
 * One club-fitting comparison (M7 spec §2). At most one row with status
 * IN_PROGRESS exists — the repository enforces it (bag pattern). The
 * compared club set is NOT stored here: it derives from the distinct club
 * snapshots in fitting_shots, ordered by first appearance — stable across
 * kill/resume and immune to the temp-club purge.
 */
@Entity(tableName = "fitting_sessions")
data class FittingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMs: Long,
    /** Null while the session is open; set when completed. */
    val completedAtMs: Long?,
    val status: String,            // FittingStatus values
)
