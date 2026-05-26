package com.hereliesaz.mademedance

import com.hereliesaz.mademedance.sensor.WalkingDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkingDetectorTest {

    /** Build ascending step times from a base plus inter-step intervals (ms). */
    private fun stepsFrom(vararg intervals: Long): List<Long> {
        val times = ArrayList<Long>(intervals.size + 1)
        var t = 1_000L
        times.add(t)
        for (i in intervals) {
            t += i
            times.add(t)
        }
        return times
    }

    @Test
    fun `steady walking cadence is walking`() {
        // ~120 steps/min (500 ms apart), seven steps, near-perfect regularity.
        val steps = stepsFrom(500, 500, 510, 490, 500, 505)
        assertTrue(WalkingDetector.isWalkingCadence(steps))
    }

    @Test
    fun `too few steps is not walking`() {
        val steps = stepsFrom(500, 500, 500) // 4 steps < MIN_STEPS
        assertFalse(WalkingDetector.isWalkingCadence(steps))
    }

    @Test
    fun `irregular dance footwork is not walking`() {
        // Same step count, but wildly varying intervals — high CV.
        val steps = stepsFrom(300, 900, 250, 800, 400, 1000)
        assertFalse(WalkingDetector.isWalkingCadence(steps))
    }

    @Test
    fun `cadence too slow is not walking`() {
        // ~1.2 s between steps — slower than the ambulation band.
        val steps = stepsFrom(1200, 1200, 1200, 1200, 1200, 1200)
        assertFalse(WalkingDetector.isWalkingCadence(steps))
    }

    @Test
    fun `empty input is not walking`() {
        assertFalse(WalkingDetector.isWalkingCadence(emptyList()))
    }
}
