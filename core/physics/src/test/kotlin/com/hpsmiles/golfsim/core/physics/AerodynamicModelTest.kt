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
        // Plan deviation (minimal fix): in the faithful port the right-side slope
        // just above 50k is (max(clRe60k(0.15),0) - clRe50k(0.15))/10000 =
        // -1.0e-5/Re (the negative 60k bin clamps to 0), so the +/-0.1 probes
        // differ by ~1.006e-6. Tolerance widened 1e-9 -> 2e-6; still catches any
        // swapped or mistyped constant at this boundary.
        val below = AerodynamicModel.clOf(49999.9, 0.15)
        val above = AerodynamicModel.clOf(50000.1, 0.15)
        assertEquals(below, above, 2e-6)
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
