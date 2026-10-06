package com.hpsmiles.golfsim.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hpsmiles.golfsim.core.data.record.ClubType

/** Status values for [BagMappingSessionEntity.status]. */
object BagMappingStatus {
    const val IN_PROGRESS = "IN_PROGRESS"
    const val COMPLETED = "COMPLETED"
}

/**
 * One guided bag-mapping test (M6 spec §7). At most one row with status
 * IN_PROGRESS exists — the repository enforces it (spec §4). Mapping
 * sessions are isolated from range sessions: separate tables, games pattern.
 *
 * [clubList] snapshots the bag at START as comma-joined "name:TYPE" pairs
 * (names cannot contain commas — addClub/renameClub reject them). Each pair
 * splits at the LAST colon, so club names may themselves contain ':' —
 * type names are colon-free enum constants, making the codec lossless.
 * Clubs added mid-test never join; renames/deletes never reshape a running
 * or past session (spec §4 + §9 "no data" rows for skipped clubs).
 */
@Entity(tableName = "bag_mapping_sessions")
data class BagMappingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMs: Long,
    /** Null while the session is open; set when completed. */
    val completedAtMs: Long?,
    val status: String,            // BagMappingStatus values
    val clubList: String,
) {
    /** Bag at session start, in guided order: (clubName, clubType) pairs. */
    fun clubSnapshot(): List<Pair<String, ClubType>> =
        clubList.split(',').filter { it.contains(':') }.map { entry ->
            entry.substringBeforeLast(':') to ClubType.fromName(entry.substringAfterLast(':'))
        }

    companion object {
        /** Serializes the bag snapshot; inverse of [clubSnapshot]. */
        fun encodeClubSnapshot(clubs: List<Pair<String, ClubType>>): String =
            clubs.joinToString(",") { "${it.first}:${it.second.name}" }
    }
}
