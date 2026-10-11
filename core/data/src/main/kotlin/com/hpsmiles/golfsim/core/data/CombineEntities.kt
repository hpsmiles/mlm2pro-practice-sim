package com.hpsmiles.golfsim.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "combine_sessions")
data class CombineSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val combineId: String,
    val combineName: String,
    val startedAtMs: Long,
    val completedAtMs: Long?,
    val source: Int,
    val layoutVersion: Int,
)

@Entity(tableName = "combine_shots", indices = [Index("sessionId")])
data class CombineShotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val shotIndex: Int,
    val stationIndex: Int,
    val restLie: String,
    val restDistanceM: Double,
    /** Rest position in hole metres (ball-flight [ShotResult.restX]); null on rows written before Room v7. */
    val restXM: Double? = null,
    /** Rest position in hole metres (ball-flight [ShotResult.restY]); null on rows written before Room v7. */
    val restYM: Double? = null,
)
