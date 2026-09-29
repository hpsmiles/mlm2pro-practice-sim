package com.hpsmiles.golfsim.games

import kotlin.math.hypot

enum class Difficulty { EASY, MEDIUM, HARD }

/**
 * Target Practice scoring bands (spec section 4 table). Miss distance is measured
 * from the REST position (after bounce/roll), not carry.
 */
object TargetPracticeScoring {

    const val SHOTS_PER_GAME = 5
    const val MAX_SCORE = 125

    /** Points for landing within maxMissM of the pin; bands are inclusive. */
    data class Band(val points: Int, val maxMissM: Double)

    fun bands(difficulty: Difficulty): List<Band> = when (difficulty) {
        Difficulty.EASY -> listOf(Band(25, 3.0), Band(15, 7.0), Band(10, 12.0), Band(5, 18.0))
        Difficulty.MEDIUM -> listOf(Band(25, 2.0), Band(15, 5.0), Band(10, 10.0), Band(5, 15.0))
        Difficulty.HARD -> listOf(Band(25, 1.5), Band(15, 3.5), Band(10, 6.0), Band(5, 10.0))
    }

    /** The radius of the outermost scoring band for the given difficulty. */
    fun outerBandRadiusM(difficulty: Difficulty): Double = bands(difficulty).last().maxMissM

    fun points(missM: Double, difficulty: Difficulty): Int =
        bands(difficulty).firstOrNull { missM <= it.maxMissM }?.points ?: 0

    fun missDistanceM(restX: Double, restY: Double, targetM: Double): Double =
        hypot(restX, restY - targetM)
}
