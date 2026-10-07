package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.data.record.ClubType
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure plan-ordering helper (spec item 2): wedge-first = reversed repo order, random = seeded permutation. */
class BagPlanOrderTest {
    private val plan = listOf("DRIVER", "3W", "5i", "7i", "PW", "LW").map { BagPlanClub(it, ClubType.IRON) }

    @Test
    fun `wedge first reverses the bag-order plan`() {
        assertEquals(plan.reversed(), orderPlan(plan, BagOrderMode.WEDGE_FIRST))
    }

    @Test
    fun `random returns a permutation and is deterministic per seed`() {
        val a = orderPlan(plan, BagOrderMode.RANDOM, Random(42))
        val b = orderPlan(plan, BagOrderMode.RANDOM, Random(42))
        assertEquals(a, b)
        assertEquals(plan.map { it.name }.sorted(), a.map { it.name }.sorted())
    }

    @Test
    fun `random differs across seeds for realistic bags`() {
        assertTrue(orderPlan(plan, BagOrderMode.RANDOM, Random(1)) != orderPlan(plan, BagOrderMode.RANDOM, Random(2)))
    }
}
