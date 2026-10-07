package com.hpsmiles.golfsim.bag

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.physics.BallFlightEngine
import com.hpsmiles.golfsim.core.physics.Environment
import com.hpsmiles.golfsim.core.physics.LaunchConditions
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.physics.Surface
import com.hpsmiles.golfsim.core.physics.UniformSurface

/** One club in the guided plan (snapshot — the clubs-table id is irrelevant here). */
data class BagPlanClub(val name: String, val type: ClubType)

/** A computed mapping shot handed to AppRoot for write-through persistence. */
data class BagMappedShot(
    val clubName: String,
    val clubType: ClubType,
    val timestampMs: Long,
    val ballData: BallData,
    val launch: LaunchConditions,
    val result: ShotResult,
    val carryM: Double,
    val totalM: Double,
)

/**
 * Guided collection state holder (games pattern: plain Compose state,
 * injectable clock — spec §4). Holds ONLY session-ephemeral guidance state:
 * the plan snapshot, current club, gate-prompt latch, and the in-memory
 * "no read" counter. The shots themselves are Room truth — the UI renders
 * from repository flows and every accepted shot is write-through persisted
 * by AppRoot, so kill + resume reconstructs everything (spec §4/§9).
 */
class BagMappingCollector {

    val currentIndex = mutableIntStateOf(0)
    /** ASK_MORE prompt currently visible for the current club (spec §5). */
    val gatePrompt = mutableStateOf(false)
    /** Club the player answered HIT MORE for — suppresses re-prompting until they advance. */
    val moreGrantedFor = mutableStateOf<String?>(null)
    val noReadCount = mutableIntStateOf(0)
    /** Bumps on collection-flow state changes so keyed UI effects re-run deterministically. */
    val tick = mutableIntStateOf(0)

    /** Injectable clock (ms). AppRoot can leave the default. */
    var clockMs: () -> Long = { System.currentTimeMillis() }

    /**
     * Injectable simulation seam. Mapping renders carry (surface-independent)
     * and total; the default simulates onto plain fairway. Settings surfaces
     * are deliberately NOT wired: carry is the gapping metric and must not
     * move with the range's green/turf choices (spec §6).
     */
    var simulator: (LaunchConditions) -> ShotResult = { launch ->
        BallFlightEngine.simulate(launch, Environment(), UniformSurface(Surface.FAIRWAY_NORMAL))
    }

    var sessionId: Long = -1L
        private set
    var plan: List<BagPlanClub> = emptyList()
        private set

    val currentClub: BagPlanClub? get() = plan.getOrNull(currentIndex.intValue)

    /** True once the index runs past the last club — AppRoot then completes the session. */
    val done: Boolean get() = plan.isNotEmpty() && currentIndex.intValue >= plan.size

    fun begin(sessionId: Long, plan: List<BagPlanClub>) = beginAt(sessionId, plan, 0)

    /** Resume: AppRoot derives the club index from the persisted shots. */
    fun beginAt(sessionId: Long, plan: List<BagPlanClub>, clubIndex: Int) {
        this.sessionId = sessionId
        this.plan = plan
        currentIndex.intValue = clubIndex.coerceIn(0, maxOf(0, plan.size - 1))
        gatePrompt.value = false
        moreGrantedFor.value = null
        noReadCount.intValue = 0
        lastNoReadMs = 0L
        tick.intValue++
    }

    /**
     * One decoded measurement → payload for write-through persistence.
     * Null on a LaunchConditions guard violation (bad decode — never stored,
     * M4b discipline) or when there is no club to collect for.
     */
    fun add(ballData: BallData): BagMappedShot? {
        val club = currentClub ?: return null
        val launch = try {
            LaunchConditions(
                ballSpeedMps = ballData.ballSpeed,
                launchAngleDeg = ballData.launchAngle,
                spinRpm = ballData.totalSpin,
                spinAxisDeg = ballData.spinAxis,
                launchDirDeg = ballData.launchDirection,
            )
        } catch (_: IllegalArgumentException) {
            return null
        }
        val result = simulator(launch)
        tick.intValue++
        return BagMappedShot(club.name, club.type, clockMs(), ballData, launch, result, result.carryM, result.totalM)
    }

    /**
     * Coalesced "no read" for the guided pill (same 500 ms window discipline
     * as RangeSession.markMisread — one real mishit emits BOTH the EVENTS
     * alert and the all-zero sentinel). In-memory only; resets on resume,
     * like the range pill (RangeSession.restore). Returns true when counted.
     */
    fun markNoRead(atMs: Long = clockMs()): Boolean {
        if (atMs - lastNoReadMs < NO_READ_COALESCE_MS) return false
        lastNoReadMs = atMs
        noReadCount.intValue++
        return true
    }

    /** Player chose HIT MORE: hide the prompt, latch the club. */
    fun hitMore() {
        gatePrompt.value = false
        moreGrantedFor.value = currentClub?.name
        tick.intValue++
    }

    /** Player accepted the result at the prompt (or the UI auto-advances on ACCEPT). */
    fun acceptResult() {
        gatePrompt.value = false
        advance()
    }

    /** Moves to the next club; clears prompt + latch. Past the last club → [done]. */
    fun advance() {
        gatePrompt.value = false
        moreGrantedFor.value = null
        currentIndex.intValue++
        tick.intValue++
    }

    /** Clears all session-ephemeral state (after completion or abandonment). */
    fun reset() {
        sessionId = -1L
        plan = emptyList()
        currentIndex.intValue = 0
        gatePrompt.value = false
        moreGrantedFor.value = null
        noReadCount.intValue = 0
        lastNoReadMs = 0L
        tick.intValue++
    }

    private var lastNoReadMs = 0L

    companion object {
        const val NO_READ_COALESCE_MS = 500L
    }
}
