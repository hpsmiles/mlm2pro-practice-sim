package com.hpsmiles.golfsim.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.data.record.FittingClubCountRow
import kotlinx.coroutines.flow.Flow

@Dao
interface FittingShotDao {
    @Insert
    suspend fun insert(shot: FittingShotEntity): Long

    /**
     * Deliberate insertion-order choice (ORDER BY id), NOT timestampMs:
     * robust to wall-clock jumps on a single-device tablet (no NTP). Newer
     * shots are always last in the drill-down regardless of clock skew.
     */
    @Query("SELECT * FROM fitting_shots WHERE sessionId = :sessionId ORDER BY id")
    fun observeBySession(sessionId: Long): Flow<List<FittingShotEntity>>

    @Query("UPDATE fitting_shots SET excluded = :excluded WHERE id IN (:ids)")
    suspend fun setExcluded(ids: List<Long>, excluded: Boolean)

    /** Per-(session, club) shot counts; MIN(id) preserves first-appearance order. */
    @Query(
        "SELECT sessionId, clubName, COUNT(*) AS shotCount FROM fitting_shots " +
            "GROUP BY sessionId, clubName ORDER BY sessionId, MIN(id)",
    )
    fun observeClubCounts(): Flow<List<FittingClubCountRow>>
}
