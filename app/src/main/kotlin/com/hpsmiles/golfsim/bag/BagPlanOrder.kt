package com.hpsmiles.golfsim.bag

/** Bag-mapping test order chosen on the intro screen (spec item 2). */
enum class BagOrderMode {
    /** Shortest club first (LW → driver), ascending to driver. */
    WEDGE_FIRST,
    /** Full plan shuffled (kotlin.random, default seed). */
    RANDOM,
}
