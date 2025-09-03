package com.hereliesaz.mademedance.ui

import android.app.SearchManager
import android.content.Intent
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipListScreen() {
    val context = LocalContext.current
    val clips = remember {
        val dir = context.getExternalFilesDir(null)
        if (dir != null) {
            dir.listFiles { file -> file.name.endsWith(".mmd") }?.map { it.name } ?: emptyList()
        } else {
            emptyList()
        }
    }

    var showDialog by remember { mutableStateOf(false) }
    var selectedClip by remember { mutableStateOf<String?>(null) }

    val mediaPlayer = remember { MediaPlayer() }
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    if (showDialog && selectedClip != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(selectedClip!!) },
            text = { Text("What would you like to do with this clip?") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    Toast.makeText(context, "Tap the microphone and hum the tune!", Toast.LENGTH_LONG).show()
                    val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra(SearchManager.QUERY, "what is this song")
                    }
                    context.startActivity(intent)
                }) {
                    Text("Identify")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                    val file = File(context.getExternalFilesDir(null), selectedClip!!)
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(file.absolutePath)
                    mediaPlayer.prepare()
                    mediaPlayer.start()
                }) {
                    Text("Review")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Recorded Clips") })
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            items(clips) { clipName ->
                Text(
                    text = clipName,
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .clickable {
                            selectedClip = clipName
                            showDialog = true
                        }
                        .padding(8.dp)
                )
            }
        }
    }
}
