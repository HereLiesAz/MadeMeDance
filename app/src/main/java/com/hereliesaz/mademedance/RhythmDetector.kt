package com.hereliesaz.mademedance

import android.hardware.SensorEvent
import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import java.util.*

class RhythmDetector {

    private val windowSize = 20 // Number of samples to analyze
    private val dataQueue = ArrayDeque<FloatArray>(windowSize)
    private var previousTimestamp: Long = 0

    fun detectRhythm(gyroValues: FloatArray): Boolean {
        previousTimestamp = System.currentTimeMillis()

        // Add to queue and maintain window size
        dataQueue.addLast(gyroValues)
        if (dataQueue.size > windowSize) {
            dataQueue.removeFirst()
        }

        // Analyze only when the window is full
        if (dataQueue.size == windowSize) {
            // Fourier Transform analysis
            val transformer = FastFourierTransformer(DftNormalization.STANDARD)
            val complexData = dataQueue.flatMap { it.map { Complex(it.toDouble()) } }.toTypedArray()
            val results = transformer.transform(complexData, TransformType.FORWARD)

            // Focus on lower frequencies for rhythmic patterns (adjust range)
            val frequencies = results.copyOfRange(1, results.size / 2)  // Skip DC component
            val magnitudes = frequencies.map { it.abs() }

            // Find dominant frequency and its magnitude
            val maxMagnitudeIndex = magnitudes.indices.maxByOrNull { magnitudes[it] } ?: 0
            val maxMagnitude = magnitudes[maxMagnitudeIndex]
            val dominantFrequency = maxMagnitudeIndex + 1 // Account for skipped DC

            // Thresholds for rhythm detection (adjust these!)
            val magnitudeThreshold = 0.1  // Minimum magnitude of dominant frequency
            val frequencyThreshold = 5.0  // Maximum frequency for rhythm (Hz)

            val isRhythmic = (maxMagnitude > magnitudeThreshold) &&
                    (dominantFrequency < frequencyThreshold)

            // Logging (optional, for debugging)
            //Log.d("RhythmDetector", "Dominant Freq: $dominantFrequency, MaxMag: $maxMagnitude, Rhythmic: $isRhythmic")

            return isRhythmic
        }
        return false
    }

}
