package com.hpsmiles.golfsim.games

import com.hpsmiles.golfsim.range.PovProjector
import com.hpsmiles.golfsim.range.RangeCamera
import kotlin.math.cos
import kotlin.math.sin

/**
 * Near-plane clip for VERTICAL quads (the glass pane). PovProjector's
 * clipGroundPath only handles z=0 polygons; the pane needs the general
 * camera-depth Sutherland-Hodgman (spec 5). Pure + JVM-tested.
 */
object PaneClip {

    data class Vertex(val x: Double, val y: Double, val z: Double)

    /** Same camera-plane depth test PovProjector.project uses. */
    fun depth(v: Vertex, cam: RangeCamera): Double {
        val dy = v.y - cam.y
        val dz = v.z - cam.z
        return dy * cos(cam.pitchRad) - dz * sin(cam.pitchRad)
    }

    fun clip(
        corners: List<Vertex>,
        cam: RangeCamera,
        minDepthM: Double = PovProjector.GROUND_MIN_DEPTH_M,
    ): List<Vertex> {
        if (corners.size < 3) return emptyList()
        val out = ArrayList<Vertex>(corners.size + 4)
        var prev = corners.last()
        var prevD = depth(prev, cam)
        var prevIn = prevD >= minDepthM
        for (curr in corners) {
            val d = depth(curr, cam)
            val inside = d >= minDepthM
            if (inside != prevIn) {
                val t = (minDepthM - prevD) / (d - prevD)
                out.add(
                    Vertex(
                        prev.x + (curr.x - prev.x) * t,
                        prev.y + (curr.y - prev.y) * t,
                        prev.z + (curr.z - prev.z) * t,
                    ),
                )
            }
            if (inside) out.add(curr)
            prev = curr
            prevD = d
            prevIn = inside
        }
        return out
    }
}
