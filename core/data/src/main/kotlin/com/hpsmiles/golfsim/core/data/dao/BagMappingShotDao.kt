package com.hpsmiles.golfsim.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import kotlinx.coroutines.flow.Flow

/** Per-session shot counts for history rows (spec §3). */
data class BagMappingSessionShotCount(val sessionId: Long, val count: Int)

@Dao
interface BagMappingShotDao {

    @Insert
    suspend fun insert(shot: BagMappingShotEntity): Long

    @Query("SELECT sessionId, COUNT(*) as count FROM bag_mapping_shots GROUP BY sessionId")
    suspend fun countsBySession(): List<BagMappingSessionShotCount>

    @Query("SELECT * FROM bag_mapping_shots WHERE sessionId = :sessionId ORDER BY timestampMs, id")
    suspend fun shotsForSession(sessionId: Long): List<BagMappingShotEntity>

    @Query("SELECT * FROM bag_mapping_shots WHERE sessionId = :sessionId ORDER BY timestampMs, id")
    fun observeShots(sessionId: Long): Flow<List<BagMappingShotEntity>>

    @Query("SELECT * FROM bag_mapping_shots WHERE sessionId = :sessionId AND clubName = :clubName ORDER BY timestampMs, id")
    suspend fun shotsForClub(sessionId: Long, clubName: String): List<BagMappingShotEntity>

    @Query("UPDATE bag_mapping_shots SET filtered = :filtered, filterReason = :reason WHERE id = :id")
    suspend fun setFiltered(id: Long, filtered: Boolean, reason: String?)

    /**
     * Sets the user include/exclude override (2026-10-16): null clears it
     * (back to following the auto gate verdict), true forces include, false
     * forces exclude. The auto recompute path never touches this column.
     */
    @Query("UPDATE bag_mapping_shots SET includeOverride = :includeOverride WHERE id = :id")
    suspend fun setIncludeOverride(id: Long, includeOverride: Boolean?)
}
