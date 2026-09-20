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
