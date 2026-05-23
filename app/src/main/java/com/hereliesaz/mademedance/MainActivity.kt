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
import com.hereliesaz.mademedance.ui.ClipListScreen
import com.hereliesaz.mademedance.ui.MainScreen
import com.hereliesaz.mademedance.ui.theme.MadeMeDanceTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_CLIPS = "open_clips"
    }

    private lateinit var viewModel: MainViewModel
    private var pendingStartAfterPermissions = false

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
                val navController = rememberNavController()

                var navigatedToClips by remember { mutableStateOf(false) }
                LaunchedEffect(openClipsOnLaunch) {
                    if (openClipsOnLaunch && !navigatedToClips) {
                        navController.navigate("clip_list")
                        navigatedToClips = true
                    }
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
                            onSensitivityChange = { vm.setSensitivity(it) },
                            onStartClick = { requestPermissionsAndStart() },
                            onStopClick = { vm.stopService() },
                            onPermissionClick = { requestPermissionsAndStart() },
                            onClipListClick = { navController.navigate("clip_list") }
                        )
                    }
                    composable("clip_list") {
                        ClipListScreen(
                            clipRepository = vm.clipRepository,
                            onAccept = { vm.acceptClip() },
                            onReject = { vm.rejectClip(it) },
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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
