// app/src/main/kotlin/com/hpsmiles/golfsim/range/LastShotChips.kt
package com.hpsmiles.golfsim.range

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.MetricChip
import com.hpsmiles.golfsim.fitting.FittingFormats
import java.util.Locale

/**
 * The LAST SHOT chip sequence (carry · total · ball · club · smash · spin ·
 * launch · dir · axis), shared verbatim by RANGE, the FIT compare panel and
 * the game metrics panel so the three render identically. Ball speed and club
 * head speed display in MPH (× FittingFormats.MPH_PER_MS, "%.1f"); smash is
 * derived client-side as ball/club speed (both m/s) and renders "-" when the
 * club speed is ≤ 0.
 */
@Composable
internal fun LastShotChips(shot: DisplayShot) {
    val r = shot.shotResult
    MetricChip("carry", String.format(Locale.US, "%.0f", r.carryM), "M")
    Spacer(Modifier.size(GolfSpacing.Xs))
    MetricChip("total", String.format(Locale.US, "%.0f", r.totalM), "M")
    Spacer(Modifier.size(GolfSpacing.Xs))
    MetricChip(
        "ball", FittingFormats.mph(shot.ballData.ballSpeed), "MPH",
        accent = GolfColors.Amber,
    )
    Spacer(Modifier.size(GolfSpacing.Xs))
    // Club head speed is the only club metric the MLM2PRO transmits (MEASUREMENT 0-1);
    // smash factor is derived client-side as ball speed / club speed (both m/s).
    MetricChip("club", FittingFormats.mph(shot.ballData.clubHeadSpeed), "MPH")
    Spacer(Modifier.size(GolfSpacing.Xs))
    MetricChip(
        "smash",
        if (shot.ballData.clubHeadSpeed > 0.0) {
            String.format(Locale.US, "%.2f", shot.ballData.ballSpeed / shot.ballData.clubHeadSpeed)
        } else "-",
        "",
    )
    Spacer(Modifier.size(GolfSpacing.Xs))
    // totalSpin is an Int (M1 BallData) — %d, not %.0f (IllegalFormatConversionException).
    MetricChip("spin", String.format(Locale.US, "%d", shot.ballData.totalSpin), "RPM")
    Spacer(Modifier.size(GolfSpacing.Xs))
    MetricChip("launch", String.format(Locale.US, "%.1f", shot.ballData.launchAngle), "DEG")
    Spacer(Modifier.size(GolfSpacing.Xs))
    // HLA sign verified on-device M4c: − left / + right of target.
    MetricChip("dir", String.format(Locale.US, "%+.1f", shot.ballData.launchDirection), "DEG")
    Spacer(Modifier.size(GolfSpacing.Xs))
    MetricChip("axis", String.format(Locale.US, "%.1f", shot.ballData.spinAxis), "DEG")
}
