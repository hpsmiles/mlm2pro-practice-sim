// app/src/main/kotlin/com/hpsmiles/golfsim/settings/BagReorderPanel.kt
package com.hpsmiles.golfsim.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.SectionCard

private val RowHeight = 44.dp
private val HandleSize = 40.dp
private val AutoScrollEdge = 48.dp
private val AutoScrollStep = 6.dp

/**
 * Focused reorder mode (spec §5.2): all clubs grouped by type, drag handles on
 * the left; a drop moves the club within its own type group only. Plain
 * Compose state holder, no ViewModel — the math lives in BagReorderMath.kt.
 */
@Composable
fun BagReorderPanel(
    clubs: List<ClubRecord>,
    onMoveClub: (Long, Int) -> Unit,
    onDone: () -> Unit,
) {
    val density = LocalDensity.current
    val rowPitchPx = with(density) { RowHeight.toPx() }
    val edgePx = with(density) { AutoScrollEdge.toPx() }
    val stepPx = with(density) { AutoScrollStep.toPx() }
    val scrollState = rememberScrollState()
    val dragState = remember { ReorderDragState() }

    val groups = remember(clubs) { bagGroups(clubs) }
    val ids = remember(clubs) { clubs.map { it.id } }
    val previewIds = remember(clubs, dragState.clubId, dragState.appliedSlot) {
        if (dragState.clubId != null && dragState.appliedSlot >= 0) {
            moveId(ids, dragState.originSlot, dragState.appliedSlot)
        } else {
            ids
        }
    }

    SectionCard("REORDER CLUBS", modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Drag a club's handle to move it within its type group — the Driver → Putter group order is fixed.",
            style = GolfTypography.Body,
            color = GolfColors.TextSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = GolfSpacing.Sm)) {
            Text(
                text = "DONE",
                style = GolfTypography.MetricLabel,
                color = GolfColors.Teal,
                modifier = Modifier
                    .border(1.dp, GolfColors.Teal, RoundedCornerShape(50))
                    .clickable(onClick = onDone)
                    .padding(horizontal = GolfSpacing.Lg, vertical = 8.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .onGloballyPositioned { coords ->
                    dragState.viewportTopPx = coords.positionInRoot().y
                    dragState.viewportHeightPx = coords.size.height.toFloat()
                },
        ) {
            groups.forEach { group ->
                Text(
                    text = groupHeader(group.type),
                    style = GolfTypography.MetricLabel,
                    color = GolfColors.TextMuted,
                    modifier = Modifier.padding(top = GolfSpacing.Sm, bottom = GolfSpacing.Xs),
                )
                group.clubs.forEachIndexed { index, club ->
                    ReorderRow(
                        club = club,
                        layoutIndex = group.startIndex + index,
                        workingIndex = previewIds.indexOf(club.id),
                        group = group,
                        groups = groups,
                        dragState = dragState,
                        rowPitchPx = rowPitchPx,
                        edgePx = edgePx,
                        stepPx = stepPx,
                        scrollState = scrollState,
                        onMoveClub = onMoveClub,
                    )
                }
            }
        }
    }
}

/** Section header: labels pluralize; DRIVER and PUTTER stay singular. */
private fun groupHeader(type: ClubType): String = when (type) {
    ClubType.DRIVER -> "DRIVER"
    ClubType.PUTTER -> "PUTTER"
    else -> type.label.uppercase() + "S"
}

/**
 * Panel-local drag state: the dragged club, the raw delta since drag start and
 * the previewed flat slot. Slots are indexes into the rendered clubs list; the
 * drag is always clamped to the dragged club's group span.
 */
private class ReorderDragState {
    var clubId by mutableStateOf<Long?>(null)
        private set
    var offsetPx by mutableStateOf(0f)
        private set
    var appliedSlot by mutableStateOf(-1)
        private set
    var originSlot = -1

    /** Row content tops (root Y relative to the scroll content), captured idle. */
    val rowTops = HashMap<Long, Float>()
    var viewportTopPx = 0f
    var viewportHeightPx = 0f

    fun start(id: Long, layoutIndex: Int) {
        clubId = id
        originSlot = layoutIndex
        appliedSlot = layoutIndex
        offsetPx = 0f
    }

    fun drag(
        deltaY: Float,
        groups: List<BagGroup>,
        type: ClubType,
        rowPitchPx: Float,
        scroll: ScrollState,
        edgePx: Float,
        stepPx: Float,
    ) {
        val id = clubId ?: return
        val group = groups.firstOrNull { it.type == type } ?: return
        offsetPx += deltaY
        appliedSlot = dropTargetIndex(originSlot, offsetPx, rowPitchPx, group)

        // Edge auto-scroll: the dragged row is the finger's proxy (spec §5.2).
        val visualTop = (rowTops[id] ?: 0f) + visualOffsetPx(groups, rowPitchPx) - scroll.value
        when {
            visualTop < edgePx -> scroll.dispatchRawDelta(-stepPx)
            visualTop + rowPitchPx > viewportHeightPx - edgePx -> scroll.dispatchRawDelta(stepPx)
        }
    }

