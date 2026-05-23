package com.hereliesaz.mademedance.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.hereliesaz.mademedance.RhythmDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MovementTracker(private val sensorManager: SensorManager) : SensorEventListener {

    private val rhythmDetector = RhythmDetector()
    private val accelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val isAvailable: Boolean = accelerometerSensor != null

    private val _bpm = MutableStateFlow<Float?>(null)
    val bpm: StateFlow<Float?> = _bpm.asStateFlow()

    fun setEnergyThreshold(value: Double) {
        rhythmDetector.energyThreshold = value
    }

    fun start() {
        accelerometerSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            _bpm.value = rhythmDetector.getMovementBpm(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* Not used */ }
}
