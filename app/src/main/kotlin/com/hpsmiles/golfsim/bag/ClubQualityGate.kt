package com.hpsmiles.golfsim.bag

import com.hpsmiles.golfsim.core.data.bag.BagMappingStats

/**
 * M6 quality gate (spec §5): once a club reaches [TARGET_KEPT] kept shots,
 * the player is asked to hit more iff the club is unreliable — at least
 * [MAX_FILTERED_FOR_PROMPT] filtered shots, or the kept carry window
 * exceeding [MAX_WINDOW_FRACTION] of the kept median. Hard cap [MAX_KEPT]:
 * the gate never prompts past it (the player can always accept the result).
 * Constants live here so UI copy and tests share the exact numbers.
 */
object ClubQualityGate {
    const val TARGET_KEPT = 5
    const val MAX_KEPT = 15
    const val MAX_FILTERED_FOR_PROMPT = 3
    const val MAX_WINDOW_FRACTION = 0.20

    enum class Verdict { ACCEPT, ASK_MORE }

    /**
     * Call whenever the kept set changes while collecting. Below
     * [TARGET_KEPT] the gate is dormant (ACCEPT; the caller distinguishes
     * "keep collecting" by kept count). Deterministic; pure.
     */
    fun evaluate(keptCarriesM: List<Double>, filteredCount: Int): Verdict {
        val keptCount = keptCarriesM.size
        if (keptCount < TARGET_KEPT) return Verdict.ACCEPT
        if (keptCount >= MAX_KEPT) return Verdict.ACCEPT
        if (filteredCount >= MAX_FILTERED_FOR_PROMPT) return Verdict.ASK_MORE
        if (keptCount < 2) return Verdict.ACCEPT
        val median = BagMappingStats.median(keptCarriesM) ?: return Verdict.ACCEPT
        if (median <= 0.0) return Verdict.ACCEPT
        val window = keptCarriesM.max() - keptCarriesM.min()
        return if (window > MAX_WINDOW_FRACTION * median) Verdict.ASK_MORE else Verdict.ACCEPT
    }
}
