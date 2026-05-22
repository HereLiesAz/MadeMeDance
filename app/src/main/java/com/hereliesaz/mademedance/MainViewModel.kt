package com.hereliesaz.mademedance

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.mademedance.data.ClipRepository
import com.hereliesaz.mademedance.sensor.MovementTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import kotlin.math.abs

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _movementBpm = MutableStateFlow<Float?>(null)
    val movementBpm: StateFlow<Float?> = _movementBpm.asStateFlow()

    private val _audioBpm = MutableStateFlow<Float?>(null)
    val audioBpm: StateFlow<Float?> = _audioBpm.asStateFlow()

    private val _movementStatus = MutableStateFlow("Movement: -- BPM")
    val movementStatus: StateFlow<String> = _movementStatus.asStateFlow()

    private val _audioStatus = MutableStateFlow("Song: -- BPM")
    val audioStatus: StateFlow<String> = _audioStatus.asStateFlow()

    private val _systemStatus = MutableStateFlow("Listening...")
    val systemStatus: StateFlow<String> = _systemStatus.asStateFlow()

    private val _hasAudioPermission = MutableStateFlow(false)
    val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission.asStateFlow()

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val movementTracker = MovementTracker(sensorManager)

    private val audioBpmDetector = AudioBpmDetector()
    private var audioJob: Job? = null

    val clipRepository = ClipRepository(application.getExternalFilesDir(null))

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    val hasGyroscope: Boolean = movementTracker.isAvailable

    init {
        refreshPermissionState()
        if (!hasGyroscope) {
            _movementStatus.value = "No gyroscope sensor"
            _systemStatus.value = "This device lacks a gyroscope sensor."
        }
        viewModelScope.launch {
            movementTracker.bpm.collect { bpm ->
                if (bpm != null) {
                    _movementBpm.value = bpm
                    _movementStatus.value = "Movement: ${"%.1f".format(bpm)} BPM"
                    checkForMatch()
                }
            }
        }
    }

    fun refreshPermissionState() {
        _hasAudioPermission.value = ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun onPermissionGranted() {
        _hasAudioPermission.value = true
        startAudioProcessing()
    }

    fun startAudioProcessing() {
        if (audioJob?.isActive == true) return
        audioJob = viewModelScope.launch {
            audioBpmDetector.start()
            while (isActive) {
                val bpm = audioBpmDetector.processAudio()
                if (bpm != null) {
                    _audioBpm.value = bpm
                    _audioStatus.value = "Song: ${"%.1f".format(bpm)} BPM"
                    checkForMatch()
                }
                delay(100)
            }
        }
    }

    fun stopAudioProcessing() {
        audioJob?.cancel()
        audioBpmDetector.stop()
    }

    fun startSensors() {
        movementTracker.start()
    }

    fun stopSensors() {
        movementTracker.stop()
    }

    private fun checkForMatch() {
        val movementBpm = _movementBpm.value
        val audioBpm = _audioBpm.value

        if (movementBpm != null && audioBpm != null) {
            if (abs(movementBpm - audioBpm) < 5.0) {
                _systemStatus.value = "BPM Match Found! Saving snippet..."
                vibrateOnMatch()
                saveAudioSnippet()
                _movementBpm.value = null
                _audioBpm.value = null
                _movementStatus.value = "Movement: -- BPM"
                _audioStatus.value = "Song: -- BPM"
            }
        }
    }

    private fun vibrateOnMatch() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(200)
            }
        } catch (_: Exception) { /* Vibration not available */ }
    }

    private fun saveAudioSnippet() {
        try {
            val timestamp = System.currentTimeMillis()
            val storageDir = getApplication<Application>().getExternalFilesDir(null)
            val file = File(storageDir, "MadeMeDance_snippet_$timestamp.mmd")
            audioBpmDetector.saveSnippet(file)
            _systemStatus.value = "Snippet saved to ${file.name}"
        } catch (e: IOException) {
            _systemStatus.value = "Error saving snippet."
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopSensors()
        stopAudioProcessing()
    }
}
