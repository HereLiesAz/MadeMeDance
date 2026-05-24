package com.hereliesaz.mademedance

import android.hardware.SensorEvent
import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import kotlin.math.sqrt

class RhythmDetector {

    companion object {
        // Sensitivity 0 demands vigorous movement; 1 triggers on gentle motion.
        // These m/s² endpoints are empirical and need on-device tuning.
        private const val THRESHOLD_AT_MIN_SENSITIVITY = 120.0
        private const val THRESHOLD_AT_MAX_SENSITIVITY = 15.0

        fun thresholdForSensitivity(sensitivity: Float): Double {
            val s = sensitivity.coerceIn(0f, 1f).toDouble()
            return THRESHOLD_AT_MIN_SENSITIVITY +
                s * (THRESHOLD_AT_MAX_SENSITIVITY - THRESHOLD_AT_MIN_SENSITIVITY)
        }
    }

    private val windowSize = 128

    // Circular buffers of primitives — sensor events arrive at SENSOR_DELAY_GAME,
    // so a per-event Pair allocation would churn the GC. head = oldest sample.
    private val timestamps = LongArray(windowSize)
    private val magnitudes = DoubleArray(windowSize)
    private var head = 0
    private var size = 0

    // Reused FFT input (chronological, gravity-removed); avoids re-allocating
    // an array every event. The transformer is stateless and reusable.
    private val fftInput = DoubleArray(windowSize)
    private val transformer = FastFourierTransformer(DftNormalization.STANDARD)

    // Threshold on the dominant FFT bin of the gravity-removed accelerometer
    // magnitude (m/s²). Driven by the user's sensitivity setting; updated live
    // by the service as the knob, ratings, or battery pressure change it.
    @Volatile
    var energyThreshold: Double = thresholdForSensitivity(0.5f)

    fun getMovementBpm(event: SensorEvent): Float? {
        val v = event.values
        val magnitude = sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble())

        val index = if (size < windowSize) {
            ((head + size) % windowSize).also { size++ }
        } else {
            head.also { head = (head + 1) % windowSize }
        }
        timestamps[index] = event.timestamp
        magnitudes[index] = magnitude

        if (size < windowSize) {
            return null // Not enough data yet
        }

        val firstTimestamp = timestamps[head]
        val lastTimestamp = timestamps[(head + windowSize - 1) % windowSize]
        val durationNanos = lastTimestamp - firstTimestamp
        if (durationNanos <= 0L) return null

        // windowSize samples span (windowSize - 1) sampling intervals
        val sampleRate = (windowSize - 1).toDouble() / durationNanos * 1_000_000_000.0

        // Subtract the mean so gravity (a large DC offset on the accelerometer)
        // doesn't dominate; what's left is the oscillation from body movement.
        var sum = 0.0
        for (m in magnitudes) sum += m
        val mean = sum / windowSize

        val firstPart = windowSize - head
        System.arraycopy(magnitudes, head, fftInput, 0, firstPart)
        System.arraycopy(magnitudes, 0, fftInput, firstPart, head)
        for (i in 0 until windowSize) {
            fftInput[i] -= mean
        }
        val fftResults = transformer.transform(fftInput, TransformType.FORWARD)

        var maxMagnitude = -1.0
        var dominantBin = 0
        // Iterate up to half the window size (Nyquist), skipping the DC bin
        for (i in 1 until windowSize / 2) {
            val mag = fftResults[i].abs()
            if (mag > maxMagnitude) {
                maxMagnitude = mag
                dominantBin = i
            }
        }

        if (dominantBin == 0 || maxMagnitude <= energyThreshold) {
            return null
        }

        // Each bin is ~sampleRate/windowSize Hz wide (≈23 BPM here), far coarser
        // than the 5 BPM match tolerance. Parabolic interpolation around the peak
        // recovers a sub-bin frequency estimate.
        val refinedBin = parabolicPeak(fftResults, dominantBin)
        val dominantFrequencyHz = refinedBin * sampleRate / windowSize
        val bpm = (dominantFrequencyHz * 60).toFloat()

        return if (bpm in 60f..180f) bpm else null
    }

    private fun parabolicPeak(fft: Array<Complex>, k: Int): Double {
        if (k <= 0 || k >= fft.size / 2 - 1) return k.toDouble()
        val alpha = fft[k - 1].abs()
        val beta = fft[k].abs()
        val gamma = fft[k + 1].abs()
        val denom = alpha - 2 * beta + gamma
        if (denom == 0.0) return k.toDouble()
        val offset = 0.5 * (alpha - gamma) / denom
        return k + offset
    }
}
