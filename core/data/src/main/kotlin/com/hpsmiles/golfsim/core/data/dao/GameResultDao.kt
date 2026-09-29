package com.hpsmiles.golfsim.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hpsmiles.golfsim.core.data.entity.GameResultEntity

@Dao
interface GameResultDao {

    @Insert
    suspend fun insert(result: GameResultEntity): Long

    @Query("SELECT * FROM game_results ORDER BY playedAtEpochMs DESC, id DESC")
    suspend fun getAll(): List<GameResultEntity>
}
