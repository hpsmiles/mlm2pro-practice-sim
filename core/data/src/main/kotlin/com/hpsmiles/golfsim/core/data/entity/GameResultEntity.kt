package com.hpsmiles.golfsim.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Game mode discriminator for [GameResultEntity]. */
object GameModes {
    const val TARGET_PRACTICE = "TARGET_PRACTICE"
    const val BREAK_PANE = "BREAK_PANE"
}

/**
 * One summary row per completed game (spec S7). No per-shot game rows -
 * game shots never enter the range session pipeline. `score` means points
 * (max 125) for TARGET_PRACTICE and shots-taken (lower is better) for BREAK_PANE.
 */
@Entity(tableName = "game_results")
data class GameResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    /** EASY/MEDIUM/HARD; null for Break the Pane. */
    val difficulty: String?,
    /** Target distance rounded to the nearest 10 m. */
    val distanceBin: Int,
    val score: Int,
    /** Mirrors ShotSource: LIVE=0, DEMO=1. */
    val source: Int,
    val playedAtEpochMs: Long,
)
