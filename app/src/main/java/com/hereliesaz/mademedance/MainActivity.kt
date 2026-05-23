package com.hereliesaz.mademedance

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hereliesaz.mademedance.ui.ClipListScreen
import com.hereliesaz.mademedance.ui.MainScreen
import com.hereliesaz.mademedance.ui.theme.MadeMeDanceTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    private val requestAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                viewModel.onPermissionGranted()
                viewModel.startService()
            }
        }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Proceed regardless — notification permission isn't strictly required
            requestAudioIfNeeded()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MadeMeDanceTheme {
                val vm: MainViewModel = viewModel()
                viewModel = vm

                val movement by vm.movementStatus.collectAsState()
                val audio by vm.audioStatus.collectAsState()
                val system by vm.systemStatus.collectAsState()
                val hasPermission by vm.hasAudioPermission.collectAsState()
                val movementBpm by vm.movementBpm.collectAsState()
                val audioBpm by vm.audioBpm.collectAsState()
                val isRunning by vm.isServiceRunning.collectAsState()
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            movementStatus = movement,
                            audioStatus = audio,
                            systemStatus = system,
                            hasAudioPermission = hasPermission,
                            movementBpm = movementBpm,
                            audioBpm = audioBpm,
                            isServiceRunning = isRunning,
                            onPermissionClick = { requestPermissions() },
                            onToggleService = {
                                if (isRunning) vm.stopService() else requestPermissions()
                            },
                            onClipListClick = { navController.navigate("clip_list") }
                        )
                    }
                    composable("clip_list") {
                        ClipListScreen(
                            clipRepository = vm.clipRepository,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.refreshPermissionState()
            // Rebind to service if it's running but we lost the binding
            if (viewModel.hasAudioPermission.value && !viewModel.isServiceRunning.value) {
                viewModel.startService()
            }
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestAudioIfNeeded()
        }
    }

    private fun requestAudioIfNeeded() {
        if (viewModel.hasAudioPermission.value) {
            viewModel.startService()
        } else {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
