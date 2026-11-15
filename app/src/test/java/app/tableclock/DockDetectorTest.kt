package app.tableclock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DockDetectorTest {
    private val calibration = Calibration(ambientUt = 40f, dockedUt = 180f)

    @Test
    fun docksWhenTheMagnetIsSteadyAndThePhoneIsOnItsSide() {
        val detector = DockDetector(calibration)
        assertFalse(detector.onSample(sample(0, 150f, landscape = true, stable = true)))
        assertTrue(detector.onSample(sample(220, 150f, landscape = true, stable = true)))
    }

    @Test
    fun staysClosedWhileUpright() {
        val detector = DockDetector(calibration)
        assertFalse(detector.onSample(sample(0, 200f, landscape = false, stable = true)))
        assertFalse(detector.onSample(sample(2000, 200f, landscape = false, stable = true)))
        assertFalse(detector.docked)
    }

    @Test
    fun waitsUntilThePhoneIsStill() {
        val detector = DockDetector(calibration)
        assertFalse(detector.onSample(sample(0, 200f, landscape = true, stable = false)))
        assertFalse(detector.onSample(sample(800, 200f, landscape = true, stable = false)))
        assertFalse(detector.docked)
    }

    @Test
    fun strongMagnetDocksWithoutCalibration() {
        val detector = DockDetector(Calibration())
        assertFalse(detector.onSample(sample(0, 120f, landscape = true, stable = true)))
        assertTrue(detector.onSample(sample(80, 120f, landscape = true, stable = true)))
    }

    @Test
    fun earthFieldDoesNotDockWithoutCalibration() {
        val detector = DockDetector(Calibration())
        assertFalse(detector.onSample(sample(0, 45f, landscape = true, stable = true)))
        assertFalse(detector.onSample(sample(3000, 45f, landscape = true, stable = true)))
        assertFalse(detector.docked)
    }

    @Test
    fun poweredPadDocksQuicklyWithoutACalibration() {
        val detector = DockDetector(Calibration())
        assertFalse(detector.onSample(sample(0, 40f, landscape = true, stable = true, wireless = true)))
        assertTrue(detector.onSample(sample(80, 40f, landscape = true, stable = true, wireless = true)))
        assertTrue(detector.onSample(sample(5000, 30f, landscape = true, stable = true, wireless = true)))
    }

    @Test
    fun releasesAfterThePhoneIsPickedUp() {
        val detector = DockDetector(calibration)
        detector.onSample(sample(0, 150f, landscape = true, stable = true))
        assertTrue(detector.onSample(sample(220, 150f, landscape = true, stable = true)))
        assertTrue(detector.onSample(sample(400, 40f, landscape = false, stable = true)))
        assertFalse(detector.onSample(sample(1100, 40f, landscape = false, stable = true)))
    }

    @Test
    fun ignoresABriefDipInTheField() {
        val detector = DockDetector(calibration)
        detector.onSample(sample(0, 150f, landscape = true, stable = true))
        assertTrue(detector.onSample(sample(220, 150f, landscape = true, stable = true)))
        assertTrue(detector.onSample(sample(300, 50f, landscape = true, stable = true)))
        assertTrue(detector.onSample(sample(900, 150f, landscape = true, stable = true)))
        assertTrue(detector.docked)
    }

    @Test
    fun holdsBetweenTheEngageAndReleaseLevels() {
        val detector = DockDetector(calibration)
        detector.onSample(sample(0, 110f, landscape = true, stable = true))
        assertTrue(detector.onSample(sample(220, 110f, landscape = true, stable = true)))
        assertTrue(detector.onSample(sample(4000, 80f, landscape = true, stable = true)))
    }

    @Test
    fun dropsWirelessWhenThePadTurnsOffAndThereIsNoMagnet() {
        val detector = DockDetector(Calibration())
        detector.onSample(sample(0, 30f, landscape = true, stable = true, wireless = true))
        assertTrue(detector.onSample(sample(80, 30f, landscape = true, stable = true, wireless = true)))
        assertTrue(detector.onSample(sample(200, 30f, landscape = true, stable = false, wireless = false)))
        assertFalse(detector.onSample(sample(900, 30f, landscape = true, stable = true, wireless = false)))
    }

    @Test
    fun weakGapIsNotAMagnet() {
        assertFalse(Calibration(40f, 55f).usable)
        assertTrue(calibration.usable)
        assertEquals(96f, calibration.engageUt, 0.01f)
    }

    @Test
    fun gravityTellsSideFromUprightAndFlat() {
        assertEquals(true, PoseMath.fromGravity(floatArrayOf(9.2f, 0.8f, 2f)))
        assertEquals(false, PoseMath.fromGravity(floatArrayOf(0.6f, 9.1f, 1.2f)))
        assertNull(PoseMath.fromGravity(floatArrayOf(0.3f, 0.4f, 9.7f)))
        assertTrue(PoseMath.isSideAngle(90))
        assertTrue(PoseMath.isSideAngle(270))
        assertFalse(PoseMath.isSideAngle(5))
    }

    private fun sample(
        timeMs: Long,
        magnitudeUt: Float,
        landscape: Boolean,
        stable: Boolean,
        wireless: Boolean = false,
    ) = DockSample(timeMs, magnitudeUt, landscape, stable, wireless)
}
