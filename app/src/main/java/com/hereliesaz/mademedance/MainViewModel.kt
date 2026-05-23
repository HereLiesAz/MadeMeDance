package com.hereliesaz.mademedance

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.hereliesaz.mademedance.data.ClipRepository
import com.hereliesaz.mademedance.service.BeatMatcherService
import com.hereliesaz.mademedance.service.BeatMatcherState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val movementBpm: StateFlow<Float?> = BeatMatcherState.movementBpm
    val audioBpm: StateFlow<Float?> = BeatMatcherState.audioBpm
    val movementStatus: StateFlow<String> = BeatMatcherState.movementStatus
    val audioStatus: StateFlow<String> = BeatMatcherState.audioStatus
    val systemStatus: StateFlow<String> = BeatMatcherState.systemStatus
    val isServiceRunning: StateFlow<Boolean> = BeatMatcherState.isRunning

    private val _hasAudioPermission = MutableStateFlow(false)
    val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission.asStateFlow()

    private val _hasNotificationPermission = MutableStateFlow(false)
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    val clipRepository = ClipRepository(application.getExternalFilesDir(null))

    val hasGyroscope: Boolean = run {
        val sm = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
    }

    init {
        refreshPermissionState()
    }

    fun refreshPermissionState() {
        val ctx = getApplication<Application>()
        _hasAudioPermission.value = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        _hasNotificationPermission.value =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
    }

    fun startService() {
        BeatMatcherService.start(getApplication())
    }

    fun stopService() {
        BeatMatcherService.stop(getApplication())
    }
}
