package com.hpsmiles.golfsim.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hpsmiles.golfsim.core.data.entity.SessionEntity
import com.hpsmiles.golfsim.core.data.record.SessionSummaryRow
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT * FROM sessions WHERE endedAtEpochMs IS NULL ORDER BY startedAtEpochMs DESC LIMIT 1")
    suspend fun findOpen(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE endedAtEpochMs IS NULL ORDER BY startedAtEpochMs DESC LIMIT 1")
    fun observeOpen(): Flow<SessionEntity?>

    @Query("UPDATE sessions SET endedAtEpochMs = :endedAtEpochMs WHERE id = :id")
    suspend fun end(id: Long, endedAtEpochMs: Long)

    @Query("UPDATE sessions SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String?)

    @Query("UPDATE sessions SET misreadCount = misreadCount + 1 WHERE id = :id")
    suspend fun incrementMisread(id: Long)

    @Query(
        """
        SELECT s.id AS id, s.startedAtEpochMs AS startedAtEpochMs,
               s.endedAtEpochMs AS endedAtEpochMs, s.title AS title,
               s.misreadCount AS misreadCount,
               COUNT(sh.id) AS shotCount,
               COALESCE(SUM(CASE WHEN sh.source = 0 THEN 1 ELSE 0 END), 0) AS liveCount,
               COALESCE(SUM(CASE WHEN sh.source = 1 THEN 1 ELSE 0 END), 0) AS demoCount,
               AVG(CASE WHEN sh.source = 0 THEN sh.carryM END) AS liveAvgCarryM,
               MAX(CASE WHEN sh.source = 0 THEN sh.carryM END) AS liveMaxCarryM,
               AVG(sh.carryM) AS allAvgCarryM,
               MAX(sh.carryM) AS allMaxCarryM,
               GROUP_CONCAT(DISTINCT CASE WHEN sh.source = 0 THEN sh.clubName END) AS liveClubsCsv,
               GROUP_CONCAT(DISTINCT sh.clubName) AS allClubsCsv
        FROM sessions s
        LEFT JOIN shots sh ON sh.sessionId = s.id
        GROUP BY s.id
        ORDER BY s.startedAtEpochMs DESC
        """
    )
    fun observeSummaries(): Flow<List<SessionSummaryRow>>

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}
