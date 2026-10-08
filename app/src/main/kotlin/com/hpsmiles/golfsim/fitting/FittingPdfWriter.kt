package com.hpsmiles.golfsim.fitting

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * M7 fitting-session PDF export (2026-10-08): renders [FittingPdfData.Pdf] as
 * an A4 LANDSCAPE (842×595 pt) document with the exact row layout the user
 * specified — per club a summary row + that club's shot rows, then the Δ row(s)
 * at the very end — and, below the table, the top-down range view re-drawn as
 * vector android.graphics (same world mapping, y-flip and rotation-sign fix as
 * FittingTopDownPane). Light/print-friendly theme: white background, dark text,
 * club-colour dots/rings preserved, excluded shot rows greyed with a "✕" marker.
 *
 * Delivery is a share sheet: the PDF is written to `context.cacheDir/exports/`
 * (`fitting-<yyyy-MM-dd-HHmm>.pdf`) and shared via FileProvider + ACTION_SEND —
 * no storage permissions. Multi-page: a long table simply breaks to page 2.
 */
object FittingPdfWriter {

    private const val TAG = "FittingPdfWriter"

    // A4 landscape, points (PdfDocument's unit).
    private const val PAGE_W = 842f
    private const val PAGE_H = 595f
    private const val MARGIN_X = 24f
    private const val MARGIN_TOP = 26f
    private const val MARGIN_BOTTOM = 24f

    // Top-down pane geometry (below the table on the last page).
    private const val TOP_DOWN_H = 190f
    private const val TABLE_TO_PANE_GAP = 16f

    // Row heights (pt).
    private const val TITLE_H = 40f
    private const val HEADER_H = 16f
    private const val SUMMARY_H = 18f
    private const val SHOT_HEADER_H = 13f
    private const val SHOT_H = 13f
    private const val DELTA_H = 18f
    private const val BLOCK_GAP = 8f

    // Light/print palette.
    private val INK = Color.rgb(0x1F, 0x24, 0x29)          // near-black text
    private val INK_SOFT = Color.rgb(0x5C, 0x68, 0x73)     // secondary text
    private val INK_MUTED = Color.rgb(0x8F, 0xA0, 0xAC)    // excluded values
    private val RULE = Color.rgb(0xC9, 0xD0, 0xD7)         // light row separators
    private val PANE_BORDER = Color.rgb(0x9A, 0xA5, 0xAE)  // top-down frame

