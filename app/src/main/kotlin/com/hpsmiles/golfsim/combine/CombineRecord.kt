package com.hpsmiles.golfsim.combine

enum class CombineRecordOutcome { FIRST_SCORE, NEW_RECORD, OFF_RECORD }

data class CombineRecordComparison(
    val outcome: CombineRecordOutcome,
    val best: Double?,
    val delta: Double,
)

/** Lower overall HC is better. */
object CombineRecord {
    fun compare(previousBest: Double?, est: Double): CombineRecordComparison = when {
        previousBest == null -> CombineRecordComparison(CombineRecordOutcome.FIRST_SCORE, null, 0.0)
        est < previousBest -> CombineRecordComparison(CombineRecordOutcome.NEW_RECORD, previousBest, previousBest - est)
        else -> CombineRecordComparison(CombineRecordOutcome.OFF_RECORD, previousBest, est - previousBest)
    }

    fun headline(comparison: CombineRecordComparison): String = when (comparison.outcome) {
        CombineRecordOutcome.FIRST_SCORE -> "FIRST SCORE"
        CombineRecordOutcome.NEW_RECORD -> "NEW RECORD"
        CombineRecordOutcome.OFF_RECORD -> "OFF YOUR BEST"
    }

    fun detail(comparison: CombineRecordComparison): String = when (comparison.outcome) {
        CombineRecordOutcome.FIRST_SCORE -> "First completed combine"
        CombineRecordOutcome.NEW_RECORD -> "Previous best ${CombineFormats.formatHc(comparison.best!!)}"
        CombineRecordOutcome.OFF_RECORD ->
            "Best ${CombineFormats.formatHc(comparison.best!!)} · ${CombineFormats.oneDp(comparison.delta)} off"
    }
}
