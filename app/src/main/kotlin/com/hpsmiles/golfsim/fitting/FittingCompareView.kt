package com.hpsmiles.golfsim.fitting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.bag.ClubQualityGate
import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.data.fitting.FittingStats
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import com.hpsmiles.golfsim.range.ChipFont
import com.hpsmiles.golfsim.range.ClubPickerOverlay
import com.hpsmiles.golfsim.range.DisplayShot
import com.hpsmiles.golfsim.range.LastShotChips
import com.hpsmiles.golfsim.range.RangeLiveView
import com.hpsmiles.golfsim.range.SpeedMult

/**
 * FIT live view (design notes §1): the extracted M6 range live view + a 210 dp
 * right panel — LAST SHOT (full chip set, verbatim from RANGE), COMPARISON
 * (CARRY|TOTAL toggle + per-club colour dot / name / count badge / avg), VIEW
 * RESULTS, HISTORY. ACTIVE CLUB renders as the filled canvas trigger, exactly
 * the RANGE idiom. ADD COMPARISON pre-checks TEST and auto-activates the new
 * club (wired via onAddClub in AppRoot).
 */
@Composable
internal fun FittingCompareView(
    modifier: Modifier = Modifier,
    clubs: List<ClubRecord>,
    activeClubName: String?,
    sessionShots: List<FittingShotEntity>,
    liveShots: List<DisplayShot>,
    armed: Boolean,
    info: String,
    distanceMode: FittingDistanceMode,
    historyCount: Int,
    onDistanceModeChange: (FittingDistanceMode) -> Unit,
    onSelectClub: (String?) -> Unit,
    onAddClub: suspend (String, ClubType, Boolean) -> Boolean,
    onViewResults: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    var speedMult by remember { mutableStateOf(SpeedMult.X15) }
    Row(modifier.fillMaxSize().background(GolfColors.Base)) {
        // Canvas region (notes §1.1): live tracer exactly as RANGE/BAG — no
        // club colours/rings while hitting (those live in RESULTS). The FIT
        // sim runs on the user's surfaces (AppRoot syncs fitSession), with no
        // practice green. The StatusStrip is the global one already rendered
        // by AppRoot below the tab content — none is drawn here (armed/info
        // are kept for dispatcher parity).
        Box(Modifier.weight(1f).fillMaxHeight()) {
            RangeLiveView(
                shots = liveShots,
                modifier = Modifier.fillMaxSize(),
                speedMult = speedMult,
                customGreen = null,
            )
            // Top-right overlay mirroring RANGE: the filled ACTIVE CLUB
            // trigger. The VIEW chip is omitted in v1 (design notes §1.1.2 /
            // §4.2 — COMPARE has only one canvas, so omission was ratified).
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(GolfSpacing.Sm),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
            ) {
                FittingActiveClubButton(activeClubName) { pickerOpen = true }
            }
            // Speed multiplier chips — copied verbatim from RANGE (notes §1.1).
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(GolfSpacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Xs),
            ) {
                for (speed in SpeedMult.entries) {
                    Text(
                        speed.label,
                        color = if (speed == speedMult) GolfColors.Teal else GolfColors.TextMuted,
                        style = GolfTypography.Status,
                        modifier = Modifier
                            .clickable { speedMult = speed }
                            .border(1.dp, if (speed == speedMult) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(50))
                            .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
                    )
                }
            }
        }
        // Right panel (notes §1.2): LAST SHOT → COMPARISON → VIEW RESULTS → HISTORY.
        Column(
            Modifier
                .width(210.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .background(GolfColors.Panel)
                .padding(GolfSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(GolfSpacing.Md),
        ) {
            SectionCard("LAST SHOT") {
                val shot = liveShots.lastOrNull()
                if (shot == null) {
                    Text("Fire a shot", style = GolfTypography.BodySmall, color = GolfColors.TextMuted)
                } else {
                    LastShotChips(shot)
                }
            }
            SectionCard("COMPARISON") {
                // CARRY | TOTAL toggle (notes §1.2) — bound to distanceMode.
                Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                    FittingDistanceMode.entries.forEach { mode ->
                        val selected = mode == distanceMode
                        Text(
                            mode.label,
                            style = ChipFont,
                            color = if (selected) GolfColors.Teal else GolfColors.TextSecondary,
                            modifier = Modifier
                                .clickable { onDistanceModeChange(mode) }
                                .border(1.dp, if (selected) GolfColors.Teal else GolfColors.Line, RoundedCornerShape(50))
                                .padding(horizontal = GolfSpacing.Sm, vertical = 2.dp),
                        )
                    }
                }
                // clubOrder + summarize are derived from the persisted shot
                // set only — remember so they don't recompute every
                // recomposition (toggle / armed / info changes).
                val rows = remember(sessionShots) {
                    FittingStats.clubOrder(sessionShots).mapIndexed { index, key ->
                        Triple(index, key, FittingStats.summarize(key, sessionShots))
                    }
                }
                if (rows.isEmpty()) {
                    Text("Hit shots to start comparing", style = GolfTypography.BodySmall, color = GolfColors.TextMuted)
                }
                rows.forEach { (index, key, s) ->
                    val avg = when (distanceMode) {
                        FittingDistanceMode.CARRY -> s.carry
                        FittingDistanceMode.TOTAL -> s.total
                    }
                    val isActive = key.name == activeClubName
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(RoundedCornerShape(GolfSpacing.CornerCard / 2))
                            .background(GolfColors.Panel)
                            .border(
                                1.dp,
                                if (isActive) GolfColors.Teal else GolfColors.Panel,
                                RoundedCornerShape(GolfSpacing.CornerCard / 2),
                            )
                            .padding(horizontal = GolfSpacing.Sm),
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(FittingColors.clubColor(index)))
                        Text(
                            key.name,
                            style = GolfTypography.Body,
                            color = GolfColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        FittingCountBadge(s.kept)
                        Text(
                            avg?.let { FittingFormats.m1(it) } ?: "-",
                            style = GolfTypography.MetricValue,
                            color = GolfColors.TextPrimary,
                        )
                        // Unit only when there is an average — an all-excluded
                        // club shows bare "-", never "- M".
                        if (avg != null) {
                            Text(" M", style = GolfTypography.Unit, color = GolfColors.TextMuted)
                        }
                    }
                }
                if (rows.size > 4) {
                    Text(
                        "Many clubs — 4 or fewer compares best",
                        style = GolfTypography.BodySmall,
                        color = GolfColors.TextMuted,
                    )
                }
            }
            // VIEW RESULTS (notes §1.2.3): ghost pill, teal; disabled while
            // there is nothing to view (no active session / no shots).
            val canViewResults = sessionShots.isNotEmpty()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .border(
                        1.dp,
                        if (canViewResults) GolfColors.Teal else GolfColors.Line,
                        RoundedCornerShape(50),
                    )
                    .clickable(enabled = canViewResults, onClick = onViewResults),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "VIEW RESULTS",
                    style = ChipFont,
                    color = if (canViewResults) GolfColors.Teal else GolfColors.TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
            // HISTORY chip (notes §1.2.4): the " · N" count suffix renders in
            // TextMuted when history is non-empty (label stays TextSecondary).
            Text(
                buildAnnotatedString {
                    append("HISTORY")
                    if (historyCount > 0) {
                        withStyle(SpanStyle(color = GolfColors.TextMuted)) {
                            append(" · $historyCount")
                        }
                    }
                },
                style = ChipFont,
                color = GolfColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenHistory)
                    .border(1.dp, GolfColors.Line, RoundedCornerShape(50))
                    .padding(horizontal = GolfSpacing.Lg, vertical = 4.dp),
            )
        }
    }
    if (pickerOpen) {
        ClubPickerOverlay(
            activeClubName = activeClubName,
            clubs = clubs,
            onDismiss = { pickerOpen = false },
            onSelectClub = onSelectClub,
            onAddClub = onAddClub,
            addLabel = "＋ ADD COMPARISON",
            initialTest = true,
        )
    }
}

