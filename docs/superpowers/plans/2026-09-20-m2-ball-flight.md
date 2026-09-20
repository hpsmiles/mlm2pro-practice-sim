# M2 Ball-Flight Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the deterministic ball-flight engine in `:core:physics` — launch conditions (speed, angles, spin) in, ShotResult (carry, rollout, total, side, apex, flight time) out — meeting the tiered regression gate of the M2 spec.

**Architecture:** Layered pipeline: `BallFlightEngine` (entry point) → `FlightSolver` (symplectic Euler ODE with `AerodynamicModel` Cd/Cl/spin-decay) → `BounceRollModel` (openfairway-faithful bounce loop + roll to rest on `Surface`s from a `SurfaceProvider`). Pure JVM Kotlin, JUnit 4, no Android dependencies, no `:core:ble` dependency, no build-file changes. The numerics are a faithful port of the verified Java calibration prototype (`CalOf.java`) with identical constants, operation order, and IEEE-754 double arithmetic — the Kotlin engine must reproduce the spec §8 pinned table bit-for-bit-identically (±0.1 yd / ±0.05 m / ±0.01 s from printed precision). Wind, launch direction, and spin axis are orthogonal additions (zero on all tour-gate runs).

**Tech Stack:** Kotlin 2.4.20 (JVM), JUnit 4.13.2, java.lang.Math only (portable, deterministic).

**Sources (read before implementing tasks that cite them):**
- Spec: `docs/superpowers/specs/2026-09-20-m2-ball-flight-design.md` (315 lines) — §5 aero, §6 ground, §7 validation, §8 pinned gate table.
- Numeric model of truth: `C:/Users/harry/AppData/Local/Temp/opencode/gfsim/CalOf.java` (296 lines) — every Kotlin equation below is a line-for-line translation of it.
- Harness pattern for Task 7: `core/ble/src/test/kotlin/com/hpsmiles/golfsim/core/ble/GoldenFixturesTest.kt`.

