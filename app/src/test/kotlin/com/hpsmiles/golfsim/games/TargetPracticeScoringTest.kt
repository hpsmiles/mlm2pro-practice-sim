package com.hpsmiles.golfsim.games

import org.junit.Assert.assertEquals
import org.junit.Test

class TargetPracticeScoringTest {

    @Test
    fun `medium bands per spec table`() {
        val bands = TargetPracticeScoring.bands(Difficulty.MEDIUM)
        assertEquals(listOf(25 to 2.0, 15 to 5.0, 10 to 10.0, 5 to 15.0), bands.map { it.points to it.maxMissM })
    }

    @Test
    fun `band edges are inclusive`() {
        assertEquals(25, TargetPracticeScoring.points(2.0, Difficulty.MEDIUM))
        assertEquals(15, TargetPracticeScoring.points(2.0001, Difficulty.MEDIUM))
        assertEquals(15, TargetPracticeScoring.points(5.0, Difficulty.MEDIUM))
        assertEquals(5, TargetPracticeScoring.points(15.0, Difficulty.MEDIUM))
        assertEquals(0, TargetPracticeScoring.points(15.0001, Difficulty.MEDIUM))
    }

    @Test
    fun `easy and hard inner bands per spec table`() {
        assertEquals(3.0, TargetPracticeScoring.bands(Difficulty.EASY).first().maxMissM, 0.0)
        assertEquals(1.5, TargetPracticeScoring.bands(Difficulty.HARD).first().maxMissM, 0.0)
        assertEquals(10.0, TargetPracticeScoring.bands(Difficulty.HARD).last().maxMissM, 0.0)
    }

    @Test
    fun `miss distance from rest position`() {
        assertEquals(2.0, TargetPracticeScoring.missDistanceM(restX = 0.0, restY = 142.0, targetM = 140.0), 1e-9)
        assertEquals(3.0, TargetPracticeScoring.missDistanceM(restX = 3.0, restY = 140.0, targetM = 140.0), 1e-9)
    }
}