/**
 * Count badge (design notes §0.3): 18 dp pill — Line/TextSecondary normally,
 * Amber border+text under the 5-kept target. Amber marks count only, never a
 * club. Shared by the COMPARISON card and the TOP-DOWN legend.
 */
@Composable
internal fun FittingCountBadge(count: Int) {
    val low = count < ClubQualityGate.TARGET_KEPT
    Box(
        modifier = Modifier
            .height(18.dp)
            .background(GolfColors.Panel, RoundedCornerShape(50))
            .border(1.dp, if (low) GolfColors.Amber else GolfColors.Line, RoundedCornerShape(50))
            .padding(horizontal = GolfSpacing.Sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$count",
            style = GolfTypography.Status,
            color = if (low) GolfColors.Amber else GolfColors.TextSecondary,
        )
    }
}

/**
 * Filled ACTIVE CLUB trigger — the exact RANGE idiom (design notes §1.1):
 * solid teal ~170×58, big club name with ▾, "ACTIVE CLUB" label beneath.
 * The only filled control in FIT. Tap opens the FIT club picker.
 */
@Composable
private fun FittingActiveClubButton(clubName: String?, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = GolfColors.Teal,
            shape = RoundedCornerShape(GolfSpacing.Sm),
            modifier = Modifier.size(170.dp, 58.dp).clickable(onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "${clubName ?: "—"} ▾",
                    style = GolfTypography.Hero.copy(fontSize = 26.sp),
                    color = GolfColors.Panel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = "ACTIVE CLUB",
            style = GolfTypography.MetricLabel,
            color = GolfColors.TextMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
