package com.hpsmiles.golfsim.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.audio.GameAudio
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import java.util.Locale
import kotlin.math.roundToInt

/** Which game (if any) currently owns the shot stream. Hoisted to AppRoot for dispatch. */
enum class GameMode { NONE, TARGET_PRACTICE, BREAK_PANE }

private const val DIST_MIN_M = 50.0
private const val DIST_MAX_M = 350.0
private const val DEFAULT_TARGET_M = 140.0

/**
 * Games tab: picker -> per-game setup -> play -> result. Plain when-enum
 * sub-state, repo convention (no Navigation-Compose). Setup sliders and
 * typed fields stay bidirectionally synced, typed values clamp (spec section 4/8).
 */
@Composable
fun GamesScreen(
    targetPractice: TargetPracticeGame,
    breakPane: BreakThePaneGame,
    activeGame: GameMode,
    onActiveGameChange: (GameMode) -> Unit,
    gameAudio: GameAudio,
    soundsEnabled: Boolean,
    onSoundsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerState by remember { mutableStateOf("PICKER") } // PICKER | SETUP | PLAYING

    when {
        pickerState == "PICKER" && activeGame == GameMode.NONE -> {
            Column(
                modifier = modifier.fillMaxSize().background(GolfColors.Base).padding(GolfSpacing.Xl),
                verticalArrangement = Arrangement.spacedBy(GolfSpacing.Lg),
            ) {
                GameCard(
                    title = "TARGET PRACTICE",
                    blurb = "5 shots at a pinned green. Points by miss distance at rest. Max 125.",
                    onClick = { pickerState = "SETUP_TP" },
                )
                GameCard(
                    title = "BREAK THE PANE",
                    blurb = "Break all 9 glass cells floating 20% of the way to the target (customizable in setup). Fewer shots wins.",
                    onClick = { pickerState = "SETUP_BP" },
                )
            }
        }
        pickerState == "SETUP_TP" -> {
            Column(modifier = modifier.fillMaxSize().background(GolfColors.Base).padding(GolfSpacing.Xl)) {
                TargetPracticeSetup(
                    onStart = { target, difficulty ->
                        targetPractice.start(target, difficulty)
                        onActiveGameChange(GameMode.TARGET_PRACTICE)
                        pickerState = "PLAYING"
                    },
                    onCancel = { pickerState = "PICKER" },
                )
            }
        }
        pickerState == "SETUP_BP" -> {
            Column(modifier = modifier.fillMaxSize().background(GolfColors.Base).padding(GolfSpacing.Xl)) {
                BreakPaneSetup(
                    onStart = { paneDistanceM, target, difficulty ->
                        breakPane.start(target, difficulty, paneDistanceM)
                        onActiveGameChange(GameMode.BREAK_PANE)
                        pickerState = "PLAYING"
                    },
                    onCancel = { pickerState = "PICKER" },
                )
            }
        }
        activeGame == GameMode.TARGET_PRACTICE -> {
            TargetPracticePlay(
                game = targetPractice,
                onBack = {
                    onActiveGameChange(GameMode.NONE)
                    pickerState = "PICKER"
                },
                soundsEnabled = soundsEnabled,
                onSoundsChange = onSoundsChange,
                modifier = modifier,
            )
        }
        activeGame == GameMode.BREAK_PANE -> {
            BreakPanePlay(
                game = breakPane,
                gameAudio = gameAudio,
                onBack = {
                    onActiveGameChange(GameMode.NONE)
                    pickerState = "PICKER"
                },
                soundsEnabled = soundsEnabled,
                onSoundsChange = onSoundsChange,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun GameCard(title: String, blurb: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GolfColors.Line, RoundedCornerShape(GolfSpacing.CornerCard))
            .clickable(onClick = onClick)
            .padding(GolfSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
    ) {
        Text(title, style = GolfTypography.ScreenTitle, color = GolfColors.Teal)
        Text(blurb, style = GolfTypography.Body, color = GolfColors.TextSecondary)
    }
}

@Composable
private fun TargetPracticeSetup(onStart: (Double, Difficulty) -> Unit, onCancel: () -> Unit) {
    GameSetupScreen(title = "TARGET PRACTICE", onStart = onStart, onCancel = onCancel)
}

@Composable
private fun BreakPaneSetup(onStart: (Double?, Double, Difficulty) -> Unit, onCancel: () -> Unit) {
    var targetM by remember { mutableStateOf(DEFAULT_TARGET_M) }
    var customPane by remember { mutableStateOf(false) }
    var paneM by remember { mutableStateOf(PaneGeom.PLANE_FRACTION * DEFAULT_TARGET_M) }

    val paneMinM = targetM * PaneGeom.MIN_FRACTION
    val paneMaxM = targetM * PaneGeom.MAX_FRACTION

    GameSetupScreen(
        title = "BREAK THE PANE",
        onStart = { target, difficulty ->
            val custom = if (customPane) {
                paneM.coerceIn(target * PaneGeom.MIN_FRACTION, target * PaneGeom.MAX_FRACTION)
            } else null
            onStart(custom, target, difficulty)
        },
        onCancel = onCancel,
        onTargetChange = { newTarget ->
            targetM = newTarget
            // Spec: target changes clamp the custom value; OFF->ON re-seeds at 20%.
            if (customPane) {
                paneM = paneM.coerceIn(newTarget * PaneGeom.MIN_FRACTION, newTarget * PaneGeom.MAX_FRACTION)
            }
        },
        extraContent = {
            Text(
                text = if (customPane) "CUSTOM PANE DISTANCE: ON" else "CUSTOM PANE DISTANCE: OFF",
                style = GolfTypography.MetricLabel,
                color = if (customPane) GolfColors.Teal else GolfColors.TextMuted,
                modifier = Modifier
                    .border(1.dp, if (customPane) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(50))
                    .clickable {
                        customPane = !customPane
                        if (customPane) paneM = targetM * PaneGeom.PLANE_FRACTION
                    }
                    .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
            )
            if (customPane) {
                Text(
                    text = "Moves the glass pane closer to or farther from the tee.",
                    style = GolfTypography.Body,
                    color = GolfColors.TextSecondary,
                )
                Text(
                    text = "PANE DISTANCE (m): " + String.format(Locale.US, "%.0f", paneM) +
                        "  (" + Math.round(paneM / targetM * 100).toInt() + "% of target)",
                    style = GolfTypography.Body,
                    color = GolfColors.TextPrimary,
                )
                Slider(
                    value = paneM.toFloat(),
                    onValueChange = { paneM = it.toDouble() },
                    valueRange = paneMinM.toFloat()..paneMaxM.toFloat(),
                    steps = ((paneMaxM - paneMinM).roundToInt() - 1).coerceAtLeast(0),
                )
            }
        },
    )
}

@Composable
private fun GameSetupScreen(
    title: String,
    onStart: (Double, Difficulty) -> Unit,
    onCancel: () -> Unit,
    onTargetChange: (Double) -> Unit = {},
    extraContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var sliderM by remember { mutableStateOf(DEFAULT_TARGET_M) }
    var text by remember { mutableStateOf(DEFAULT_TARGET_M.toInt().toString()) }
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }

    Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Lg)) {
        Text(title, style = GolfTypography.ScreenTitle, color = GolfColors.Teal)
        Slider(
            value = sliderM.toFloat(),
            onValueChange = {
                sliderM = it.toDouble()
                text = String.format(Locale.US, "%.0f", sliderM)
                onTargetChange(sliderM)
            },
            valueRange = DIST_MIN_M.toFloat()..DIST_MAX_M.toFloat(),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                text = raw
                raw.toDoubleOrNull()?.let {
                    sliderM = it.coerceIn(DIST_MIN_M, DIST_MAX_M)
                    onTargetChange(sliderM)
                }
            },
            label = { Text("Distance (m, $DIST_MIN_M-$DIST_MAX_M)") },
            modifier = Modifier.width(260.dp),
        )
        extraContent?.invoke(this)
        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
            Difficulty.entries.forEach { d ->
                Box(
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (difficulty == d) GolfColors.Teal else GolfColors.Line,
                            RoundedCornerShape(GolfSpacing.Sm),
                        )
                        .clickable { difficulty = d }
                        .padding(horizontal = GolfSpacing.Md, vertical = GolfSpacing.Xs),
                ) {
                    Text(d.name, style = GolfTypography.Status, color = if (difficulty == d) GolfColors.Teal else GolfColors.TextSecondary)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
            Button(onClick = { onStart(sliderM, difficulty) }) { Text("START") }
            OutlinedButton(onClick = onCancel) { Text("CANCEL") }
        }
    }
}
