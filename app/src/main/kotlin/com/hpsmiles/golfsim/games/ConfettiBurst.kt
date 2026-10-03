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
 * with the games' manual withFrameNanos loop (no Animatable), ~2.8 s, gravity
 * + horizontal drift + a delayed second wave. Amber stays reserved for the live
 * tracer, so the palette is teal / white / gold.
 */
@Composable
fun ConfettiBurst(
    modifier: Modifier = Modifier,
    durationMs: Float = 2800f,
    particleCount: Int = 128,
) {
    val palette = remember {
        listOf(GolfColors.Teal, GolfColors.TextPrimary, Color(0xFFF0D64A), GolfColors.Teal55)
    }
    val particles = remember {
        val rnd = Random(System.nanoTime())
        List(particleCount) { i ->
            val secondWave = i >= particleCount * 2 / 3
            Particle(
                startX = 0.25f + rnd.nextFloat() * 0.5f,
                startY = 0.32f + rnd.nextFloat() * 0.18f,
                drift = -1f + rnd.nextFloat() * 2f,
                speed = 0.7f + rnd.nextFloat() * 0.8f,
                rise = 0.10f + rnd.nextFloat() * 0.20f,
                color = palette[rnd.nextInt(palette.size)],
                size = 8f + rnd.nextFloat() * 14f,
                delay = if (secondWave) 0.15f + rnd.nextFloat() * 0.20f else 0f,
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
        particles.forEach { c ->
            if (p < c.delay) return@forEach
            val lp = (p - c.delay) / (1f - c.delay)
            val alpha = (1f - lp).coerceIn(0f, 1f)
            val cx = w * c.startX + c.drift * w * 0.35f * c.speed * lp
            val cy = h * c.startY - h * c.rise * lp + 0.5f * 1400f * c.speed * lp * lp
            if (cy > h + 32f) return@forEach
            drawCircle(c.color.copy(alpha = alpha), c.size, Offset(cx, cy))
        }
    }
}

private data class Particle(
    val startX: Float,
    val startY: Float,
    val drift: Float,
    val speed: Float,
    val rise: Float,
    val color: Color,
    val size: Float,
    val delay: Float,
)
