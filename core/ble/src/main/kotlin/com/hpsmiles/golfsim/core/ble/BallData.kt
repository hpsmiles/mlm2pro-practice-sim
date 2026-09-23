package com.hpsmiles.golfsim.core.ble

/**
 * Ball/club metrics decoded from one MLM2PRO MEASUREMENT notification.
 *
 * SI units (m/s, degrees, rpm), full precision — no rounding anywhere in the
 * decoder; the display layer (M3+) converts to mph/yards and rounds.
 *
 * Sign conventions [Verified on-device] — 2026-09-23 M4c shed session (8i,
 * shaped shots; see golden fixture mlm2pro-live-shapetest-2026-09-23):
 * launchDirection (HLA) negative = left of target / positive = right;
 * spinAxis positive = right curve (fade) / negative = left curve (draw).
 * Offsets 12–15 remain unresolved by every public reverse-engineering; they
 * ride along raw so M4 side-by-side captures can establish their meaning.
 */
data class BallData(
    val clubHeadSpeed: Double,    // m/s   — offsets 0–1, raw/10
    val ballSpeed: Double,        // m/s   — offsets 2–3, raw/10
    val launchDirection: Double,  // HLA ° — offsets 4–5, raw/10, signed; − = left of target, + = right [Verified on-device]
    val launchAngle: Double,      // VLA ° — offsets 6–7, raw/10, signed, sign preserved
    val spinAxis: Double,         // °     — offsets 8–9, raw/10, signed; + = right curve (fade), − = left curve (draw) [Verified on-device]
    val totalSpin: Int,           // rpm   — offsets 10–11, UInt16, unscaled
    val unknown1: Int,            // raw   — offsets 12–13, uninterpreted
    val unknown2: Int,            // raw   — offsets 14–15, uninterpreted
)
