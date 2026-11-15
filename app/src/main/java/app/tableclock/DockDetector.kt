package app.tableclock

data class Calibration(
    val ambientUt: Float = 0f,
    val dockedUt: Float = 0f,
) {
    val usable: Boolean get() = dockedUt >= ambientUt + MIN_GAP_UT

    /** Field strength that means the charger magnet is holding the phone. */
    val engageUt: Float get() = ambientUt + (dockedUt - ambientUt) * 0.40f

    /** Lower bar so a small wobble does not clear the dock. */
    val releaseUt: Float get() = ambientUt + (dockedUt - ambientUt) * 0.22f

    companion object {
        const val MIN_GAP_UT = 20f
    }
}

data class DockSample(
    val timeMs: Long,
    val magnitudeUt: Float,
    val landscape: Boolean,
    val stable: Boolean,
    val wirelessPower: Boolean,
)

class DockDetector(
    calibration: Calibration = Calibration(),
    private val engageAfterMs: Long = 220,
    private val wirelessEngageAfterMs: Long = 80,
    private val releaseAfterMs: Long = 700,
) {
    var calibration: Calibration = calibration
        set(value) {
            val changed = value != field
            field = value
            if (changed) reset()
        }

    var docked: Boolean = false
        private set

    private var candidateSince: Long? = null
    private var releaseSince: Long? = null

    fun reset() {
        docked = false
        candidateSince = null
        releaseSince = null
    }

    fun onSample(sample: DockSample): Boolean {
        val holding = signal(sample)
        if (!docked) {
            releaseSince = null
            if (!holding) {
                candidateSince = null
                return false
            }
            val since = candidateSince ?: sample.timeMs.also { candidateSince = it }
            val wait = if (sample.wirelessPower || sample.magnitudeUt >= ABSOLUTE_ENGAGE_UT) {
            wirelessEngageAfterMs
        } else {
            engageAfterMs
        }
            if (sample.timeMs - since >= wait) {
                docked = true
                candidateSince = null
            }
        } else if (holding) {
            releaseSince = null
        } else {
            val since = releaseSince ?: sample.timeMs.also { releaseSince = it }
            if (sample.timeMs - since >= releaseAfterMs) {
                docked = false
                releaseSince = null
                candidateSince = null
            }
        }
        return docked
    }

    private fun signal(sample: DockSample): Boolean {
        if (!sample.landscape) return false
        val magnet = magnetHolding(sample.magnitudeUt)
        if (docked) return magnet || sample.wirelessPower
        if (!sample.stable) return false
        return magnet || sample.wirelessPower
    }

    private fun magnetHolding(magnitude: Float): Boolean {
        val absolute = if (docked) ABSOLUTE_RELEASE_UT else ABSOLUTE_ENGAGE_UT
        if (magnitude >= absolute) return true
        if (!calibration.usable) return false
        val line = if (docked) calibration.releaseUt else calibration.engageUt
        return magnitude >= line
    }

    companion object {
        /** Above a normal Earth field, so a charger magnet counts with no calibration. */
        const val ABSOLUTE_ENGAGE_UT = 75f
        const val ABSOLUTE_RELEASE_UT = 55f
    }
}

fun median(values: List<Float>): Float {
    if (values.isEmpty()) return 0f
    val sorted = values.sorted()
    return sorted[sorted.size / 2]
}
