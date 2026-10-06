// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/BatteryIndicator.kt
package com.hpsmiles.golfsim.core.designsystem

/** Clamped battery readout state; the readout renders entirely from this. */
internal data class BatteryDisplay(val percent: Int, val low: Boolean)

/**
 * Maps a raw EVENTS 0x03 battery byte to its display state: clamped to
 * 0..100 (semantics "believed percent", formally unverified) and low
 * strictly below 20. Pure so the JVM test pins the boundary.
 */
internal fun batteryDisplay(percent: Int): BatteryDisplay {
    val clamped = percent.coerceIn(0, 100)
    return BatteryDisplay(percent = clamped, low = clamped < 20)
}
