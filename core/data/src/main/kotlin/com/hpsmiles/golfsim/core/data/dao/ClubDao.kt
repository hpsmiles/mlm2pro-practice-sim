package com.hpsmiles.golfsim.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hpsmiles.golfsim.core.data.entity.ClubEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClubDao {

    @Insert
    suspend fun insert(club: ClubEntity): Long

    @Query("SELECT * FROM clubs ORDER BY sortOrder")
    fun observeClubs(): Flow<List<ClubEntity>>

    @Query("SELECT COUNT(*) FROM clubs")
    suspend fun count(): Int

    @Query("SELECT * FROM clubs WHERE name = :name")
    suspend fun findByName(name: String): ClubEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM clubs")
    suspend fun maxSortOrder(): Int

    @Query("UPDATE clubs SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM clubs WHERE id = :id")
    suspend fun delete(id: Long)

    /** M5x E3: single purge rule — every END SESSION deletes TEST clubs. */
    @Query("DELETE FROM clubs WHERE isTemp = 1")
    suspend fun deleteTempClubs()

    @Query("DELETE FROM clubs")
    suspend fun deleteAll()
}
