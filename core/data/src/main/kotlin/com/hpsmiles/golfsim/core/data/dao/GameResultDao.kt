package com.hpsmiles.golfsim.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hpsmiles.golfsim.core.data.entity.GameResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameResultDao {

    @Insert
    suspend fun insert(result: GameResultEntity): Long

    @Query("SELECT * FROM game_results ORDER BY playedAtEpochMs DESC, id DESC")
    suspend fun getAll(): List<GameResultEntity>

    @Query("SELECT * FROM game_results ORDER BY playedAtEpochMs DESC, id DESC")
    fun observeAll(): Flow<List<GameResultEntity>>

    /** Prior-best score (higher is better) for a record key, or null when none. */
    @Query(
        "SELECT MAX(score) FROM game_results " +
            "WHERE mode = :mode AND difficulty = :difficulty AND distanceBin = :distanceBin",
    )
    suspend fun bestHighScore(mode: String, difficulty: String, distanceBin: Int): Int?

    /** Prior-best score (lower is better) for a record key, or null when none. */
    @Query(
        "SELECT MIN(score) FROM game_results " +
            "WHERE mode = :mode AND difficulty = :difficulty AND distanceBin = :distanceBin",
    )
    suspend fun bestLowScore(mode: String, difficulty: String, distanceBin: Int): Int?
}
