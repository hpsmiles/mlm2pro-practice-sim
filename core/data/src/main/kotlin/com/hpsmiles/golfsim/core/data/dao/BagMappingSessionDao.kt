package com.hpsmiles.golfsim.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hpsmiles.golfsim.core.data.entity.BagMappingSessionEntity
import com.hpsmiles.golfsim.core.data.entity.BagMappingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BagMappingSessionDao {

    @Insert
    suspend fun insert(session: BagMappingSessionEntity): Long

    @Query("SELECT * FROM bag_mapping_sessions WHERE status = 'IN_PROGRESS' ORDER BY id DESC LIMIT 1")
    suspend fun findInProgress(): BagMappingSessionEntity?

    @Query("SELECT * FROM bag_mapping_sessions WHERE status = 'IN_PROGRESS' ORDER BY id DESC LIMIT 1")
    fun observeInProgress(): Flow<BagMappingSessionEntity?>

    @Query("SELECT * FROM bag_mapping_sessions WHERE status = 'COMPLETED' ORDER BY completedAtMs DESC, id DESC LIMIT 1")
    fun observeLatestCompleted(): Flow<BagMappingSessionEntity?>

    @Query("SELECT * FROM bag_mapping_sessions WHERE status = 'COMPLETED' ORDER BY completedAtMs DESC, id DESC")
    fun observeHistory(): Flow<List<BagMappingSessionEntity>>

    @Query("UPDATE bag_mapping_sessions SET status = '${BagMappingStatus.COMPLETED}', completedAtMs = :completedAtMs WHERE id = :id")
    suspend fun complete(id: Long, completedAtMs: Long)
}