**Environment conventions (every task):**
- Windows PowerShell 5.1: chain with `;` or `if ($?) { }` — NEVER `&&`.
- JDK not on PATH — every Gradle command in one line: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat <tasks>`
- bash tool timeout 600000 for all Gradle commands.
- Module tests: `.\gradlew.bat :core:physics:test --tests "com.hpsmiles.golfsim.core.physics.<TestClass>"`
- M0 marker files `BallFlight.kt` and `ScaffoldSmokeTest.kt` in `:core:physics` are NOT touched until Task 8. `:app` and `:core:ble` are never touched.
- SI units and Doubles everywhere; **no rounding anywhere** in main-source code.
- `Math.*` calls: use `java.lang.Math` explicitly (`Math.hypot`, `Math.signum`, `Math.atan2`, `Math.exp`) — identical JVM intrinsics to the prototype. Free functions `min`, `max`, `abs`, `sin`, `cos` may be used only where the prototype used equivalent plain arithmetic… **safest rule: write `Math.` prefix for every math function** so the operation matches the prototype exactly.

---

### Task 1: LaunchConditions + Environment + BallPhysical + Vec3

**Files:**
- Create: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/Vec3.kt`
- Create: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BallPhysical.kt`
- Create: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/LaunchConditions.kt`
- Create: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/Environment.kt`
- Test: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/LaunchConditionsTest.kt`
- Test: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/EnvironmentTest.kt`

- [ ] **Step 1: Write the failing tests**

`LaunchConditionsTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LaunchConditionsTest {

    @Test
    fun validShotConstructs() {
        val lc = LaunchConditions(
            ballSpeedMps = 76.67,
            launchAngleDeg = 10.4,
            spinRpm = 2545,
            launchDirDeg = -3.0,
            spinAxisDeg = -5.6,
        )
        assertTrue(lc.spinRpm == 2545)
    }

    @Test
    fun ballSpeedTooLowThrows() {
        try {
            LaunchConditions(ballSpeedMps = 0.4, launchAngleDeg = 10.0, spinRpm = 2000)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun ballSpeedTooHighThrows() {
        try {
            LaunchConditions(ballSpeedMps = 100.1, launchAngleDeg = 10.0, spinRpm = 2000)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun negativeSpinThrows() {
        try {
            LaunchConditions(ballSpeedMps = 70.0, launchAngleDeg = 10.0, spinRpm = -1)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun spinAboveTwelveThousandThrows() {
        try {
            LaunchConditions(ballSpeedMps = 70.0, launchAngleDeg = 10.0, spinRpm = 12001)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun launchAngleBoundsThrow() {
        try {
            LaunchConditions(ballSpeedMps = 70.0, launchAngleDeg = -0.1, spinRpm = 2000)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
        try {
            LaunchConditions(ballSpeedMps = 70.0, launchAngleDeg = 85.5, spinRpm = 2000)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun directionAndAxisBoundsThrow() {
        try {
            LaunchConditions(ballSpeedMps = 70.0, launchAngleDeg = 10.0, spinRpm = 2000, launchDirDeg = 91.0)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
        try {
            LaunchConditions(ballSpeedMps = 70.0, launchAngleDeg = 10.0, spinRpm = 2000, spinAxisDeg = -90.5)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }
}
```

`EnvironmentTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class EnvironmentTest {

    @Test
    fun defaultAirDensityIsOnePointTwoFive() {
        assertEquals(1.225, Environment().airDensity, 0.001)
    }

    @Test
    fun hotLowPressureDayIsLessDense() {
        val env = Environment(temperatureC = 30.0, pressureHpa = 1000.0)
        assertEquals(1.1492, env.airDensity, 0.001)
    }

    @Test
    fun excessiveWindThrows() {
        try {
            Environment(windXmps = 41.0)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun negativePressureThrows() {
        try {
            Environment(pressureHpa = -1.0)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:physics:test --tests "com.hpsmiles.golfsim.core.physics.LaunchConditionsTest" --tests "com.hpsmiles.golfsim.core.physics.EnvironmentTest"
```

Expected: FAIL with `Unresolved reference 'LaunchConditions'` / `Unresolved reference 'Environment'` (line numbers point at the new test files). BUILD FAILED.

- [ ] **Step 3: Write minimal implementation**

`Vec3.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

/**
 * Minimal immutable 3D vector for the ball-flight engine.
 * Scene frame: x lateral (right, looking downrange), y down-range, z up.
 */
data class Vec3(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = Math.sqrt(x * x + y * y + z * z)
}
```

`BallPhysical.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

/** Ball + world physical constants (R&A/USGA maximums; SI). Spec §6. */
object BallPhysical {
    const val MASS_KG = 0.04593
    const val RADIUS_M = 0.021335
    const val DIAMETER_M = 0.04267
    val AREA_M2 = Math.PI * RADIUS_M * RADIUS_M
    const val GRAVITY_MPS2 = 9.81
    const val MU_AIR = 1.7894e-5
    const val METERS_PER_YARD = 0.9144
}
```

`LaunchConditions.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

/**
 * Measured launch conditions straight from the launch monitor (M4 wires
 * [com.hpsmiles.golfsim.core.ble.BallData] onto this — speeds are already m/s
 * tenths there, no unit conversion drift).
 *
 * Sign conventions (unverified against live capture — M1 spec §9 list):
 * +launchDirDeg pushes toward +x; +spinAxisDeg tilts the spin axis so the
 * ball curves toward +x. Both preserved raw through the decoder.
 */
data class LaunchConditions(
    val ballSpeedMps: Double,
    val launchAngleDeg: Double,
    val spinRpm: Int,
    val launchDirDeg: Double = 0.0,
    val spinAxisDeg: Double = 0.0,
) {
    init {
        // Programmer errors fail fast at the construction boundary (spec §7).
        require(ballSpeedMps in 0.5..100.0) { "ball speed $ballSpeedMps m/s outside [0.5, 100]" }
        require(spinRpm in 0..12000) { "spin $spinRpm rpm outside [0, 12000]" }
        require(launchAngleDeg in 0.0..85.0) { "launch angle $launchAngleDeg deg outside [0, 85]" }
        require(launchDirDeg in -90.0..90.0) { "launch direction $launchDirDeg deg outside [-90, 90]" }
        require(spinAxisDeg in -90.0..90.0) { "spin axis $spinAxisDeg deg outside [-90, 90]" }
    }
}
```

`Environment.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

/**
 * Ambient conditions. Air density via ideal-gas law: rho = P / (287.05 · T).
 * Wind is horizontal in the scene frame (x lateral, y down-range).
 */
data class Environment(
    val windXmps: Double = 0.0,
    val windYmps: Double = 0.0,
    val temperatureC: Double = 15.0,
    val pressureHpa: Double = 1013.25,
) {
    init {
        require(Math.hypot(windXmps, windYmps) <= 40.0) {
            "wind ${Math.hypot(windXmps, windYmps)} m/s exceeds 40"
        }
        require(temperatureC > -40.0 && temperatureC < 60.0) { "temperature $temperatureC C outside [-40, 60]" }
        require(pressureHpa > 0.0) { "pressure must be positive, got $pressureHpa hPa" }
    }

    val airDensity: Double
        get() = (pressureHpa * 100.0) / (287.05 * (temperatureC + 273.15))
}
```

- [ ] **Step 4: Run tests to verify they pass**

Same command as Step 2. Expected: `BUILD SUCCESSFUL`; XML `core/physics/build/test-results/test/TEST-…LaunchConditionsTest.xml` shows `tests="7" failures="0" errors="0"` and `TEST-…EnvironmentTest.xml` shows `tests="4" failures="0" errors="0"`.

- [ ] **Step 5: Commit**

```powershell
git add core/physics/src; git commit -m "feat(core-physics): launch conditions, environment, ball constants"
```

---

### Task 2: AerodynamicModel (openfairway-faithful port)

**Files:**
- Create: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/AerodynamicModel.kt`
- Test: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/AerodynamicModelTest.kt`

- [ ] **Step 1: Write the failing test**

`AerodynamicModelTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.exp

/**
 * Numeric pins from the openfairway FlightProfile Default constants and the
 * Bearman-binned lift polynomials (spec §5). Tolerance 1e-6 catches any
 * swapped or mistyped constant; it tolerates printed-precision only.
 */
class AerodynamicModelTest {

    @Test
    fun cdPlateauAboveTwoHundredK() {
        assertEquals(0.200, AerodynamicModel.cdRaw(250000.0), 1e-9)
    }

    @Test
    fun cdCubicAtHundredK() {
        // 1.1948 - 2.09661e-5*1e5 + 1.42472e-10*1e10 - 3.14383e-16*1e15
        assertEquals(0.208527, AerodynamicModel.cdRaw(100000.0), 1e-6)
    }

    @Test
    fun cdFloorBelowThirtyK() {
        assertEquals(0.38, AerodynamicModel.cdRaw(20000.0), 1e-9)
    }

    @Test
    fun cdBlendMidpointAtFortyK() {
        // smoothstep(0.5) = 0.5 -> lerp(0.38, 0.4632, 0.5) = 0.4216
        assertEquals(0.4216, AerodynamicModel.cdRaw(40000.0), 1e-6)
    }

    @Test
    fun spinDragMultIsOneAtZeroSpin() {
        assertEquals(1.0, AerodynamicModel.spinDragMult(0.0, 250000.0), 1e-12)
    }

    @Test
    fun spinDragMultUltraHighSpinCap() {
        // S=0.8, Re=250k: base 1+8*0.64=6.12; caps chain -> 1.21
        assertEquals(1.21, AerodynamicModel.spinDragMult(0.8, 250000.0), 1e-6)
    }

    @Test
    fun dragCoefficientAppliesCdMinFloor() {
        // Re 250k, S=0: raw 0.200 -> floored to 0.223; vla 20 deactivates HL boost
        assertEquals(0.223, AerodynamicModel.dragCoefficient(250000.0, 0.0, 20.0), 1e-9)
    }

    @Test
    fun liftHighReHillSaturation() {
        // S=0.2, Re=100k: clMax(0.2)=0.268; 0.268*0.2*16/(1+3.2) = 0.204190476
        assertEquals(0.204190476, AerodynamicModel.clOf(100000.0, 0.2), 1e-6)
    }

    @Test
    fun liftSeventyKBin() {
        // Cl_70k(0.15) = 0.0496189 + 0.00211396*0.15 + 2.34201*0.0225
        assertEquals(0.102631219, AerodynamicModel.clOf(70000.0, 0.15), 1e-6)
    }

    @Test
    fun liftIsZeroBelowThirtyKRe() {
        assertEquals(0.0, AerodynamicModel.clOf(25000.0, 0.2), 1e-12)
    }

    @Test
    fun liftIsZeroAtZeroSpin() {
        assertEquals(0.0, AerodynamicModel.clOf(100000.0, 0.0), 1e-12)
    }

    @Test
    fun liftBinIsContinuousAtFiftyK() {
        val below = AerodynamicModel.clOf(49999.9, 0.15)
        val above = AerodynamicModel.clOf(50000.1, 0.15)
        assertEquals(below, above, 1e-9)
    }

    @Test
    fun highLaunchDragBoostEdges() {
        assertEquals(1.24, AerodynamicModel.highLaunchDragScale(31.5, 0.7), 1e-12)
        assertEquals(1.0, AerodynamicModel.highLaunchDragScale(20.0, 0.7), 1e-12)
    }

    @Test
    fun lowLaunchLiftRecoveryEdges() {
        assertEquals(1.08, AerodynamicModel.lowLaunchLiftScale(6.5, 0.18, 110000.0), 1e-12)
        assertEquals(1.0, AerodynamicModel.lowLaunchLiftScale(10.0, 0.18, 110000.0), 1e-12)
    }

    @Test
    fun midSpinClBoostIsDisabled() {
        // msBoostMax = 0 (M2 tuned value) -> identity at every spin ratio
        assertEquals(1.0, AerodynamicModel.midSpinClBoost(0.24), 1e-12)
    }

    @Test
    fun spinDecayIsExponentialWithTwelveSecondTau() {
        assertEquals(100.0 * exp(-1.0 / 12.0), AerodynamicModel.spinDecay(100.0, 1.0), 1e-12)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:physics:test --tests "com.hpsmiles.golfsim.core.physics.AerodynamicModelTest"
```

Expected: FAIL with `Unresolved reference 'AerodynamicModel'`. BUILD FAILED.

- [ ] **Step 3: Write minimal implementation**

`AerodynamicModel.kt` — faithful port of CalOf.java lines 19–134 (identical constants, identical branch structure; NO correction for style, only Kotlin syntax):

```kotlin
package com.hpsmiles.golfsim.core.physics

/**
 * Drag/lift/spin-decay for a dimpled golf ball.
 *
 * Reynolds-piecewise drag and Bearman-binned lift polynomials, structure and
 * constants from openfairway's FlightProfile/AerodynamicsModel (MIT — see
 * spec §2/§5; ultimate provenance Bearman & Harvey 1976, Lyu et al. 2018).
 * Tuned named parameters (spec §5, calibration sweep 2026-09-20):
 * sdCoeff = 8.0, sdCap = 1.55, msBoostMax = 0.0, spin tau = 12 s.
 * OF Default reference values: 4.0 / 1.20 / 0.45 / 5 s.
 */
object AerodynamicModel {

    // ---- FlightProfile Default constants (verbatim) ----
    private const val CD_POLY_A = 1.1948
    private const val CD_POLY_B = -2.09661e-5
    private const val CD_POLY_C = 1.42472e-10
    private const val CD_POLY_D = -3.14383e-16
    private const val HIGH_RE_CD_CAP = 0.200
    private const val LOW_RE_CD_FLOOR = 0.38
    private const val LOW_RE_BLEND_START = 30000.0
    private const val CD_AT_50K = 0.4632
    const val CD_MIN = 0.223
    private const val CLMAX_BASE = 0.268
    private const val CLMAX_HI = 0.32
    private const val CLMAX_SRS = 0.35
    private const val CLMAX_SRE = 0.50

    // Tuned (M2 calibration): sdCoeff 8.0, sdCap 1.55, msBoostMax 0.0
    const val SD_COEFF = 8.0
    const val SD_CAP = 1.55
    const val MS_BOOST_MAX = 0.0
    private const val SD_HIGH_SPIN_MAX = 1.03
    private const val SD_ULTRA_MAX = 1.21
    private const val HSD_SRS = 0.30
    private const val HSD_SRE = 0.48
    private const val HSD_RELIEF_RE_FULL = 90000.0
    private const val HSD_RELIEF_RE_ZERO = 105000.0
    private const val UHSD_SRS = 0.57
    private const val UHSD_SRE = 0.77
    private const val PROG_SRS = 0.33
    private const val PROG_SRE = 0.50
    private const val PROG_BOOST_MAX = 0.25
    private const val HIGH_RE_START = 75000.0
    private const val HRE_GAIN = 16.0
    private const val HRE_MID_GAIN = 16.0
    private const val HRE_RED_S = 0.10
    private const val HRE_RED_E = 0.18
    private const val HRE_REC_S = 0.26
    private const val HRE_REC_E = 0.40
    private const val HS_ATT_S = 0.45
    private const val HS_ATT_E = 0.55
    private const val HS_ATT_MAX = 0.09
    private const val UHS_ATT_S = 0.58
    private const val UHS_ATT_E = 0.85
    private const val UHS_ATT_MAX = 0.10
    private const val LOWRE_HS_ATT_MAX = 0.10
    private const val LOWRE_UHS_ATT_MAX = 0.06
    private const val LL_MAX = 1.08
    private const val LL_VLA_FULL = 6.5
    private const val LL_VLA_ZERO = 9.5
    private const val LL_RE_S = 85000.0
    private const val LL_RE_E = 110000.0
    private const val LL_SRS = 0.18
    private const val LL_SRE = 0.22
    private const val HL_BOOST_MAX = 1.24
    private const val HL_VLA_S = 24.5
    private const val HL_VLA_F = 31.5
    private const val HL_SRS = 0.50
    private const val HL_SRE = 0.70
    private const val MS_BOOST_SRS = 0.17
    private const val MS_BOOST_SRE = 0.31

    /** Spin-decay time constant (s) — tuned M2 value, verify vs M4 live captures. */
    const val SPIN_TAU_SEC = 12.0

    private fun smooth01(t: Double): Double {
        if (t <= 0.0) return 0.0
        if (t >= 1.0) return 1.0
        return t * t * (3.0 - 2.0 * t)
    }

    private fun ss01(v: Double, a: Double, b: Double): Double {
        val rng = b - a
        if (Math.abs(rng) < 1e-9) return if (v >= b) 1.0 else 0.0
        return smooth01((v - a) / rng)
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    private fun clampd(v: Double, lo: Double, hi: Double) = Math.max(lo, Math.min(hi, v))

    /** Reynolds-piecewise drag without spin/launch modifiers. */
    fun cdRaw(re: Double): Double {
        if (re > 200000.0) return HIGH_RE_CD_CAP
        if (re >= 50000.0) return CD_POLY_A + CD_POLY_B * re + CD_POLY_C * re * re + CD_POLY_D * re * re * re
        if (re <= LOW_RE_BLEND_START) return LOW_RE_CD_FLOOR
        return lerp(LOW_RE_CD_FLOOR, CD_AT_50K, ss01(re, LOW_RE_BLEND_START, 50000.0))
    }

    /** Spin-dependent drag multiplier with openfairway's cap-relief structure. */
    fun spinDragMult(sr: Double, re: Double): Double {
        if (sr <= 0.0) return 1.0
        val highSpinW = ss01(sr, HSD_SRS, HSD_SRE)
        val reReliefW = 1.0 - ss01(re, HSD_RELIEF_RE_FULL, HSD_RELIEF_RE_ZERO)
        var cap = lerp(SD_CAP, SD_HIGH_SPIN_MAX, highSpinW * reReliefW)
        cap += PROG_BOOST_MAX * ss01(sr, PROG_SRS, PROG_SRE)
        cap = lerp(cap, SD_ULTRA_MAX, ss01(sr, UHSD_SRS, UHSD_SRE))
        return Math.min(1.0 + SD_COEFF * sr * sr, cap)
    }

    fun highLaunchDragScale(vlaDeg: Double, sr: Double): Double {
        val lf = ss01(vlaDeg, HL_VLA_S, HL_VLA_F)
        if (lf <= 0.0) return 1.0
        val sf = ss01(sr, HL_SRS, HL_SRE)
        if (sf <= 0.0) return 1.0
        return lerp(1.0, HL_BOOST_MAX, lf * sf)
    }

    fun lowLaunchLiftScale(vlaDeg: Double, sr: Double, re: Double): Double {
        val lf = ss01(LL_VLA_ZERO - vlaDeg, 0.0, LL_VLA_ZERO - LL_VLA_FULL)
        if (lf <= 0.0) return 1.0
        val rf = ss01(re, LL_RE_S, LL_RE_E)
        if (rf <= 0.0) return 1.0
        val sf = 1.0 - ss01(sr, LL_SRS, LL_SRE)
        if (sf <= 0.0) return 1.0
        return lerp(1.0, LL_MAX, lf * rf * sf)
    }

    fun midSpinClBoost(sr: Double): Double {
        val t = ss01(sr, MS_BOOST_SRS, MS_BOOST_SRE)
        return 1.0 + MS_BOOST_MAX * (t * (1.0 - t) * 4.0)
    }

    fun clMax(sr: Double) = lerp(CLMAX_BASE, CLMAX_HI, ss01(sr, CLMAX_SRS, CLMAX_SRE))

    private fun clRe50k(s: Double) = 0.0472121 + 2.84795 * s - 23.4342 * s * s + 45.4849 * s * s * s
    private fun clRe60k(s: Double) = 0.320524 - 4.7032 * s + 14.0613 * s * s
    private fun clRe65k(s: Double) = 0.266667 - 4.0 * s + 13.3333 * s * s
    private fun clRe70k(s: Double) = 0.0496189 + 0.00211396 * s + 2.34201 * s * s

    private fun highReGain(sr: Double): Double {
        if (sr <= HRE_RED_S) return HRE_GAIN
        if (sr < HRE_RED_E) return lerp(HRE_GAIN, HRE_MID_GAIN, ss01(sr, HRE_RED_S, HRE_RED_E))
        if (sr <= HRE_REC_S) return HRE_MID_GAIN
        if (sr < HRE_REC_E) return lerp(HRE_MID_GAIN, HRE_GAIN, ss01(sr, HRE_REC_S, HRE_REC_E))
        return HRE_GAIN
    }

    private fun clHighRe(sr: Double): Double {
        val g = highReGain(sr)
        return clMax(sr) * sr * g / (1.0 + sr * g)
    }

    private fun attHigh(cl: Double, sr: Double): Double {
        val a1 = 1.0 - HS_ATT_MAX * ss01(sr, HS_ATT_S, HS_ATT_E)
        val a2 = 1.0 - UHS_ATT_MAX * ss01(sr, UHS_ATT_S, UHS_ATT_E)
        return cl * a1 * a2
    }

    private fun attLowRe(cl: Double, sr: Double): Double {
        val a1 = 1.0 - LOWRE_HS_ATT_MAX * ss01(sr, HS_ATT_S, HS_ATT_E)
        val a2 = 1.0 - LOWRE_UHS_ATT_MAX * ss01(sr, UHS_ATT_S, UHS_ATT_E)
        return cl * a1 * a2
    }

    private fun binVal(i: Int, s: Double): Double = when (i) {
        0 -> clRe50k(s)
        1 -> clRe60k(s)
        2 -> clRe65k(s)
        3 -> clRe70k(s)
        else -> clHighRe(s)
    }

    /** Full lift coefficient at (Re, spin ratio S) before launch modifiers. */
    fun clOf(re: Double, sr: Double): Double {
        if (sr <= 0.0) return 0.0
        val cmax = clMax(sr)
        if (re < 50000.0) {
            if (re <= 30000.0) return 0.0
            val lowReT = smooth01((re - 30000.0) / 20000.0)
            return attLowRe(clampd(clRe50k(sr), 0.0, cmax) * lowReT, sr)
        }
        if (re >= HIGH_RE_START) {
            return attHigh(clampd(clHighRe(sr), 0.0, cmax), sr)
        }
        val anchors = intArrayOf(50000, 60000, 65000, 70000, 75000)
        var hi = 4
        for (i in 0..4) {
            if (re <= anchors[i]) { hi = i; break }
        }
        val lo = Math.max(hi - 1, 0)
        val clLo = Math.max(binVal(lo, sr), 0.0)
        val clHi = Math.max(binVal(hi, sr), 0.0)
        val w = if (anchors[hi] != anchors[lo]) (re - anchors[lo]) / (anchors[hi] - anchors[lo]) else 0.0
        return attLowRe(clampd(lerp(clLo, clHi, w), 0.0, cmax), sr)
    }

    /** Composite drag: raw x spin multiplier x high-launch boost, floored at CdMin. */
    fun dragCoefficient(re: Double, s: Double, vlaDeg: Double): Double =
        Math.max(cdRaw(re) * spinDragMult(s, re) * highLaunchDragScale(vlaDeg, s), CD_MIN)

    /** Composite lift: bins x low-launch recovery x mid-spin boost (disabled). */
    fun liftCoefficient(re: Double, s: Double, vlaDeg: Double): Double =
        clOf(re, s) * lowLaunchLiftScale(vlaDeg, s, re) * midSpinClBoost(s)

    /** Exponential spin decay over dt with the named tau. */
    fun spinDecay(omega: Double, dt: Double, tauSec: Double = SPIN_TAU_SEC): Double =
        omega * Math.exp(-dt / tauSec)
}
```

- [ ] **Step 4: Run test to verify it passes**

Same command as Step 2. Expected: `BUILD SUCCESSFUL`; `TEST-…AerodynamicModelTest.xml` shows `tests="16" failures="0" errors="0"`.

- [ ] **Step 5: Commit**

```powershell
git add core/physics/src; git commit -m "feat(core-physics): openfairway-faithful aerodynamic model"
```

---

### Task 3: Surfaces, firmness, and the SurfaceProvider

**Files:**
- Create: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/Surface.kt`
- Create: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/SurfaceProvider.kt`
- Test: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/SurfaceTest.kt`

- [ ] **Step 1: Write the failing test**

`SurfaceTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceTest {

    @Test
    fun greenDecelIsStimpAnchored() {
        // Stimpmeter physics: 1.83 m/s release, decel = 5.49 / stimp m/s^2 (spec §6)
        assertEquals(0.4991, Surface.GREEN_NORMAL.rollDecelMps2, 1e-4)
        assertEquals(0.549, Surface.green(stimp = 10.0).rollDecelMps2, 1e-4)
    }

    @Test
    fun fairwayAndRoughDecelAreMuAnchored() {
        assertEquals(0.2943, Surface.FAIRWAY_NORMAL.rollDecelMps2, 1e-6)   // 0.030 * 9.81
        assertEquals(0.93195, Surface.ROUGH_NORMAL.rollDecelMps2, 1e-6)   // 0.095 * 9.81
    }

    @Test
    fun firmnessScalesRollDecel() {
        assertEquals(0.2943 * 0.60, Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM).rollDecelMps2, 1e-6)
        assertEquals(0.2943 * 1.40, Surface.FAIRWAY_NORMAL.withFirmness(Firmness.SOFT).rollDecelMps2, 1e-6)
    }

    @Test
    fun firmnessScalesCorThetaAndSpin() {
        assertEquals(0.432, Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM).cor, 1e-9)   // 0.40 * 1.08
        assertEquals(0.368, Surface.FAIRWAY_NORMAL.withFirmness(Firmness.SOFT).cor, 1e-9)   // 0.40 * 0.92
        assertEquals(0.29 * (0.25 / 0.29), Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM).thetaCritRad, 1e-9)
        assertEquals(0.35 * (0.92 / 0.78), Surface.ROUGH_NORMAL.withFirmness(Firmness.SOFT).spinbackScale, 1e-9)
    }

    @Test
    fun uniformSurfaceAlwaysAnswersTheSameSurface() {
        val provider = UniformSurface(Surface.ROUGH_NORMAL)
        assertEquals(Surface.ROUGH_NORMAL, provider.surfaceAt(-5.0, 0.0))
        assertEquals(Surface.ROUGH_NORMAL, provider.surfaceAt(50.0, 300.0))
    }

    @Test
    fun zoneTableAnswersByDistanceBand() {
        val zones = ZoneTable()
        assertEquals(Surface.GREEN_NORMAL, zones.surfaceAt(0.0, 150.0))
        assertEquals(Surface.FAIRWAY_NORMAL, zones.surfaceAt(0.0, 230.0))
        assertEquals(Surface.ROUGH_NORMAL, zones.surfaceAt(0.0, 300.0))
    }

    @Test
    fun firmnessOrderingRollsFartherToShorter() {
        // roll distance is inversely proportional to rollDecel on the same speed
        assertTrue(
            Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM).rollDecelMps2 <
                Surface.FAIRWAY_NORMAL.rollDecelMps2
        )
        assertTrue(
            Surface.FAIRWAY_NORMAL.rollDecelMps2 <
                Surface.FAIRWAY_NORMAL.withFirmness(Firmness.SOFT).rollDecelMps2
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:physics:test --tests "com.hpsmiles.golfsim.core.physics.SurfaceTest"
```

Expected: FAIL with `Unresolved reference 'Surface'`. BUILD FAILED.

- [ ] **Step 3: Write minimal implementation**

`Surface.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

/**
 * Ground-contact parameters for one turf (spec §6 table).
 *
 * The firmness modifiers are multiplicative ratios derived from the
 * openfairway FairwaySoft/Fairway column pairs (roll mu x1.40 soft / x0.60
 * firm; theta_crit x0.32/0.29 soft / x0.25/0.29 firm; spin scale x0.92/0.78
 * soft / x0.60/0.78 firm) plus a small named COR delta (x0.92 soft / x1.08
 * firm — our choice, spec §6). The catalog's mu_kinetic column is NOT
 * represented: the shipped ground model uses openfairway's retention-based
 * shallow-bounce law (spec §5 amendment), so nothing consumes it (YAGNI).
 *
 * Green roll is stimp-anchored: decel = 5.49/stimp m/s^2 (USGA Stimpmeter
 * release speed 1.83 m/s). Fairway/rough roll: decel = mu x g.
 */
data class Surface(
    val name: String,
    /** Base COR scale relative to the fairway parabola reference (0.40). */
    val cor: Double,
    /** Critical impact angle for the Penner spin-back branch (rad). */
    val thetaCritRad: Double,
    /** Spin-back impulse scale in the Penner branch (fairway tuned to 0.35). */
    val spinbackScale: Double,
    /** Spin retained after each bounce (fairway 0.75, green 0.85). */
    val spinRetention: Double,
    /** Constant roll deceleration on this surface (m/s^2). */
    val rollDecelMps2: Double,
) {
    fun withFirmness(firmness: Firmness): Surface = when (firmness) {
        Firmness.SOFT -> copy(
            name = "$name (soft)",
            cor = cor * 0.92,
            thetaCritRad = thetaCritRad * (0.32 / 0.29),
            spinbackScale = spinbackScale * (0.92 / 0.78),
            rollDecelMps2 = rollDecelMps2 * 1.40,
        )
        Firmness.NORMAL -> this
        Firmness.FIRM -> copy(
            name = "$name (firm)",
            cor = cor * 1.08,
            thetaCritRad = thetaCritRad * (0.25 / 0.29),
            spinbackScale = spinbackScale * (0.60 / 0.78),
            rollDecelMps2 = rollDecelMps2 * 0.60,
        )
    }

    companion object {
        fun green(stimp: Double = 11.0): Surface = Surface(
            name = "green",
            cor = 0.45,
            thetaCritRad = 0.36,
            spinbackScale = 1.12,
            spinRetention = 0.85,
            rollDecelMps2 = 5.49 / stimp,
        )

        /** Fairway with M2-tuned roll mu (0.030) and spin-back scale (0.35). */
        val FAIRWAY_NORMAL: Surface = Surface(
            name = "fairway",
            cor = 0.40,
            thetaCritRad = 0.29,
            spinbackScale = 0.35,
            spinRetention = 0.75,
            rollDecelMps2 = 0.030 * BallPhysical.GRAVITY_MPS2,
        )

        val ROUGH_NORMAL: Surface = Surface(
            name = "rough",
            cor = 0.35,
            thetaCritRad = 0.35,
            spinbackScale = 0.70,
            spinRetention = 0.75,
            rollDecelMps2 = 0.095 * BallPhysical.GRAVITY_MPS2,
        )

        val GREEN_NORMAL: Surface = green(stimp = 11.0)
    }
}

enum class Firmness { SOFT, NORMAL, FIRM }
```

`SurfaceProvider.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

/** Supplies the turf under any landing spot. M4's real target layouts implement this. */
fun interface SurfaceProvider {
    fun surfaceAt(x: Double, y: Double): Surface
}

/** One surface everywhere (tour fixtures run on this). */
class UniformSurface(private val surface: Surface) : SurfaceProvider {
    override fun surfaceAt(x: Double, y: Double): Surface = surface
}

/**
 * Simple down-range zoning (spec §4): green inside greenEndY, fairway mid,
 * rough beyond fairwayEndY. M4 replaces the rules with real target ovals.
 */
class ZoneTable(
    private val green: Surface = Surface.GREEN_NORMAL,
    private val fairway: Surface = Surface.FAIRWAY_NORMAL,
    private val rough: Surface = Surface.ROUGH_NORMAL,
    private val greenEndY: Double = 200.0,
    private val fairwayEndY: Double = 250.0,
) : SurfaceProvider {
    override fun surfaceAt(x: Double, y: Double): Surface = when {
        y <= greenEndY -> green
        y <= fairwayEndY -> fairway
        else -> rough
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Same command as Step 2. Expected: `BUILD SUCCESSFUL`; `TEST-…SurfaceTest.xml` shows `tests="7" failures="0" errors="0"`.

- [ ] **Step 5: Commit**

```powershell
git add core/physics/src; git commit -m "feat(core-physics): surfaces with firmness modifiers and zone table"
```

---

### Task 4: FlightSolver (symplectic Euler ODE)

**Files:**
- Create: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/LandingState.kt`
- Create: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/FlightSolver.kt`
- Test: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/FlightSolverTest.kt`

- [ ] **Step 1: Write the failing test**

`FlightSolverTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightSolverTest {

    private fun driverShot() = Triple(
        Vec3(0.0, 65.0, 13.0),                       // velocity: down-range 65, up 13
        Vec3(2545.0 * 2.0 * Math.PI / 60.0, 0.0, 0.0), // backspin around +x
        11.3,                                        // launch angle for layer scaling
    )

    @Test
    fun repeatSolvesAreBitIdentical() {
        val (v0, s0, vla) = driverShot()
        val a = FlightSolver.solve(v0, s0, vla, Environment())
        val b = FlightSolver.solve(v0, s0, vla, Environment())
        assertEquals(a, b)
    }

    @Test
    fun landsExactlyAtGroundZero() {
        val (v0, s0, vla) = driverShot()
        val landing = FlightSolver.solve(v0, s0, vla, Environment())
        assertEquals(0.0, landing.position.z, 1e-9)
    }

    @Test
    fun headwindShortensCarry() {
        val (v0, s0, vla) = driverShot()
        val still = FlightSolver.solve(v0, s0, vla, Environment())
        val headwind = FlightSolver.solve(v0, s0, vla, Environment(windYmps = -5.0))
        assertTrue(headwind.position.y < still.position.y)
    }

    @Test
    fun backspinCarriesFartherThanNoSpin() {
        val (v0, _, vla) = driverShot()
        val spun = FlightSolver.solve(v0, Vec3(266.0, 0.0, 0.0), vla, Environment())
        val plain = FlightSolver.solve(v0, Vec3(0.0, 0.0, 0.0), vla, Environment())
        assertTrue(spun.position.y > plain.position.y)
        assertTrue(spun.apexM > plain.apexM)
    }

    @Test
    fun apexAndFlightTimeArePositive() {
        val (v0, s0, vla) = driverShot()
        val landing = FlightSolver.solve(v0, s0, vla, Environment())
        assertTrue(landing.apexM > 10.0)
        assertTrue(landing.flightTimeSec > 3.0)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:physics:test --tests "com.hpsmiles.golfsim.core.physics.FlightSolverTest"
```

Expected: FAIL with `Unresolved reference 'FlightSolver'` (and `LandingState`). BUILD FAILED.

- [ ] **Step 3: Write minimal implementation**

`LandingState.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

/** State of the ball at first ground contact, after fractional-step interpolation. */
data class LandingState(
    val position: Vec3,
    val velocity: Vec3,
    val spin: Vec3,
    val apexM: Double,
    val flightTimeSec: Double,
)
```

`FlightSolver.kt` — fixed-step symplectic Euler, identical per-step operation order to CalOf.java `fly()` (lines 137–166, post-interpolation-fix):

```kotlin
package com.hpsmiles.golfsim.core.physics

/**
 * Fixed-step symplectic Euler flight integration (dt = 10 ms), deterministic
 * by construction: pure doubles, no clock, no randomness.
 *
 * Landing interpolation (corrected — spec §8 note): after the step that first
 * crosses z = 0, back off the BELOW-GROUND fraction f = pz/(vz*dt) of that
 * step (velocity is constant within a symplectic Euler step), landing at
 * exactly z = 0 with time t - f*dt.
 */
object FlightSolver {

    private const val DT = 0.01
    private const val MIN_AERO_SPEED = 0.5
    private const val MAX_FLIGHT_SEC = 30.0
    const val HOP_TAU_SEC = 60.0

    fun solve(
        initialVelocity: Vec3,
        initialSpin: Vec3,
        launchAngleDeg: Double,
        environment: Environment,
        spinTauSec: Double = AerodynamicModel.SPIN_TAU_SEC,
    ): LandingState {
        var vx = initialVelocity.x
        var vy = initialVelocity.y
        var vz = initialVelocity.z
        var px = 0.0
        var py = 0.0
        var pz = 0.0
        var t = 0.0
        var apex = 0.0
        var spin = initialSpin
        val wind = Vec3(environment.windXmps, environment.windYmps, 0.0)

        while (true) {
            val rvx = vx - wind.x
            val rvy = vy - wind.y
            val rvz = vz - wind.z
            val vrel = Math.sqrt(rvx * rvx + rvy * rvy + rvz * rvz)
            val omega = spin.length()
            var ax = 0.0
            var ay = 0.0
            var az = -BallPhysical.GRAVITY_MPS2
            if (vrel >= MIN_AERO_SPEED) {
                val sr = BallPhysical.RADIUS_M * omega / vrel
                val re = environment.airDensity * vrel * BallPhysical.DIAMETER_M / BallPhysical.MU_AIR
                val cd = AerodynamicModel.dragCoefficient(re, sr, launchAngleDeg)
                val cl = AerodynamicModel.liftCoefficient(re, sr, launchAngleDeg)
                val k = -0.5 * environment.airDensity * BallPhysical.AREA_M2 * cd * vrel / BallPhysical.MASS_KG
                ax = k * rvx
                ay = k * rvy
                az = k * rvz - BallPhysical.GRAVITY_MPS2
                if (omega > 0.0) {
                    // Magnus: 1/2 rho A Cl |vrel|^2 / m  applied along (omegaHat x vRelHat)
                    val fm = 0.5 * environment.airDensity * BallPhysical.AREA_M2 * cl * vrel * vrel / BallPhysical.MASS_KG
                    val rx = spin.x / omega
                    val ry = spin.y / omega
                    val rz = spin.z / omega
                    ax += fm * (ry * rvz - rz * rvy) / vrel
                    ay += fm * (rz * rvx - rx * rvz) / vrel
                    az += fm * (rx * rvy - ry * rvx) / vrel
                }
            }
            vx += ax * DT
            vy += ay * DT
            vz += az * DT
            px += vx * DT
            py += vy * DT
            pz += vz * DT
            t += DT
            if (pz > apex) apex = pz
            if (omega > 0.0) spin = spin.times(Math.exp(-DT / spinTauSec))
            if (pz <= 0.0 && vz < 0.0) break
            if (t > MAX_FLIGHT_SEC) break
        }

        val f = if (vz != 0.0) Math.min(Math.max(pz / (vz * DT), 0.0), 1.0) else 0.5
        return LandingState(
            position = Vec3(px - f * vx * DT, py - f * vy * DT, pz - f * vz * DT),
            velocity = Vec3(vx, vy, vz),
            spin = spin,
            apexM = apex,
            flightTimeSec = t - f * DT,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Same command as Step 2. Expected: `BUILD SUCCESSFUL`; `TEST-…FlightSolverTest.xml` shows `tests="5" failures="0" errors="0"`.

- [ ] **Step 5: Commit**

```powershell
git add core/physics/src; git commit -m "feat(core-physics): deterministic flight solver"
```

---

### Task 5: BounceRollModel (ground contact)

**Files:**
- Create: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/GroundResult.kt`
- Create: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollModel.kt`
- Test: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/BounceRollModelTest.kt`

- [ ] **Step 1: Write the failing test**

`BounceRollModelTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BounceRollModelTest {

    private val env = Environment()

    @Test
    fun wedgeBackspinRollsBackwardOnGreen() {
        // Steep, fast, high-spin impact — the Penner 2R*omega/7 impulse
        // overpowers forward momentum (CalOf hand-check: newTan ~ -3.4 m/s).
        val landing = LandingState(
            position = Vec3(0.0, 50.0, 0.0),
            velocity = Vec3(0.0, 14.0, -12.0),
            spin = Vec3(700.0, 0.0, 0.0),   // ~6685 rpm backspin
            apexM = 10.0,
            flightTimeSec = 3.0,
        )
        val ground = BounceRollModel.bounceAndRoll(landing, Surface.GREEN_NORMAL, 45.0, env)
        assertTrue("expected negative rollout, got ${ground.deltaY}", ground.deltaY < -1.0)
    }

    @Test
    fun bounceLoopIsCappedAtFour() {
        val landing = LandingState(
            position = Vec3(0.0, 0.0, 0.0),
            velocity = Vec3(0.0, 25.0, -20.0),
            spin = Vec3(0.0, 0.0, 0.0),
            apexM = 20.0,
            flightTimeSec = 5.0,
        )
        val ground = BounceRollModel.bounceAndRoll(landing, Surface.FAIRWAY_NORMAL, 12.0, env)
        assertTrue(ground.bounces in 1..4)
        assertTrue(ground.deltaY.isFinite())
    }

    @Test
    fun roughStopsShorterThanFairway() {
        val landing = LandingState(
            position = Vec3(0.0, 0.0, 0.0),
            velocity = Vec3(0.0, 30.0, -15.0),
            spin = Vec3(0.0, 0.0, 0.0),
            apexM = 25.0,
            flightTimeSec = 6.0,
        )
        val fairway = BounceRollModel.bounceAndRoll(landing, Surface.FAIRWAY_NORMAL, 12.0, env)
        val rough = BounceRollModel.bounceAndRoll(landing, Surface.ROUGH_NORMAL, 12.0, env)
        assertTrue(rough.deltaY < fairway.deltaY)
    }

    @Test
    fun highSpinStopsMuchShorterOnGreenThanFairway() {
        // Approach-shot profile: same steep fast impact, high backspin.
        // Green spin-back scale 1.12 vs fairway 0.35 -> Penner impulse
        // reverses the ball on green (CalOf hand-check: newTan green ~ -2.1,
        // fairway ~ +0.5 m/s).
        val landing = LandingState(
            position = Vec3(0.0, 0.0, 0.0),
            velocity = Vec3(0.0, 15.0, -12.0),
            spin = Vec3(500.0, 0.0, 0.0),   // ~4775 rpm backspin
            apexM = 20.0,
            flightTimeSec = 5.0,
        )
        val fairway = BounceRollModel.bounceAndRoll(landing, Surface.FAIRWAY_NORMAL, 40.0, env)
        val green = BounceRollModel.bounceAndRoll(landing, Surface.GREEN_NORMAL, 40.0, env)
        assertTrue(green.deltaY < fairway.deltaY)
    }

    @Test
    fun repeatRunsAreBitIdentical() {
        val landing = LandingState(
            position = Vec3(0.0, 0.0, 0.0),
            velocity = Vec3(2.0, 40.0, -18.0),
            spin = Vec3(400.0, 0.0, 0.0),
            apexM = 28.0,
            flightTimeSec = 6.2,
        )
        val a = BounceRollModel.bounceAndRoll(landing, Surface.FAIRWAY_NORMAL, 14.8, env)
        val b = BounceRollModel.bounceAndRoll(landing, Surface.FAIRWAY_NORMAL, 14.8, env)
        assertEquals(a, b)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:physics:test --tests "com.hpsmiles.golfsim.core.physics.BounceRollModelTest"
```

Expected: FAIL with `Unresolved reference 'BounceRollModel'`. BUILD FAILED.

- [ ] **Step 3: Write minimal implementation**

`GroundResult.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

/** Total ground displacement from first bounce to rest. */
data class GroundResult(
    val deltaX: Double,
    val deltaY: Double,
    val bounces: Int,
)
```

`BounceRollModel.kt` — faithful port of CalOf.java `simulateShot`'s bounce loop (lines 165–223) with per-surface parameters. The COR parabola is scaled by `surface.cor / 0.40` (fairway reference; the tuned pins are fairway values so this factor is exactly 1.0 there):

```kotlin
package com.hpsmiles.golfsim.core.physics

/**
 * Bounce loop + roll to rest on a [Surface] (spec §6; openfairway structure
 * with Penner's 2R*omega/7 spin-back impulse). Never throws; hard bounce cap
 * prevents non-termination.
 */
object BounceRollModel {

    private const val FAIRWAY_COR_REFERENCE = 0.40
    private const val MAX_BOUNCES = 4

    /** Base COR parabola: 0.45 - 0.01 vn + 0.0002 vn^2, capped/zeroed. */
    internal fun baseCor(vn: Double): Double {
        if (vn > 20.0) return 0.25
        if (vn < 2.0) return 0.0
        return 0.45 - 0.01 * vn + 0.0002 * vn * vn
    }

    fun bounceAndRoll(
        landing: LandingState,
        surface: Surface,
        launchAngleDeg: Double,
        environment: Environment,
    ): GroundResult {
        var vx = landing.velocity.x
        var vy = landing.velocity.y
        var vz = landing.velocity.z
        var spin = landing.spin
        var fromFlight = true
        var bounces = 0
        var dx = 0.0
        var dy = 0.0

        while (true) {
            val vh = Math.hypot(vx, vy)
            val vn = Math.abs(vz)
            val impactSpeed = Math.hypot(vh, vn)
            val impactAngle = Math.atan2(vn, vh)
            val hx = if (vh > 1e-9) vx / vh else 0.0
            val hy = if (vh > 1e-9) vy / vh else 1.0
            val tAx = hy
            val tAy = -hx
            val omegaT = spin.x * tAx + spin.y * tAy
            val rpmNow = Math.abs(omegaT) * 60.0 / (2.0 * Math.PI)

            var retention: Double
            var cor: Double
            var newTan: Double
            if (fromFlight) {
                retention = 0.55 * Math.max(Math.min(1.0 - rpmNow / 8000.0, 1.0), 0.40)
                val steep = impactAngle >= surface.thetaCritRad
                var pennerThresh = 20.0
                if (rpmNow > 4000.0) {
                    pennerThresh = 12.0 + 8.0 * Math.max(0.0, 1.0 - (rpmNow - 4000.0) / 4000.0)
                }
                newTan = if (steep && impactSpeed >= pennerThresh) {
                    retention * impactSpeed * Math.sin(impactAngle - surface.thetaCritRad) -
                        2.0 * BallPhysical.RADIUS_M * omegaT * surface.spinbackScale / 7.0
                } else {
                    vh * retention
                }
                val velScale: Double = when {
                    vn < 12.0 -> 0.5 * vn / 12.0
                    vn < 25.0 -> 0.5 + 0.5 * (vn - 12.0) / 13.0
                    else -> 1.0
                }
                val spinCorRed = if (rpmNow < 1500.0) {
                    (rpmNow / 1500.0) * 0.30
                } else {
                    (0.30 + Math.min((rpmNow - 1500.0) / 1500.0, 1.0) * 0.40) * velScale
                }
                cor = baseCor(vn) * (surface.cor / FAIRWAY_COR_REFERENCE) * (1.0 - spinCorRed)
            } else {
                val spinRatio = if (impactSpeed > 0.1) {
                    Math.abs(omegaT) * BallPhysical.RADIUS_M / impactSpeed
                } else {
                    0.0
                }
                retention = if (spinRatio < 0.20) 0.85 - 0.15 * (spinRatio / 0.20) else 0.70
                newTan = vh * retention
                cor = if (vn < 4.0) 0.0 else baseCor(vn) * (surface.cor / FAIRWAY_COR_REFERENCE) * 0.5
            }

            val vzNew = vn * cor
            val mag = Math.abs(newTan)
            val sgn = Math.signum(newTan)
            vx = hx * mag * sgn
            vy = hy * mag * sgn
            vz = vzNew
            spin = Vec3(tAx, tAy, 0.0).times(
                (if (fromFlight) mag / BallPhysical.RADIUS_M else Math.abs(omegaT)) * surface.spinRetention
            )
            fromFlight = false
            bounces++

            if (vz < 0.05 || bounces >= MAX_BOUNCES) {
                val vroll = Math.hypot(vx, vy)
                val a = surface.rollDecelMps2
                if (vroll > 0.1) {
                    val droll = vroll * vroll / (2 * a)
                    dx += hx * droll
                    dy += hy * droll
                }
                break
            }

            val hop = FlightSolver.solve(
                Vec3(vx, vy, vz), spin, launchAngleDeg, environment, FlightSolver.HOP_TAU_SEC,
            )
            dx += hop.position.x
            dy += hop.position.y
            vx = hop.velocity.x
            vy = hop.velocity.y
            vz = hop.velocity.z
            spin = hop.spin
        }

        return GroundResult(dx, dy, bounces)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Same command as Step 2. Expected: `BUILD SUCCESSFUL`; `TEST-…BounceRollModelTest.xml` shows `tests="5" failures="0" errors="0"`.

- [ ] **Step 5: Commit**

```powershell
git add core/physics/src; git commit -m "feat(core-physics): bounce and roll ground model"
```

---

### Task 6: BallFlightEngine + ShotResult (public entry point)

**Files:**
- Create: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/ShotResult.kt`
- Create: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BallFlightEngine.kt`
- Test: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/BallFlightEngineTest.kt`

- [ ] **Step 1: Write the failing test**

`BallFlightEngineTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BallFlightEngineTest {

    private val pgaDriver = LaunchConditions(
        ballSpeedMps = 171.5 * 0.44704,
        launchAngleDeg = 10.4,
        spinRpm = 2545,
    )

    @Test
    fun pgaDriverCarriesTourDistance() {
        val result = BallFlightEngine.simulate(pgaDriver)
        assertEquals(275.9, result.carryM / BallPhysical.METERS_PER_YARD, 0.1)
    }

    @Test
    fun drawAndFadeMirrorExactly() {
        val draw = BallFlightEngine.simulate(LaunchConditions(70.0, 12.0, 3000, spinAxisDeg = 5.0))
        val fade = BallFlightEngine.simulate(LaunchConditions(70.0, 12.0, 3000, spinAxisDeg = -5.0))
        assertEquals(draw.sideM, -fade.sideM, 1e-9)
        assertEquals(draw.totalM, fade.totalM, 1e-9)
    }

    @Test
    fun firmRollsFartherThanSoft() {
        val normal = BallFlightEngine.simulate(pgaDriver)
        val firm = BallFlightEngine.simulate(
            pgaDriver, surfaces = UniformSurface(Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM)),
        )
        val soft = BallFlightEngine.simulate(
            pgaDriver, surfaces = UniformSurface(Surface.FAIRWAY_NORMAL.withFirmness(Firmness.SOFT)),
        )
        assertTrue(firm.totalM > normal.totalM)
        assertTrue(normal.totalM > soft.totalM)
    }

    @Test
    fun greenZoneStopsTheBallShorterThanFairway() {
        // LPGA 7-iron carries ~118.6 m — inside the zone table's green band.
        val launch = LaunchConditions(99.5 * 0.44704, 17.1, 6417)
        val zoned = BallFlightEngine.simulate(launch, surfaces = ZoneTable(greenEndY = 125.0))
        val fairway = BallFlightEngine.simulate(launch, surfaces = UniformSurface(Surface.FAIRWAY_NORMAL))
        assertTrue(zoned.totalM < fairway.totalM)
    }

    @Test
    fun invalidLaunchFailsFast() {
        try {
            BallFlightEngine.simulate(LaunchConditions(0.3, 10.0, 2000))
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun spinlessChipIsSane() {
        val result = BallFlightEngine.simulate(LaunchConditions(8.0, 15.0, 0))
        assertTrue(result.carryM > 2.0)
        assertTrue(result.totalM >= result.carryM)
        assertTrue(result.flightTimeSec > 0.5)
        assertEquals(0.0, result.sideM, 1e-12)
    }

    @Test
    fun extremeSpinsAndAxesYieldSaneResults() {
        // Spec §7 runtime guards: never throws, finite output at 12000 rpm
        // and at the +/-90 deg spin-axis boundaries (pure sidespin).
        val maxSpin = BallFlightEngine.simulate(LaunchConditions(65.0, 15.0, 12000))
        assertTrue(maxSpin.carryM.isFinite() && maxSpin.carryM > 0.0)
        assertTrue(maxSpin.sideM.isFinite())

        val axisPlus = BallFlightEngine.simulate(LaunchConditions(65.0, 15.0, 3000, spinAxisDeg = 90.0))
        val axisMinus = BallFlightEngine.simulate(LaunchConditions(65.0, 15.0, 3000, spinAxisDeg = -90.0))
        assertTrue(axisPlus.carryM.isFinite() && axisPlus.carryM > 0.0)
        assertTrue(axisMinus.sideM.isFinite())
        assertEquals(axisPlus.sideM, -axisMinus.sideM, 1e-9)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:physics:test --tests "com.hpsmiles.golfsim.core.physics.BallFlightEngineTest"
```

Expected: FAIL with `Unresolved reference 'BallFlightEngine'` (and `ShotResult`). BUILD FAILED.

- [ ] **Step 3: Write minimal implementation**

`ShotResult.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

/**
 * End-to-end shot outcome, SI, full precision (display converts at M3+).
 * rolloutM = totalM - carryM (can be negative — spin-back on soft greens).
 * sideM: +x is right of the target line looking down-range.
 */
data class ShotResult(
    val carryM: Double,
    val rolloutM: Double,
    val totalM: Double,
    val sideM: Double,
    val apexM: Double,
    val flightTimeSec: Double,
)
```

`BallFlightEngine.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

/**
 * The public M2 entry point (replaces the M0 BallFlight marker). Deterministic:
 * identical inputs produce bit-identical output (spec §1). M4 wires M1's
 * [com.hpsmiles.golfsim.core.ble.BallData] into [LaunchConditions] and renders
 * [ShotResult] on the practice grid.
 */
object BallFlightEngine {

    fun simulate(
        launch: LaunchConditions,
        environment: Environment = Environment(),
        surfaces: SurfaceProvider = UniformSurface(Surface.FAIRWAY_NORMAL),
    ): ShotResult {
        val el = Math.toRadians(launch.launchAngleDeg)
        val az = Math.toRadians(launch.launchDirDeg)
        val v0 = Vec3(
            launch.ballSpeedMps * Math.sin(az) * Math.cos(el),
            launch.ballSpeedMps * Math.cos(az) * Math.cos(el),
            launch.ballSpeedMps * Math.sin(el),
        )
        // Backspin axis (CalOf equivalent: omega along +x for launchDir 0):
        // tilt the horizontal backspin axis b = (cos az, -sin az, 0) around the
        // horizontal launch direction h = (sin az, cos az, 0) by spinAxisDeg.
        // +spinAxisDeg curves the ball toward +x (sign unverified — M1 §9 list).
        val tilt = Math.toRadians(launch.spinAxisDeg)
        val spinHat = Vec3(
            Math.cos(az) * Math.cos(tilt),
            -Math.sin(az) * Math.cos(tilt),
            -Math.sin(tilt),
        )
        val omega = launch.spinRpm * 2.0 * Math.PI / 60.0

        val landing = FlightSolver.solve(v0, spinHat.times(omega), launch.launchAngleDeg, environment)
        val surface = surfaces.surfaceAt(landing.position.x, landing.position.y)
        val ground = BounceRollModel.bounceAndRoll(landing, surface, launch.launchAngleDeg, environment)

        val carryM = Math.hypot(landing.position.x, landing.position.y)
        val endX = landing.position.x + ground.deltaX
        val endY = landing.position.y + ground.deltaY
        val totalM = Math.hypot(endX, endY)
        return ShotResult(
            carryM = carryM,
            rolloutM = totalM - carryM,
            totalM = totalM,
            sideM = endX,
            apexM = landing.apexM,
            flightTimeSec = landing.flightTimeSec,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Same command as Step 2. Expected: `BUILD SUCCESSFUL`; `TEST-…BallFlightEngineTest.xml` shows `tests="6" failures="0" errors="0"`.

- [ ] **Step 5: Commit**

```powershell
git add core/physics/src; git commit -m "feat(core-physics): ball-flight engine entry point"
```

---

### Task 7: Tour fixtures — the tiered-gate exit criterion

**Files:**
- Create: `core/physics/src/test/resources/tour/pga-driver.properties`
- Create: `core/physics/src/test/resources/tour/pga-3-wood.properties`
- Create: `core/physics/src/test/resources/tour/pga-5-iron.properties`
- Create: `core/physics/src/test/resources/tour/pga-7-iron.properties`
- Create: `core/physics/src/test/resources/tour/pga-pitching-wedge.properties`
- Create: `core/physics/src/test/resources/tour/lpga-driver.properties`
- Create: `core/physics/src/test/resources/tour/lpga-7-iron.properties`
- Create: `core/physics/src/test/resources/tour/lpga-pitching-wedge.properties`
- Test: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/TourAveragesTest.kt`
- Test: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/TourOrderingTest.kt`

This is a **regression harness**, not new behavior — the M1 golden-fixture pattern applies (harness + green + negative control) instead of red-first TDD.

- [ ] **Step 1: Write the eight fixtures**

`pga-driver.properties`:

```properties
# PGA driver - TrackMan Tour 2023 average (171.5 mph / 10.4 deg / 2545 rpm)
name=PGA driver
ballSpeedMph=171.5
launchAngleDeg=10.4
spinRpm=2545
expectedCarryYd=275.9
expectedRolloutYd=13.1
expectedTotalYd=289.0
expectedApexM=29.9
expectedFlightTimeSec=6.63
```

`pga-3-wood.properties`:

```properties
# PGA 3-wood - TrackMan Tour 2023 average (162.0 mph / 9.3 deg / 3663 rpm)
name=PGA 3-wood
ballSpeedMph=162.0
launchAngleDeg=9.3
spinRpm=3663
expectedCarryYd=255.6
expectedRolloutYd=16.3
expectedTotalYd=271.9
expectedApexM=27.8
expectedFlightTimeSec=6.50
```

`pga-5-iron.properties`:

```properties
# PGA 5-iron - TrackMan Tour 2023 average (135.0 mph / 14.8 deg / 5280 rpm)
name=PGA 5-iron
ballSpeedMph=135.0
launchAngleDeg=14.8
spinRpm=5280
expectedCarryYd=190.8
expectedRolloutYd=8.9
expectedTotalYd=199.7
expectedApexM=26.0
expectedFlightTimeSec=5.88
```

`pga-7-iron.properties`:

```properties
# PGA 7-iron - TrackMan Tour 2023 average (123.0 mph / 16.3 deg / 7124 rpm)
name=PGA 7-iron
ballSpeedMph=123.0
launchAngleDeg=16.3
spinRpm=7124
expectedCarryYd=174.2
expectedRolloutYd=2.8
expectedTotalYd=177.0
expectedApexM=23.5
expectedFlightTimeSec=5.65
```

`pga-pitching-wedge.properties`:

```properties
# PGA pitching wedge - TrackMan Tour 2023 average (102.0 mph / 24.2 deg / 9304 rpm)
name=PGA pitching wedge
ballSpeedMph=102.0
launchAngleDeg=24.2
spinRpm=9304
expectedCarryYd=134.3
expectedRolloutYd=2.3
expectedTotalYd=136.6
expectedApexM=24.9
expectedFlightTimeSec=5.31
```

`lpga-driver.properties`:

```properties
# LPGA driver - TrackMan Tour 2023 average (140.0 mph / 13.2 deg / 2611 rpm)
name=LPGA driver
ballSpeedMph=140.0
launchAngleDeg=13.2
spinRpm=2611
expectedCarryYd=219.4
expectedRolloutYd=9.1
expectedTotalYd=228.5
expectedApexM=23.3
expectedFlightTimeSec=5.70
```

`lpga-7-iron.properties`:

```properties
# LPGA 7-iron - TrackMan Tour 2023 average (99.5 mph / 17.1 deg / 6417 rpm)
name=LPGA 7-iron
ballSpeedMph=99.5
launchAngleDeg=17.1
spinRpm=6417
expectedCarryYd=129.7
expectedRolloutYd=0.8
expectedTotalYd=130.5
expectedApexM=14.4
expectedFlightTimeSec=4.38
```

`lpga-pitching-wedge.properties`:

```properties
# LPGA pitching wedge - TrackMan Tour 2023 average (82.0 mph / 24.6 deg / 8525 rpm)
name=LPGA pitching wedge
ballSpeedMph=82.0
launchAngleDeg=24.6
spinRpm=8525
expectedCarryYd=98.8
expectedRolloutYd=0.7
expectedTotalYd=99.5
expectedApexM=15.4
expectedFlightTimeSec=4.12
```

- [ ] **Step 2: Write the harness tests**

`TourAveragesTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.Properties

/**
 * The M2 exit criterion (spec §8): the engine reproduces the pinned prototype
 * table (locked config: tau=12, sdCoeff=8, sdCap=1.55, msBoostMax=0, fairway
 * roll mu=0.030, spin-back scale=0.35) for all 8 Tour shots. Tolerances are
 * printed-precision only: +/-0.1 yd on carry/rollout/total, +/-0.05 m apex,
 * +/-0.01 s flight time. Any shift is a deliberate recalibration requiring a
 * fixture update plus a spec note.
 */
@RunWith(Parameterized::class)
class TourAveragesTest(private val fixture: File) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): Collection<File> {
            val files = mutableListOf<File>()
            val urls = TourAveragesTest::class.java.classLoader.getResources("tour")
            while (urls.hasMoreElements()) {
                // toURI() URL-decodes the '%20' in the workspace path (M1 lesson).
                val dir = File(urls.nextElement().toURI())
                files += dir.listFiles()!!.filter { it.name.endsWith(".properties") }
            }
            return files.sortedBy { it.name }
        }
    }

    @Test
    fun matchesPinnedPrototypeNumbers() {
        val props = Properties()
        fixture.inputStream().use { props.load(it) }

        val launch = LaunchConditions(
            ballSpeedMps = props.getProperty("ballSpeedMph").toDouble() * 0.44704,
            launchAngleDeg = props.getProperty("launchAngleDeg").toDouble(),
            spinRpm = props.getProperty("spinRpm").toInt(),
        )
        val result = BallFlightEngine.simulate(launch)

        assertEquals(
            "${fixture.name} carry",
            props.getProperty("expectedCarryYd").toDouble(),
            result.carryM / BallPhysical.METERS_PER_YARD,
            0.1,
        )
        assertEquals(
            "${fixture.name} rollout",
            props.getProperty("expectedRolloutYd").toDouble(),
            result.rolloutM / BallPhysical.METERS_PER_YARD,
            0.1,
        )
        assertEquals(
            "${fixture.name} total",
            props.getProperty("expectedTotalYd").toDouble(),
            result.totalM / BallPhysical.METERS_PER_YARD,
            0.1,
        )
        assertEquals(
            "${fixture.name} apex",
            props.getProperty("expectedApexM").toDouble(),
            result.apexM,
            0.05,
        )
        assertEquals(
            "${fixture.name} flight time",
            props.getProperty("expectedFlightTimeSec").toDouble(),
            result.flightTimeSec,
            0.01,
        )
    }
}
```

`TourOrderingTest.kt`:

```kotlin
package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tiered-gate ordering assertions (spec §8 clauses 2-4). Absolute vs-Tour
 * accuracy is a documented bias and is deliberately NOT asserted here.
 */
class TourOrderingTest {

    private fun shot(mph: Double, vla: Double, spin: Int): ShotResult =
        BallFlightEngine.simulate(LaunchConditions(mph * 0.44704, vla, spin))

    private val pgaDriver = shot(171.5, 10.4, 2545)
    private val pga3W = shot(162.0, 9.3, 3663)
    private val pga5i = shot(135.0, 14.8, 5280)
    private val pga7i = shot(123.0, 16.3, 7124)
    private val pgaPw = shot(102.0, 24.2, 9304)
    private val lpgaDriver = shot(140.0, 13.2, 2611)
    private val lpga7i = shot(99.5, 17.1, 6417)
    private val lpgaPw = shot(82.0, 24.6, 8525)

    @Test
    fun carryLadderIsStrictlyOrdered() {
        assertTrue(pgaDriver.carryM > pga3W.carryM)
        assertTrue(pga3W.carryM > pga5i.carryM)
        assertTrue(pga5i.carryM > pga7i.carryM)
        assertTrue(pga7i.carryM > pgaPw.carryM)
        assertTrue(lpgaDriver.carryM > lpga7i.carryM)
        assertTrue(lpga7i.carryM > lpgaPw.carryM)
    }

    @Test
    fun rolloutFamilyOrderingHolds() {
        val driverFamily = listOf(pgaDriver.rolloutM, pga3W.rolloutM)
        assertTrue(driverFamily.min() > pga5i.rolloutM)
        assertTrue(pga5i.rolloutM > pga7i.rolloutM)
        assertTrue(pga7i.rolloutM > pgaPw.rolloutM)
        assertTrue(lpgaDriver.rolloutM > lpga7i.rolloutM)
        assertTrue(lpga7i.rolloutM > lpgaPw.rolloutM)
    }

    @Test
    fun allRolloutsAreNonNegative() {
        listOf(pgaDriver, pga3W, pga5i, pga7i, pgaPw, lpgaDriver, lpga7i, lpgaPw).forEach {
            assertTrue("negative rollout on ${it.carryM}", it.rolloutM >= 0.0)
        }
    }

    @Test
    fun firmnessAndMirrorSymmetryHold() {
        val launch = LaunchConditions(171.5 * 0.44704, 10.4, 2545)
        val normal = BallFlightEngine.simulate(launch)
        val firm = BallFlightEngine.simulate(
            launch, surfaces = UniformSurface(Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM)),
        )
        val soft = BallFlightEngine.simulate(
            launch, surfaces = UniformSurface(Surface.FAIRWAY_NORMAL.withFirmness(Firmness.SOFT)),
        )
        assertTrue(firm.totalM > normal.totalM)
        assertTrue(normal.totalM > soft.totalM)

        val draw = BallFlightEngine.simulate(LaunchConditions(70.0, 12.0, 3000, spinAxisDeg = 5.0))
        val fade = BallFlightEngine.simulate(LaunchConditions(70.0, 12.0, 3000, spinAxisDeg = -5.0))
        assertEquals(draw.sideM, -fade.sideM, 1e-9)
        assertEquals(draw.totalM, fade.totalM, 1e-9)
    }
}
```

- [ ] **Step 3: Run the harness — expect green**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:physics:test --tests "com.hpsmiles.golfsim.core.physics.TourAveragesTest" --tests "com.hpsmiles.golfsim.core.physics.TourOrderingTest"
```

Expected: `BUILD SUCCESSFUL`; `TEST-…TourAveragesTest.xml` shows `tests="8" failures="0" errors="0"` with cases named `matchesPinnedPrototypeNumbers[<fixture>.properties]`; `TEST-…TourOrderingTest.xml` shows `tests="4" failures="0" errors="0"`.

- [ ] **Step 4: Negative control — prove the pins bind**

Edit `pga-7-iron.properties`, change `expectedTotalYd=177.0` to `expectedTotalYd=180.0`. Re-run the Step 3 command. Expected: **FAILURE** with exactly one failing case, `matchesPinnedPrototypeNumbers[pga-7-iron.properties]` (`java.lang.AssertionError` in the total assertion, `expected:<180.0> but was:<177.0...>`).

Restore the file to `expectedTotalYd=177.0` (verify with `git diff` — must be empty). Re-run Step 3 command. Expected: green again.

- [ ] **Step 5: Commit**

```powershell
git add core/physics/src/test; git commit -m "test(core-physics): tour regression fixtures and tiered ordering gate"
```

---

### Task 8: Remove M0 markers, full build, PR + CI

**Files:**
- Delete: `core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BallFlight.kt` (M0 marker)
- Delete: `core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/ScaffoldSmokeTest.kt` (M0 smoke test)

The real engine (`BallFlightEngine`) supersedes the marker object. `:app`'s `ScaffoldSmokeTest.kt` is a different file — untouched.

- [ ] **Step 1: Verify clean start state**

```powershell
git status; git log --oneline -3
```

Expected: clean working tree, HEAD is the Task 7 commit on `m2-ball-flight`.

- [ ] **Step 2: Remove the markers and run the full build**

```powershell
git rm core/physics/src/main/kotlin/com/hpsmiles/golfsim/core/physics/BallFlight.kt core/physics/src/test/kotlin/com/hpsmiles/golfsim/core/physics/ScaffoldSmokeTest.kt
```

Then:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat build
```

Expected: `BUILD SUCCESSFUL` (all 3 modules; `:core:physics:test` executes with **63 tests, 0 failures, 0 errors** across 9 suites: AerodynamicModel 16, LaunchConditions 7, Environment 4, Surface 7, FlightSolver 5, BounceRollModel 5, BallFlightEngine 7, TourAverages 8, TourOrdering 4 — and NO ScaffoldSmokeTest XML remains). Lint green.

- [ ] **Step 3: Commit**

```powershell
git commit -m "chore: remove m0 scaffold markers from core-physics"
```

(The `git rm` already staged the deletions; this commits them.)

- [ ] **Step 4: Push and open the PR**

```powershell
git push -u origin m2-ball-flight
```

```powershell
gh pr create --title "M2: Ball-flight engine" --body "## Summary
- Deterministic ball-flight engine in :core:physics: FlightSolver (symplectic Euler, openfairway-faithful aero with tuned constants tau=12, sdCoeff=8, sdCap=1.55, msBoostMax=0) + BounceRollModel (openfairway bounce with Penner spin-back, stimp-anchored green roll, tuned fairway roll mu=0.030 / spin-back 0.35) + SurfaceProvider zoning
- Tiered regression gate (spec §8): 8 pinned tour fixtures (verified prototype numbers), strict carry ladder + rollout family ordering, firmness orderings, draw/fade mirror, bit-identical determinism
- Replaces the M0 BallFlight marker; spec + plan under docs/superpowers/

## Test plan
- [x] :core:physics:test — 63 cases green (16 aero, 7 launch, 4 env, 7 surface, 5 solver, 5 bounce, 7 engine, 8 tour fixtures, 4 ordering)
- [x] gradlew build green across all 3 modules
- [ ] CI green on PR head"
```

Expected: PR URL printed (e.g. `https://github.com/hpsmiles/mlm2pro-practice-sim/pull/N`).

- [ ] **Step 5: Poll CI once (non-blocking pattern)**

```powershell
Start-Sleep 120; gh pr checks
```

Expected: `build  pass  <duration>`. If `pending`, wait 60–120 s and poll again (up to 4 times total — do NOT use `--watch`). The workflow is unchanged from M1, so a green run is expected on the same runner image.

- [ ] **Step 6: Mark the third test-plan checkbox**

Once CI passes, tick the third checkbox in the PR body:

```powershell
gh pr edit <PR-NUMBER> --body "## Summary
- Deterministic ball-flight engine in :core:physics: FlightSolver (symplectic Euler, openfairway-faithful aero with tuned constants tau=12, sdCoeff=8, sdCap=1.55, msBoostMax=0) + BounceRollModel (openfairway bounce with Penner spin-back, stimp-anchored green roll, tuned fairway roll mu=0.030 / spin-back 0.35) + SurfaceProvider zoning
- Tiered regression gate (spec §8): 8 pinned tour fixtures (verified prototype numbers), strict carry ladder + rollout family ordering, firmness orderings, draw/fade mirror, bit-identical determinism
- Replaces the M0 BallFlight marker; spec + plan under docs/superpowers/

## Test plan
- [x] :core:physics:test — 63 cases green (16 aero, 7 launch, 4 env, 7 surface, 5 solver, 5 bounce, 7 engine, 8 tour fixtures, 4 ordering)
- [x] gradlew build green across all 3 modules
- [x] CI green on PR head"
```

**Status: DONE criteria for this task:** working tree clean, markers gone (verified via `git ls-files core/physics` — 13 main + 9 test source files + 8 fixtures), PR open with all three checkboxes ticked and `gh pr checks` showing `build pass`.
