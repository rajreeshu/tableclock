package app.tableclock

import kotlin.math.abs

enum class Pose {
    Side,
    Upright,
    Flat,
}

object PoseMath {
    const val UNKNOWN_ANGLE = -1

    /**
     * @return true when the phone is on its side, false when upright,
     * null when it is lying flat and gravity cannot tell the rotation.
     */
    fun fromGravity(g: FloatArray): Boolean? {
        if (g.size < 3) return null
        val ax = abs(g[0])
        val ay = abs(g[1])
        val az = abs(g[2])
        if (az > 7.5f && ax < 3.2f && ay < 3.2f) return null
        if (ax < 2.2f && ay < 2.2f) return null
        return ax > ay * 1.2f
    }

    fun isSideAngle(orientation: Int): Boolean {
        if (orientation == UNKNOWN_ANGLE) return false
        return orientation in 50..130 || orientation in 230..310
    }
}
