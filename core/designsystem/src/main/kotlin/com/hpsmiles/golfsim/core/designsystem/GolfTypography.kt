// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfTypography.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Roboto-only scale (M3 spec §5). All metric styles are tabular ("tnum") so live
 * numbers do not jitter as digits change. Uppercase for labels is applied at the
 * call site (e.g. MetricChip uppercases its label); TextStyle has no case transform.
 */
object GolfTypography {
    val Hero = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        fontFeatureSettings = "tnum",
    )

    val MetricValue = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        fontFeatureSettings = "tnum",
    )

    val Unit = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        fontFeatureSettings = "tnum",
    )

    val MetricLabel = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
    )

    val Status = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
    )

    val ScreenTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
    )

    val Body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    )

    val BodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    )

    /** Material3 slots used by kit components. */
    internal val Material = Typography(
        titleMedium = ScreenTitle,
        titleSmall = MetricValue,
        bodyLarge = Body,
        bodyMedium = BodySmall,
        labelSmall = MetricLabel,
    )
}
