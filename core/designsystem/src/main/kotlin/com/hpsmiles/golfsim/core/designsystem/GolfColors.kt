// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfColors.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Performance Dark palette (M3 spec §5). Two accents: Teal = structure/history/targets,
 * Amber = the live moment only (last shot, landing, hot values). Amber is RESERVED;
 * it must never be used as a category or surface color.
 */
object GolfColors {
    val Base = Color(0xFF0E1114)
    val Panel = Color(0xFF0B0E11)
    val Card = Color(0xFF151A1F)
    val Line = Color(0xFF28303A)
    val HoleFairway = Color(0xFF161B21)

    val TextPrimary = Color(0xFFE7EBEE)
    val TextSecondary = Color(0xFF8FA0AC)
    val TextMuted = Color(0xFF5C6873)

    val Teal = Color(0xFF3FA7A0)
    val Teal55 = Color(0x8C3FA7A0)
    val Teal40 = Color(0x663FA7A0)

    val Amber = Color(0xFFF2A93B)
    val AmberHalo = Color(0x33F2A93B)
    val AmberGlow = Color(0x99F2A93B)

    val BleArmedGreen = Color(0xFF7BC96F)
    val AlertRed = Color(0xFFE86A5E)

    /**
     * Categorical comparison hues (M7 A/B testing + club fitting), assigned by
     * club position per design notes §0.1: letter = Nth distinct club, so the
     * values are ordered for consecutive hue separation on-device
     * (consecutive separations 173°/67°/179°/64°/179°; A teal = the reference).
     * Amber stays RESERVED for the live moment — never a category hue here.
     */
    object Comparison {
        val A = Color(0xFF3FA7A0) // teal — club 1
        val B = Color(0xFFE85C7A) // rose — club 2
        val C = Color(0xFFF0D64A) // yellow — club 3
        val D = Color(0xFF6E7BF2) // indigo — club 4 (was blue 0xFF5B9DF9)
        val E = Color(0xFFD66FD8) // magenta — club 5
        val F = Color(0xFF6FD86F) // green — club 6
    }
}
