package com.hereliesaz.mademedance.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hereliesaz.mademedance.AudioBpmDetector
import com.hereliesaz.mademedance.MainActivity
import com.hereliesaz.mademedance.R
import com.hereliesaz.mademedance.RhythmDetector
import com.hereliesaz.mademedance.data.writeClipMeta
import com.hereliesaz.mademedance.identify.IdentifyResult
import com.hereliesaz.mademedance.identify.SongIdentifier
import com.hereliesaz.mademedance.sensor.MovementTracker
import com.hereliesaz.mademedance.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.ArrayDeque
import kotlin.math.abs

class BeatMatcherService : Service() {

    companion object {
        const val ACTION_START = "com.hereliesaz.mademedance.action.START"
        const val ACTION_STOP = "com.hereliesaz.mademedance.action.STOP"

        private const val CHANNEL_STATUS = "beat_matcher_status"
        private const val CHANNEL_MATCHES = "beat_matcher_matches"
        private const val NOTIFICATION_ID_STATUS = 1001
        private const val NOTIFICATION_ID_MATCH_BASE = 2000

        private const val BPM_MATCH_THRESHOLD = 5.0
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 1000L

        // The accelerometer (cheap) runs continuously; the mic (expensive,
        // privacy-sensitive) only wakes while you're dancing and shuts off
        // after this long without a detected movement BPM.
        private const val MIC_IDLE_TIMEOUT_MS = 8_000L
        private const val MIC_IDLE_CHECK_INTERVAL_MS = 2_000L

        // Battery-driven power saving. Drain below DRAIN_OK_PER_HOUR is fine;
        // at/above DRAIN_HIGH_PER_HOUR we apply the full backoff. Values are
        // %/hour and empirical.
        private const val BATTERY_SAMPLE_INTERVAL_MS = 60_000L
        private const val BATTERY_WINDOW_MS = 10 * 60 * 1000L
        private const val DRAIN_OK_PER_HOUR = 8f
        private const val DRAIN_HIGH_PER_HOUR = 25f
        private const val MAX_BATTERY_SENSITIVITY_PENALTY = 0.4f

        // Audio processing cadence stretches under power saving — the real
        // battery lever, since the mic + FFT loop dominates consumption.
        private const val AUDIO_INTERVAL_MIN_MS = 100L
        private const val AUDIO_INTERVAL_MAX_MS = 400L

        fun start(context: Context) {
            val intent = Intent(context, BeatMatcherService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BeatMatcherService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var audioJob: Job? = null
    private var sensorJob: Job? = null
    private var notificationJob: Job? = null
    private var batteryJob: Job? = null
    private var sensitivityJob: Job? = null
    private var micWatchdogJob: Job? = null

    private lateinit var audioBpmDetector: AudioBpmDetector
    private lateinit var movementTracker: MovementTracker
    private var wakeLock: PowerManager.WakeLock? = null
    private var matchCounter = 0

    @Volatile
    private var lastDanceMs = 0L

    private val batterySamples = ArrayDeque<Pair<Long, Float>>()

    @Volatile
    private var batteryPenalty = 0f

    @Volatile
    private var audioIntervalMs = AUDIO_INTERVAL_MIN_MS

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        createNotificationChannels()
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        movementTracker = MovementTracker(sensorManager)
        audioBpmDetector = AudioBpmDetector()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopWork()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startWork()
        }
        return START_STICKY
    }

    private fun startWork() {
        if (BeatMatcherState.isRunning.value) return

        startForegroundWithNotification()
        acquireWakeLock()

        BeatMatcherState.setRunning(true)
        BeatMatcherState.setSystemStatus("Starting…")

        val hasMic = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            BeatMatcherState.setSystemStatus("Microphone permission required.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        if (movementTracker.isAvailable) {
            movementTracker.start()
            BeatMatcherState.setSystemStatus("Waiting for you to dance…")
            sensorJob = scope.launch {
                movementTracker.bpm.collect { bpm ->
                    if (bpm != null) {
                        BeatMatcherState.setMovementBpm(bpm)
                        lastDanceMs = System.currentTimeMillis()
                        ensureAudioRunning()
                        checkForMatch()
                    }
                }
            }
            // Shut the mic back off once the dancing stops.
            micWatchdogJob = scope.launch {
                while (isActive) {
                    delay(MIC_IDLE_CHECK_INTERVAL_MS)
                    if (audioJob?.isActive == true &&
                        System.currentTimeMillis() - lastDanceMs > MIC_IDLE_TIMEOUT_MS
                    ) {
                        stopAudio()
                        BeatMatcherState.setSystemStatus("Waiting for you to dance…")
                    }
                }
            }
        } else {
            BeatMatcherState.setSystemStatus("No motion sensor on this device.")
        }

        notificationJob = scope.launch {
            while (isActive) {
                delay(NOTIFICATION_UPDATE_INTERVAL_MS)
                updateForegroundNotification()
            }
        }

        // React to the user moving the sensitivity knob (or a rating lowering it).
        sensitivityJob = scope.launch {
            SettingsStore.sensitivity.collect { applyBackoff() }
        }

        batterySamples.clear()
        batteryJob = scope.launch {
            while (isActive) {
                sampleBattery()
                delay(BATTERY_SAMPLE_INTERVAL_MS)
            }
        }
    }

    @Synchronized
    private fun ensureAudioRunning() {
        if (audioJob?.isActive == true) return
        BeatMatcherState.setMicActive(true)
        BeatMatcherState.setSystemStatus("Dancing detected — listening for the song…")
        audioJob = scope.launch {
            audioBpmDetector.start()
            while (isActive) {
                val bpm = audioBpmDetector.processAudio()
                if (bpm != null) {
                    BeatMatcherState.setAudioBpm(bpm)
                    checkForMatch()
                }
                delay(audioIntervalMs)
            }
        }
    }

    @Synchronized
    private fun stopAudio() {
        audioJob?.cancel(); audioJob = null
        try { audioBpmDetector.stop() } catch (_: Exception) {}
        BeatMatcherState.setAudioBpm(null)
        BeatMatcherState.setMicActive(false)
    }

    /**
     * Combine the persisted sensitivity with the current battery penalty
     * (both can only lower it) into the effective threshold and processing
     * cadence, then push them to the detector + audio loop.
     */
    private fun applyBackoff() {
        val base = SettingsStore.sensitivity.value
        val effective = (base - batteryPenalty).coerceIn(0f, 1f)
        movementTracker.setEnergyThreshold(RhythmDetector.thresholdForSensitivity(effective))

        val penaltyNorm = (batteryPenalty / MAX_BATTERY_SENSITIVITY_PENALTY).coerceIn(0f, 1f)
        audioIntervalMs = AUDIO_INTERVAL_MIN_MS +
            (penaltyNorm * (AUDIO_INTERVAL_MAX_MS - AUDIO_INTERVAL_MIN_MS)).toLong()

        BeatMatcherState.setPowerSaving(batteryPenalty > 0f)
    }

    private fun sampleBattery() {
        val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (pct !in 0..100) return

        val now = System.currentTimeMillis()
        batterySamples.addLast(now to pct.toFloat())
        while (batterySamples.size > 1 && now - batterySamples.first.first > BATTERY_WINDOW_MS) {
            batterySamples.removeFirst()
        }

        val oldest = batterySamples.first
        val hours = (now - oldest.first) / 3_600_000.0
        // Negative drain = charging; clamp to 0.
        val drain = if (hours > 0.0) ((oldest.second - pct) / hours).toFloat().coerceAtLeast(0f) else null

        batteryPenalty = if (drain == null) {
            0f
        } else {
            val span = (DRAIN_HIGH_PER_HOUR - DRAIN_OK_PER_HOUR)
            (((drain - DRAIN_OK_PER_HOUR) / span).coerceIn(0f, 1f)) * MAX_BATTERY_SENSITIVITY_PENALTY
        }

        BeatMatcherState.setBatteryDrainPerHour(drain)
        applyBackoff()
    }

    private fun checkForMatch() {
        val movement = BeatMatcherState.movementBpm.value
        val audio = BeatMatcherState.audioBpm.value
        if (movement != null && audio != null && abs(movement - audio) < BPM_MATCH_THRESHOLD) {
            BeatMatcherState.setSystemStatus("BPM match! Saving snippet...")
            vibrateOnMatch()
            val savedFile = saveAudioSnippet()
            BeatMatcherState.setMovementBpm(null)
            BeatMatcherState.setAudioBpm(null)
            if (savedFile != null) {
                // The music is live right now, so capture what's playing and
                // remember it with the clip (works when it plays through the phone).
                val nowPlaying = SongIdentifier.currentlyPlaying(this)
                if (nowPlaying != null) {
                    writeClipMeta(savedFile, nowPlaying.title, nowPlaying.artist)
                }
                postMatchNotification(nowPlaying)
                BeatMatcherState.notifyClipsChanged()
            }
            updateForegroundNotification()
        }
    }

    private fun saveAudioSnippet(): File? = try {
        val timestamp = System.currentTimeMillis()
        val file = File(getExternalFilesDir(null), "MadeMeDance_snippet_$timestamp.mmd")
        audioBpmDetector.saveSnippet(file)
        BeatMatcherState.setSystemStatus("Snippet saved: ${file.name}")
        file
    } catch (e: IOException) {
        BeatMatcherState.setSystemStatus("Error saving snippet.")
        null
    }

    private fun vibrateOnMatch() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(200)
            }
        } catch (_: Exception) { /* no vibrator */ }
    }

    private fun stopWork() {
        sensorJob?.cancel(); sensorJob = null
        micWatchdogJob?.cancel(); micWatchdogJob = null
        notificationJob?.cancel(); notificationJob = null
        batteryJob?.cancel(); batteryJob = null
        sensitivityJob?.cancel(); sensitivityJob = null
        stopAudio()
        try { movementTracker.stop() } catch (_: Exception) {}
        releaseWakeLock()
        batterySamples.clear()
        batteryPenalty = 0f
        BeatMatcherState.setRunning(false)
        BeatMatcherState.setMovementBpm(null)
        BeatMatcherState.setBatteryDrainPerHour(null)
        BeatMatcherState.setPowerSaving(false)
        BeatMatcherState.setSystemStatus("Service stopped.")
    }

    override fun onDestroy() {
        stopWork()
        scope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MadeMeDance::BeatMatcher").apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L) // 4-hour timeout safety net
        }
    }

    private fun releaseWakeLock() {
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
        wakeLock = null
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                "Listening status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing notification while MadeMeDance is listening."
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MATCHES,
                "BPM matches",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Posted when your movement matches the music's beat."
            }
        )
    }

    private fun startForegroundWithNotification() {
        val notification = buildStatusNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID_STATUS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID_STATUS, notification)
        }
    }

    private fun updateForegroundNotification() {
        val nm = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        nm.notify(NOTIFICATION_ID_STATUS, buildStatusNotification())
    }

    private fun buildStatusNotification(): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPi = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, BeatMatcherActionReceiver::class.java)
                .setAction(BeatMatcherActionReceiver.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val movement = BeatMatcherState.movementBpm.value
        val audio = BeatMatcherState.audioBpm.value
        val drain = BeatMatcherState.batteryDrainPerHour.value
        val powerSaving = BeatMatcherState.powerSaving.value
        val base = if (BeatMatcherState.micActive.value) {
            "Listening for the song"
        } else {
            "Watching for dancing"
        }
        val title = if (powerSaving) "$base (power-saving)" else base
        val body = buildString {
            append(movement?.let { "You: ${"%.0f".format(it)} BPM" } ?: "You: -- BPM")
            append("   ")
            append(audio?.let { "Song: ${"%.0f".format(it)} BPM" } ?: "Song: -- BPM")
            if (drain != null) {
                append("   ~${"%.0f".format(drain)}%/hr")
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Stop", stopPi)
            .build()
    }

    private fun postMatchNotification(nowPlaying: IdentifyResult.NowPlaying?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val openClips = PendingIntent.getActivity(
            this, 100,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_CLIPS, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_MATCHES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openClips)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (nowPlaying != null) {
            // We already know the song — no need to offer identification.
            val song = listOfNotNull(nowPlaying.artist, nowPlaying.title).joinToString(" — ")
            builder.setContentTitle("You're dancing to $song")
                .setContentText("Saved — tap to view your clips.")
        } else {
            val identify = PendingIntent.getActivity(
                this, 101,
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_IDENTIFY, true)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.setContentTitle("You're dancing to something!")
                .setContentText("15 s snippet saved — tap Identify to find the song.")
                .addAction(0, "Identify", identify)
        }

        NotificationManagerCompat.from(this)
            .notify(NOTIFICATION_ID_MATCH_BASE + (matchCounter++), builder.build())
    }
}
