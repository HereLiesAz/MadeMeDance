package com.hereliesaz.mademedance

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.mademedance.data.ClipRepository
import com.hereliesaz.mademedance.service.BpmMatchingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // UI-facing state (mirrored from service)
    private val _movementBpm = MutableStateFlow<Float?>(null)
    val movementBpm: StateFlow<Float?> = _movementBpm.asStateFlow()

    private val _audioBpm = MutableStateFlow<Float?>(null)
    val audioBpm: StateFlow<Float?> = _audioBpm.asStateFlow()

    private val _movementStatus = MutableStateFlow("Movement: -- BPM")
    val movementStatus: StateFlow<String> = _movementStatus.asStateFlow()

    private val _audioStatus = MutableStateFlow("Song: -- BPM")
    val audioStatus: StateFlow<String> = _audioStatus.asStateFlow()

    private val _systemStatus = MutableStateFlow("Starting service...")
    val systemStatus: StateFlow<String> = _systemStatus.asStateFlow()

    private val _hasAudioPermission = MutableStateFlow(false)
    val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    var clipRepository: ClipRepository? = null
        private set

    var hasGyroscope: Boolean = true
        private set

    // Service binding
    private var service: BpmMatchingService? = null
    private var bound = false
    private var collectionJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as BpmMatchingService.LocalBinder
            service = localBinder.service
            bound = true
            _isServiceRunning.value = true
            clipRepository = localBinder.service.clipRepository
            hasGyroscope = localBinder.service.hasGyroscope
            if (!hasGyroscope) {
                _movementStatus.value = "No gyroscope sensor"
                _systemStatus.value = "This device lacks a gyroscope sensor."
            }
            startCollecting()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            _isServiceRunning.value = false
            collectionJob?.cancel()
        }
    }

    init {
        refreshPermissionState()
    }

    fun startService() {
        val app = getApplication<Application>()
        val intent = Intent(app, BpmMatchingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
        app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun stopService() {
        val app = getApplication<Application>()
        collectionJob?.cancel()
        if (bound) {
            app.unbindService(connection)
            bound = false
        }
        app.stopService(Intent(app, BpmMatchingService::class.java))
        _isServiceRunning.value = false
        _systemStatus.value = "Service stopped"
    }

    private fun startCollecting() {
        val svc = service ?: return
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            launch { svc.movementBpm.collect { _movementBpm.value = it } }
            launch { svc.audioBpm.collect { _audioBpm.value = it } }
            launch { svc.statusMessage.collect { _systemStatus.value = it } }
            launch { svc.movementStatusText.collect { _movementStatus.value = it } }
            launch { svc.audioStatusText.collect { _audioStatus.value = it } }
        }
    }

    fun refreshPermissionState() {
        _hasAudioPermission.value = ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun onPermissionGranted() {
        _hasAudioPermission.value = true
    }

    override fun onCleared() {
        super.onCleared()
        collectionJob?.cancel()
        if (bound) {
            try {
                getApplication<Application>().unbindService(connection)
            } catch (_: Exception) {}
            bound = false
        }
        // Do NOT stop the service — it keeps running in the background
    }
}
