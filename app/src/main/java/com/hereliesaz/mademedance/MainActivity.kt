package com.hereliesaz.mademedance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity(), SensorEventListener {

    private val _currentSong = MutableStateFlow("No song detected")
    private val currentSong = _currentSong.asStateFlow()

    private val _status = MutableStateFlow("Listening for dance moves...")
    private val status = _status.asStateFlow()

    private lateinit var nlServiceReceiver: BroadcastReceiver

    private lateinit var sensorManager: SensorManager
    private var gyroscopeSensor: Sensor? = null
    private val rhythmDetector = RhythmDetector()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nlServiceReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val title = intent.getStringExtra(NLService.EXTRA_SONG_TITLE)
                val artist = intent.getStringExtra(NLService.EXTRA_SONG_ARTIST)
                if (title != null && artist != null) {
                    _currentSong.value = "$title by $artist"
                }
            }
        }
        registerReceiver(nlServiceReceiver, IntentFilter(NLService.ACTION_UPDATE_CURRENT_SONG), RECEIVER_NOT_EXPORTED)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        if (!isNotificationServiceEnabled()) {
            _status.value = "Please grant Notification Access in Settings."
        }

        setContent {
            MadeMeDanceTheme {
                val song by currentSong.collectAsState()
                val currentStatus by status.collectAsState()
                MainScreen(
                    currentSong = song,
                    status = currentStatus
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
            val values = event.values
            if (rhythmDetector.detectRhythm(values)) {
                val song = _currentSong.value
                if (song != "No song detected") {
                    _status.value = "Dance detected! '$song' would be added to 'Made Me Dance'."
                } else {
                    _status.value = "Dance detected! But no song is playing."
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* Not used */ }

    private fun isNotificationServiceEnabled(): Boolean {
        val enabledListeners = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return enabledListeners?.contains(packageName) == true
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(nlServiceReceiver)
        stopListening()
    }
}
