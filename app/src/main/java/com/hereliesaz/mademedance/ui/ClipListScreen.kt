package com.hereliesaz.mademedance.ui

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hereliesaz.mademedance.ClipRecognition
import com.hereliesaz.mademedance.data.ClipItem
import com.hereliesaz.mademedance.data.ClipRepository
import com.hereliesaz.mademedance.identify.IdentifyResult
import com.hereliesaz.mademedance.identify.SongIdentifier
import com.hereliesaz.mademedance.service.BeatMatcherState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val LOOPBACK_DURATION_MS = 30_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipListScreen(
    clipRepository: ClipRepository?,
    acrConfigured: Boolean = false,
    recognition: ClipRecognition = ClipRecognition.Idle,
    onAccept: (ClipItem) -> Unit = {},
    onReject: (ClipItem) -> Unit = {},
    onRecognize: (ClipItem) -> Unit = {},
    onClearRecognition: () -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val clips = remember { mutableStateListOf<ClipItem>() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

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

    val mediaPlayer = remember { MediaPlayer() }
    var loopbackJob by remember { mutableStateOf<Job?>(null) }

    fun stopLoopback() {
        loopbackJob?.cancel()
        loopbackJob = null
        try {
            if (mediaPlayer.isPlaying) mediaPlayer.stop()
            mediaPlayer.isLooping = false
        } catch (_: Exception) { /* ignore */ }
    }

    DisposableEffect(Unit) {
        onDispose {
            loopbackJob?.cancel()
            mediaPlayer.release()
        }
    }

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

    fun closeDialog() {
        showDialog = false
        onClearRecognition()
    }

    if (showDialog && selectedClip != null) {
        val clip = selectedClip!!
        // Prefer a just-recognized result, else any song stored with the clip.
        val done = recognition as? ClipRecognition.Done
        val songTitle = done?.title ?: clip.title
        val songArtist = done?.artist ?: clip.artist

        AlertDialog(
            onDismissRequest = { closeDialog() },
            title = { Text(clip.formattedDate) },
            text = {
                Column {
                    if (!songTitle.isNullOrBlank()) {
                        Text(
                            text = listOfNotNull(songArtist, songTitle).joinToString(" — "),
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(onClick = {
                            SongIdentifier.searchForKnownSong(context, songArtist, songTitle)
                        }) {
                            Text("Search the web")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text("Was this a real catch? Rating tunes how sensitive the detector is.")
                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = {
                        val file = clipRepository?.getClipFile(clip.name)
                        if (file != null) {
                            try {
                                stopLoopback()
                                mediaPlayer.reset()
                                mediaPlayer.setDataSource(file.absolutePath)
                                mediaPlayer.isLooping = false
                                mediaPlayer.prepare()
                                mediaPlayer.start()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to play clip", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Text("Review (play clip)")
                    }

                    if (acrConfigured) {
                        when (recognition) {
                            ClipRecognition.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Identifying this clip…")
                            }
                            ClipRecognition.NoMatch -> Text(
                                "No match found for this clip.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            is ClipRecognition.Error -> Text(
                                (recognition as ClipRecognition.Error).message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            else -> TextButton(onClick = { onRecognize(clip) }) {
                                Text("Identify this clip")
                            }
                        }
                    }

                    TextButton(onClick = {
                        val file = clipRepository?.getClipFile(clip.name)
                        if (file == null) {
                            Toast.makeText(context, "Clip not found", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        try {
                            mediaPlayer.reset()
                            mediaPlayer.setDataSource(file.absolutePath)
                            mediaPlayer.isLooping = true
                            mediaPlayer.prepare()
                            mediaPlayer.start()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to play clip", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        val label = SongIdentifier.launchAnyRecognizer(context)
                        if (label == null) {
                            stopLoopback()
                            Toast.makeText(
                                context,
                                "No song finder installed — grab Shazam, SoundHound, or the Google app.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                context, "Looping your clip — tap Listen in $label", Toast.LENGTH_LONG
                            ).show()
                            loopbackJob?.cancel()
                            loopbackJob = scope.launch {
                                delay(LOOPBACK_DURATION_MS)
                                stopLoopback()
                            }
                            closeDialog()
                        }
                    }) {
                        Text("Play to a song finder")
                    }

                    TextButton(onClick = {
                        closeDialog()
                        runIdentify(context) { nowPlaying = it }
                    }) {
                        Text("What's playing now")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    closeDialog()
                    onAccept(clip)
                }) {
                    Text("Good catch")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    closeDialog()
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
                                onClearRecognition()
                                selectedClip = clip
                                showDialog = true
                            }
                            .padding(8.dp)
                    ) {
                        Text(
                            text = if (clip.knownSong) {
                                listOfNotNull(clip.artist, clip.title).joinToString(" — ")
                            } else {
                                clip.formattedDate
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = if (clip.knownSong) clip.formattedDate else "15s audio clip",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
