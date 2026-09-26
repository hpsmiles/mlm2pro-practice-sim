package com.hpsmiles.golfsim.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hpsmiles.golfsim.core.data.entity.ShotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShotDao {

    @Insert
    suspend fun insert(shot: ShotEntity): Long

    @Query("SELECT * FROM shots WHERE sessionId = :sessionId ORDER BY seq")
    fun observeShots(sessionId: Long): Flow<List<ShotEntity>>

    @Query("SELECT * FROM shots WHERE sessionId = :sessionId ORDER BY seq")
    suspend fun shotsForSession(sessionId: Long): List<ShotEntity>

    @Query("SELECT COUNT(*) FROM shots WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int

    @Query("UPDATE shots SET clubName = :clubName WHERE id IN (:ids)")
    suspend fun retagShotIds(ids: List<Long>, clubName: String?)

    @Query("DELETE FROM shots")
    suspend fun deleteAll()
}
