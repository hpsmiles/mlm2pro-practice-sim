package com.hpsmiles.golfsim.combine

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hpsmiles.golfsim.games.GameScene
import com.hpsmiles.golfsim.range.RangeCamera
import com.hpsmiles.golfsim.range.DisplayShot

/** Combine play canvas: ground + (approach only) visit green & pin + tracer. No rings, no labels. */
@Composable
internal fun CombineCanvas(
    camera: RangeCamera,
    entry: CombineCatalogEntry,
    shotIndex: Int,
    tracer: DisplayShot?,
    playFraction: Float,
    modifier: Modifier = Modifier,
) {
    // Copy the labelPaint remember block verbatim from TargetPracticeCanvas.kt.
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
    }
    Canvas(modifier) {
        with(GameScene) {
            drawGameGround(camera, size.width, size.height, labelPaint)
            val station = entry.stations[CombinePlan.stationIndexFor(shotIndex)]
            if (station.kind == StationKind.APPROACH) {
                val pin = CombinePlan.pinFor(entry, shotIndex)!!
                drawGreen(camera, size.width, size.height, pin.dx, station.startDistanceM + pin.dy, station.greenRadiusM)
                drawPin(camera, size.width, size.height, pin.dx, station.startDistanceM + pin.dy)
            }
            if (tracer != null) {
                drawTracer(
                    camera,
                    size.width,
                    size.height,
                    tracer.shotResult,
                    playFraction,
                    GameScene.apexVMin(size.width, size.height),
                )
            }
        }
    }
}
