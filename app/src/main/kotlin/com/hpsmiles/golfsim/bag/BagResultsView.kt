package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity

/** Which chart the results page leads with (2026-10-16 redesign). */
enum class BagResultsView { SIDE, TOP_DOWN }

/**
 * Which distance the new views and the gap analysis use. CARRY is the gapping
 * truth (total varies with ground conditions), so it is the default; TOTAL runs
 * the ball through the rollout and is what the user picks when they want to
 * see the whole shot.
 */
enum class DistanceBasis { CARRY, TOTAL }

/**
 * The persisted distance cache for this shot under [basis]. Every number on the
 * page — arcs, dots, labels, the summary strip and the gap block — comes from
 * here, never from a fresh simulation, so the page can never disagree with
 * itself.
 */
fun BagMappingShotEntity.distanceFor(basis: DistanceBasis): Double = when (basis) {
    DistanceBasis.CARRY -> carryM
    DistanceBasis.TOTAL -> totalM
}
