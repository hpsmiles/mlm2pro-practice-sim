package com.hpsmiles.golfsim.combine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import com.hpsmiles.golfsim.games.GameQuickSettingsButton
import com.hpsmiles.golfsim.games.GameQuickSettingsDialog
import com.hpsmiles.golfsim.games.GameScene
import com.hpsmiles.golfsim.games.GameSpeedMult
import com.hpsmiles.golfsim.games.SpeedChipRow
import com.hpsmiles.golfsim.games.rememberGameResultOverlayGate
import com.hpsmiles.golfsim.range.FollowCam
import com.hpsmiles.golfsim.range.RangeCamera
import kotlinx.coroutines.delay

@Composable
internal fun CombinePlay(
    entry: CombineCatalogEntry,
    game: CombineGame,
    soundsEnabled: Boolean,
    onSoundsChange: (Boolean) -> Unit,
    onPlayAgain: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var speedMult by remember { mutableStateOf(GameSpeedMult.X15) }
    var playFraction by remember { mutableFloatStateOf(1f) }
    var showSettings by remember { mutableStateOf(false) }

    val lastShot = game.shots.lastOrNull()

    LaunchedEffect(game.tick, speedMult) {
        val shot = game.shots.lastOrNull() ?: return@LaunchedEffect
        val result = shot.display.shotResult
        if (result.samples.isEmpty()) {
            playFraction = FollowCam.endFraction(result).toFloat()
            return@LaunchedEffect
        }
        if (result.flightTimeSec <= 0.0) return@LaunchedEffect
        val durationMs = result.flightTimeSec * 1000.0 / speedMult.divisor
        val end = FollowCam.endFraction(result).toFloat()
        var last = withFrameNanos { it }
        playFraction = 0f
        while (playFraction < end) {
            val now = withFrameNanos { it }
            playFraction = (playFraction + ((now - last) / 1_000_000.0 / durationMs).toFloat()).coerceAtMost(end)
            last = now
        }
    }

    val showResults = rememberGameResultOverlayGate(game.complete, lastShot?.display?.shotResult, playFraction)

    if (showResults) {
        CombineResultsView(
            entry = entry,
            facts = game.allFacts,
            record = game.record,
            readOnly = false,
            onPlayAgain = onPlayAgain,
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    val takenCount = game.takenCount
    val currentIndex = takenCount.coerceAtMost(CombinePlan.SHOTS_TOTAL - 1)
    val currentStation = entry.stations[CombinePlan.stationIndexFor(currentIndex)]
    val currentVisit = CombinePlan.visitFor(currentIndex) + 1

    var bannerVisible by remember { mutableStateOf(false) }
    var bannerText by remember { mutableStateOf("") }
    LaunchedEffect(takenCount) {
        if (CombinePlan.isBannerShot(takenCount)) {
            bannerText = CombinePlan.bannerLabel(entry, takenCount)
            bannerVisible = true
            delay(2000)
            bannerVisible = false
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(GolfColors.Base)) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).clipToBounds()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wPx = constraints.maxWidth.toFloat()
                    val hPx = constraints.maxHeight.toFloat()
                    val apexVMin = GameScene.apexVMin(wPx, hPx)
                    val camera = if (lastShot == null) {
                        RangeCamera.STATIC
                    } else {
                        FollowCam.cameraAt(lastShot.display.shotResult, playFraction, apexVMin)
                    }
                    CombineCanvas(
                        camera = camera,
                        entry = entry,
                        shotIndex = currentIndex,
                        tracer = lastShot?.display,
                        playFraction = playFraction,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(GolfSpacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
                ) {
                    SpeedChipRow(selected = speedMult, onSelect = { speedMult = it })
                    Text(
                        "${currentStation.label} · VISIT $currentVisit/${CombinePlan.CYCLES}",
                        color = GolfColors.TextSecondary,
                    )
                }
                if (bannerVisible) {
                    Box(Modifier.align(Alignment.Center)) {
                        SectionCard("NEXT") {
                            Text(bannerText, color = GolfColors.Teal)
                        }
                    }
                }
                GameQuickSettingsButton(
                    onClick = { showSettings = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(GolfSpacing.Sm),
                )
            }
            CombineMetricsPanel(
                lastShot = lastShot?.display,
                entry = entry,
                takenCount = takenCount,
            )
        }
    }

    if (showSettings) {
        GameQuickSettingsDialog(
            soundsEnabled = soundsEnabled,
            onSoundsChange = onSoundsChange,
            onDismiss = { showSettings = false },
        )
    }
}
