# M3 Design System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `:core:designsystem` — a Compose Android library holding the Performance Dark theme, design tokens, and a small component kit — plus a showcase screen in `:app`, per `docs/superpowers/specs/2026-09-20-m3-design-system-design.md`.

**Architecture:** A new `build-logic/` included build provides a `golf-android-library` convention plugin (Android library + Compose + Java/Kotlin 17 in one place; this is the project's 4th Gradle module). `:core:designsystem` carries token objects (colors/typography/spacing/motion), `GolfTheme`, and six composables. `:app` gains only a project dependency and swaps the scaffold screen for the showcase.

**Tech Stack:** Kotlin 2.4.20, AGP 9.4.1 (built-in Kotlin — **never** apply `org.jetbrains.kotlin.android`), Compose BOM 2026.09.00, Material3, JUnit 4.

---

## Conventions (read before every task)

- **Windows PowerShell 5.1** shell: chain with `;` or `if ($?) { }`, NEVER `&&`. Double-quoted strings interpolate — prefer single quotes.
- **JDK is not on PATH.** Every Gradle command must set JAVA_HOME in the SAME command:
  `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat <task>`
- Bash-tool timeout: use **600000 ms** for any Gradle command.
- Run one test class: `.\gradlew.bat :core:designsystem:test --tests "com.hpsmiles.golfsim.core.designsystem.GolfColorsTest"` (repeat pattern for other classes).
- **TDD applies to Tasks 2** (pure token objects + JVM tests). **Task 3 components are declarative composables** — no Robolectric in this project, so there is no unit-testable logic; Task 3's verification is compile + assembly (documented deviation, approved in spec §7: "no screenshot tests").
- All Kotlin source uses `Math.`-free plain Compose/Kotlin APIs; sizes in `dp`/`sp`; **no rounding** anywhere.
- The M0 marker pair in `:app` (`MainActivity.kt` scaffold content is replaced in Task 4 — that file IS the showcase target; `:app`'s `ScaffoldSmokeTest.kt` stays untouched. `:core:ble` and `:core:physics` are untouched by this milestone.
- Reference docs: spec at `docs/superpowers/specs/2026-09-20-m3-design-system-design.md` (authoritative for token values); mockups in `.superpowers/brainstorm/2003-1789875726/content/` (visual reference only, not committed build inputs).

---

### Task 1: build-logic convention plugin + :core:designsystem module skeleton

**Files:**
- Create: `build-logic/settings.gradle.kts`
- Create: `build-logic/build.gradle.kts`
- Create: `build-logic/src/main/kotlin/golf-android-library.gradle.kts`
- Modify: `settings.gradle.kts` (append includeBuild + module include)
- Modify: `gradle/libs.versions.toml` (add 3 library aliases + 1 plugin alias)
- Create: `core/designsystem/build.gradle.kts`

- [ ] **Step 1: Create the build-logic settings file**

```kotlin
// build-logic/settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
```

- [ ] **Step 2: Create the build-logic build file**

```kotlin
// build-logic/build.gradle.kts
plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.compose.gradlePlugin)
}
```

- [ ] **Step 3: Add the plugin-classpath library aliases to the version catalog**

In `gradle/libs.versions.toml`, append to `[libraries]`:

```toml
android-gradlePlugin = { group = "com.android.tools.build", name = "gradle", version.ref = "agp" }
kotlin-gradlePlugin = { group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version.ref = "kotlin" }
kotlin-compose-gradlePlugin = { group = "org.jetbrains.kotlin.plugin.compose", name = "org.jetbrains.kotlin.plugin.compose.gradle.plugin", version.ref = "kotlin" }
compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
```

(BOM-managed `compose-foundation` is needed by the kit's layout code in Task 3; it adds no new external dependency family.)

And append to `[plugins]`:

```toml
golf-android-library = { id = "golf-android-library" }
```

- [ ] **Step 4: Create the convention plugin**

```kotlin
// build-logic/src/main/kotlin/golf-android-library.gradle.kts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    compileSdk = 37
    defaultConfig {
        minSdk = 31
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}
```

(Note: `org.jetbrains.kotlin.android` is NOT applied — AGP 9 has built-in Kotlin. Namespace is deliberately left to each module.)

- [ ] **Step 5: Register the included build and the new module**

In root `settings.gradle.kts`, add BEFORE the `include(":core:ble")` line:

```kotlin
includeBuild("build-logic")
```

And append as the last line:

```kotlin
include(":core:designsystem")
```

- [ ] **Step 6: Create the module build file**

```kotlin
// core/designsystem/build.gradle.kts
plugins {
    alias(libs.plugins.golf.android.library)
}

android {
    namespace = "com.hpsmiles.golfsim.core.designsystem"
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
}
```

- [ ] **Step 7: Verify the skeleton assembles (infra task — no TDD)**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:designsystem:assembleDebug`
Expected: `BUILD SUCCESSFUL` (empty module assembles an AAR; library-debug.aar appears under `core/designsystem/build/outputs/aar/`). Also confirm no other module broke: `.\gradlew.bat :app:assembleDebug` → `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```powershell
git add build-logic settings.gradle.kts gradle/libs.versions.toml core/designsystem/build.gradle.kts
git commit -m "build: convention plugin and core-designsystem module"
```

---

### Task 2: Design tokens + GolfTheme + JVM token tests

**Files:**
- Create: `core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfColors.kt`
- Create: `core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfTypography.kt`
- Create: `core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfSpacing.kt`
- Create: `core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfMotion.kt`
- Create: `core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfTheme.kt`
- Create: `core/designsystem/src/test/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfColorsTest.kt`
- Create: `core/designsystem/src/test/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfTypographyTest.kt`
- Create: `core/designsystem/src/test/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfSpacingTest.kt`
- Create: `core/designsystem/src/test/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfMotionTest.kt`

- [ ] **Step 1: Write the failing token tests**

```kotlin
// core/designsystem/src/test/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfColorsTest.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class GolfColorsTest {
    @Test fun surfacesMatchSpec() {
        assertEquals(Color(0xFF0E1114), GolfColors.Base)
        assertEquals(Color(0xFF0B0E11), GolfColors.Panel)
        assertEquals(Color(0xFF151A1F), GolfColors.Card)
        assertEquals(Color(0xFF28303A), GolfColors.Line)
        assertEquals(Color(0xFF161B21), GolfColors.HoleFairway)
    }

    @Test fun textTiersMatchSpec() {
        assertEquals(Color(0xFFE7EBEE), GolfColors.TextPrimary)
        assertEquals(Color(0xFF8FA0AC), GolfColors.TextSecondary)
        assertEquals(Color(0xFF5C6873), GolfColors.TextMuted)
    }

    @Test fun structuralTealHasAlphaVariants() {
        assertEquals(Color(0xFF3FA7A0), GolfColors.Teal)
        // Color.alpha is Float in Compose, so expected/delta need Float literals.
        // Teal55's alpha byte is 0x8C = 140 -> 140/255 = 0.54902 (prints as
        // "0.55"); pin the byte-exact value so the 1e-6 tolerance holds.
        assertEquals(140f / 255f, GolfColors.Teal55.alpha, 1e-6f)
        assertEquals(0.40f, GolfColors.Teal40.alpha, 1e-6f)
    }

    @Test fun liveAmberHasHaloAndGlow() {
        assertEquals(Color(0xFFF2A93B), GolfColors.Amber)
        // Same Float-literal fix; 0x33 = 51/255 = 0.2, 0x99 = 153/255 = 0.6.
        assertEquals(0.20f, GolfColors.AmberHalo.alpha, 1e-6f)
        assertEquals(0.60f, GolfColors.AmberGlow.alpha, 1e-6f)
    }

    @Test fun comparisonHuesAreWideSpread() {
        assertEquals(Color(0xFF3FA7A0), GolfColors.Comparison.A)
        assertEquals(Color(0xFF5B9DF9), GolfColors.Comparison.B)
        assertEquals(Color(0xFFD66FD8), GolfColors.Comparison.C)
        assertEquals(Color(0xFFF0D64A), GolfColors.Comparison.D)
    }

    @Test fun semanticColorsMatchSpec() {
        assertEquals(Color(0xFF7BC96F), GolfColors.BleArmedGreen)
        assertEquals(Color(0xFFE86A5E), GolfColors.AlertRed)
    }
}
```

```kotlin
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
```

```kotlin
// core/designsystem/src/test/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfSpacingTest.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class GolfSpacingTest {
    @Test fun spacingScaleMatchesSpec() {
        assertEquals(4.dp, GolfSpacing.Xs)
        assertEquals(8.dp, GolfSpacing.Sm)
        assertEquals(12.dp, GolfSpacing.Md)
        assertEquals(16.dp, GolfSpacing.Lg)
        assertEquals(24.dp, GolfSpacing.Xl)
        assertEquals(32.dp, GolfSpacing.Xxl)
    }

    @Test fun componentDimensionsMatchSpec() {
        assertEquals(56.dp, GolfSpacing.NavRailWidth)
        assertEquals(24.dp, GolfSpacing.StatusStripHeight)
        assertEquals(14.dp, GolfSpacing.CornerCard)
    }
}
```

```kotlin
// core/designsystem/src/test/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfMotionTest.kt
package com.hpsmiles.golfsim.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class GolfMotionTest {
    @Test fun durationsMatchSpec() {
        assertEquals(500, GolfMotion.LandingPulseMs)
        assertEquals(700, GolfMotion.TracerDrawMs)
        assertEquals(150, GolfMotion.FadeInMs)
    }

    @Test fun replaySpeedsIncludeToggleSteps() {
        assertEquals(1, GolfMotion.ReplaySpeed1x)
        assertEquals(2, GolfMotion.ReplaySpeed2x)
        assertEquals(4, GolfMotion.ReplaySpeed4x)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:designsystem:test`
Expected: FAIL — `Unresolved reference 'GolfColors'` (and `GolfTypography`, `GolfSpacing`, `GolfMotion`) in all four test classes.

- [ ] **Step 3: Implement the token objects**

```kotlin
// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfColors.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Performance Dark palette (M3 spec §5). Two accents: Teal = structure/history/targets,
 * Amber = the live moment only (last shot, landing, hot values). Amber is RESERVED;
 * it must never be used as a category or surface color.
 */
object GolfColors {
    val Base = Color(0xFF0E1114)
    val Panel = Color(0xFF0B0E11)
    val Card = Color(0xFF151A1F)
    val Line = Color(0xFF28303A)
    val HoleFairway = Color(0xFF161B21)

    val TextPrimary = Color(0xFFE7EBEE)
    val TextSecondary = Color(0xFF8FA0AC)
    val TextMuted = Color(0xFF5C6873)

    val Teal = Color(0xFF3FA7A0)
    val Teal55 = Color(0x8C3FA7A0)
    val Teal40 = Color(0x663FA7A0)

    val Amber = Color(0xFFF2A93B)
    val AmberHalo = Color(0x33F2A93B)
    val AmberGlow = Color(0x99F2A93B)

    val BleArmedGreen = Color(0xFF7BC96F)
    val AlertRed = Color(0xFFE86A5E)

    /** Categorical comparison hues (M7 A/B testing; minimum 90 deg hue separation). */
    object Comparison {
        val A = Color(0xFF3FA7A0)
        val B = Color(0xFF5B9DF9)
        val C = Color(0xFFD66FD8)
        val D = Color(0xFFF0D64A)
    }
}
```

```kotlin
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
```

```kotlin
// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfSpacing.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.ui.unit.dp

/** Spacing scale and component dimensions (M3 spec §5). */
object GolfSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp

    val NavRailWidth = 56.dp
    val StatusStripHeight = 24.dp
    val CornerCard = 14.dp
}
```

```kotlin
// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/GolfMotion.kt
package com.hpsmiles.golfsim.core.designsystem

/**
 * Motion durations in milliseconds (M3 spec §5). Replay plays at real duration by
 * default; 2x/4x are user toggle steps. Easings use Material standard curves at
 * call sites. Metrics NEVER wait for animation (M4 instant-feedback rule).
 */
object GolfMotion {
    const val LandingPulseMs = 500
    const val TracerDrawMs = 700
    const val FadeInMs = 150

    const val ReplaySpeed1x = 1
    const val ReplaySpeed2x = 2
    const val ReplaySpeed4x = 4
}
```

```kotlin
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
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:designsystem:test`
Expected: `BUILD SUCCESSFUL`; test XMLs: GolfColorsTest 6/0/0, GolfTypographyTest 4/0/0, GolfSpacingTest 2/0/0, GolfMotionTest 2/0/0 (14 methods total).

- [ ] **Step 5: Commit**

```powershell
git add core/designsystem/src
git commit -m "feat(core-designsystem): performance dark tokens and theme"
```

---

### Task 3: Component kit (six composables)

**Files:**
- Create: `core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/MetricChip.kt`
- Create: `core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/MetricRow.kt`
- Create: `core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/NavRail.kt`
- Create: `core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/StatusStrip.kt`
- Create: `core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/SectionCard.kt`

(Declarative composables — verification is compile + assemble, per plan Conventions.)

- [ ] **Step 1: Implement the five component files**

```kotlin
// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/MetricChip.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Pill telemetry chip (range-view side panel + status areas). The accent color
 * paints a 3 dp left bar; amber marks the live moment (LAST/hot values only).
 */
@Composable
fun MetricChip(
    label: String,
    value: String,
    unit: String?,
    accent: Color = GolfColors.Teal,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(GolfColors.Panel, RoundedCornerShape(50))
            .padding(horizontal = GolfSpacing.Md, vertical = GolfSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .width(3.dp)
                .height(24.dp)
                .background(accent, RoundedCornerShape(2.dp)),
        )
        Column(modifier = Modifier.padding(start = GolfSpacing.Sm)) {
            Text(
                text = label.uppercase(),
                style = GolfTypography.MetricLabel,
                color = GolfColors.TextSecondary,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = GolfTypography.MetricValue,
                    color = GolfColors.TextPrimary,
                )
                if (unit != null) {
                    Text(
                        text = unit,
                        style = GolfTypography.Unit,
                        color = GolfColors.TextMuted,
                        modifier = Modifier.padding(start = 2.dp, bottom = 1.dp),
                    )
                }
            }
        }
    }
}
```

```kotlin
// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/MetricRow.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Side-panel metric row: label left (secondary), value right (bold).
 * hot = the live moment: amber left bar + amber value. Never use amber for history.
 */
@Composable
fun MetricRow(
    label: String,
    value: String,
    unit: String?,
    hot: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(GolfColors.Panel, RoundedCornerShape(GolfSpacing.CornerCard / 2))
            .padding(horizontal = GolfSpacing.Md, vertical = GolfSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .background(
                    if (hot) GolfColors.Amber else GolfColors.Teal,
                    RoundedCornerShape(2.dp),
                ),
        )
        Text(
            text = label.uppercase(),
            style = GolfTypography.MetricLabel,
            color = GolfColors.TextSecondary,
            modifier = Modifier
                .weight(1f)
                .padding(start = GolfSpacing.Sm),
        )
        Text(
            text = value,
            style = GolfTypography.MetricValue,
            color = if (hot) GolfColors.Amber else GolfColors.TextPrimary,
        )
        if (unit != null) {
            Text(
                text = unit,
                style = GolfTypography.Unit,
                color = GolfColors.TextMuted,
                modifier = Modifier.padding(start = 2.dp, bottom = 1.dp),
            )
        }
    }
}
```

```kotlin
// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/NavRail.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Slim 56 dp navigation rail for landscape (left edge of every screen). */
@Composable
fun NavRail(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .width(GolfSpacing.NavRailWidth)
            .fillMaxHeight()
            .background(GolfColors.Panel)
            .padding(vertical = GolfSpacing.Sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(GolfSpacing.Sm),
        content = content,
    )
}

/** One rail button: selected tints teal, otherwise muted. */
@Composable
fun NavRailButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = GolfTypography.Status,
        color = if (selected) GolfColors.Teal else GolfColors.TextMuted,
        modifier = modifier
            .clip(RoundedCornerShape(GolfSpacing.Sm))
            .background(
                if (selected) GolfColors.Teal40 else GolfColors.Panel,
                RoundedCornerShape(GolfSpacing.Sm),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = GolfSpacing.Sm, vertical = GolfSpacing.Xs)
            .size(width = 40.dp, height = 40.dp),
    )
}
```

```kotlin
// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/StatusStrip.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Bottom status strip: BLE armed dot + session info (avg/sigma/misreads). */
@Composable
fun StatusStrip(
    armed: Boolean,
    info: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(GolfSpacing.StatusStripHeight)
            .background(GolfColors.Panel)
            .padding(horizontal = GolfSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    if (armed) GolfColors.BleArmedGreen else GolfColors.AlertRed,
                    CircleShape,
                ),
        )
        Text(
            text = info,
            style = GolfTypography.Status,
            color = GolfColors.TextMuted,
            modifier = Modifier.padding(start = GolfSpacing.Sm),
        )
    }
}
```

(`Box` and `size` imports would collide in a single import list above; the implementer may let the IDE organize imports — behavior must stay identical.)

```kotlin
// core/designsystem/src/main/kotlin/com/hpsmiles/golfsim/core/designsystem/SectionCard.kt
package com.hpsmiles.golfsim.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Card panel: card background, 14 dp corners, title + content slot. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .background(GolfColors.Card, RoundedCornerShape(GolfSpacing.CornerCard))
            .padding(GolfSpacing.Lg),
    ) {
        Text(
            text = title,
            style = GolfTypography.ScreenTitle,
            color = GolfColors.TextPrimary,
        )
        Column(
            modifier = Modifier.padding(top = GolfSpacing.Sm),
            content = content,
        )
    }
}
```

- [ ] **Step 2: Verify the kit assembles**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:designsystem:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```powershell
git add core/designsystem/src/main
git commit -m "feat(core-designsystem): metric chip row nav rail status strip and section card"
```

---

### Task 4: :app showcase screen

**Files:**
- Modify: `app/build.gradle.kts` (add one dependency line)
- Create: `app/src/main/kotlin/com/hpsmiles/golfsim/ShowcaseScreen.kt`
- Modify: `app/src/main/kotlin/com/hpsmiles/golfsim/MainActivity.kt` (replace scaffold content)

- [ ] **Step 1: Add the project dependency**

In `app/build.gradle.kts` dependencies block, add as the FIRST line of the block:

```kotlin
    implementation(project(":core:designsystem"))
```

- [ ] **Step 2: Create the showcase screen**

```kotlin
// app/src/main/kotlin/com/hpsmiles/golfsim/ShowcaseScreen.kt
package com.hpsmiles.golfsim

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTheme
import com.hpsmiles.golfsim.core.designsystem.MetricChip
import com.hpsmiles.golfsim.core.designsystem.MetricRow
import com.hpsmiles.golfsim.core.designsystem.NavRail
import com.hpsmiles.golfsim.core.designsystem.NavRailButton
import com.hpsmiles.golfsim.core.designsystem.SectionCard
import com.hpsmiles.golfsim.core.designsystem.StatusStrip

/**
 * M3 acceptance surface: every kit component in its states, laid out like the
 * range screen (rail left, status strip bottom, card stack in between).
 */
@Composable
fun DesignSystemShowcase() {
    GolfTheme {
        // Edge-to-edge (enforced for targetSdk 35+): keep the Base paint
        // full-bleed, but keep content clear of status/nav bars and cutouts.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(GolfColors.Base)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(modifier = Modifier.weight(1f)) {
                NavRail {
                    NavRailButton(label = "RANGE", selected = true, onClick = {})
                    NavRailButton(label = "BAG", selected = false, onClick = {})
                    NavRailButton(label = "STATS", selected = false, onClick = {})
                    NavRailButton(label = "SET", selected = false, onClick = {})
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(GolfSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(GolfSpacing.Lg),
                ) {
                    SectionCard(title = "Telemetry") {
                        Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                                MetricChip("carry", "163", "M")
                                MetricChip("total", "171", "M")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                                MetricChip("ball", "68.6", "MPH")
                                MetricChip("spin", "5 620", "RPM")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                                MetricChip("launch", "16.3", "\u00B0")
                                MetricChip("axis", "-5.6", "\u00B0")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                                MetricChip("last", "163", "M", accent = GolfColors.Amber)
                                MetricChip("club", "7", "IRON")
                            }
                        }
                    }
                    SectionCard(title = "Session") {
                        Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                            MetricRow("carry", "163.4", "M", hot = true)
                            MetricRow("total", "171.0", "M")
                            MetricRow("ball speed", "68.6", "MPH")
                            MetricRow("spin", "5 620", "RPM")
                            MetricRow("launch", "16.3", "\u00B0")
                        }
                    }
                    SectionCard(title = "Comparison hues") {
                        Column(verticalArrangement = Arrangement.spacedBy(GolfSpacing.Sm)) {
                            listOf(
                                "A" to GolfColors.Comparison.A,
                                "B" to GolfColors.Comparison.B,
                                "C" to GolfColors.Comparison.C,
                                "D" to GolfColors.Comparison.D,
                            ).forEach { (name, hue) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(hue, RoundedCornerShape(4.dp)),
                                    )
                                    Text(
                                        text = name,
                                        modifier = Modifier.padding(start = GolfSpacing.Sm),
                                        color = GolfColors.TextSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            StatusStrip(armed = true, info = "BLE ARMED - AVG 163 M - SD 4.1 - MISREADS 2")
        }
    }
}
```

**Note:** every listed component appears in the showcase with the values above; import organization may be auto-fixed by the IDE as long as behavior stays identical.

- [ ] **Step 3: Point MainActivity at the showcase**

Replace the whole `setContent` block in `MainActivity.kt` so the file reads:

```kotlin
package com.hpsmiles.golfsim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DesignSystemShowcase()
        }
    }
}
```

(Removes the old MaterialTheme/darkColorScheme/Surface/Text scaffold imports; GolfTheme now owns darkness.)

- [ ] **Step 4: Verify the app builds**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` (app-debug.apk rebuilt).

- [ ] **Step 5: Commit**

```powershell
git add app/build.gradle.kts app/src/main/kotlin/com/hpsmiles/golfsim
git commit -m "feat(app): design system showcase replaces scaffold"
```

---

### Task 5: Full build, PR, CI — then the tablet gate

**Files:** none (build/push/PR only; no source changes)

- [ ] **Step 1: Full clean build across all 4 modules**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat build`
Expected: `BUILD SUCCESSFUL`. Test census: `:core:designsystem` 14 (GolfColors 6, GolfTypography 4, GolfSpacing 2, GolfMotion 2), `:app` ScaffoldSmokeTest 1, `:core:ble` 31, `:core:physics` 65 — **111 total, 0 failures**. Confirm no ScaffoldSmokeTest was touched in `:app` and both core modules' counts match M1/M2 exactly.

- [ ] **Step 2: Commit any leftover and push the branch**

```powershell
git push -u origin m3-design-system
```

- [ ] **Step 3: Open the PR**

```powershell
gh pr create --title "M3: Design system" --body "## Summary
- :core:designsystem Compose library: Performance Dark theme + color/typography/spacing/motion tokens + component kit (MetricChip, MetricRow, NavRail, StatusStrip, SectionCard)
- build-logic convention plugin (golf-android-library) for shared Android-library config
- :app scaffold replaced by the design-system showcase screen

## Test Plan
- [ ] gradlew build green across all 4 modules (111 tests)
- [ ] CI build check green on this PR
- [ ] Tablet visual check: showcase installs (gradlew :app:installDebug) and renders dark, readable, chips/rows/colours per mockups"
```

- [ ] **Step 4: Wait for CI, then tick the second checkbox**

```powershell
Start-Sleep 120
gh pr checks
```
Expected: `build  pass` (single poll; if still running, sleep 90 and poll once more — never `--watch`). Then:

```powershell
gh pr edit <PR-NUMBER> --body "## Summary
- :core:designsystem Compose library: Performance Dark theme + color/typography/spacing/motion tokens + component kit (MetricChip, MetricRow, NavRail, StatusStrip, SectionCard)
- build-logic convention plugin (golf-android-library) for shared Android-library config
- :app scaffold replaced by the design-system showcase screen

## Test Plan
- [ ] gradlew build green across all 4 modules (111 tests)
- [x] CI build check green on this PR
- [ ] Tablet visual check: showcase installs (gradlew :app:installDebug) and renders dark, readable, chips/rows/colours per mockups"
```

- [ ] **Step 5: Tablet install (M0-style user gate — do NOT merge before it)**

Ask the user to connect the Lenovo tablet (USB debugging on). Then:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:installDebug
```
Expected: `Installed on 1 device`. The user then visually confirms: dark Performance Dark background, amber/red status strip at the bottom with a green BLE dot, teal rail on the left, telemetry chips, comparison swatches visible. This is the M3 exit criterion (spec §1.3). Only after the user's accept does this plan hand off to finishing-a-development-branch (merge per M0-M2 precedent: `gh pr merge --merge --delete-branch`).

---

## Self-review checklist (for the plan executor's controller)

1. **Spec coverage:** spec §4 files = Tasks 1-4 (all 10 named files created; no extras beyond build-logic infra); §5 token values = Task 2 verbatim; §6 component contracts = Task 3 (MetricChip/MetricRow/NavRail+NavRailButton/StatusStrip/SectionCard/GolfTheme); §7 verification = Tasks 1/2/5 (convention plugin details, JVM token tests, CI unchanged, tablet gate); showcase = Task 4 (spec §6 showcase layout). Exit criteria §1.1-1.3 all covered.
2. **Placeholders:** none — the placeholder-guard import in Task 4 Step 2 is deliberate (a blind-paste tripwire, flagged with a removal note).
3. **Type consistency:** GolfColors/GolfTypography/GolfSpacing/GolfMotion names identical across Tasks 2-4; GolfTheme used by ShowcaseScreen in Task 4; `libs.plugins.golf.android.library` alias defined in Task 1 Step 3 and used in Task 1 Step 6. Test-class names in Task 2 Step 4 match the files in the header.


