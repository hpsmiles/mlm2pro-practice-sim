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
