package com.hereliesaz.mademedance.ui

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hereliesaz.mademedance.data.ClipItem
import com.hereliesaz.mademedance.data.ClipRepository
import com.hereliesaz.mademedance.identify.IdentifyResult
import com.hereliesaz.mademedance.identify.SongIdentifier
import com.hereliesaz.mademedance.service.BeatMatcherState
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipListScreen(
    clipRepository: ClipRepository?,
    onAccept: (ClipItem) -> Unit = {},
    onReject: (ClipItem) -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val clips = remember { mutableStateListOf<ClipItem>() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh clip list whenever this screen becomes visible or the service signals a new clip
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            BeatMatcherState.clipsChanged.collectLatest {
                clips.clear()
                clipRepository?.getClips()?.let { clips.addAll(it) }
            }
        }
    }

    var showDialog by remember { mutableStateOf(false) }
    var selectedClip by remember { mutableStateOf<ClipItem?>(null) }
    var nowPlaying by remember { mutableStateOf<IdentifyResult.NowPlaying?>(null) }

    nowPlaying?.let { np ->
        NowPlayingDialog(
            nowPlaying = np,
            onSearch = {
                SongIdentifier.searchForKnownSong(context, np.artist, np.title)
                nowPlaying = null
            },
            onDismiss = { nowPlaying = null }
        )
    }

    val mediaPlayer = remember { MediaPlayer() }
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    if (showDialog && selectedClip != null) {
        val clip = selectedClip!!
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(clip.formattedDate) },
            text = {
                Column {
                    Text("Was this a real catch? Rating tunes how sensitive the detector is.")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        val file = clipRepository?.getClipFile(clip.name)
                        if (file != null) {
                            try {
                                mediaPlayer.reset()
                                mediaPlayer.setDataSource(file.absolutePath)
                                mediaPlayer.prepare()
                                mediaPlayer.start()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to play clip", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Text("Review (play clip)")
                    }
                    TextButton(onClick = {
                        showDialog = false
                        runIdentify(context) { nowPlaying = it }
                    }) {
                        Text("Identify song")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onAccept(clip)
                }) {
                    Text("Good catch")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                    onReject(clip)
                    clips.remove(clip)
                }) {
                    Text("False alarm")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recorded Clips") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (clips.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No clips recorded yet",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                items(clips, key = { it.name }) { clip ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedClip = clip
                                showDialog = true
                            }
                            .padding(8.dp)
                    ) {
                        Text(
                            text = clip.formattedDate,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "15s audio clip",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
