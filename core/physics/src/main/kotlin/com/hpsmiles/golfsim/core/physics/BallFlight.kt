package com.hpsmiles.golfsim.core.physics

/**
 * Landing zone for the 3D ballistic ODE ball-flight engine (ROADMAP milestone M2).
 *
 * Pure JVM library: no Android dependencies. M2 implements drag/lift
 * integration here (ported from libgolf / golfmodel reference math), producing
 * carry, total distance, side-curve, and rollout from launch conditions.
 */
object BallFlight
