package com.hereliesaz.mademedance

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hereliesaz.mademedance.identify.IdentifyResult
import com.hereliesaz.mademedance.identify.SongIdentifier
import com.hereliesaz.mademedance.ui.ClipListScreen
import com.hereliesaz.mademedance.ui.MainScreen
import com.hereliesaz.mademedance.ui.NowPlayingDialog
import com.hereliesaz.mademedance.ui.SettingsScreen
import com.hereliesaz.mademedance.ui.runIdentify
import com.hereliesaz.mademedance.ui.theme.MadeMeDanceTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_CLIPS = "open_clips"
        const val EXTRA_IDENTIFY = "identify"
    }

    private lateinit var viewModel: MainViewModel
    private var pendingStartAfterPermissions = false

    // Bumped each time an "Identify" intent arrives (cold start or onNewIntent),
    // so the Compose tree can run the identification chain exactly once per tap.
    private val identifyTrigger = MutableStateFlow(0)

    private val requestAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.refreshPermissionState()
            if (granted) maybeRequestNotificationPermission()
            else pendingStartAfterPermissions = false
        }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            viewModel.refreshPermissionState()
            if (pendingStartAfterPermissions && viewModel.hasAudioPermission.value) {
                viewModel.startService()
            }
            pendingStartAfterPermissions = false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val openClipsOnLaunch = intent?.getBooleanExtra(EXTRA_OPEN_CLIPS, false) == true
        if (intent?.getBooleanExtra(EXTRA_IDENTIFY, false) == true) {
            identifyTrigger.value++
        }

        setContent {
            MadeMeDanceTheme {
                val vm: MainViewModel = viewModel()
                viewModel = vm

                val movement by vm.movementStatus.collectAsState()
                val audio by vm.audioStatus.collectAsState()
                val system by vm.systemStatus.collectAsState()
                val hasPermission by vm.hasAudioPermission.collectAsState()
                val isRunning by vm.isServiceRunning.collectAsState()
                val movementBpm by vm.movementBpm.collectAsState()
                val audioBpm by vm.audioBpm.collectAsState()
                val sensitivity by vm.sensitivity.collectAsState()
                val batteryDrain by vm.batteryDrainPerHour.collectAsState()
                val powerSaving by vm.powerSaving.collectAsState()
                val hasNowPlayingAccess by vm.hasNowPlayingAccess.collectAsState()
                val acrConfigured by vm.acrConfigured.collectAsState()
                val recognition by vm.recognition.collectAsState()
                val acrHost by vm.acrHost.collectAsState()
                val acrKey by vm.acrKey.collectAsState()
                val acrSecret by vm.acrSecret.collectAsState()
                val navController = rememberNavController()

                var navigatedToClips by remember { mutableStateOf(false) }
                LaunchedEffect(openClipsOnLaunch) {
                    if (openClipsOnLaunch && !navigatedToClips) {
                        navController.navigate("clip_list")
                        navigatedToClips = true
                    }
                }

                var nowPlaying by remember { mutableStateOf<IdentifyResult.NowPlaying?>(null) }
                val identify by identifyTrigger.collectAsState()
                LaunchedEffect(identify) {
                    if (identify > 0) {
                        runIdentify(this@MainActivity) { nowPlaying = it }
                    }
                }
                nowPlaying?.let { np ->
                    NowPlayingDialog(
                        nowPlaying = np,
                        onSearch = {
                            SongIdentifier.searchForKnownSong(this@MainActivity, np.artist, np.title)
                            nowPlaying = null
                        },
                        onDismiss = { nowPlaying = null }
                    )
                }

                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            movementStatus = movement,
                            audioStatus = audio,
                            systemStatus = system,
                            hasAudioPermission = hasPermission,
                            isServiceRunning = isRunning,
                            movementBpm = movementBpm,
                            audioBpm = audioBpm,
                            sensitivity = sensitivity,
                            batteryDrainPerHour = batteryDrain,
                            powerSaving = powerSaving,
                            hasNowPlayingAccess = hasNowPlayingAccess,
                            onSensitivityChange = { vm.setSensitivity(it) },
                            onEnableNowPlaying = { SongIdentifier.openNotificationAccessSettings(this@MainActivity) },
                            onStartClick = { requestPermissionsAndStart() },
                            onStopClick = { vm.stopService() },
                            onPermissionClick = { requestPermissionsAndStart() },
                            onClipListClick = { navController.navigate("clip_list") },
                            onSettingsClick = { navController.navigate("settings") }
                        )
                    }
                    composable("clip_list") {
                        ClipListScreen(
                            clipRepository = vm.clipRepository,
                            acrConfigured = acrConfigured,
                            recognition = recognition,
                            onAccept = { vm.acceptClip() },
                            onReject = { vm.rejectClip(it) },
                            onRecognize = { vm.recognizeClip(it) },
                            onClearRecognition = { vm.clearRecognition() },
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            acrHost = acrHost,
                            acrKey = acrKey,
                            acrSecret = acrSecret,
                            onSave = { h, k, s -> vm.setAcrCredentials(h, k, s) },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_IDENTIFY, false)) {
            identifyTrigger.value++
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.refreshPermissionState()
        }
    }

    private fun requestPermissionsAndStart() {
        if (!viewModel.hasAudioPermission.value) {
            pendingStartAfterPermissions = true
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!viewModel.hasNotificationPermission.value &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingStartAfterPermissions = true
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        viewModel.startService()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !viewModel.hasNotificationPermission.value) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (pendingStartAfterPermissions) {
            viewModel.startService()
            pendingStartAfterPermissions = false
        }
    }
}
