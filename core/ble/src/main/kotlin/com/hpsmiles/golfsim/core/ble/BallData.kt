package com.hpsmiles.golfsim.core.ble

/**
 * Ball/club metrics decoded from one MLM2PRO MEASUREMENT notification.
 *
 * SI units (m/s, degrees, rpm), full precision — no rounding anywhere in the
 * decoder; the display layer (M3+) converts to mph/yards and rounds.
 *
 * Sign conventions are UNVERIFIED until live captures (M4): HLA direction and
 * fade-vs-draw spin axis pass through from the device uninterpreted. Offsets
 * 12–15 are unresolved by every public reverse-engineering; they ride along
 * raw so M4 side-by-side captures can establish their meaning.
 */
data class BallData(
    val clubHeadSpeed: Double,    // m/s   — offsets 0–1, raw/10
    val ballSpeed: Double,        // m/s   — offsets 2–3, raw/10
    val launchDirection: Double,  // HLA ° — offsets 4–5, raw/10, signed, sign preserved
    val launchAngle: Double,      // VLA ° — offsets 6–7, raw/10, signed, sign preserved
    val spinAxis: Double,         // °     — offsets 8–9, raw/10, signed, sign preserved
    val totalSpin: Int,           // rpm   — offsets 10–11, UInt16, unscaled
    val unknown1: Int,            // raw   — offsets 12–13, uninterpreted
    val unknown2: Int,            // raw   — offsets 14–15, uninterpreted
)
