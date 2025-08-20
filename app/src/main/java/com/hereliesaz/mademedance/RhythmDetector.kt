package com.hereliesaz.mademedance

import android.hardware.SensorEvent
import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import java.util.*

class RhythmDetector {

    private val windowSize = 128 // Increased window size for better frequency resolution
    private val dataQueue = ArrayDeque<Pair<Long, FloatArray>>(windowSize)
    private val magnitudeThreshold = 20.0 // Adjusted threshold for significant movement

    fun getMovementBpm(event: SensorEvent): Float? {
        val timestamp = event.timestamp
        val gyroValues = event.values

        dataQueue.addLast(Pair(timestamp, gyroValues))
        if (dataQueue.size > windowSize) {
            dataQueue.removeFirst()
        }

        if (dataQueue.size < windowSize) {
            return null // Not enough data yet
        }

        val firstTimestamp = dataQueue.first.first
        val lastTimestamp = dataQueue.last.first
        val durationNanos = lastTimestamp - firstTimestamp
        if (durationNanos == 0L) return null

        // Calculate the sampling rate in Hz
        val sampleRate = (windowSize.toDouble() / durationNanos) * 1_000_000_000.0

        // We'll analyze the magnitude of the gyroscope vector
        val magnitudes = dataQueue.map {
            val x = it.second[0]
            val y = it.second[1]
            val z = it.second[2]
            Math.sqrt((x * x + y * y + z * z).toDouble())
        }

        val transformer = FastFourierTransformer(DftNormalization.STANDARD)
        val complexData = magnitudes.map { Complex(it) }.toTypedArray()
        val fftResults = transformer.transform(complexData, TransformType.FORWARD)

        var maxMagnitude = -1.0
        var dominantFrequencyIndex = 0
        // Iterate up to half the window size (Nyquist theorem)
        for (i in 1 until windowSize / 2) {
            val mag = fftResults[i].abs()
            if (mag > maxMagnitude) {
                maxMagnitude = mag
                dominantFrequencyIndex = i
            }
        }

        if (maxMagnitude > magnitudeThreshold) {
            val dominantFrequencyHz = dominantFrequencyIndex * sampleRate / windowSize
            // Convert to BPM, but only for a reasonable dance tempo range (e.g., 60-180 BPM)
            val bpm = (dominantFrequencyHz * 60).toFloat()
            if (bpm in 60f..180f) {
                return bpm
            }
        }

        return null
    }
}
