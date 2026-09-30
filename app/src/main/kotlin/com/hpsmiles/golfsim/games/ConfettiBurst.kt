package com.hpsmiles.golfsim.games

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import kotlin.random.Random

/**
 * One-shot confetti burst for a NEW high score (spec 2026-09-30 §3.4). Drawn
 * with the games' manual withFrameNanos loop (no Animatable), ~2.5 s, gravity
 * + horizontal drift. Amber stays reserved for the live tracer, so the
 * palette is teal / white / gold.
 */
@Composable
fun ConfettiBurst(
    modifier: Modifier = Modifier,
    durationMs: Float = 2500f,
    particleCount: Int = 64,
) {
    val palette = remember {
        listOf(GolfColors.Teal, GolfColors.TextPrimary, Color(0xFFF0D64A), GolfColors.Teal55)
    }
    val particles = remember {
        val rnd = Random(System.nanoTime())
        List(particleCount) {
            Particle(
                startX = rnd.nextFloat(),
                drift = -1f + rnd.nextFloat() * 2f,
                speed = 0.6f + rnd.nextFloat() * 0.9f,
                color = palette[rnd.nextInt(palette.size)],
                size = 5f + rnd.nextFloat() * 7f,
            )
        }
    }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        t = 0f
        while (t < 1f) {
            val now = withFrameNanos { it }
            t = (t + (now - last) / 1_000_000f / durationMs).coerceAtMost(1f)
            last = now
        }
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = t
        if (p >= 1f) return@Canvas
        val alpha = (1f - p).coerceIn(0f, 1f)
        particles.forEach { c ->
            val cx = w * c.startX + c.drift * w * 0.30f * c.speed * p
            val cy = h * 0.12f + 0.5f * 1400f * c.speed * p * p
            if (cy > h + 24f) return@forEach
            drawCircle(c.color.copy(alpha = alpha), c.size, Offset(cx, cy))
        }
    }
}

private data class Particle(
    val startX: Float,
    val drift: Float,
    val speed: Float,
    val color: Color,
    val size: Float,
)
