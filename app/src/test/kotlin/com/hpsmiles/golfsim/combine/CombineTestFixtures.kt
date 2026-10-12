package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.scoring.Lie

/** Shared shot fixtures for combine scoring tests and the fitting harness (spec §7). */
internal val goldenFacts: List<CombineShotFacts> = listOf(
    CombineShotFacts(0, Lie.GREEN, 1.0),
    CombineShotFacts(1, Lie.GREEN, 6.0),
    CombineShotFacts(6, Lie.GREEN, 7.0),
    CombineShotFacts(7, Lie.GREEN, 8.0),
    CombineShotFacts(12, Lie.GREEN, 10.0),
    CombineShotFacts(13, Lie.GREEN, 12.0),
)

internal val driverDemoFacts: List<CombineShotFacts> = listOf(
    CombineShotFacts(4, Lie.FAIRWAY, 74.48),
    CombineShotFacts(5, Lie.FAIRWAY, 138.55),
    CombineShotFacts(10, Lie.FAIRWAY, 184.30),
    CombineShotFacts(11, Lie.FAIRWAY, 205.57),
    CombineShotFacts(16, Lie.FAIRWAY, 102.32),
    CombineShotFacts(17, Lie.FAIRWAY, 169.54),
)
