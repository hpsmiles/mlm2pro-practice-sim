// app/src/main/kotlin/com/hpsmiles/golfsim/history/HistoryGrouping.kt
package com.hpsmiles.golfsim.history

import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ShotRecord
import kotlin.math.sqrt

/** Metres/second to mph (same constant as HistoryFormats). */
private const val MPH_PER_MS = 2.23694

/** One club's shot list (spec §5.3). `clubName == null` = the "—" group. */
data class ClubGroup(
    val clubName: String?,
    val shots: List<ShotRecord>,
) {
    /** TEST badge rule: any included-or-excluded shot's capture snapshot. */
    val wasTemp: Boolean get() = shots.any { it.clubWasTemp }
}

/** Per-club stats over NON-excluded shots. Averages null when nothing included. */
data class ClubStats(
    val count: Int,
    val avgCarryM: Double?,
    val avgTotalM: Double?,
    val avgSideM: Double?,
    val avgApexM: Double?,
    val avgBallMph: Double?,
    val avgClubMph: Double?,
    val avgSmash: Double?,
    val avgLaunchDeg: Double?,
    val avgDirDeg: Double?,
    val avgAxisDeg: Double?,
    val avgSpinRpm: Double?,
    val sigmaCarryM: Double,
    val sigmaBallMph: Double,
)

/** Bag-order first (type-first order arrives via the repository), unknown names alphabetical, "—" last. */
fun groupByClub(shots: List<ShotRecord>, bag: List<ClubRecord>): List<ClubGroup> {
    val byName = shots.groupBy { it.clubName }
    val out = ArrayList<ClubGroup>()
    val bagNames = bag.map { it.name }.toSet()
    bag.forEach { club -> byName[club.name]?.let { out += ClubGroup(club.name, it.sortedBy { s -> s.seq }) } }
    byName.keys
        .filter { it != null && it !in bagNames }
        .sortedBy { it!! }
        .forEach { out += ClubGroup(it, byName[it]!!.sortedBy { s -> s.seq }) }
    byName[null]?.let { out += ClubGroup(null, it.sortedBy { s -> s.seq }) }
    return out
}

/** Population σ over non-excluded shots (same formula as the range SESSION card). */
fun statsFor(group: ClubGroup): ClubStats {
    val incl = group.shots.filter { !it.excluded }
    fun avg(values: List<Double>): Double? = if (values.isEmpty()) null else values.average()
    fun sigma(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }
    // Club-speed dependent stats only over rows with a real (>0) club speed,
    // mirroring the range panel's "-" for unknown smash.
    val withClubSpeed = incl.filter { it.ballData.clubHeadSpeed > 0.0 }
    return ClubStats(
        count = incl.size,
        avgCarryM = avg(incl.map { it.carryM }),
        avgTotalM = avg(incl.map { it.totalM }),
        avgSideM = avg(incl.map { it.sideM }),
        avgApexM = avg(incl.map { it.apexM }),
        avgBallMph = avg(incl.map { it.ballData.ballSpeed * MPH_PER_MS }),
        avgClubMph = avg(withClubSpeed.map { it.ballData.clubHeadSpeed * MPH_PER_MS }),
        avgSmash = avg(
            withClubSpeed.map { it.ballData.ballSpeed / it.ballData.clubHeadSpeed },
        ),
        avgLaunchDeg = avg(incl.map { it.ballData.launchAngle }),
        avgDirDeg = avg(incl.map { it.ballData.launchDirection }),
        avgAxisDeg = avg(incl.map { it.ballData.spinAxis }),
        avgSpinRpm = avg(incl.map { it.ballData.totalSpin.toDouble() }),
        sigmaCarryM = sigma(incl.map { it.carryM }),
        sigmaBallMph = sigma(incl.map { it.ballData.ballSpeed * MPH_PER_MS }),
    )
}
