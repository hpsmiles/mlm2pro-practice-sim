// core/ble/src/main/kotlin/com/hpsmiles/golfsim/core/ble/ShotSource.kt
package com.hpsmiles.golfsim.core.ble

/**
 * A source of decoded ball measurements. The range UI consumes this
 * interface only — demo and (later) live BLE sources are interchangeable.
 */
fun interface ShotSource {
    fun nextShot(): BallData
}
