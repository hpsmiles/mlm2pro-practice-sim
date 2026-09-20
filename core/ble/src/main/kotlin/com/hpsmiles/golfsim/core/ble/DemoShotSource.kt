// core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/DemoShotSource.kt
package com.hpsmiles.golfsim.core.ble

import kotlin.random.Random

/**
 * Deterministic demo source: cycles Driver / 5-iron / 7-iron / PW with
 * ±9% jitter on speed/spin/launch and bounded directional noise. Same
 * seed always yields the same sequence (JVM unit-testable).
 *
 * Phase A placeholder until the live BLE source lands in Phase B/C.
 */
class DemoShotSource(
    seed: Long = DEFAULT_SEED,
) : ShotSource {

    private val random = Random(seed)
    private var nextClub = 0

    override fun nextShot(): BallData {
        val club = CLUBS[nextClub]
        nextClub = (nextClub + 1) % CLUBS.size

        val ballSpeed = club.ballSpeedMps * jitter()
        val spin = club.spinRpm * jitter()
        val vla = club.vlaDeg * jitter()
        val hla = random.nextDouble() * 5.0 - 2.5          // ±2.5°
        val axis = random.nextDouble() * 14.0 - 7.0        // ±7.0°
        // Plan deviation (mechanical): BallData's shipped constructor parameter
        // names (M1 API) are clubHeadSpeed/ballSpeed/launchDirection/
        // launchAngle/spinAxis/totalSpin — mapped 1:1 from the plan's
        // clubHeadSpeedMps/ballSpeedMps/hlaDeg/vlaDeg/spinAxisDeg/totalSpinRpm.
        return BallData(
            clubHeadSpeed = ballSpeed / SMASH_FACTOR,
            ballSpeed = ballSpeed,
            launchDirection = hla,
            launchAngle = vla,
            spinAxis = axis,
            totalSpin = spin.toInt(),
            unknown1 = 0,
            unknown2 = 0,
        )
    }

    /** Multiplicative jitter in [0.91, 1.09]. */
    private fun jitter(): Double = 1.0 + (random.nextDouble() * JITTER_SPAN - JITTER)

    private data class DemoClub(
        val ballSpeedMps: Double,
        val spinRpm: Double,
        val vlaDeg: Double,
    )

    companion object {
        const val DEFAULT_SEED = 42L
        private const val JITTER = 0.09
        private const val JITTER_SPAN = 2 * JITTER
        private const val SMASH_FACTOR = 1.49
        private val CLUBS = listOf(
            DemoClub(ballSpeedMps = 75.0, spinRpm = 2500.0, vlaDeg = 12.0), // Driver
            DemoClub(ballSpeedMps = 60.0, spinRpm = 5300.0, vlaDeg = 15.0), // 5-iron
            DemoClub(ballSpeedMps = 55.0, spinRpm = 7100.0, vlaDeg = 16.5), // 7-iron
            DemoClub(ballSpeedMps = 46.0, spinRpm = 9200.0, vlaDeg = 24.5), // PW
        )
    }
}
