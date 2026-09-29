package com.hpsmiles.golfsim.games

import org.junit.Assert.assertEquals
import org.junit.Test

class TargetPracticeRingLabelsTest {

    @Test
    fun `band points match difficulty spec`() {
        assertEquals(
            listOf(25, 15, 10, 5),
            TargetPracticeScoring.bands(Difficulty.EASY).map { it.points },
        )
        assertEquals(
            listOf(25, 15, 10, 5),
            TargetPracticeScoring.bands(Difficulty.MEDIUM).map { it.points },
        )
        assertEquals(
            listOf(25, 15, 10, 5),
            TargetPracticeScoring.bands(Difficulty.HARD).map { it.points },
        )
    }

    @Test
    fun `easy bands are widest`() {
        assertEquals(listOf(3.0, 7.0, 12.0, 18.0), TargetPracticeScoring.bands(Difficulty.EASY).map { it.maxMissM })
        assertEquals(listOf(2.0, 5.0, 10.0, 15.0), TargetPracticeScoring.bands(Difficulty.MEDIUM).map { it.maxMissM })
        assertEquals(listOf(1.5, 3.5, 6.0, 10.0), TargetPracticeScoring.bands(Difficulty.HARD).map { it.maxMissM })
    }
}
