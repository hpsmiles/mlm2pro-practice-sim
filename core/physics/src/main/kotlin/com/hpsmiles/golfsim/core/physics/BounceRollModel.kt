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
