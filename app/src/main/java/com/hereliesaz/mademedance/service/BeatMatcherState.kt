package com.hereliesaz.mademedance.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BeatMatcherState {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _movementBpm = MutableStateFlow<Float?>(null)
    val movementBpm: StateFlow<Float?> = _movementBpm.asStateFlow()

    private val _audioBpm = MutableStateFlow<Float?>(null)
    val audioBpm: StateFlow<Float?> = _audioBpm.asStateFlow()

    private val _movementStatus = MutableStateFlow("Movement: -- BPM")
    val movementStatus: StateFlow<String> = _movementStatus.asStateFlow()

    private val _audioStatus = MutableStateFlow("Song: -- BPM")
    val audioStatus: StateFlow<String> = _audioStatus.asStateFlow()

    private val _systemStatus = MutableStateFlow("Service stopped.")
    val systemStatus: StateFlow<String> = _systemStatus.asStateFlow()

    private val _clipsChanged = MutableStateFlow(0)
    val clipsChanged: StateFlow<Int> = _clipsChanged.asStateFlow()

    private val _batteryDrainPerHour = MutableStateFlow<Float?>(null)
    val batteryDrainPerHour: StateFlow<Float?> = _batteryDrainPerHour.asStateFlow()

    private val _powerSaving = MutableStateFlow(false)
    val powerSaving: StateFlow<Boolean> = _powerSaving.asStateFlow()

    internal fun setBatteryDrainPerHour(drain: Float?) { _batteryDrainPerHour.value = drain }
    internal fun setPowerSaving(saving: Boolean) { _powerSaving.value = saving }

    internal fun setRunning(running: Boolean) { _isRunning.value = running }
    internal fun setMovementBpm(bpm: Float?) {
        _movementBpm.value = bpm
        _movementStatus.value = bpm?.let { "Movement: ${"%.1f".format(it)} BPM" } ?: "Movement: -- BPM"
    }
    internal fun setAudioBpm(bpm: Float?) {
        _audioBpm.value = bpm
        _audioStatus.value = bpm?.let { "Song: ${"%.1f".format(it)} BPM" } ?: "Song: -- BPM"
    }
    internal fun setSystemStatus(message: String) { _systemStatus.value = message }
    internal fun notifyClipsChanged() { _clipsChanged.value = _clipsChanged.value + 1 }
}