    /** Continuous, group-clamped translation of the dragged row from its slot. */
    fun visualOffsetPx(groups: List<BagGroup>, rowPitchPx: Float): Float {
        val id = clubId ?: return 0f
        val group = groups.firstOrNull { g -> g.clubs.any { it.id == id } } ?: return 0f
        val firstPx = group.startIndex * rowPitchPx
        val lastPx = (group.startIndex + group.clubs.size - 1) * rowPitchPx
        val continuousPx = originSlot * rowPitchPx + offsetPx
        return continuousPx.coerceIn(firstPx, lastPx) - originSlot * rowPitchPx
    }

    fun finish(commit: Boolean, groups: List<BagGroup>, onMoveClub: (Long, Int) -> Unit) {
        val id = clubId
        if (commit && id != null && appliedSlot != originSlot) {
            val group = groups.firstOrNull { g -> g.clubs.any { it.id == id } }
            if (group != null) onMoveClub(id, appliedSlot - group.startIndex)
        }
        clubId = null
        appliedSlot = -1
        originSlot = -1
        offsetPx = 0f
    }
}

@Composable
private fun ReorderRow(
    club: ClubRecord,
    layoutIndex: Int,
    workingIndex: Int,
    group: BagGroup,
    groups: List<BagGroup>,
    dragState: ReorderDragState,
    rowPitchPx: Float,
    edgePx: Float,
    stepPx: Float,
    scrollState: ScrollState,
    onMoveClub: (Long, Int) -> Unit,
) {
    val isDragging = dragState.clubId == club.id
    val draggable = group.clubs.size >= 2

    val shiftTargetPx =
        if (dragState.clubId == null || isDragging) 0f else (workingIndex - layoutIndex) * rowPitchPx
    val animatedShiftPx by animateFloatAsState(
        targetValue = shiftTargetPx,
        animationSpec = tween(durationMillis = 150),
        label = "reorderRowShift",
    )
    val shiftPx = if (isDragging) dragState.visualOffsetPx(groups, rowPitchPx) else animatedShiftPx

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = shiftPx }
            .background(
                if (isDragging) GolfColors.Panel else Color.Transparent,
                RoundedCornerShape(GolfSpacing.Sm),
            )
            .then(
                if (isDragging) {
                    Modifier.border(1.dp, GolfColors.Teal, RoundedCornerShape(GolfSpacing.Sm))
                } else {
                    Modifier
                },
            )
            .onGloballyPositioned { coords ->
                if (dragState.clubId == null) {
                    dragState.rowTops[club.id] =
                        coords.positionInRoot().y - dragState.viewportTopPx + scrollState.value
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .size(HandleSize)
                .pointerInput(club.id, groups) {
                    if (!draggable) return@pointerInput
                    detectDragGestures(
                        onDragStart = { dragState.start(club.id, layoutIndex) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragState.drag(
                                deltaY = dragAmount.y,
                                groups = groups,
                                type = club.type,
                                rowPitchPx = rowPitchPx,
                                scroll = scrollState,
                                edgePx = edgePx,
                                stepPx = stepPx,
                            )
                        },
                        onDragEnd = {
                            dragState.finish(commit = true, groups = groups, onMoveClub = onMoveClub)
                        },
                        onDragCancel = {
                            dragState.finish(commit = false, groups = groups, onMoveClub = onMoveClub)
                        },
                    )
                },
        ) {
            val barWidth = 18.dp.toPx()
            val barHeight = 2.dp.toPx()
            val gap = 6.dp.toPx()
            val left = (size.width - barWidth) / 2f
            val top = (size.height - (barHeight * 3 + gap * 2)) / 2f
            val barColor = if (draggable) GolfColors.TextSecondary else GolfColors.TextMuted
            repeat(3) { i ->
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, top + i * (barHeight + gap)),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barHeight / 2f),
                )
            }
        }
        Text(
            text = club.name,
            style = GolfTypography.Body,
            color = if (draggable) GolfColors.TextPrimary else GolfColors.TextMuted,
            modifier = Modifier.weight(1f),
        )
        if (club.isTemp) {
            Text(
                text = "TEST",
                style = GolfTypography.MetricLabel,
                color = GolfColors.Amber,
                modifier = Modifier.padding(end = GolfSpacing.Sm),
            )
        }
    }
}
