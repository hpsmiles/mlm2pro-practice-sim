// core/designsystem/src/test/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfTypographyTest.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GolfTypographyTest {
    @Test fun heroIsTwentyTwoBold() {
        assertEquals(22.sp, GolfTypography.Hero.fontSize)
        assertEquals(FontWeight.Bold, GolfTypography.Hero.fontWeight)
    }

    @Test fun metricValueIsSeventeenBoldTabular() {
        assertEquals(17.sp, GolfTypography.MetricValue.fontSize)
        assertEquals(FontWeight.Bold, GolfTypography.MetricValue.fontWeight)
        assertTrue(GolfTypography.MetricValue.fontFeatureSettings!!.contains("tnum"))
        assertTrue(GolfTypography.Hero.fontFeatureSettings!!.contains("tnum"))
    }

    @Test fun supportingStylesMatchSpec() {
        assertEquals(10.sp, GolfTypography.Unit.fontSize)
        assertEquals(11.sp, GolfTypography.MetricLabel.fontSize)
        assertEquals(10.sp, GolfTypography.Status.fontSize)
        assertEquals(15.sp, GolfTypography.ScreenTitle.fontSize)
        assertEquals(FontWeight.SemiBold, GolfTypography.ScreenTitle.fontWeight)
        assertEquals(14.sp, GolfTypography.Body.fontSize)
        assertEquals(13.sp, GolfTypography.BodySmall.fontSize)
    }

    @Test fun metricLabelHasWideTracking() {
        assertEquals(0.8.sp, GolfTypography.MetricLabel.letterSpacing)
    }
}
