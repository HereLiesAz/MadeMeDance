package com.hereliesaz.mademedance

import android.hardware.SensorEvent
import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import java.util.*
import kotlin.math.sqrt

class RhythmDetector {

    private val windowSize = 128
    private val dataQueue = ArrayDeque<Pair<Long, Double>>(windowSize)

    // Threshold on the dominant FFT bin of the gravity-removed accelerometer
    // magnitude (m/s²). Empirical — needs on-device tuning; too high and gentle
    // dancing won't register, too low and walking/jitter triggers it.
    private val energyThreshold = 50.0

    fun getMovementBpm(event: SensorEvent): Float? {
        val timestamp = event.timestamp
        val v = event.values
        val magnitude = sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble())

        dataQueue.addLast(Pair(timestamp, magnitude))
        if (dataQueue.size > windowSize) {
            dataQueue.removeFirst()
        }
        if (dataQueue.size < windowSize) {
            return null // Not enough data yet
        }

        val firstTimestamp = dataQueue.first.first
        val lastTimestamp = dataQueue.last.first
        val durationNanos = lastTimestamp - firstTimestamp
        if (durationNanos <= 0L) return null

        // windowSize samples span (windowSize - 1) sampling intervals
        val sampleRate = (windowSize - 1).toDouble() / durationNanos * 1_000_000_000.0

        // Subtract the mean so gravity (a large DC offset on the accelerometer)
        // doesn't dominate; what's left is the oscillation from body movement.
        val mean = dataQueue.sumOf { it.second } / windowSize
        val transformer = FastFourierTransformer(DftNormalization.STANDARD)
        val complexData = dataQueue.map { Complex(it.second - mean) }.toTypedArray()
        val fftResults = transformer.transform(complexData, TransformType.FORWARD)

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
