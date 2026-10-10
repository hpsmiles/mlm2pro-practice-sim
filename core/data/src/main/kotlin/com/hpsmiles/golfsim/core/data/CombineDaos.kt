package com.hpsmiles.golfsim.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CombineSessionDao {
    @Insert
    suspend fun insert(session: CombineSessionEntity): Long

    @Query("SELECT * FROM combine_sessions WHERE completedAtMs IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun findInProgress(): CombineSessionEntity?

    @Query("SELECT * FROM combine_sessions WHERE completedAtMs IS NULL ORDER BY id DESC LIMIT 1")
    fun observeInProgress(): Flow<CombineSessionEntity?>

    @Query("SELECT * FROM combine_sessions WHERE completedAtMs IS NOT NULL ORDER BY id DESC")
    fun observeHistory(): Flow<List<CombineSessionEntity>>

    @Query("SELECT * FROM combine_sessions WHERE completedAtMs IS NOT NULL AND combineId = :combineId")
    suspend fun completedForCombine(combineId: String): List<CombineSessionEntity>

    @Query("UPDATE combine_sessions SET completedAtMs = :completedAtMs WHERE id = :id")
    suspend fun complete(id: Long, completedAtMs: Long)

    @Query("DELETE FROM combine_sessions WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CombineShotDao {
    @Insert
    suspend fun insert(shot: CombineShotEntity): Long

    @Query("SELECT * FROM combine_shots WHERE sessionId = :sessionId ORDER BY shotIndex")
    fun observeShots(sessionId: Long): Flow<List<CombineShotEntity>>

    @Query("SELECT * FROM combine_shots WHERE sessionId IN (:sessionIds)")
    suspend fun shotsForSessions(sessionIds: List<Long>): List<CombineShotEntity>

    @Query(
        "SELECT * FROM combine_shots WHERE sessionId IN " +
            "(SELECT id FROM combine_sessions WHERE completedAtMs IS NOT NULL) ORDER BY sessionId, shotIndex"
    )
    fun observeHistoryShots(): Flow<List<CombineShotEntity>>

    @Query("DELETE FROM combine_shots WHERE sessionId = :sessionId")
    suspend fun deleteShots(sessionId: Long)
}