    private val FILENAME_STAMP = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US)
    private val DATE_STAMP = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    /**
     * Renders [data] and writes `exports/fitting-<timestamp>.pdf` under the app
     * cache dir. Returns the file, or null on failure (logged).
     */
    fun writeToCache(context: Context, data: FittingPdfData.Pdf): File? {
        val bytes = render(data) ?: return null
        val exports = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exports, "fitting-${FILENAME_STAMP.format(Date(sessionDate(data)))}.pdf")
        return try {
            file.writeBytes(bytes)
            file
        } catch (t: Throwable) {
            Log.w(TAG, "pdf write failed", t)
            null
        }
    }

    /** Fires the ACTION_SEND share sheet for [file] via FileProvider (no storage permission). */
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            val chooser = Intent.createChooser(send, "Share fitting PDF")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (t: Throwable) {
            Log.w(TAG, "share sheet failed", t)
        }
    }

    private fun sessionDate(data: FittingPdfData.Pdf): Long = data.sessionDateMs ?: System.currentTimeMillis()

    /** Renders the full multi-page document; null on failure. */
    fun render(data: FittingPdfData.Pdf): ByteArray? {
        val document = PdfDocument()
        try {
            val paints = Paints()

            // Summary column geometry: FittingTable dp proportions (CLUB 150 /
            // n 40 / SMASH 64 / rest 88) scaled proportionally into the content
            // width. Index 0 = frozen CLUB column, 1..12 = the 12 metrics
            // (… OFFLINE, AREA). The scale shrinks automatically to fit 13 columns.
            val widths = listOf(150f, 40f, 88f, 88f, 64f, 88f, 88f, 88f, 88f, 88f, 88f, 88f, 88f)
            val scale = (PAGE_W - 2 * MARGIN_X) / widths.sum()
            val colW = widths.map { it * scale }
            val colX = FloatArray(colW.size + 1)
            var acc = MARGIN_X
            colW.forEachIndexed { i, w -> colX[i] = acc; acc += w }
            colX[colW.size] = acc // right edge sentinel

            var pageNumber = 1
            var page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNumber).create(),
            )
            var canvas = page.canvas
            var cursor = MARGIN_TOP

            // Advances to a fresh page when the next row would overflow the body.
            fun breakIfNeeded(h: Float) {
                if (cursor + h > PAGE_H - MARGIN_BOTTOM) {
                    document.finishPage(page)
                    pageNumber++
                    page = document.startPage(
                        PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNumber).create(),
                    )
                    canvas = page.canvas
                    cursor = MARGIN_TOP
                }
            }

            // ---- title band ----
            paints.title.textAlign = Paint.Align.LEFT
            canvas.drawText("FITTING SESSION", MARGIN_X, cursor + 14f, paints.title)
            paints.subtitle.textAlign = Paint.Align.LEFT
            val subtitle = buildString {
                append(DATE_STAMP.format(Date(sessionDate(data))))
                append("  ·  ${data.totalShots} shots")
                if (data.excludedCount > 0) append("  ·  ${data.excludedCount} excluded")
                append("  ·  ${data.clubs.size} club${if (data.clubs.size == 1) "" else "s"}")
            }
            canvas.drawText(subtitle, MARGIN_X, cursor + 27f, paints.subtitle)
            canvas.drawRect(MARGIN_X, cursor + 31f, PAGE_W - MARGIN_X, cursor + 31.8f, paints.rule)
            cursor += TITLE_H

            // ---- summary header + per-club blocks ----
            drawSummaryHeader(canvas, colX, colW, cursor, paints)
            cursor += HEADER_H

            data.clubs.forEachIndexed { index, club ->
                if (index > 0) {
                    breakIfNeeded(BLOCK_GAP)
                    cursor += BLOCK_GAP
                }
                breakIfNeeded(SUMMARY_H)
                drawSummaryRow(canvas, club, colX, colW, cursor, paints)
                cursor += SUMMARY_H

                breakIfNeeded(SHOT_HEADER_H + 2f)
                drawShotHeader(canvas, colX, cursor + 1f, paints)
                cursor += SHOT_HEADER_H + 2f
                club.shots.forEach { shot ->
                    breakIfNeeded(SHOT_H)
                    drawShotRow(canvas, shot, colX, cursor, paints)
                    cursor += SHOT_H
                }
            }

            // ---- Δ row(s): at the very end, after every club block ----
            if (data.deltaRows.isNotEmpty()) {
                breakIfNeeded(BLOCK_GAP)
                cursor += BLOCK_GAP
                data.deltaRows.forEach { delta ->
                    breakIfNeeded(DELTA_H)
                    drawDeltaRow(canvas, delta, colX, colW, cursor, paints)
                    cursor += DELTA_H
                }
            }

            // ---- top-down pane below the table ----
            if (cursor + TABLE_TO_PANE_GAP + TOP_DOWN_H > PAGE_H - MARGIN_BOTTOM) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNumber).create(),
                )
                canvas = page.canvas
                cursor = MARGIN_TOP
            } else {
                cursor += TABLE_TO_PANE_GAP
            }
            drawTopDown(canvas, data, MARGIN_X, cursor, PAGE_W - 2 * MARGIN_X, TOP_DOWN_H, paints)
            document.finishPage(page)

            val out = ByteArrayOutputStream()
            document.writeTo(out)
            return out.toByteArray()
        } catch (t: Throwable) {
            Log.w(TAG, "pdf render failed", t)
            return null
        } finally {
            document.close()
        }
    }

    // ---- table rows (rowTop = the row's top edge on the current page) ----

    private fun drawSummaryHeader(canvas: Canvas, colX: FloatArray, colW: List<Float>, rowTop: Float, paints: Paints) {
        val labels = listOf("CLUB", "n", "CHS", "BALL\nSPEED", "SMASH", "CARRY", "TOTAL", "LAUNCH", "DIR", "SPIN", "SPIN\nAXIS", "OFFLINE", "AREA")
        paints.header.textAlign = Paint.Align.CENTER
        labels.forEachIndexed { i, label ->
            val x = colX[i] + colW[i] / 2f
            if (label.contains('\n')) {
                val parts = label.split('\n')
                val fm = paints.header.fontMetrics
                val lineH = fm.descent - fm.ascent
                val top = rowTop + (HEADER_H - 2 * lineH) / 2f
                parts.forEachIndexed { li, part ->
                    canvas.drawText(part, x, top - fm.ascent + li * lineH, paints.header)
                }
            } else {
                canvas.drawText(label, x, rowTop + paints.headerBaseline(paints.header), paints.header)
            }
        }
    }

    private fun drawSummaryRow(
        canvas: Canvas,
        club: FittingPdfData.ClubBlock,
        colX: FloatArray,
        colW: List<Float>,
        rowTop: Float,
        paints: Paints,
    ) {
        // Club dot + name in the frozen column.
        val dotRadius = 3.2f
        val dotX = colX[0] + dotRadius + 4f
        paints.clubDot.color = FittingColors.clubColor(club.colorIndex).toArgb()
        canvas.drawCircle(dotX, rowTop + SUMMARY_H / 2f, dotRadius, paints.clubDot)
        paints.summary.textAlign = Paint.Align.LEFT
        paints.summary.color = INK
        canvas.drawText(club.name, dotX + dotRadius + 4f, rowTop + paints.summaryBaseline(paints.summary), paints.summary)
        // 11 metric cells, centered in their columns (column 0 = club skipped).
        paints.summary.textAlign = Paint.Align.CENTER
        club.summary.forEachIndexed { i, cell ->
            val col = i + 1
            canvas.drawText(cell, colX[col] + colW[col] / 2f, rowTop + paints.summaryBaseline(paints.summary), paints.summary)
        }
        // Light separator under the summary row.
        canvas.drawRect(colX[0], rowTop + SUMMARY_H, PAGE_W - MARGIN_X, rowTop + SUMMARY_H + 0.5f, paints.rule)
    }

    private fun drawShotHeader(canvas: Canvas, colX: FloatArray, rowTop: Float, paints: Paints) {
        // CARRY/TOTAL/BALL/CHS/SMASH/SPIN/DIR align under the same summary
        // metric columns (indices 5,6,3,2,4,9,8 of the 12-column layout).
        val header = listOf("CARRY", "TOTAL", "BALL", "CHS", "SMASH", "SPIN", "DIR")
        val colIndices = listOf(5, 6, 3, 2, 4, 9, 8)
        paints.shotHeader.textAlign = Paint.Align.CENTER
        header.forEachIndexed { i, label ->
            val idx = colIndices[i]
            canvas.drawText(label, colX[idx] + colWFor(colX, idx) / 2f, rowTop + paints.shotBaseline(paints.shotHeader), paints.shotHeader)
        }
    }

    private fun drawShotRow(canvas: Canvas, shot: FittingPdfData.ShotRow, colX: FloatArray, rowTop: Float, paints: Paints) {
        val values = listOf(shot.carry, shot.total, shot.ball, shot.chs, shot.smash, shot.spin, shot.dir)
        val colIndices = listOf(5, 6, 3, 2, 4, 9, 8)
        paints.shot.textAlign = Paint.Align.CENTER
        paints.shot.color = if (shot.isExcluded) INK_MUTED else INK_SOFT
        if (shot.isExcluded) {
            canvas.drawText("✕", colX[0] + 12f, rowTop + paints.shotBaseline(paints.shot), paints.shot)
        }
        values.forEachIndexed { i, value ->
            val idx = colIndices[i]
            canvas.drawText(value, colX[idx] + colWFor(colX, idx) / 2f, rowTop + paints.shotBaseline(paints.shot), paints.shot)
        }
    }

    private fun drawDeltaRow(
        canvas: Canvas,
        delta: FittingPdfData.DeltaRow,
        colX: FloatArray,
        colW: List<Float>,
        rowTop: Float,
        paints: Paints,
    ) {
        // A rule above the Δ row sets it apart visually.
        canvas.drawRect(colX[0], rowTop, PAGE_W - MARGIN_X, rowTop + 1.2f, paints.rule)
        paints.deltaLabel.textAlign = Paint.Align.LEFT
        canvas.drawText(delta.label, colX[0] + 6f, rowTop + paints.deltaBaseline(paints.deltaLabel), paints.deltaLabel)
        paints.delta.textAlign = Paint.Align.CENTER
        delta.cells.forEachIndexed { i, cell ->
            val col = i + 1
            canvas.drawText(cell, colX[col] + colW[col] / 2f, rowTop + paints.deltaBaseline(paints.delta), paints.delta)
        }
    }

    private fun colWFor(colX: FloatArray, index: Int): Float = colX[index + 1] - colX[index]

    // ---- top-down pane (FittingTopDownPane world mapping as vector drawing) ----

    private fun drawTopDown(
        canvas: Canvas,
        data: FittingPdfData.Pdf,
        left: Float,
        top: Float,
        w: Float,
        h: Float,
        paints: Paints,
    ) {
        // White pane with a light frame.
        canvas.drawRect(left, top, left + w, top + h, paints.paneBg)
        canvas.drawRect(left, top, left + w, top + h, paints.paneBorder)

        val fit = computeTopDownFit(
            width = w.toDouble(), height = h.toDouble(),
            minY = data.topDown.minY, maxY = data.topDown.maxY, maxAbsX = data.topDown.maxAbsX,
            hasData = data.topDown.hasData,
            bottomMarginPx = 8.0,
        )
        val pxPerM = fit.pxPerM.toFloat()
        val originX = (left + fit.originX).toFloat()
        val originY = (top + fit.originY).toFloat()

        // Simplified grid: light horizontal distance lines every 25 m (labelled
        // every 50 m) + a vertical centre line at world x = 0, matching the y-flip.
        var m = 0f
        while (m <= 400f) {
            val y = originY - m * pxPerM
            if (y in top..(top + h)) {
                canvas.drawLine(left, y, left + w, y, paints.grid)
                if (m.toInt() % 50 == 0) {
                    paints.gridLabel.textAlign = Paint.Align.LEFT
                    canvas.drawText("${m.toInt()} m", left + 3f, y - 2f, paints.gridLabel)
                }
            }
            m += 25f
        }
        if (originX in left..(left + w)) {
            canvas.drawLine(originX, top, originX, top + h, paints.grid)
        }

        // Dispersion rings FIRST (rings under dots) — one rotated MVEE per club,
        // the same ellipse whose area the AREA column reports. The rotation-sign
        // negation mirrors FittingTopDownPane's `-Math.toDegrees(angleRad)`
        // (screen y grows down while world total grows up).
        data.clubs.forEach { club ->
            val ell = club.ellipse ?: return@forEach
            val cx = originX + (ell.centreSideM * pxPerM).toFloat()
            val cy = originY - (ell.centreTotalM * pxPerM).toFloat()
            val a = (ell.semiAxisM * pxPerM).toFloat()
            val b = (ell.semiCrossM * pxPerM).toFloat()
            paints.ring.color = FittingColors.clubColor(club.colorIndex).toArgb()
            paints.ring.alpha = 140 // ~55 %
            canvas.save()
            canvas.rotate(-Math.toDegrees(ell.angleRad).toFloat(), cx, cy)
            canvas.drawOval(RectF(cx - a, cy - b, cx + a, cy + b), paints.ring)
            canvas.restore()
        }

        // Kept shot dots (≈6 dp) in club colours.
        data.clubs.forEach { club ->
            paints.dot.color = FittingColors.clubColor(club.colorIndex).toArgb()
            paints.dot.alpha = 204 // ~0.8
            club.keptPoints.forEach { (side, total) ->
                val x = originX + (side * pxPerM).toFloat()
                val y = originY - (total * pxPerM).toFloat()
                if (x in left..(left + w) && y in top..(top + h)) {
                    canvas.drawCircle(x, y, 3f, paints.dot)
                }
            }
        }
    }

    // ---- paints ----

    /** All paints for one render pass (Paints are not thread-safe). */
    private class Paints {
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 14f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_SOFT; textSize = 9f
        }
        val header = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_SOFT; textSize = 7.5f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val summary = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 8.5f }
        val shotHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_MUTED; textSize = 6.8f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val shot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK_SOFT; textSize = 7.5f }
        val deltaLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_SOFT; textSize = 8.5f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val delta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 8.5f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val clubDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val rule = Paint().apply { color = RULE }
        val paneBg = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        val paneBorder = Paint().apply { color = PANE_BORDER; style = Paint.Style.STROKE; strokeWidth = 1f }
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RULE; strokeWidth = 0.6f }
        val gridLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK_MUTED; textSize = 6.5f }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.4f }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        // Row-top → text baseline (rows are centered vertically in their band).
        fun headerBaseline(p: Paint): Float = (HEADER_H - (p.fontMetrics.descent - p.fontMetrics.ascent)) / 2f - p.fontMetrics.ascent
        fun summaryBaseline(p: Paint): Float = (SUMMARY_H - (p.fontMetrics.descent - p.fontMetrics.ascent)) / 2f - p.fontMetrics.ascent
        fun shotBaseline(p: Paint): Float = (SHOT_H - (p.fontMetrics.descent - p.fontMetrics.ascent)) / 2f - p.fontMetrics.ascent
        fun deltaBaseline(p: Paint): Float = (DELTA_H - (p.fontMetrics.descent - p.fontMetrics.ascent)) / 2f - p.fontMetrics.ascent
    }
}
