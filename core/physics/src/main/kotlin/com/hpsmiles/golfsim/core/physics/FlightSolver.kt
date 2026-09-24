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

        val samples = ArrayList<TrajectorySample>()
        samples.add(TrajectorySample(px, py, pz, t))

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
            samples.add(TrajectorySample(px, py, pz, t))
            if (omega > 0.0) spin = spin.times(Math.exp(-DT / spinTauSec))
            if (pz <= 0.0 && vz < 0.0) break
            if (t > MAX_FLIGHT_SEC) break
        }

        val f = if (vz != 0.0) Math.min(Math.max(pz / (vz * DT), 0.0), 1.0) else 0.5
        val landingTime = t - f * DT
        // The loop's final sample is the below-ground overshoot of the crossing
        // step; the interpolated landing replaces it so samples stay on-flight
        // and strictly monotonic in time. Pure bookkeeping — no math touched.
        if (samples.isNotEmpty() && samples.last().pz < 0.0) samples.removeAt(samples.lastIndex)
        samples.add(TrajectorySample(
            px - f * vx * DT, py - f * vy * DT, pz - f * vz * DT, landingTime,
        ))
        return LandingState(
            position = Vec3(px - f * vx * DT, py - f * vy * DT, pz - f * vz * DT),
            velocity = Vec3(vx, vy, vz),
            spin = spin,
            apexM = apex,
            flightTimeSec = landingTime,
            samples = samples,
        )
    }
}
