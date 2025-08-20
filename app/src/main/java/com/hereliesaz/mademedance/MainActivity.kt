package com.hereliesaz.mademedance

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.hereliesaz.mademedance.ui.MainScreen
import com.hereliesaz.mademedance.ui.theme.MadeMeDanceTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class MainActivity : ComponentActivity(), SensorEventListener {

    private val _movementBpm = MutableStateFlow<Float?>(null)
    private val _audioBpm = MutableStateFlow<Float?>(null)

    private val _movementStatus = MutableStateFlow("Movement: -- BPM")
    val movementStatus = _movementStatus.asStateFlow()
    private val _audioStatus = MutableStateFlow("Song: -- BPM")
    val audioStatus = _audioStatus.asStateFlow()
    private val _systemStatus = MutableStateFlow("Listening...")
    val systemStatus = _systemStatus.asStateFlow()

    private lateinit var sensorManager: SensorManager
    private var gyroscopeSensor: Sensor? = null
    private val rhythmDetector = RhythmDetector()

    private val audioBpmDetector = AudioBpmDetector()
    private var audioJob: kotlinx.coroutines.Job? = null

    private val requestPermissionLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                _systemStatus.value = "Microphone permission granted. Listening..."
                startAudioProcessing()
            } else {
                _systemStatus.value = "Microphone permission denied."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        setContent {
            MadeMeDanceTheme {
                val movement by movementStatus.collectAsState()
                val audio by audioStatus.collectAsState()
                val system by systemStatus.collectAsState()
                MainScreen(
                    movementStatus = movement,
                    audioStatus = audio,
                    systemStatus = system,
                    onPermissionClick = { requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startListening()
    }

    override fun onPause() {
        super.onPause()
        stopListening()
        stopAudioProcessing()
    }

    private fun startAudioProcessing() {
        audioJob?.cancel()
        audioJob = MainScope().launch {
            audioBpmDetector.start()
            while (isActive) {
                val bpm = audioBpmDetector.processAudio()
                if (bpm != null) {
                    _audioBpm.value = bpm
                    _audioStatus.value = "Song: ${"%.1f".format(bpm)} BPM"
                    checkForMatch()
                }
                kotlinx.coroutines.delay(100)
            }
        }
    }

    private fun stopAudioProcessing() {
        audioJob?.cancel()
        audioBpmDetector.stop()
    }

    private fun startListening() {
        gyroscopeSensor?.also { gyro ->
            sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            val bpm = rhythmDetector.getMovementBpm(event)
            if (bpm != null) {
                _movementBpm.value = bpm
                _movementStatus.value = "Movement: ${"%.1f".format(bpm)} BPM"
                checkForMatch()
            }
        }
    }

    private fun checkForMatch() {
        val movementBpm = _movementBpm.value
        val audioBpm = _audioBpm.value

        if (movementBpm != null && audioBpm != null) {
            if (kotlin.math.abs(movementBpm - audioBpm) < 5.0) {
                _systemStatus.value = "BPM Match Found! Saving snippet..."
                saveAudioSnippet()
                _movementBpm.value = null
                _audioBpm.value = null
            }
        }
    }

    private fun saveAudioSnippet() {
        try {
            val timestamp = System.currentTimeMillis()
            val file = File(getExternalFilesDir(null), "MadeMeDance_snippet_$timestamp.wav")
            audioBpmDetector.saveSnippet(file)
            _systemStatus.value = "Snippet saved to ${file.name}"
        } catch (e: IOException) {
            _systemStatus.value = "Error saving snippet."
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* Not used */ }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        stopAudioProcessing()
    }
}
