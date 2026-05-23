package com.hereliesaz.mademedance

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.mademedance.data.ClipItem
import com.hereliesaz.mademedance.data.ClipRepository
import com.hereliesaz.mademedance.identify.AcrCloudClient
import com.hereliesaz.mademedance.identify.AcrOutcome
import com.hereliesaz.mademedance.identify.SongIdentifier
import com.hereliesaz.mademedance.service.BeatMatcherService
import com.hereliesaz.mademedance.service.BeatMatcherState
import com.hereliesaz.mademedance.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ClipRecognition {
    data object Idle : ClipRecognition
    data object Loading : ClipRecognition
    data class Done(val artist: String?, val title: String) : ClipRecognition
    data object NoMatch : ClipRecognition
    data class Error(val message: String) : ClipRecognition
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val movementBpm: StateFlow<Float?> = BeatMatcherState.movementBpm
    val audioBpm: StateFlow<Float?> = BeatMatcherState.audioBpm
    val movementStatus: StateFlow<String> = BeatMatcherState.movementStatus
    val audioStatus: StateFlow<String> = BeatMatcherState.audioStatus
    val systemStatus: StateFlow<String> = BeatMatcherState.systemStatus
    val isServiceRunning: StateFlow<Boolean> = BeatMatcherState.isRunning

    val sensitivity: StateFlow<Float> = SettingsStore.sensitivity
    val batteryDrainPerHour: StateFlow<Float?> = BeatMatcherState.batteryDrainPerHour
    val powerSaving: StateFlow<Boolean> = BeatMatcherState.powerSaving

    val acrHost: StateFlow<String> = SettingsStore.acrHost
    val acrKey: StateFlow<String> = SettingsStore.acrKey
    val acrSecret: StateFlow<String> = SettingsStore.acrSecret
    val acrConfigured: StateFlow<Boolean> = SettingsStore.acrConfigured

    private val _recognition = MutableStateFlow<ClipRecognition>(ClipRecognition.Idle)
    val recognition: StateFlow<ClipRecognition> = _recognition.asStateFlow()

    private val _hasAudioPermission = MutableStateFlow(false)
    val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission.asStateFlow()

    private val _hasNotificationPermission = MutableStateFlow(false)
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    private val _hasNowPlayingAccess = MutableStateFlow(false)
    val hasNowPlayingAccess: StateFlow<Boolean> = _hasNowPlayingAccess.asStateFlow()

    val clipRepository = ClipRepository(application.getExternalFilesDir(null))

    init {
        SettingsStore.init(application)
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
        _hasNowPlayingAccess.value = SongIdentifier.hasNotificationAccess(ctx)
    }

    fun startService() {
        BeatMatcherService.start(getApplication())
    }

    fun stopService() {
        BeatMatcherService.stop(getApplication())
    }

    fun setSensitivity(value: Float) {
        SettingsStore.setSensitivity(value)
    }

    /** Good catch — keep the clip, confirm the current sensitivity. */
    fun acceptClip() {
        SettingsStore.recordAccept()
    }

    /** False trigger — delete the clip and back the detector off. */
    fun rejectClip(clip: ClipItem) {
        SettingsStore.recordReject()
        clipRepository.deleteClip(clip.name)
    }

    fun setAcrCredentials(host: String, key: String, secret: String) {
        SettingsStore.setAcrCredentials(host, key, secret)
    }

    /** Identify a saved clip via ACRCloud (works even after the song has stopped). */
    fun recognizeClip(clip: ClipItem) {
        _recognition.value = ClipRecognition.Loading
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                clipRepository.getClipFile(clip.name)?.readBytes()
            }
            if (bytes == null) {
                _recognition.value = ClipRecognition.Error("Clip not found")
                return@launch
            }
            val outcome = AcrCloudClient.recognize(
                host = SettingsStore.acrHost.value,
                accessKey = SettingsStore.acrKey.value,
                accessSecret = SettingsStore.acrSecret.value,
                audio = bytes
            )
            _recognition.value = when (outcome) {
                is AcrOutcome.Success -> {
                    withContext(Dispatchers.IO) {
                        clipRepository.writeMeta(clip.name, outcome.title, outcome.artist)
                    }
                    BeatMatcherState.notifyClipsChanged()
                    ClipRecognition.Done(outcome.artist, outcome.title)
                }
                AcrOutcome.NoMatch -> ClipRecognition.NoMatch
                AcrOutcome.NotConfigured ->
                    ClipRecognition.Error("Add ACRCloud credentials in Settings")
                is AcrOutcome.Error -> ClipRecognition.Error(outcome.message)
            }
        }
    }

    fun clearRecognition() {
        _recognition.value = ClipRecognition.Idle
    }
}
