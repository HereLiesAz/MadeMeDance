package com.hereliesaz.mademedance

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class AudioBpmDetector {

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val fftSize = 1024 // Must be power of 2 for FFT
    private var bufferSize = 0
    private var audioRecord: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun start() {
        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            .coerceAtLeast(fftSize * 2) // Ensure buffer is at least fftSize samples (16-bit = 2 bytes each)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
        audioRecord?.startRecording()
    }

    private val energyHistory = mutableListOf<Double>()
    private val historySize = 43 // Approx 1 second of history at 44100/1024 rate
    private val beatTimestamps = mutableListOf<Long>()
    private val beatThresholdFactor = 1.5

    // Pre-allocated ring buffer: 15 seconds of mono 16-bit audio
    private val rollingBufferSizeSeconds = 15
    private val ringBufferCapacity = sampleRate * rollingBufferSizeSeconds
    private val ringBuffer = ShortArray(ringBufferCapacity)
    private var ringWritePos = 0
    private var ringBufferFilled = false

    suspend fun processAudio(): Float? {
        if (audioRecord == null || bufferSize == 0) return null

        val buffer = ShortArray(bufferSize)
        val readResult = audioRecord?.read(buffer, 0, bufferSize)

        if (readResult == null || readResult <= 0) {
            return null
        }

        // Write to ring buffer (no allocations)
        for (i in 0 until readResult) {
            ringBuffer[ringWritePos] = buffer[i]
            ringWritePos++
            if (ringWritePos >= ringBufferCapacity) {
                ringWritePos = 0
                ringBufferFilled = true
            }
        }

        return withContext(Dispatchers.Default) {
            // Use only first fftSize samples for FFT (guaranteed power of 2)
            val fftData = Array(fftSize) { i ->
                if (i < readResult) Complex(buffer[i].toDouble()) else Complex.ZERO
            }
            val transformer = FastFourierTransformer(DftNormalization.STANDARD)
            val fftResults = transformer.transform(fftData, TransformType.FORWARD)

            // Calculate energy in bass frequency range (60-250 Hz)
            val bassStartIndex = (60.0 * fftSize / sampleRate).toInt()
            val bassEndIndex = (250.0 * fftSize / sampleRate).toInt()
            var currentEnergy = 0.0
            for (i in bassStartIndex..bassEndIndex) {
                currentEnergy += fftResults[i].abs()
            }

            if (energyHistory.size < historySize) {
                energyHistory.add(currentEnergy)
                return@withContext null
            }

            val averageEnergy = energyHistory.average()
            val isBeat = currentEnergy > averageEnergy * beatThresholdFactor

            energyHistory.add(currentEnergy)
            if (energyHistory.size > historySize) {
                energyHistory.removeAt(0)
            }

            if (isBeat) {
                val now = System.currentTimeMillis()
                beatTimestamps.add(now)
                if (beatTimestamps.size > 10) {
                    beatTimestamps.removeAt(0)
                }

                if (beatTimestamps.size > 2) {
                    val intervals = beatTimestamps.zipWithNext { a, b -> (b - a).toDouble() }
                    val averageInterval = intervals.average()
                    if (averageInterval > 0) {
                        return@withContext (60_000.0 / averageInterval).toFloat()
                    }
                }
            }
            null
        }
    }

    fun stop() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    @Throws(IOException::class)
    fun saveSnippet(file: File) {
        val data: ShortArray = if (ringBufferFilled) {
            // Buffer has wrapped: read from writePos to end, then start to writePos
            val result = ShortArray(ringBufferCapacity)
            System.arraycopy(ringBuffer, ringWritePos, result, 0, ringBufferCapacity - ringWritePos)
            System.arraycopy(ringBuffer, 0, result, ringBufferCapacity - ringWritePos, ringWritePos)
            result
        } else {
            // Buffer hasn't wrapped yet: read from 0 to writePos
            ringBuffer.copyOfRange(0, ringWritePos)
        }
        val outputStream = FileOutputStream(file)
        writeWavHeader(outputStream, data.size * 2)
        outputStream.write(shortArrayToByteArray(data))
        outputStream.close()
    }

    private fun shortArrayToByteArray(shortArray: ShortArray): ByteArray {
        val byteData = ByteArray(shortArray.size * 2)
        for (i in shortArray.indices) {
            byteData[i * 2] = (shortArray[i].toInt() and 0x00FF).toByte()
            byteData[i * 2 + 1] = (shortArray[i].toInt() shr 8).toByte()
        }
        return byteData
    }

    @Throws(IOException::class)
    private fun writeWavHeader(out: FileOutputStream, totalAudioLen: Int) {
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = (sampleRate * 16 * channels / 8).toLong()

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (2 * 16 / 8).toByte()
        header[33] = 0
        header[34] = 16
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()
        out.write(header, 0, 44)
    }
}
