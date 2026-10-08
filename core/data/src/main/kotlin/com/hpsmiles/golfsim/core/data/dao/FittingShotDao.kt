package com.hpsmiles.golfsim.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FittingShotDao {
    @Insert
    suspend fun insert(shot: FittingShotEntity): Long

    @Query("SELECT * FROM fitting_shots WHERE sessionId = :sessionId ORDER BY id")
    fun observeBySession(sessionId: Long): Flow<List<FittingShotEntity>>

    @Query("UPDATE fitting_shots SET excluded = :excluded WHERE id IN (:ids)")
    suspend fun setExcluded(ids: List<Long>, excluded: Boolean)
}
