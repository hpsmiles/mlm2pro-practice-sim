// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfTheme.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable

private val GolfColorScheme = darkColorScheme(
    primary = GolfColors.Teal,
    background = GolfColors.Base,
    surface = GolfColors.Base,
    surfaceVariant = GolfColors.Card,
    outline = GolfColors.Line,
    tertiary = GolfColors.Amber,
    error = GolfColors.AlertRed,
    onSurface = GolfColors.TextPrimary,
    onSurfaceVariant = GolfColors.TextSecondary,
)

private val GolfShapes = Shapes(small = RoundedCornerShape(GolfSpacing.CornerCard))

/** The app-wide dark theme. All screens wrap their content in GolfTheme. */
@Composable
fun GolfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GolfColorScheme,
        typography = GolfTypography.Material,
        shapes = GolfShapes,
        content = content,
    )
}
