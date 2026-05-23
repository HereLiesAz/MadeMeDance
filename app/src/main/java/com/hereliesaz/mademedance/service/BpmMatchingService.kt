package com.hereliesaz.mademedance.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.hereliesaz.mademedance.AudioBpmDetector
import com.hereliesaz.mademedance.data.ClipRepository
import com.hereliesaz.mademedance.sensor.MovementTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import kotlin.math.abs

class BpmMatchingService : Service() {

    // Public state for UI observation
    private val _movementBpm = MutableStateFlow<Float?>(null)
    val movementBpm: StateFlow<Float?> = _movementBpm.asStateFlow()

    private val _audioBpm = MutableStateFlow<Float?>(null)
    val audioBpm: StateFlow<Float?> = _audioBpm.asStateFlow()

    private val _statusMessage = MutableStateFlow("Listening for movement...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _movementStatusText = MutableStateFlow("Movement: -- BPM")
    val movementStatusText: StateFlow<String> = _movementStatusText.asStateFlow()

    private val _audioStatusText = MutableStateFlow("Song: -- BPM")
    val audioStatusText: StateFlow<String> = _audioStatusText.asStateFlow()

    // Internal components
    private lateinit var movementTracker: MovementTracker
    private lateinit var audioBpmDetector: AudioBpmDetector
    lateinit var clipRepository: ClipRepository
        private set

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var audioJob: Job? = null
    private var movementTimeoutJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null

    val hasGyroscope: Boolean
        get() = if (::movementTracker.isInitialized) movementTracker.isAvailable else false

    // Binding
    inner class LocalBinder : Binder() {
        val service: BpmMatchingService get() = this@BpmMatchingService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()

        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        movementTracker = MovementTracker(sensorManager)
        audioBpmDetector = AudioBpmDetector()
        clipRepository = ClipRepository(getExternalFilesDir(null))

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        ServiceNotification.createChannel(this)

        // Partial wake lock for sensor delivery when screen off
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MadeMeDance::BpmMatching"
        ).apply { acquire() }

        // Start gyroscope monitoring
        movementTracker.start()

        // Collect movement BPM — start audio when rhythm detected
        serviceScope.launch {
            movementTracker.bpm.collect { bpm ->
                if (bpm != null) {
                    _movementBpm.value = bpm
                    _movementStatusText.value = "Movement: ${"%.1f".format(bpm)} BPM"
                    updateNotification("Moving: ${"%.0f".format(bpm)} BPM")
                    ensureAudioStarted()
                    resetMovementTimeout()
                    checkForMatch()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = ServiceNotification.build(this, "Listening for movement...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ServiceNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(ServiceNotification.NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    // Audio lifecycle tied to movement detection

    private fun ensureAudioStarted() {
        if (audioJob?.isActive == true) return
        audioJob = serviceScope.launch {
            audioBpmDetector.start()
            _audioStatusText.value = "Song: listening..."
            while (isActive) {
                val bpm = audioBpmDetector.processAudio()
                if (bpm != null) {
                    _audioBpm.value = bpm
                    _audioStatusText.value = "Song: ${"%.1f".format(bpm)} BPM"
                    checkForMatch()
                }
                delay(100)
            }
        }
    }

    private fun stopAudio() {
        audioJob?.cancel()
        audioJob = null
        audioBpmDetector.stop()
        _audioBpm.value = null
        _audioStatusText.value = "Song: -- BPM"
    }

    /** Stop audio after 10s of no movement to save battery. */
    private fun resetMovementTimeout() {
        movementTimeoutJob?.cancel()
        movementTimeoutJob = serviceScope.launch {
            delay(10_000)
            stopAudio()
            _movementBpm.value = null
            _movementStatusText.value = "Movement: -- BPM"
            _statusMessage.value = "Listening for movement..."
            updateNotification("Listening for movement...")
        }
    }

    // Match logic

    private fun checkForMatch() {
        val mBpm = _movementBpm.value
        val aBpm = _audioBpm.value

        if (mBpm != null && aBpm != null && abs(mBpm - aBpm) < 5.0f) {
            _statusMessage.value = "BPM Match Found! Saving snippet..."
            updateNotification("Match! Saving clip...")
            vibrateOnMatch()
            saveAudioSnippet()

            _movementBpm.value = null
            _audioBpm.value = null
            _movementStatusText.value = "Movement: -- BPM"
            _audioStatusText.value = "Song: -- BPM"
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
        } catch (_: Exception) {}
    }

    private fun saveAudioSnippet() {
        try {
            val timestamp = System.currentTimeMillis()
            val file = File(getExternalFilesDir(null), "MadeMeDance_snippet_$timestamp.mmd")
            audioBpmDetector.saveSnippet(file)
            _statusMessage.value = "Snippet saved: ${file.name}"
            updateNotification("Clip saved!")
        } catch (_: IOException) {
            _statusMessage.value = "Error saving snippet."
        }
    }

    private fun updateNotification(text: String) {
        val notification = ServiceNotification.build(this, text)
        getSystemService(android.app.NotificationManager::class.java)
            .notify(ServiceNotification.NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        movementTracker.stop()
        audioBpmDetector.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
