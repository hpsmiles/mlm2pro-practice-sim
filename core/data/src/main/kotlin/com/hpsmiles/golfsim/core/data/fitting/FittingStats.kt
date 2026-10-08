package com.hpsmiles.golfsim.core.data.fitting

import com.hpsmiles.golfsim.core.data.bag.BagMappingStats
import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.data.record.ClubType
import kotlin.math.abs

/**
 * Pure M7 fitting math (spec §4): per-club aggregates over KEPT shots,
 * per-shot smash, offline stats, signed deltas with noise bands, and the
 * shared duff rule over fitting rows. No framework types; deterministic.
 * The comparison club set derives from shot snapshots, ordered by first
 * appearance (spec §2) — stable under kill/resume and temp-club purge.
 */
object FittingStats {

    // Delta noise bands (spec §4): |Δ| below the band renders dimmed.
    const val NOISE_CHS_MPS = 0.5
    const val NOISE_BALL_SPEED_MPS = 0.7
    const val NOISE_SMASH = 0.010
    const val NOISE_CARRY_M = 2.0
    const val NOISE_TOTAL_M = 2.0
    const val NOISE_LAUNCH_DEG = 0.5
    const val NOISE_DIR_DEG = 0.5
    const val NOISE_SPIN_RPM = 150.0
    const val NOISE_SPIN_AXIS_DEG = 2.0
    const val NOISE_OFFLINE_M = 1.0

    /** Club identity snapshotted per shot; the comparison key. */
    data class ClubKey(val id: Long, val name: String, val type: ClubType, val wasTemp: Boolean)

    /** Distinct clubs in first-appearance order (deduped). */
    fun clubOrder(shots: List<FittingShotEntity>): List<ClubKey> {
        val seen = LinkedHashMap<Long, ClubKey>()
        for (s in shots) {
            seen.getOrPut(s.clubId) {
                ClubKey(s.clubId, s.clubName, ClubType.fromName(s.clubType), s.clubWasTemp)
            }
        }
        return seen.values.toList()
    }

    data class ClubSummary(
        val key: ClubKey,
        val kept: Int,
        val excluded: Int,
        val chs: Double?,
        val ballSpeed: Double?,
        val smash: Double?,
        val carry: Double?,
        val carrySigma: Double?,
        val total: Double?,
        val totalSigma: Double?,
        val launch: Double?,
        val dir: Double?,
        val spin: Double?,
        val spinAxis: Double?,
        val offlineAvg: Double?,
        val offlineWorst: Double?,
    )

    /** Aggregates over one club's KEPT shots only (excluded drops everywhere). */
    fun summarize(key: ClubKey, shots: List<FittingShotEntity>): ClubSummary {
        val mine = shots.filter { it.clubId == key.id }
        val kept = mine.filter { !it.excluded }
        val chs = BagMappingStats.distribution(kept.map { it.clubHeadSpeedMps })
        val ball = BagMappingStats.distribution(kept.map { it.ballSpeedMps })
        val carry = BagMappingStats.distribution(kept.map { it.carryM })
        val total = BagMappingStats.distribution(kept.map { it.totalM })
        val launch = BagMappingStats.distribution(kept.map { it.launchAngleDeg })
        val dir = BagMappingStats.distribution(kept.map { it.launchDirDeg })
        val spin = BagMappingStats.distribution(kept.map { it.totalSpinRpm.toDouble() })
        val axis = BagMappingStats.distribution(kept.map { it.spinAxisDeg })
        val offline = kept.map { abs(it.sideM) }
        val smashes = kept.filter { it.clubHeadSpeedMps > 0.0 }.map { it.ballSpeedMps / it.clubHeadSpeedMps }
        return ClubSummary(
            key = key,
            kept = kept.size,
            excluded = mine.size - kept.size,
            chs = chs?.mean,
            ballSpeed = ball?.mean,
            smash = BagMappingStats.distribution(smashes)?.mean,
            carry = carry?.mean,
            carrySigma = carry?.sigma,
            total = total?.mean,
            totalSigma = total?.sigma,
            launch = launch?.mean,
            dir = dir?.mean,
            spin = spin?.mean,
            spinAxis = axis?.mean,
            offlineAvg = if (offline.isEmpty()) null else offline.average(),
            offlineWorst = offline.maxOrNull(),
        )
    }

    data class DeltaVerdict(val delta: Double, val significant: Boolean)

    /** Signed delta (other − baseline) + noise verdict; null when either side lacks data. */
    fun delta(other: Double?, baseline: Double?, noise: Double): DeltaVerdict? {
        if (other == null || baseline == null) return null
        val d = other - baseline
        return DeltaVerdict(d, abs(d) >= noise)
    }

    /** Shared duff rule (bag constants) over fitting rows: ball speed < 85 % of the median of ALL the club's shots, dormant under 3. */
    fun applyDuffFilter(shots: List<FittingShotEntity>): List<BagMappingStats.FilterVerdict> {
        if (shots.size < BagMappingStats.DUFF_MIN_SHOTS) {
            return shots.map { BagMappingStats.FilterVerdict(it.id, filtered = false, reason = null) }
        }
        val medianBallSpeed = BagMappingStats.median(shots.map { it.ballSpeedMps })
            ?: return shots.map { BagMappingStats.FilterVerdict(it.id, filtered = false, reason = null) }
        val cutoff = medianBallSpeed * BagMappingStats.DUFF_BALL_SPEED_FRACTION
        return shots.map {
            if (it.ballSpeedMps < cutoff) {
                BagMappingStats.FilterVerdict(it.id, filtered = true, BagMappingStats.REASON_DUFF_LOW_BALL_SPEED)
            } else {
                BagMappingStats.FilterVerdict(it.id, filtered = false, reason = null)
            }
        }
    }
}
