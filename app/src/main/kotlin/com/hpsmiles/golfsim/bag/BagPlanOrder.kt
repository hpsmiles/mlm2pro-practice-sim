package com.hpsmiles.golfsim.bag

import kotlin.random.Random

/** Bag-mapping test order chosen on the intro screen (spec item 2). */
enum class BagOrderMode {
    /** Shortest club first (LW → driver), ascending to driver. */
    WEDGE_FIRST,
    /** Full plan shuffled (kotlin.random, default seed). */
    RANDOM,
}

/**
 * Orders the mapping plan BEFORE the session snapshot (spec item 2): the
 * ordered list is what gets persisted, so resume semantics are unchanged.
 * The repository sorts the bag long→short, so reversal = wedge first,
 * ascending to driver. RANDOM is a deterministic permutation under a seeded
 * [Random] (testable; default seed for production).
 */
fun <T> orderPlan(items: List<T>, mode: BagOrderMode, random: Random = Random.Default): List<T> = when (mode) {
    BagOrderMode.WEDGE_FIRST -> items.asReversed()
    BagOrderMode.RANDOM -> items.shuffled(random)
}
