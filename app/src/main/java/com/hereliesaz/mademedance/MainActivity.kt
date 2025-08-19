package com.hereliesaz.mademedance // Your package name

// ... (imports for playlist handling, etc.)
// ... other imports ...
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity


abstract class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var gyroscopeSensor: Sensor? = null
    private val rhythmDetector = RhythmDetector() // Hypothetical class for analysis

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Your layout file

        // Initialize sensor manager and gyroscope
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // ... (UI elements for toggle, sensitivity, status)
    }

    override fun onResume() {
        super.onResume()
        gyroscopeSensor?.also { gyro ->
            sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // ... (other lifecycle methods)

    // Handle gyroscope events
    override fun onSensorChanged(event: SensorEvent?) {


        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            val values = event.values
        if (rhythmDetector.detectRhythm(values) && currentSong != null) {
            addToPlaylist(currentSong!!, "Made Me Dance")
            val currentSong = getCurrentSong()

            // Feedback (optional)
            Toast.makeText(this, "Added to playlist: $currentSong", Toast.LENGTH_SHORT).show()
        }
        }
    }

    private fun addToPlaylist(currentSong: String, s: String) {

    }

    // ... (helper functions: getCurrentSong, addToPlaylist)
    private fun getCurrentSong(): String? {
        val packageManager = packageManager
        val componentName = ComponentName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.soundsearch.SoundSearchService")
        if (packageManager.getComponentEnabledSetting(componentName) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            return null // Now Playing is not enabled
        }

        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                return "$title - $artist"
            }
        }
        return null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* Not used here */ }
}



// ... inside MainActivity class ...

private var currentSong: String? = null
private lateinit var nlServiceReceiver: BroadcastReceiver

fun onResume() {
    super.onResume()
    // ... (gyroscope registration)

    nlServiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == NLService.ACTION_UPDATE_CURRENT_SONG) {
                currentSong = intent.getStringExtra(NLService.EXTRA_SONG_TITLE)
                // ... (update UI or use in rhythm detection logic)
            }
        }
    }

    registerReceiver(nlServiceReceiver, IntentFilter(NLService.ACTION_UPDATE_CURRENT_SONG))
}

override fun onPause() {
    super.onPause()
    // ... (unregister gyroscope)
    unregisterReceiver(nlServiceReceiver)
}

fun unregisterReceiver(nlServiceReceiver: BroadcastReceiver) {

}

// ... inside onSensorChanged ...


// ... rest of MainActivity class ...

