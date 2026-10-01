package com.hpsmiles.golfsim.core.physics

/**
 * Bounce loop + roll to rest on a [Surface] (spec §6; openfairway structure
 * with Penner's 2R*omega/7 spin-back impulse). Green surfaces gate the
 * spin-back impulse by spin dominance (R*omega vs vh) — see constants below.
 * Never throws; hard bounce cap prevents non-termination.
 */
object BounceRollModel {

    private const val FAIRWAY_COR_REFERENCE = 0.40
    private const val MAX_BOUNCES = 4

    // Shallow skid-and-release (item 2, 2026-10-01): punch shots land shallow
    // with moderate spin and must skid/release instead of dying in the lossy
    // low-retention bounce branch. Extra tangential retention ramps with
    // shallowness (0 at thetaCrit, max at a graze) and fades to nothing at
    // wedge-class spin (8000 rpm = the retention ramp reference), so high-spin
    // wedges keep checking up. Steep impacts take the Penner branch untouched.
    private const val SHALLOW_SKID_MAX = 0.30
    private const val SHALLOW_SKID_SPIN_RPM = 8000.0

    // Item 1 (2026-10-01): vertical-axis (yaw) spin — the sidespin that curves
    // the ball in flight — must also shape the ground phase. Friction on a
    // yawing ball deflects each bounce and the final roll toward the curve
    // direction. Per BallFlightEngine, +spinAxisDeg (right curve) gives
    // spin.z < 0, so the kick uses -spin.z ("+" = kick right). Capped and
    // scaled by the surface's spinbackScale (greens bite harder than fairway).
    private const val SIDE_KICK_GAIN = 0.0008
    private const val SIDE_KICK_CAP_MPS = 0.65

    // Spin-dominance gate (gated surfaces only, green). Penner's 2R*omega/7
    // reversal is the perfect-grip condition; real green turf shears under the
    // ball, so the reversal impulse only applies when backspin actually
    // dominates tangential speed. Blend ramps 0 -> 1 over the ratio window and
    // the forward ejection is additionally damped while spin is not dominant
    // (ball-mark shear absorbs tangential energy). Calibrated against the
    // 2026-09-30 live 8i capture: ratio ~0.60 -> no spin-back, minimal forward
    // release; wedge-class ratio ~1.07 -> full Penner reversal (test-pinned).
    private const val GRIP_RATIO_ZERO = 0.80
    private const val GRIP_RATIO_FULL = 1.05
    private const val NON_DOMINANT_FORWARD_KEEP = 0.35

    /** 0 below [GRIP_RATIO_ZERO], 1 at/above [GRIP_RATIO_FULL], linear between. */
    internal fun gripBlend(gripRatio: Double): Double = when {
        gripRatio <= GRIP_RATIO_ZERO -> 0.0
        gripRatio >= GRIP_RATIO_FULL -> 1.0
        else -> (gripRatio - GRIP_RATIO_ZERO) / (GRIP_RATIO_FULL - GRIP_RATIO_ZERO)
    }

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
        val hops = ArrayList<GroundHop>()

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
                    val ejection = retention * impactSpeed * Math.sin(impactAngle - surface.thetaCritRad)
                    val backImpulse = 2.0 * BallPhysical.RADIUS_M * omegaT * surface.spinbackScale / 7.0
                    if (surface.spinDominanceGate && omegaT != 0.0) {
                        val gripRatio = Math.abs(omegaT) * BallPhysical.RADIUS_M / Math.max(vh, 1e-6)
                        val blend = gripBlend(gripRatio)
                        ejection * (1.0 - (1.0 - NON_DOMINANT_FORWARD_KEEP) * (1.0 - blend)) - blend * backImpulse
                    } else {
                        ejection - backImpulse
                    }
                } else {
                    // Shallow skid-and-release boost (item 2, 2026-10-01).
                    val shallow = ((surface.thetaCritRad - impactAngle) / surface.thetaCritRad)
                        .coerceIn(0.0, 1.0)
                    val lowSpin = (1.0 - rpmNow / SHALLOW_SKID_SPIN_RPM).coerceIn(0.0, 1.0)
                    vh * (retention + SHALLOW_SKID_MAX * shallow * lowSpin)
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
            // Side-spin ground kick: deflect the new horizontal velocity toward
            // the curve direction. Right of travel (hx, hy) is (hy, -hx).
            val yawRight = -spin.z
            if (yawRight != 0.0) {
                val kick = (yawRight * SIDE_KICK_GAIN * vh * surface.spinbackScale)
                    .coerceIn(-SIDE_KICK_CAP_MPS, SIDE_KICK_CAP_MPS)
                vx += hy * kick
                vy -= hx * kick
            }
            vz = vzNew
            // Preserve the yaw component across bounces (decayed by spin
            // retention); the tangential components keep the existing law.
            val spinScale = (if (fromFlight) mag / BallPhysical.RADIUS_M else Math.abs(omegaT)) * surface.spinRetention
            spin = Vec3(tAx * spinScale, tAy * spinScale, spin.z * surface.spinRetention)
            fromFlight = false
            bounces++

            if (vz < 0.05 || bounces >= MAX_BOUNCES) {
                val vroll = Math.hypot(vx, vy)
                val a = surface.rollDecelMps2
                if (vroll > 0.1) {
                    val droll = vroll * vroll / (2 * a)
                    // Roll follows the kicked direction (side-spin curve carries
                    // through the run-out), not the pre-bounce travel line.
                    dx += (vx / vroll) * droll
                    dy += (vy / vroll) * droll
                }
                break
            }

            val hop = FlightSolver.solve(
                Vec3(vx, vy, vz), spin, launchAngleDeg, environment, FlightSolver.HOP_TAU_SEC,
            )
            dx += hop.position.x
            dy += hop.position.y
            hops.add(GroundHop(dx, dy, hop.apexM, hop.flightTimeSec))
            vx = hop.velocity.x
            vy = hop.velocity.y
            vz = hop.velocity.z
            spin = hop.spin
        }

        return GroundResult(dx, dy, bounces, hops)
    }
}
