package com.hpsmiles.golfsim.games

/** Summary handed to SessionRepository.saveGameResult once per completed game. */
data class GameResultPayload(
    val mode: String,
    val difficulty: String?,
    val targetM: Double,
    val score: Int,
)
