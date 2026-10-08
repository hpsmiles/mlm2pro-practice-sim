package com.hpsmiles.golfsim.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hpsmiles.golfsim.core.data.entity.FittingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FittingSessionDao {
    @Insert
    suspend fun insert(session: FittingSessionEntity): Long

    @Query("SELECT * FROM fitting_sessions WHERE status = 'IN_PROGRESS' LIMIT 1")
    suspend fun findInProgress(): FittingSessionEntity?

    @Query("SELECT * FROM fitting_sessions WHERE status = 'IN_PROGRESS' LIMIT 1")
    fun observeInProgress(): Flow<FittingSessionEntity?>

    @Query("SELECT * FROM fitting_sessions WHERE status = 'COMPLETED' ORDER BY completedAtMs DESC")
    fun observeHistory(): Flow<List<FittingSessionEntity>>

    @Query("UPDATE fitting_sessions SET completedAtMs = :completedAtMs, status = 'COMPLETED' WHERE id = :id")
    suspend fun complete(id: Long, completedAtMs: Long)
}
