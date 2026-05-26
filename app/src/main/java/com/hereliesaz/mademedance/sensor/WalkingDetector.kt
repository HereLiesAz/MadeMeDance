package com.hereliesaz.mademedance.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import kotlin.math.sqrt

/**
 * Flags sustained, regular walking/running gait so the beat matcher can ignore
 * it. A walk's footfall cadence lands in the same 60–180 BPM range as music, so
 * without this it reads as "dancing" and triggers constant false matches.
 *
 * Uses the platform step-detector sensor (one event per detected step). A run of
 * recent steps whose inter-step interval is steady (low coefficient of variation)
 * and within an ambulation cadence is treated as walking. Dance footwork is
 * comparatively irregular, so it's left alone.
 *
 * Degrades to a no-op — never reporting walking — when the device has no step
 * detector or the ACTIVITY_RECOGNITION permission wasn't granted, preserving the
 * prior behaviour rather than over-suppressing.
 */
class WalkingDetector(private val sensorManager: SensorManager) : SensorEventListener {

    companion object {
        // Only steps within this look-back window count; older ones are pruned so
        // the flag clears soon after walking stops.
        const val WINDOW_MS = 5_000L

        // A sustained run of steps is required before committing — a couple of
        // stray steps (or a few dance stomps) shouldn't suppress matching.
        const val MIN_STEPS = 6

        // Ambulation cadence band as the interval between steps. ~67–230 steps/min
        // spans a slow stroll through a jog.
        const val MIN_STEP_INTERVAL_MS = 260.0
        const val MAX_STEP_INTERVAL_MS = 900.0

        // Gait is highly periodic; dance footwork is not. CV = stddev / mean of the
        // inter-step intervals.
        const val MAX_INTERVAL_CV = 0.30

        /**
         * Pure classifier over recent step times (ms, ascending). Extracted for
         * testability; the live detector feeds it the pruned window.
         */
        fun isWalkingCadence(stepTimesMs: List<Long>): Boolean {
            if (stepTimesMs.size < MIN_STEPS) return false
            val intervals = DoubleArray(stepTimesMs.size - 1)
            var sum = 0.0
            for (i in 1 until stepTimesMs.size) {
                val d = (stepTimesMs[i] - stepTimesMs[i - 1]).toDouble()
                intervals[i - 1] = d
                sum += d
            }
            val mean = sum / intervals.size
            if (mean < MIN_STEP_INTERVAL_MS || mean > MAX_STEP_INTERVAL_MS) return false
            var varSum = 0.0
            for (d in intervals) {
                val diff = d - mean
                varSum += diff * diff
            }
            val cv = sqrt(varSum / intervals.size) / mean
            return cv <= MAX_INTERVAL_CV
        }
    }

    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    val isAvailable: Boolean = stepSensor != null

    // Guarded by `lock`: appended on the sensor thread, read from the service's
    // coroutine dispatcher.
    private val lock = Any()
    private val stepTimes = ArrayDeque<Long>()

    private val _walking = MutableStateFlow(false)
    val walking: StateFlow<Boolean> = _walking.asStateFlow()

    fun start() {
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        synchronized(lock) { stepTimes.clear() }
        _walking.value = false
    }

    /**
     * Prunes the look-back window to [now] and recomputes, updating [walking].
     * Call at decision time so the flag both engages and clears promptly.
     */
    fun isWalking(now: Long = System.nanoTime() / 1_000_000L): Boolean {
        val recent = synchronized(lock) {
            prune(now)
            stepTimes.toList()
        }
        return isWalkingCadence(recent).also { _walking.value = it }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_DETECTOR) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            stepTimes.addLast(now)
            prune(now)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* Not used */ }

    private fun prune(now: Long) {
        while (stepTimes.isNotEmpty() && now - stepTimes.first > WINDOW_MS) {
            stepTimes.removeFirst()
        }
    }
}
